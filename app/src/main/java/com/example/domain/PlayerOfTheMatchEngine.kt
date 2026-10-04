package com.example.domain

import com.example.data.model.BallEntity
import com.example.data.model.DismissalType
import com.example.data.model.InningsEntity

data class PlayerPerformance(
    val playerId: Long,
    val runs: Int = 0,
    val ballsFaced: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val wickets: Int = 0,
    val ballsBowled: Int = 0,
    val runsConceded: Int = 0,
    val maidens: Int = 0,
    val catchesAndRunOuts: Int = 0,
    val totalPoints: Double = 0.0
)

/**
 * Performance index used to suggest a player of the match:
 * runs + boundary bonus + milestones + strike-rate bonus, 25 per wicket + hauls + maidens + economy,
 * 10 per catch / run out / stumping. Ties go to the winning side.
 */
object PlayerOfTheMatchEngine {

    fun calculatePlayerPoints(balls: List<BallEntity>, ballsPerOver: Int = 6): Map<Long, PlayerPerformance> {
        val perf = HashMap<Long, PlayerPerformance>()
        fun get(id: Long) = perf.getOrPut(id) { PlayerPerformance(playerId = id) }

        for ((inningsId, innBalls) in balls.groupBy { it.inningsId }) {
            val pseudo = InningsEntity(id = inningsId, matchId = innBalls.first().matchId, inningsIndex = 1, battingTeamId = 0, bowlingTeamId = 0)
            val card = ScorecardBuilder.build(pseudo, innBalls, ballsPerOver)
            for (b in card.batters) {
                val p = get(b.playerId)
                perf[b.playerId] = p.copy(
                    runs = p.runs + b.runs,
                    ballsFaced = p.ballsFaced + b.balls,
                    fours = p.fours + b.fours,
                    sixes = p.sixes + b.sixes
                )
            }
            for (bw in card.bowlers) {
                val p = get(bw.playerId)
                perf[bw.playerId] = p.copy(
                    wickets = p.wickets + bw.wickets,
                    ballsBowled = p.ballsBowled + bw.legalBalls,
                    runsConceded = p.runsConceded + bw.runs,
                    maidens = p.maidens + bw.maidens
                )
            }
            for (ball in innBalls) {
                val fielder = ball.fielderPlayerId ?: continue
                if (!ball.isWicket) continue
                if (ball.dismissalType != DismissalType.CAUGHT && ball.dismissalType != DismissalType.RUN_OUT &&
                    ball.dismissalType != DismissalType.STUMPED
                ) continue
                val p = get(fielder)
                perf[fielder] = p.copy(catchesAndRunOuts = p.catchesAndRunOuts + 1)
            }
        }

        return perf.mapValues { (_, p) ->
            var pts = p.runs + p.fours * 1.0 + p.sixes * 2.0
            pts += when {
                p.runs >= 100 -> 40.0
                p.runs >= 50 -> 20.0
                p.runs >= 30 -> 10.0
                else -> 0.0
            }
            if (p.ballsFaced >= 10) {
                val sr = p.runs * 100.0 / p.ballsFaced
                if (sr >= 150) pts += 10.0 else if (sr >= 120) pts += 5.0
            }
            pts += p.wickets * 25.0
            pts += when {
                p.wickets >= 5 -> 40.0
                p.wickets >= 3 -> 20.0
                else -> 0.0
            }
            pts += p.maidens * 15.0
            if (p.ballsBowled >= ballsPerOver * 2) {
                val econ = CricketRules.runRate(p.runsConceded, p.ballsBowled, ballsPerOver)
                if (econ < 5.0) pts += 15.0 else if (econ < 7.0) pts += 8.0
            }
            pts += p.catchesAndRunOuts * 10.0
            p.copy(totalPoints = pts)
        }
    }

    fun determinePlayerOfTheMatch(
        balls: List<BallEntity>,
        ballsPerOver: Int = 6,
        winningSidePlayerIds: Set<Long> = emptySet()
    ): Long? {
        val points = calculatePlayerPoints(balls, ballsPerOver)
        return points.values
            .maxWithOrNull(
                compareBy<PlayerPerformance> { it.totalPoints }
                    .thenBy { if (it.playerId in winningSidePlayerIds) 1 else 0 }
            )
            ?.playerId
    }
}
