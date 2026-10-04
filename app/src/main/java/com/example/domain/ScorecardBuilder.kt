package com.example.domain

import com.example.data.model.BallEntity
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.PlayerEntity
import com.example.data.model.TeamEntity
import java.util.Locale

data class BatterScore(
    val playerId: Long,
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val isOut: Boolean = false,
    val dismissal: DismissalType = DismissalType.NONE,
    val bowlerId: Long? = null,
    val fielderId: Long? = null
) {
    val strikeRate: Double get() = if (balls > 0) runs * 100.0 / balls else 0.0
}

data class BowlerFigures(
    val playerId: Long,
    val legalBalls: Int = 0,
    val maidens: Int = 0,
    val runs: Int = 0,
    val wickets: Int = 0,
    val wides: Int = 0,
    val noBalls: Int = 0,
    val dots: Int = 0
) {
    fun economy(ballsPerOver: Int): Double = CricketRules.runRate(runs, legalBalls, ballsPerOver)
}

data class Extras(val wides: Int, val noBalls: Int, val byes: Int, val legByes: Int, val penalty: Int) {
    val total: Int get() = wides + noBalls + byes + legByes + penalty
}

data class FallOfWicket(val wicketNumber: Int, val score: Int, val playerId: Long, val legalBalls: Int)

data class OverSummary(
    val overIndex: Int,
    val bowlerId: Long,
    val balls: List<BallEntity>,
    val runs: Int,
    val wickets: Int,
    val cumulativeRuns: Int
)

data class InningsCard(
    val innings: InningsEntity,
    val batters: List<BatterScore>,
    val bowlers: List<BowlerFigures>,
    val extras: Extras,
    val total: Int,
    val wickets: Int,
    val legalBalls: Int,
    val fallOfWickets: List<FallOfWicket>,
    val overs: List<OverSummary>,
    val didNotBat: List<Long>
)

object ScorecardBuilder {

    fun build(
        innings: InningsEntity,
        inningsBalls: List<BallEntity>,
        ballsPerOver: Int,
        battingSquadIds: List<Long> = emptyList(),
        atCreaseIds: List<Long?> = emptyList()
    ): InningsCard {
        val bpo = ballsPerOver.coerceAtLeast(1)
        val balls = inningsBalls.sortedBy { it.id }
        val deliveries = balls.filter { !it.isPenaltyRun }

        val batting = LinkedHashMap<Long, BatterScore>()
        fun touch(id: Long?) {
            if (id != null && !batting.containsKey(id)) batting[id] = BatterScore(playerId = id)
        }

        val bowling = LinkedHashMap<Long, BowlerFigures>()
        val fow = mutableListOf<FallOfWicket>()
        var runningScore = 0
        var legalSoFar = 0
        var wicketCount = 0

        for (b in balls) {
            if (b.isPenaltyRun) {
                runningScore += b.extraRuns
                continue
            }
            touch(b.strikerPlayerId)
            touch(b.nonStrikerPlayerId)

            val bat = batting.getValue(b.strikerPlayerId)
            if (CricketRules.isBallFaced(b)) {
                batting[b.strikerPlayerId] = bat.copy(
                    runs = bat.runs + b.runsOffBat,
                    balls = bat.balls + 1,
                    fours = bat.fours + if (b.runsOffBat == 4) 1 else 0,
                    sixes = bat.sixes + if (b.runsOffBat == 6) 1 else 0
                )
            }

            val legal = CricketRules.isLegalBall(b.extraType)
            val conceded = CricketRules.runsConcededByBowler(b)
            val bowl = bowling[b.bowlerPlayerId] ?: BowlerFigures(playerId = b.bowlerPlayerId)
            bowling[b.bowlerPlayerId] = bowl.copy(
                legalBalls = bowl.legalBalls + if (legal) 1 else 0,
                runs = bowl.runs + conceded,
                wickets = bowl.wickets + if (b.isWicket && b.dismissalType.creditedToBowler) 1 else 0,
                wides = bowl.wides + if (b.extraType == ExtraType.WIDE) 1 else 0,
                noBalls = bowl.noBalls + if (b.extraType == ExtraType.NO_BALL) 1 else 0,
                dots = bowl.dots + if (legal && conceded == 0) 1 else 0
            )

            runningScore += b.runsOffBat + b.extraRuns
            if (legal) legalSoFar++

            if (b.isWicket) {
                val outId = b.outPlayerId ?: b.strikerPlayerId
                touch(outId)
                val out = batting.getValue(outId)
                batting[outId] = out.copy(
                    isOut = true,
                    dismissal = b.dismissalType,
                    bowlerId = b.bowlerPlayerId,
                    fielderId = b.fielderPlayerId
                )
                wicketCount++
                fow += FallOfWicket(wicketCount, runningScore, outId, legalSoFar)
            }
        }
        atCreaseIds.forEach { touch(it) }

        // Maidens: a completed over, one bowler, nothing conceded.
        val overs = mutableListOf<OverSummary>()
        var cumulative = 0
        for ((overIndex, overBalls) in deliveries.groupBy { it.overIndex }.toSortedMap()) {
            val runs = overBalls.sumOf { it.runsOffBat + it.extraRuns }
            cumulative += runs
            val firstBowler = overBalls.first().bowlerPlayerId
            overs += OverSummary(
                overIndex = overIndex,
                bowlerId = firstBowler,
                balls = overBalls,
                runs = runs,
                wickets = overBalls.count { it.isWicket },
                cumulativeRuns = cumulative
            )
            val complete = overBalls.count { CricketRules.isLegalBall(it.extraType) } >= bpo
            val oneBowler = overBalls.all { it.bowlerPlayerId == firstBowler }
            if (complete && oneBowler && overBalls.sumOf { CricketRules.runsConcededByBowler(it) } == 0) {
                val f = bowling[firstBowler]
                if (f != null) bowling[firstBowler] = f.copy(maidens = f.maidens + 1)
            }
        }

        val extras = Extras(
            wides = deliveries.filter { it.extraType == ExtraType.WIDE }.sumOf { it.extraRuns },
            noBalls = deliveries.filter { it.extraType == ExtraType.NO_BALL }.sumOf { it.extraRuns },
            byes = deliveries.filter { it.extraType == ExtraType.BYE }.sumOf { it.extraRuns },
            legByes = deliveries.filter { it.extraType == ExtraType.LEG_BYE }.sumOf { it.extraRuns },
            penalty = innings.penaltyRuns
        )
        val totals = CricketRules.totals(balls)
        val listed = batting.keys
        return InningsCard(
            innings = innings,
            batters = batting.values.toList(),
            bowlers = bowling.values.toList(),
            extras = extras,
            total = totals.runs + innings.penaltyRuns,
            wickets = totals.wickets,
            legalBalls = totals.legalBalls,
            fallOfWickets = fow,
            overs = overs,
            didNotBat = battingSquadIds.filter { it !in listed }
        )
    }

