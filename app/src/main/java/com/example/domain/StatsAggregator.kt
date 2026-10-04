package com.example.domain

import com.example.data.model.BallEntity
import com.example.data.model.DismissalType
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchStatus
import com.example.data.model.PlayerEntity
import com.example.data.model.TeamEntity

data class PlayerCareerStats(
    val player: PlayerEntity,
    val matches: Int = 0,
    val innings: Int = 0,
    val runs: Int = 0,
    val ballsFaced: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val notOuts: Int = 0,
    val highestScore: Int = 0,
    val highestScoreNotOut: Boolean = false,
    val fifties: Int = 0,
    val hundreds: Int = 0,
    val ducks: Int = 0,
    val strikeRate: Double = 0.0,
    val battingAverage: Double = 0.0,
    val bowlingInnings: Int = 0,
    val ballsBowled: Int = 0,
    val maidens: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0,
    val dotBalls: Int = 0,
    val bowlingAverage: Double = 0.0,
    val economyRate: Double = 0.0,
    val bestBowlingWickets: Int = 0,
    val bestBowlingRuns: Int = 0,
    val threeWicketHauls: Int = 0,
    val fiveWicketHauls: Int = 0,
    val catches: Int = 0,
    val runOuts: Int = 0,
    val stumpings: Int = 0,
    val playerOfMatchAwards: Int = 0
) {
    val oversBowledDisplay: String get() = CricketRules.oversText(ballsBowled, 6)
    val bestBowlingDisplay: String get() = if (bestBowlingWickets > 0) "$bestBowlingWickets/$bestBowlingRuns" else "-"
    val highestScoreDisplay: String get() = if (innings == 0) "-" else "$highestScore${if (highestScoreNotOut) "*" else ""}"
    val dismissals: Int get() = innings - notOuts
    val hasBatted: Boolean get() = innings > 0
    val hasBowled: Boolean get() = ballsBowled > 0
    val hasAnyRecord: Boolean get() = matches > 0
}

data class TeamStatsSummary(
    val team: TeamEntity,
    val matchesPlayed: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    val tied: Int = 0,
    val noResult: Int = 0,
    val winPercentage: Double = 0.0,
    val highestTotal: Int = 0,
    val lowestTotal: Int? = null,
    val totalRunsScored: Int = 0,
    val totalWicketsTaken: Int = 0,
    val titles: Int = 0
)

data class HeadToHead(
    val opponent: TeamEntity,
    val played: Int,
    val won: Int,
    val lost: Int,
    val other: Int
)

object StatsAggregator {

    fun aggregatePlayerStats(
        players: List<PlayerEntity>,
        matches: List<MatchEntity>,
        innings: List<InningsEntity>,
        balls: List<BallEntity>
    ): List<PlayerCareerStats> {
        val matchById = matches.associateBy { it.id }
        val ballsByInnings = balls.groupBy { it.inningsId }

        class Acc {
            val matchIds = HashSet<Long>()
            var innings = 0; var runs = 0; var balls = 0; var fours = 0; var sixes = 0; var notOuts = 0
            var hs = 0; var hsNotOut = false; var fifties = 0; var hundreds = 0; var ducks = 0
            var bowlInnings = 0; var bowlBalls = 0; var maidens = 0; var conceded = 0; var wickets = 0; var dots = 0
            var bestW = 0; var bestR = 0; var threeW = 0; var fiveW = 0
            var catches = 0; var runOuts = 0; var stumpings = 0; var potm = 0
        }

        val acc = HashMap<Long, Acc>()
        fun a(id: Long) = acc.getOrPut(id) { Acc() }

        for (inn in innings) {
            val match = matchById[inn.matchId] ?: continue
            val innBalls = ballsByInnings[inn.id].orEmpty()
            if (innBalls.isEmpty()) continue
            val card = ScorecardBuilder.build(inn, innBalls, match.ballsPerOver)

            for (b in card.batters) {
                val x = a(b.playerId)
                x.matchIds += match.id
                x.innings++
                x.runs += b.runs
                x.balls += b.balls
                x.fours += b.fours
                x.sixes += b.sixes
                if (!b.isOut) x.notOuts++
                if (b.runs > x.hs || (b.runs == x.hs && !b.isOut)) {
                    x.hs = b.runs
                    x.hsNotOut = !b.isOut
                }
                if (b.runs >= 100) x.hundreds++ else if (b.runs >= 50) x.fifties++
                if (b.isOut && b.runs == 0) x.ducks++
            }
            for (bw in card.bowlers) {
                val x = a(bw.playerId)
                x.matchIds += match.id
                x.bowlInnings++
                // Career overs are shown in 6-ball overs; convert other formats.
                x.bowlBalls += if (match.ballsPerOver == 6) bw.legalBalls else (bw.legalBalls * 6.0 / match.ballsPerOver.coerceAtLeast(1)).toInt()
                x.maidens += bw.maidens
                x.conceded += bw.runs
                x.wickets += bw.wickets
                x.dots += bw.dots
                if (bw.wickets >= 5) x.fiveW++ else if (bw.wickets >= 3) x.threeW++
                if (bw.wickets > x.bestW || (bw.wickets == x.bestW && bw.wickets > 0 && bw.runs < x.bestR)) {
                    x.bestW = bw.wickets
                    x.bestR = bw.runs
                }
            }
            for (ball in innBalls) {
                if (!ball.isWicket) continue
                val fielder = ball.fielderPlayerId ?: continue
                val x = a(fielder)
                x.matchIds += match.id
                when (ball.dismissalType) {
                    DismissalType.CAUGHT -> x.catches++
                    DismissalType.RUN_OUT -> x.runOuts++
                    DismissalType.STUMPED -> x.stumpings++
                    else -> {}
                }
            }
        }
        for (m in matches) {
            val potm = m.potmPlayerId ?: continue
            if (m.status.isFinished) a(potm).potm++
        }

        return players.map { p ->
            val x = acc[p.id] ?: return@map PlayerCareerStats(player = p)
            val dismissals = x.innings - x.notOuts
            PlayerCareerStats(
                player = p,
                matches = x.matchIds.size,
                innings = x.innings,
                runs = x.runs,
                ballsFaced = x.balls,
                fours = x.fours,
                sixes = x.sixes,
                notOuts = x.notOuts,
                highestScore = x.hs,
                highestScoreNotOut = x.hsNotOut,
                fifties = x.fifties,
                hundreds = x.hundreds,
                ducks = x.ducks,
                strikeRate = if (x.balls > 0) x.runs * 100.0 / x.balls else 0.0,
                battingAverage = when {
                    dismissals > 0 -> x.runs.toDouble() / dismissals
                    x.innings > 0 -> x.runs.toDouble()
                    else -> 0.0
                },
                bowlingInnings = x.bowlInnings,
                ballsBowled = x.bowlBalls,
                maidens = x.maidens,
                runsConceded = x.conceded,
                wickets = x.wickets,
                dotBalls = x.dots,
                bowlingAverage = if (x.wickets > 0) x.conceded.toDouble() / x.wickets else 0.0,
                economyRate = CricketRules.runRate(x.conceded, x.bowlBalls, 6),
                bestBowlingWickets = x.bestW,
                bestBowlingRuns = if (x.bestW > 0) x.bestR else 0,
                threeWicketHauls = x.threeW,
                fiveWicketHauls = x.fiveW,
                catches = x.catches,
                runOuts = x.runOuts,
                stumpings = x.stumpings,
                playerOfMatchAwards = x.potm
            )
        }
    }

