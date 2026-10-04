package com.example.domain

import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchStage
import com.example.data.model.MatchStatus
import com.example.data.model.TeamEntity
import com.example.data.model.TournamentEntity
import com.example.data.model.TournamentFormat
import com.example.data.model.TournamentStatus

/** What has to change in the database to keep a tournament's bracket and status correct. */
data class ProgressionPlan(
    val matchesToInsert: List<MatchEntity>,
    val matchIdsToDelete: List<Long>,
    val status: TournamentStatus,
    val championTeamId: Long?
) {
    val changesFixtures: Boolean get() = matchesToInsert.isNotEmpty() || matchIdsToDelete.isNotEmpty()
}

/**
 * Self-healing tournament progression. From the current fixtures and results it works out:
 *  - when the league is done, which teams qualify for the playoffs (top 4, or top 2 for small leagues);
 *  - when a knockout round is done, the next round (top seeds get byes when the count is not a power of two);
 *  - unplayed knockout fixtures that are no longer right because an earlier result changed
 *    (a match was reset, reopened or deleted) are removed and recreated;
 *  - the tournament status and champion.
 */
object TournamentProgression {

    /** Pairings for a knockout round, with byes for the top seeds. Returns (pairs, teams with a bye). */
    fun bracket(entrants: List<Long>): Pair<List<Pair<Long, Long>>, List<Long>> {
        if (entrants.size < 2) return Pair(emptyList(), entrants)
        val size = TournamentScheduler.nextPowerOfTwo(entrants.size)
        val byeCount = size - entrants.size
        val byes = entrants.take(byeCount)
        return Pair(TournamentScheduler.seededPairs(entrants.drop(byeCount)), byes)
    }

    fun plan(
        tournament: TournamentEntity,
        teams: List<TeamEntity>,
        matches: List<MatchEntity>,
        inningsByMatch: Map<Long, List<InningsEntity>>
    ): ProgressionPlan {
        val teamsById = teams.associateBy { it.id }
        fun untouched(m: MatchEntity) = m.status == MatchStatus.SCHEDULED && inningsByMatch[m.id].isNullOrEmpty()

        val league = matches.filter { it.stage == MatchStage.LEAGUE }
        val leagueDone = league.isNotEmpty() && league.all { it.status.isFinished }
        val standings = NrrEngine.calculateStandings(teams, league, inningsByMatch)
        val groundId = matches.firstOrNull { it.groundId != null }?.groundId

        val rounds: List<List<MatchEntity>> = matches
            .filter { it.stage.isKnockout }
            .groupBy { it.roundIndex }
            .toSortedMap()
            .values
            .map { r -> r.sortedBy { it.matchNumberInRound } }

        val inserts = mutableListOf<MatchEntity>()
        val deletes = mutableListOf<Long>()
        var champion: Long? = null

        fun winnersOf(round: List<MatchEntity>): List<Long>? =
            if (round.all { it.status.isFinished && it.winnerTeamId != null }) round.map { it.winnerTeamId!! } else null

        fun roundIndexFor(i: Int): Int = when {
            i > 0 && rounds.size >= i -> rounds[i - 1].first().roundIndex + 1
            tournament.format == TournamentFormat.KNOCKOUT -> 1
            else -> (league.maxOfOrNull { it.roundIndex } ?: 0) + 1
        }

        fun dropUntouchedFrom(i: Int) {
            for (r in i until rounds.size) {
                val round = rounds[r]
                if (round.all { untouched(it) }) deletes += round.map { it.id }
            }
        }

        fun createRound(entrants: List<Long>, roundIndex: Int): List<MatchEntity> {
            val (pairs, _) = bracket(entrants)
            val size = TournamentScheduler.nextPowerOfTwo(entrants.size)
            return pairs.mapIndexedNotNull { n, (a, b) ->
                val ta = teamsById[a]
                val tb = teamsById[b]
                if (ta == null || tb == null) null
                else TournamentScheduler.knockoutRound(tournament, listOf(a, b), teamsById, roundIndex, groundId)
                    .firstOrNull()
                    ?.copy(stage = TournamentScheduler.stageForRound(size, n + 1), matchNumberInRound = n + 1)
            }
        }

        val initialEntrants: List<Long>? = when (tournament.format) {
            TournamentFormat.ROUND_ROBIN -> null
            TournamentFormat.KNOCKOUT -> teams.map { it.id }
            TournamentFormat.LEAGUE_AND_KNOCKOUT -> {
                val qualifiers = when {
                    teams.size >= 4 -> 4
                    teams.size >= 2 -> 2
                    else -> 0
                }
                if (leagueDone && qualifiers > 0) standings.take(qualifiers).map { it.team.id } else null
            }
        }

        if (tournament.format == TournamentFormat.ROUND_ROBIN) {
            if (leagueDone && standings.isNotEmpty()) champion = standings.first().team.id
        } else {
            var entrants = initialEntrants
            var i = 0
            while (true) {
                val current = entrants
                if (current != null && current.size == 1) {
                    champion = current[0]
                    dropUntouchedFrom(i)
                    break
                }
                val existing = rounds.getOrNull(i)
                val expected = current?.let { bracket(it).first.map { p -> setOf(p.first, p.second) }.toSet() }
                if (existing == null) {
                    if (current != null && current.size >= 2) inserts += createRound(current, roundIndexFor(i))
                    break
                }
                val existingPairs = existing.map { setOf(it.team1Id, it.team2Id) }.toSet()
                if (existingPairs != expected && existing.all { untouched(it) }) {
                    // Out of date and not started: rebuild this round (and drop anything after it).
                    dropUntouchedFrom(i)
                    if (current != null && current.size >= 2) {
                        inserts += createRound(current, if (i > 0) existing.first().roundIndex else roundIndexFor(i))
                    }
                    break
                }
                val winners = winnersOf(existing)
                entrants = when {
                    winners == null -> null
                    current == null -> winners
                    else -> {
                        val playing = existing.flatMap { listOf(it.team1Id, it.team2Id) }.toSet()
                        current.filter { it !in playing } + winners
                    }
                }
                i++
            }
        }

        val anyStarted = matches.any { it.status != MatchStatus.SCHEDULED || !inningsByMatch[it.id].isNullOrEmpty() }
        val status = when {
            champion != null -> TournamentStatus.COMPLETED
            anyStarted -> TournamentStatus.ONGOING
            else -> TournamentStatus.UPCOMING
        }
        return ProgressionPlan(inserts, deletes.distinct(), status, champion)
    }
}
