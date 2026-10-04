package com.example.domain

import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchStatus
import com.example.data.model.TeamEntity
import java.util.Locale

data class PointsTableRow(
    val team: TeamEntity,
    val played: Int,
    val won: Int,
    val lost: Int,
    val tied: Int,
    val noResult: Int,
    val points: Int,
    val runsScored: Int,
    val oversFacedDisplay: String,
    val runsConceded: Int,
    val oversBowledDisplay: String,
    val netRunRate: Double,
    /** Most recent results first: 'W', 'L', 'T' or 'N'. */
    val form: List<Char> = emptyList()
) {
    val nrrFormatted: String
        get() {
            val sign = if (netRunRate > 0.0005) "+" else ""
            return String.format(Locale.US, "$sign%.3f", netRunRate)
        }
}

object NrrEngine {

    const val POINTS_FOR_WIN = 2
    const val POINTS_FOR_TIE_OR_NO_RESULT = 1

    /**
     * Points table for the given matches (pass only league-stage matches).
     * Win 2, tie/draw/no result 1. Awarded results count for points but not for net run rate.
     * NRR: a side bowled out is charged its full quota of overs.
     */
    fun calculateStandings(
        teams: List<TeamEntity>,
        matches: List<MatchEntity>,
        inningsMap: Map<Long, List<InningsEntity>>
    ): List<PointsTableRow> {

        class Acc(val team: TeamEntity) {
            var played = 0
            var won = 0
            var lost = 0
            var tied = 0
            var noResult = 0
            var points = 0
            var runsFor = 0
            var oversFaced = 0.0
            var ballsFaced = 0
            var runsAgainst = 0
            var oversBowled = 0.0
            var ballsBowled = 0
            val form = mutableListOf<Pair<Long, Char>>()
        }

        val acc = LinkedHashMap<Long, Acc>()
        teams.forEach { acc[it.id] = Acc(it) }
        var displayBpo = 6

        for (match in matches) {
            val a = acc[match.team1Id] ?: continue
            val b = acc[match.team2Id] ?: continue
            if (!match.status.isFinished) continue
            val order = match.createdAt * 1000 + (match.id % 1000)

            a.played++
            b.played++
            when {
                match.status == MatchStatus.ABANDONED_RAIN -> {
                    a.noResult++; b.noResult++
                    a.points += POINTS_FOR_TIE_OR_NO_RESULT; b.points += POINTS_FOR_TIE_OR_NO_RESULT
                    a.form += order to 'N'; b.form += order to 'N'
                }
                match.winnerTeamId == match.team1Id -> {
                    a.won++; b.lost++; a.points += POINTS_FOR_WIN
                    a.form += order to 'W'; b.form += order to 'L'
                }
                match.winnerTeamId == match.team2Id -> {
                    b.won++; a.lost++; b.points += POINTS_FOR_WIN
                    b.form += order to 'W'; a.form += order to 'L'
                }
                else -> {
                    a.tied++; b.tied++
                    a.points += POINTS_FOR_TIE_OR_NO_RESULT; b.points += POINTS_FOR_TIE_OR_NO_RESULT
                    a.form += order to 'T'; b.form += order to 'T'
                }
            }

            if (match.status != MatchStatus.COMPLETED) continue
            val bpo = match.ballsPerOver.coerceAtLeast(1)
            displayBpo = bpo
            val quota = match.totalOversPerInnings * bpo
            for (inn in inningsMap[match.id].orEmpty()) {
                val allOut = inn.wickets >= inn.maxWickets
                val balls = if (allOut) quota else inn.ballsBowled.coerceAtMost(quota)
                val overs = balls / bpo.toDouble()
                val runs = inn.runs + inn.penaltyRuns
                acc[inn.battingTeamId]?.let { it.runsFor += runs; it.oversFaced += overs; it.ballsFaced += balls }
                acc[inn.bowlingTeamId]?.let { it.runsAgainst += runs; it.oversBowled += overs; it.ballsBowled += balls }
            }
        }

        val rows = acc.values.map { x ->
            val forRate = if (x.oversFaced > 0) x.runsFor / x.oversFaced else 0.0
            val againstRate = if (x.oversBowled > 0) x.runsAgainst / x.oversBowled else 0.0
            PointsTableRow(
                team = x.team,
                played = x.played,
                won = x.won,
                lost = x.lost,
                tied = x.tied,
                noResult = x.noResult,
                points = x.points,
                runsScored = x.runsFor,
                oversFacedDisplay = CricketRules.oversText(x.ballsFaced, displayBpo),
                runsConceded = x.runsAgainst,
                oversBowledDisplay = CricketRules.oversText(x.ballsBowled, displayBpo),
                netRunRate = forRate - againstRate,
                form = x.form.sortedByDescending { it.first }.take(5).map { it.second }
            )
        }

        return rows.sortedWith(
            compareByDescending<PointsTableRow> { it.points }
                .thenByDescending { it.netRunRate }
                .thenByDescending { it.won }
                .thenByDescending { it.runsScored }
                .thenBy { it.team.name.lowercase() }
        )
    }
}
