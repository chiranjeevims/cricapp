package com.example

import com.example.data.model.BallEntity
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchStatus
import com.example.data.model.MatchType
import com.example.data.model.TeamEntity
import com.example.domain.Crease
import com.example.domain.CreaseLogic
import com.example.domain.CricketRules
import com.example.domain.CricketScoringEngine
import com.example.domain.DeliveryInput
import com.example.domain.NrrEngine
import com.example.domain.ScorecardBuilder
import com.example.domain.TournamentProgression
import com.example.domain.WicketInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringRulesTest {

    private fun ball(
        id: Long, over: Int, runs: Int = 0, extra: ExtraType = ExtraType.NONE, extraRuns: Int = 0,
        striker: Long = 1, nonStriker: Long? = 2, bowler: Long = 10,
        wicket: Boolean = false, dismissal: DismissalType = DismissalType.NONE, out: Long? = null
    ) = BallEntity(
        id = id, matchId = 1, inningsId = 1, overIndex = over, ballInOver = 0, legalBallNumber = 0,
        strikerPlayerId = striker, nonStrikerPlayerId = nonStriker, bowlerPlayerId = bowler,
        runsOffBat = runs, extraType = extra, extraRuns = extraRuns, isWicket = wicket,
        dismissalType = dismissal, outPlayerId = out
    )

    @Test
    fun oddRunsRotateStrikeAndOverEndSwapsEnds() {
        val c = Crease(1, 2, 10)
        val afterSingle = CreaseLogic.afterDelivery(c, DeliveryInput(1, ExtraType.NONE, 0), 0, 6, 0, 10, false, false)
        assertEquals(Crease(2, 1, 10), afterSingle)
        val lastBallSingle = CreaseLogic.afterDelivery(c, DeliveryInput(1, ExtraType.NONE, 0), 5, 6, 0, 10, false, false)
        assertEquals(Crease(1, 2, null), lastBallSingle) // crossed, then ends changed; new bowler needed
        val wideRun = CreaseLogic.afterDelivery(c, DeliveryInput(0, ExtraType.WIDE, 2), 5, 6, 0, 10, false, false)
        assertEquals(Crease(2, 1, 10), wideRun) // a wide is not the last legal ball
    }

    @Test
    fun newBatterReplacesDismissedBatter() {
        val c = Crease(1, 2, 10)
        val bowled = CreaseLogic.afterDelivery(c, DeliveryInput(0, ExtraType.NONE, 0, WicketInput(DismissalType.BOWLED, null, null, 3)), 0, 6, 0, 10, false, false)
        assertEquals(Crease(3, 2, 10), bowled)
        val runOutNonStriker = CreaseLogic.afterDelivery(c, DeliveryInput(0, ExtraType.NONE, 0, WicketInput(DismissalType.RUN_OUT, 2, null, 3)), 0, 6, 0, 10, false, false)
        assertEquals(Crease(1, 3, 10), runOutNonStriker)
        val caughtLastBall = CreaseLogic.afterDelivery(c, DeliveryInput(0, ExtraType.NONE, 0, WicketInput(DismissalType.CAUGHT, null, 5, 3)), 5, 6, 0, 10, false, false)
        assertEquals(Crease(2, 3, null), caughtLastBall)
        val allOut = CreaseLogic.afterDelivery(c, DeliveryInput(0, ExtraType.NONE, 0, WicketInput(DismissalType.LBW, null, null, null)), 2, 6, 9, 10, false, false)
        assertNull(allOut.strikerId)
    }

    @Test
    fun soloBatterNeverLeaves() {
        val c = Crease(1, null, 10)
        val out = CreaseLogic.afterDelivery(c, DeliveryInput(1, ExtraType.NONE, 0, WicketInput(DismissalType.RUN_OUT, null, null, null)), 5, 6, 0, 3, true, true)
        assertEquals(Crease(1, null, 10), out)
    }

    @Test
    fun scorecardCountsExtrasMaidensAndFallOfWickets() {
        val balls = listOf(
            ball(1, 0), ball(2, 0), ball(3, 0, extra = ExtraType.LEG_BYE, extraRuns = 1), ball(4, 0, striker = 2, nonStriker = 1),
            ball(5, 0, striker = 2, nonStriker = 1), ball(6, 0, striker = 2, nonStriker = 1, wicket = true, dismissal = DismissalType.BOWLED, out = 2),
            ball(7, 1, runs = 4, striker = 3, nonStriker = 1, bowler = 11), ball(8, 1, extra = ExtraType.WIDE, extraRuns = 1, striker = 3, nonStriker = 1, bowler = 11),
            ball(9, 1, runs = 6, extra = ExtraType.NO_BALL, extraRuns = 1, striker = 3, nonStriker = 1, bowler = 11)
        )
        val inn = InningsEntity(id = 1, matchId = 1, inningsIndex = 1, battingTeamId = 1, bowlingTeamId = 2, penaltyRuns = 5)
        val card = ScorecardBuilder.build(inn, balls, 6)
        assertEquals(1, card.bowlers.first { it.playerId == 10L }.maidens) // leg bye doesn't spoil a maiden
        assertEquals(1, card.bowlers.first { it.playerId == 10L }.wickets)
        assertEquals(12, card.bowlers.first { it.playerId == 11L }.runs)
        assertEquals(8, card.extras.total) // lb1 + wd1 + nb1 + pen5
        assertEquals(13 + 5, card.total)
        assertEquals(1, card.fallOfWickets.size)
        assertEquals(1, card.fallOfWickets[0].score)
        val p3 = card.batters.first { it.playerId == 3L }
        assertEquals(10, p3.runs)
        assertEquals(2, p3.balls) // the wide is not a ball faced, the no-ball is
        assertEquals(listOf(1L, 2L, 3L), card.batters.map { it.playerId })
    }

    @Test
    fun chaseTargetAndResultLines() {
        val match = MatchEntity(id = 1, team1Id = 1, team2Id = 2, totalOversPerInnings = 10, matchType = MatchType.STANDARD_LIMITED_OVERS)
        val t1 = TeamEntity(id = 1, name = "Lions", shortName = "LIO")
        val t2 = TeamEntity(id = 2, name = "Tigers", shortName = "TIG")
        val inn1 = InningsEntity(id = 1, matchId = 1, inningsIndex = 1, battingTeamId = 1, bowlingTeamId = 2, runs = 80, wickets = 4, ballsBowled = 60, isCompleted = true, maxWickets = 5)
        val chasing = InningsEntity(id = 2, matchId = 1, inningsIndex = 2, battingTeamId = 2, bowlingTeamId = 1, runs = 81, wickets = 2, ballsBowled = 50, maxWickets = 5)
        assertEquals(81, CricketScoringEngine.target(match, listOf(inn1, chasing), chasing))
        assertTrue(CricketScoringEngine.progress(match, chasing, listOf(inn1, chasing)).isMatchComplete)
        assertEquals("Tigers won by 3 wickets (10 balls left)", CricketScoringEngine.result(match, t1, t2, listOf(inn1, chasing)).summary)
        val short = chasing.copy(runs = 70, wickets = 5, ballsBowled = 40)
        assertEquals("Lions won by 10 runs", CricketScoringEngine.result(match, t1, t2, listOf(inn1, short)).summary)
        val tie = chasing.copy(runs = 80, ballsBowled = 60)
        assertEquals("Match tied", CricketScoringEngine.result(match, t1, t2, listOf(inn1, tie)).summary)
    }

    @Test
    fun allOutSideIsChargedFullQuotaInNrr() {
        val a = TeamEntity(id = 1, name = "A", shortName = "A")
        val b = TeamEntity(id = 2, name = "B", shortName = "B")
        val m = MatchEntity(id = 1, team1Id = 1, team2Id = 2, totalOversPerInnings = 10, status = MatchStatus.COMPLETED, winnerTeamId = 1)
        val innings = listOf(
            InningsEntity(id = 1, matchId = 1, inningsIndex = 1, battingTeamId = 1, bowlingTeamId = 2, runs = 100, wickets = 3, ballsBowled = 60, maxWickets = 10),
            InningsEntity(id = 2, matchId = 1, inningsIndex = 2, battingTeamId = 2, bowlingTeamId = 1, runs = 50, wickets = 4, ballsBowled = 30, maxWickets = 4)
        )
        val table = NrrEngine.calculateStandings(listOf(a, b), listOf(m), mapOf(1L to innings))
        assertEquals(2, table[0].points)
        assertEquals(5.0, table[0].netRunRate, 0.0001) // 10.0 for vs 5.0 against (50 runs over the full 10 overs)
        assertEquals(listOf('W'), table[0].form)
    }

    @Test
    fun knockoutBracketGivesByesToTopSeeds() {
        val (pairs, byes) = TournamentProgression.bracket(listOf(1L, 2L, 3L, 4L, 5L, 6L))
        assertEquals(listOf(1L, 2L), byes)
        assertEquals(listOf(3L to 6L, 4L to 5L), pairs)
        val (four, none) = TournamentProgression.bracket(listOf(1L, 2L, 3L, 4L))
        assertTrue(none.isEmpty())
        assertEquals(listOf(1L to 4L, 2L to 3L), four)
    }

    @Test
    fun oversAndRatesUseBallsPerOver() {
        assertEquals("3.2", CricketRules.oversText(20, 6))
        assertEquals("4", CricketRules.oversText(20, 5))
        assertEquals(6.0, CricketRules.runRate(24, 20, 5), 0.0001)
    }
}