    /** "c Rao b Iyer", "run out (Das)", "not out". */
    fun dismissalText(score: BatterScore, nameOf: (Long?) -> String): String {
        if (!score.isOut) return "not out"
        val bowler = nameOf(score.bowlerId)
        val fielder = score.fielderId?.let { nameOf(it) }
        return when (score.dismissal) {
            DismissalType.BOWLED -> "b $bowler"
            DismissalType.CAUGHT -> if (fielder == null || score.fielderId == score.bowlerId) "c & b $bowler" else "c $fielder b $bowler"
            DismissalType.RUN_OUT -> if (fielder != null) "run out ($fielder)" else "run out"
            DismissalType.STUMPED -> if (fielder != null) "st $fielder b $bowler" else "st b $bowler"
            DismissalType.LBW -> "lbw b $bowler"
            DismissalType.HIT_WICKET -> "hit wicket b $bowler"
            DismissalType.RETIRED -> "retired out"
            DismissalType.NONE -> "out"
        }
    }

    /** Short label for a ball in an over strip: "4", "W", "1wd", "nb4". */
    fun ballLabel(ball: BallEntity): String = when {
        ball.isPenaltyRun -> "P${ball.extraRuns}"
        ball.isWicket -> if (ball.runsOffBat > 0) "W${ball.runsOffBat}" else "W"
        ball.extraType == ExtraType.WIDE -> if (ball.extraRuns > 1) "${ball.extraRuns}wd" else "wd"
        ball.extraType == ExtraType.NO_BALL -> if (ball.runsOffBat > 0) "nb${ball.runsOffBat}" else "nb"
        ball.extraType == ExtraType.BYE -> "${ball.extraRuns}b"
        ball.extraType == ExtraType.LEG_BYE -> "${ball.extraRuns}lb"
        ball.runsOffBat == 0 -> "0"
        else -> "${ball.runsOffBat}"
    }

    /** Plain-text scorecard for sharing on WhatsApp and similar apps. */
    fun shareText(
        match: MatchEntity,
        team1: TeamEntity,
        team2: TeamEntity,
        innings: List<InningsEntity>,
        balls: List<BallEntity>,
        players: Map<Long, PlayerEntity>,
        resultLine: String,
        potm: PlayerEntity?,
        venue: String?
    ): String {
        fun name(id: Long?) = id?.let { players[it]?.name } ?: "Unknown"
        fun teamName(id: Long) = if (id == team1.id) team1.name else team2.name
        val bpo = match.ballsPerOver
        val sb = StringBuilder()
        sb.append("${team1.name} vs ${team2.name}\n")
        val meta = listOfNotNull(match.matchType.label, "${match.totalOversPerInnings} overs", venue).joinToString(", ")
        sb.append(meta).append('\n')
        if (resultLine.isNotBlank()) sb.append(resultLine).append('\n')
        val byInnings = balls.groupBy { it.inningsId }
        for (inn in innings.sortedBy { it.inningsIndex }) {
            val card = build(inn, byInnings[inn.id].orEmpty(), bpo)
            sb.append('\n')
            sb.append("${teamName(inn.battingTeamId)} ${card.total}/${card.wickets} (${CricketRules.oversText(card.legalBalls, bpo)} ov)\n")
            for (b in card.batters) {
                val notOut = if (b.isOut) "" else "*"
                sb.append("  ${name(b.playerId)} ${b.runs}$notOut (${b.balls})\n")
            }
            if (card.extras.total > 0) sb.append("  Extras ${card.extras.total}\n")
            for (bw in card.bowlers) {
                sb.append(
                    String.format(
                        Locale.US, "  %s %s-%d-%d-%d\n",
                        name(bw.playerId), CricketRules.oversText(bw.legalBalls, bpo), bw.maidens, bw.runs, bw.wickets
                    )
                )
            }
        }
        if (potm != null) sb.append("\nPlayer of the match: ${potm.name}\n")
        sb.append("\nScored with CricScore")
        return sb.toString()
    }
}
