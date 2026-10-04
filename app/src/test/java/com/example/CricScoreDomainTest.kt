package com.example

import com.example.data.model.BallEntity
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchStage
import com.example.data.model.MatchStatus
import com.example.data.model.MatchType
import com.example.data.model.PlayerEntity
import com.example.data.model.PlayerRole
import com.example.data.model.TeamEntity
import com.example.data.model.TournamentEntity
import com.example.data.model.TournamentFormat
import com.example.domain.CricketScoringEngine
import com.example.domain.NrrEngine
import com.example.domain.PlayerOfTheMatchEngine
import com.example.domain.TournamentScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CricScoreDomainTest {

    @Test
    fun testTournamentRoundRobinScheduler() {
        val teams = listOf(
            TeamEntity(id = 1, name = "Team Alpha", shortName = "ALP"),
            TeamEntity(id = 2, name = "Team Beta", shortName = "BET"),
            TeamEntity(id = 3, name = "Team Gamma", shortName = "GAM")
        )

        val tourney = TournamentEntity(
            id = 10,
            name = "Super Cup",
            format = TournamentFormat.ROUND_ROBIN,
            encountersPerOpponent = 2,
            oversPerInnings = 10
        )

        val fixtures = TournamentScheduler.generateInitialFixtures(tourney, teams, defaultGroundId = null)

        // For 3 teams (treated as 4 with BYE): 3 rounds * 2 encounters = 6 rounds total.
        // Each pair (A-B, B-C, A-C) plays 2 matches = 6 matches total!
        assertEquals(6, fixtures.size)
        assertTrue(fixtures.all { it.tournamentId == 10L })
    }

    @Test
    fun testTop4SemiFinalKnockoutProgression() {
        val top4 = listOf(
            TeamEntity(id = 1, name = "1st Place", shortName = "T1"),
            TeamEntity(id = 2, name = "2nd Place", shortName = "T2"),
            TeamEntity(id = 3, name = "3rd Place", shortName = "T3"),
            TeamEntity(id = 4, name = "4th Place", shortName = "T4")
        )

        val tourney = TournamentEntity(
            id = 20,
            name = "Premier League",
            format = TournamentFormat.LEAGUE_AND_KNOCKOUT,
            oversPerInnings = 10
        )

        val sfMatches = TournamentScheduler.createSemiFinalsFromLeague(tourney, top4, null)
        assertEquals(2, sfMatches.size)

        // SF1: 1st vs 4th
        val sf1 = sfMatches.find { it.stage == MatchStage.SEMI_FINAL_1 }
        assertNotNull(sf1)
        assertEquals(1L, sf1!!.team1Id)
        assertEquals(4L, sf1.team2Id)

        // SF2: 2nd vs 3rd
        val sf2 = sfMatches.find { it.stage == MatchStage.SEMI_FINAL_2 }
        assertNotNull(sf2)
        assertEquals(2L, sf2!!.team1Id)
        assertEquals(3L, sf2.team2Id)

        // Final
        val finalMatch = TournamentScheduler.createFinalFromSemiFinals(tourney, sf1.team1Id, sf2.team2Id, null)
        assertEquals(MatchStage.FINAL, finalMatch.stage)
        assertEquals(1L, finalMatch.team1Id)
        assertEquals(3L, finalMatch.team2Id)
    }

    @Test
    fun testPointsTableWithRainAbandonment() {
        val teamA = TeamEntity(id = 1, name = "Team A", shortName = "TMA")
        val teamB = TeamEntity(id = 2, name = "Team B", shortName = "TMB")

        val match = MatchEntity(
            id = 101,
            team1Id = 1,
            team2Id = 2,
            status = MatchStatus.ABANDONED_RAIN
        )

        val standings = NrrEngine.calculateStandings(
            teams = listOf(teamA, teamB),
            matches = listOf(match),
            inningsMap = emptyMap()
        )

        assertEquals(2, standings.size)
        // Both teams get 1 point each for rain abandonment
        assertEquals(1, standings[0].points)
        assertEquals(1, standings[1].points)
        assertEquals(1, standings[0].noResult)
        assertEquals(1, standings[1].noResult)
    }

    @Test
    fun testPlayerOfTheMatchCalculation() {
        val p1 = PlayerEntity(id = 100, name = "Star Batter", role = PlayerRole.BATSMAN)
        val p2 = PlayerEntity(id = 200, name = "Star Bowler", role = PlayerRole.BOWLER)

        val balls = listOf(
            BallEntity(
                id = 1, matchId = 1, inningsId = 1, overIndex = 0, ballInOver = 1, legalBallNumber = 1,
                strikerPlayerId = 100, nonStrikerPlayerId = null, bowlerPlayerId = 200,
                runsOffBat = 6
            ),
            BallEntity(
                id = 2, matchId = 1, inningsId = 1, overIndex = 0, ballInOver = 2, legalBallNumber = 2,
                strikerPlayerId = 100, nonStrikerPlayerId = null, bowlerPlayerId = 200,
                runsOffBat = 4
            ),
            BallEntity(
                id = 3, matchId = 1, inningsId = 1, overIndex = 0, ballInOver = 3, legalBallNumber = 3,
                strikerPlayerId = 100, nonStrikerPlayerId = null, bowlerPlayerId = 200,
                runsOffBat = 0, isWicket = true, dismissalType = DismissalType.BOWLED, outPlayerId = 100
            )
        )

        val potm = PlayerOfTheMatchEngine.determinePlayerOfTheMatch(balls)
        assertNotNull(potm)
    }

    @Test
    fun testMiniTestLeadTrailCalculation() {
        val inn1 = InningsEntity(id = 1, matchId = 50, inningsIndex = 1, battingTeamId = 1, bowlingTeamId = 2, runs = 120, wickets = 5, ballsBowled = 60, isCompleted = true)
        val inn2 = InningsEntity(id = 2, matchId = 50, inningsIndex = 2, battingTeamId = 2, bowlingTeamId = 1, runs = 90, wickets = 10, ballsBowled = 45, isCompleted = true)

        val note = CricketScoringEngine.calculateMiniTestLeadOrTrail(listOf(inn1, inn2), 1, 2)
        assertEquals("1st Innings Lead: 30 runs", note)
    }
}
