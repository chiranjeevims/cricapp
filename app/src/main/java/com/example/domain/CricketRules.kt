package com.example.domain

import com.example.data.model.BallEntity
import com.example.data.model.ExtraType
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import java.util.Locale

/** Small, pure cricket rules shared by the scoring console, scorecards, stats and tables. */
object CricketRules {

    /** A legal delivery counts towards the over. Wides, no-balls and penalty entries do not. */
    fun isLegalBall(extraType: ExtraType): Boolean =
        extraType != ExtraType.WIDE && extraType != ExtraType.NO_BALL && extraType != ExtraType.PENALTY

    fun isLegalBall(ball: BallEntity): Boolean = !ball.isPenaltyRun && isLegalBall(ball.extraType)

    /** Runs physically run between the wickets on a delivery; odd numbers change the strike. */
    fun runsRan(extraType: ExtraType, runsOffBat: Int, extraRuns: Int): Int = when (extraType) {
        ExtraType.NONE, ExtraType.NO_BALL -> runsOffBat
        ExtraType.BYE, ExtraType.LEG_BYE -> extraRuns
        ExtraType.WIDE -> (extraRuns - 1).coerceAtLeast(0)
        ExtraType.PENALTY -> 0
    }

    /** Total runs a delivery adds to the batting side (penalty entries are tracked separately). */
    fun runsOnBall(ball: BallEntity): Int = if (ball.isPenaltyRun) 0 else ball.runsOffBat + ball.extraRuns

    /** Runs charged against the bowler: off the bat plus wides and no-balls, never byes or leg byes. */
    fun runsConcededByBowler(ball: BallEntity): Int {
        if (ball.isPenaltyRun) return 0
        val extrasCharged = if (ball.extraType == ExtraType.WIDE || ball.extraType == ExtraType.NO_BALL) ball.extraRuns else 0
        return ball.runsOffBat + extrasCharged
    }

    /** A batter faces every delivery except wides (no-balls count as a ball faced). */
    fun isBallFaced(ball: BallEntity): Boolean = !ball.isPenaltyRun && ball.extraType != ExtraType.WIDE

    fun isWicket(ball: BallEntity): Boolean = ball.isWicket && !ball.isPenaltyRun

    data class Totals(val runs: Int, val wickets: Int, val legalBalls: Int)

    /** Recomputes an innings total from its deliveries (penalty runs are kept on the innings row). */
    fun totals(balls: List<BallEntity>): Totals {
        var runs = 0
        var wickets = 0
        var legal = 0
        for (b in balls) {
            if (b.isPenaltyRun) continue
            runs += b.runsOffBat + b.extraRuns
            if (b.isWicket) wickets++
            if (isLegalBall(b.extraType)) legal++
        }
        return Totals(runs, wickets, legal)
    }

    /** Total for an innings row, including penalty runs. */
    fun inningsTotal(innings: InningsEntity?): Int = if (innings == null) 0 else innings.runs + innings.penaltyRuns

    fun maxWicketsFor(isSoloTeam: Boolean, soloLives: Int, squadSize: Int): Int =
        if (isSoloTeam) soloLives.coerceIn(1, 10) else (squadSize - 1).coerceIn(1, 10)

    fun soloLivesFor(match: MatchEntity, teamId: Long): Int =
        if (teamId == match.team1Id) match.soloPlayer1Wickets else match.soloPlayer2Wickets

    /** "12.3" for 75 balls at 6 per over, "12" for exactly 72. */
    fun oversText(legalBalls: Int, ballsPerOver: Int): String {
        val bpo = ballsPerOver.coerceAtLeast(1)
        val overs = legalBalls / bpo
        val rem = legalBalls % bpo
        return if (rem == 0) "$overs" else "$overs.$rem"
    }

    /** Runs per over, honouring the match's balls-per-over setting. */
    fun runRate(runs: Int, legalBalls: Int, ballsPerOver: Int): Double =
        if (legalBalls <= 0) 0.0 else runs * ballsPerOver.coerceAtLeast(1).toDouble() / legalBalls

    fun formatRate(rate: Double): String = String.format(Locale.US, "%.2f", rate)

    fun plural(count: Int, word: String): String = if (count == 1) "1 $word" else "$count ${word}s"
}