    fun aggregateTeamStats(
        teams: List<TeamEntity>,
        matches: List<MatchEntity>,
        innings: List<InningsEntity>,
        titlesByTeam: Map<Long, Int> = emptyMap()
    ): List<TeamStatsSummary> {
        val inningsByMatch = innings.groupBy { it.matchId }
        return teams.map { team ->
            val finished = matches.filter { (it.team1Id == team.id || it.team2Id == team.id) && it.status.isFinished }
            val noResult = finished.count { it.status == MatchStatus.ABANDONED_RAIN }
            val decidedPool = finished.filter { it.status != MatchStatus.ABANDONED_RAIN }
            val won = decidedPool.count { it.winnerTeamId == team.id }
            val lost = decidedPool.count { it.winnerTeamId != null && it.winnerTeamId != team.id }
            val tied = decidedPool.count { it.winnerTeamId == null }

            var highest = 0
            var lowest: Int? = null
            var runs = 0
            var wickets = 0
            for (m in finished) {
                for (inn in inningsByMatch[m.id].orEmpty()) {
                    if (inn.battingTeamId == team.id && inn.ballsBowled > 0) {
                        val total = inn.runs + inn.penaltyRuns
                        runs += total
                        if (total > highest) highest = total
                        val allOutOrDone = inn.isCompleted || inn.wickets >= inn.maxWickets
                        if (allOutOrDone && (lowest == null || total < lowest!!)) lowest = total
                    }
                    if (inn.bowlingTeamId == team.id) wickets += inn.wickets
                }
            }
            TeamStatsSummary(
                team = team,
                matchesPlayed = finished.size,
                won = won,
                lost = lost,
                tied = tied,
                noResult = noResult,
                winPercentage = if (decidedPool.isNotEmpty()) won * 100.0 / decidedPool.size else 0.0,
                highestTotal = highest,
                lowestTotal = lowest,
                totalRunsScored = runs,
                totalWicketsTaken = wickets,
                titles = titlesByTeam[team.id] ?: 0
            )
        }
    }

    fun headToHead(team: TeamEntity, teams: List<TeamEntity>, matches: List<MatchEntity>): List<HeadToHead> {
        val byId = teams.associateBy { it.id }
        return matches
            .filter { it.status.isFinished && (it.team1Id == team.id || it.team2Id == team.id) }
            .groupBy { if (it.team1Id == team.id) it.team2Id else it.team1Id }
            .mapNotNull { (oppId, list) ->
                val opp = byId[oppId] ?: return@mapNotNull null
                HeadToHead(
                    opponent = opp,
                    played = list.size,
                    won = list.count { it.winnerTeamId == team.id },
                    lost = list.count { it.winnerTeamId == oppId },
                    other = list.count { it.winnerTeamId == null }
                )
            }
            .sortedByDescending { it.played }
    }
}
