package com.example.domain

import com.example.data.model.MatchEntity
import com.example.data.model.MatchStage
import com.example.data.model.MatchStatus
import com.example.data.model.MatchType
import com.example.data.model.TeamEntity
import com.example.data.model.TournamentEntity
import com.example.data.model.TournamentFormat

object TournamentScheduler {

    /**
     * Fixtures created when a tournament is set up.
     * League formats: a full round robin, each pair meeting [TournamentEntity.encountersPerOpponent] times.
     * Knockout: the first round, with byes for the top seeds when the team count is not a power of two.
     */
    fun generateInitialFixtures(
        tournament: TournamentEntity,
        teams: List<TeamEntity>,
        defaultGroundId: Long?
    ): List<MatchEntity> {
        if (teams.size < 2) return emptyList()
        return when (tournament.format) {
            TournamentFormat.ROUND_ROBIN, TournamentFormat.LEAGUE_AND_KNOCKOUT -> generateRoundRobinMatches(
                tournament = tournament,
                teams = teams,
                encounters = tournament.encountersPerOpponent.coerceIn(1, 4),
                groundId = defaultGroundId
            )
            TournamentFormat.KNOCKOUT -> knockoutFirstRound(tournament, teams, defaultGroundId)
        }
    }

    /** Circle method. Odd team counts get a rotating bye. */
    private fun generateRoundRobinMatches(
        tournament: TournamentEntity,
        teams: List<TeamEntity>,
        encounters: Int,
        groundId: Long?
    ): List<MatchEntity> {
        val matches = mutableListOf<MatchEntity>()
        val roster = teams.toMutableList<TeamEntity?>()
        if (roster.size % 2 != 0) roster.add(null) // bye
        val n = roster.size
        var roundIndex = 1

        for (cycle in 0 until encounters) {
            val current = roster.toMutableList()
            for (round in 0 until n - 1) {
                var matchNumber = 1
                for (i in 0 until n / 2) {
                    val a = current[i]
                    val b = current[n - 1 - i]
                    if (a != null && b != null) {
                        val home = if (cycle % 2 == 0) a else b
                        val away = if (cycle % 2 == 0) b else a
                        matches += fixture(tournament, home, away, MatchStage.LEAGUE, roundIndex, matchNumber++, groundId)
                    }
                }
                roundIndex++
                val last = current.removeAt(current.size - 1)
                current.add(1, last)
            }
        }
        return matches
    }

    fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p *= 2
        return p
    }

    /** Stage label for a knockout round that starts with [entrants] teams. */
    fun stageForRound(entrants: Int, matchNumber: Int): MatchStage = when {
        entrants <= 2 -> MatchStage.FINAL
        entrants <= 4 -> if (matchNumber == 1) MatchStage.SEMI_FINAL_1 else MatchStage.SEMI_FINAL_2
        entrants <= 8 -> MatchStage.QUARTER_FINAL
        else -> MatchStage.KNOCKOUT_ROUND
    }

    /** Pairs top seed with bottom seed: (0 v n-1), (1 v n-2)... */
    fun seededPairs(entrants: List<Long>): List<Pair<Long, Long>> {
        val n = entrants.size
        return (0 until n / 2).map { entrants[it] to entrants[n - 1 - it] }
    }

    fun knockoutFirstRound(
        tournament: TournamentEntity,
        teams: List<TeamEntity>,
        groundId: Long?,
        roundIndex: Int = 1
    ): List<MatchEntity> {
        if (teams.size < 2) return emptyList()
        val size = nextPowerOfTwo(teams.size)
        val byes = size - teams.size
        val playing = teams.drop(byes)
        val byId = teams.associateBy { it.id }
        return seededPairs(playing.map { it.id }).mapIndexed { i, (a, b) ->
            fixture(tournament, byId.getValue(a), byId.getValue(b), stageForRound(size, i + 1), roundIndex, i + 1, groundId)
        }
    }

    fun knockoutRound(
        tournament: TournamentEntity,
        entrants: List<Long>,
        teamsById: Map<Long, TeamEntity>,
        roundIndex: Int,
        groundId: Long?
    ): List<MatchEntity> = seededPairs(entrants).mapIndexedNotNull { i, (a, b) ->
        val ta = teamsById[a]
        val tb = teamsById[b]
        if (ta == null || tb == null) null
        else fixture(tournament, ta, tb, stageForRound(entrants.size, i + 1), roundIndex, i + 1, groundId)
    }

    /** SF1: 1st v 4th, SF2: 2nd v 3rd. */
    fun createSemiFinalsFromLeague(
        tournament: TournamentEntity,
        top4Teams: List<TeamEntity>,
        defaultGroundId: Long?
    ): List<MatchEntity> {
        if (top4Teams.size < 4) return emptyList()
        return listOf(
            fixture(tournament, top4Teams[0], top4Teams[3], MatchStage.SEMI_FINAL_1, 1000, 1, defaultGroundId),
            fixture(tournament, top4Teams[1], top4Teams[2], MatchStage.SEMI_FINAL_2, 1000, 2, defaultGroundId)
        )
    }

    fun createFinalFromSemiFinals(
        tournament: TournamentEntity,
        winnerSf1TeamId: Long,
        winnerSf2TeamId: Long,
        defaultGroundId: Long?
    ): MatchEntity = MatchEntity(
        tournamentId = tournament.id,
        stage = MatchStage.FINAL,
        roundIndex = 1001,
        matchNumberInRound = 1,
        groundId = defaultGroundId,
        team1Id = winnerSf1TeamId,
        team2Id = winnerSf2TeamId,
        matchType = MatchType.STANDARD_LIMITED_OVERS,
        totalOversPerInnings = tournament.oversPerInnings,
        ballsPerOver = tournament.ballsPerOver,
        status = MatchStatus.SCHEDULED
    )

    private fun fixture(
        tournament: TournamentEntity,
        home: TeamEntity,
        away: TeamEntity,
        stage: MatchStage,
        roundIndex: Int,
        matchNumber: Int,
        groundId: Long?
    ) = MatchEntity(
        tournamentId = tournament.id,
        stage = stage,
        roundIndex = roundIndex,
        matchNumberInRound = matchNumber,
        groundId = groundId,
        team1Id = home.id,
        team2Id = away.id,
        matchType = if (home.isSoloTeam && away.isSoloTeam) MatchType.SOLO_DUEL else MatchType.STANDARD_LIMITED_OVERS,
        totalOversPerInnings = tournament.oversPerInnings,
        ballsPerOver = tournament.ballsPerOver,
        soloPlayer1Wickets = home.defaultSoloWickets,
        soloPlayer2Wickets = away.defaultSoloWickets,
        status = MatchStatus.SCHEDULED
    )
}
