package com.example.domain

import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchType
import com.example.data.model.TeamEntity

data class InningsProgress(val isInningsComplete: Boolean, val isMatchComplete: Boolean)

data class MatchResult(
    val winnerTeamId: Long?,
    val summary: String,
    val isTie: Boolean = false,
    val isDraw: Boolean = false
)

/**
 * Match rules: when an innings ends, when the match ends, targets, lead/trail and the result line.
 * All functions expect innings rows whose runs/wickets/balls are already up to date.
 */
object CricketScoringEngine {

    fun isLegalBall(extraType: com.example.data.model.ExtraType): Boolean = CricketRules.isLegalBall(extraType)

    fun maxInnings(match: MatchEntity): Int = if (match.matchType == MatchType.MINI_TEST_2_INNINGS) 4 else 2

    fun maxBalls(match: MatchEntity): Int = match.totalOversPerInnings.coerceAtLeast(1) * match.ballsPerOver.coerceAtLeast(1)

    fun isFinalInnings(match: MatchEntity, innings: InningsEntity): Boolean = innings.inningsIndex >= maxInnings(match)

    fun isInningsOver(match: MatchEntity, innings: InningsEntity): Boolean =
        innings.isCompleted || innings.wickets >= innings.maxWickets || innings.ballsBowled >= maxBalls(match)

    /** Runs the side batting in the final innings needs to win, or null in any other innings. */
    fun target(match: MatchEntity, allInnings: List<InningsEntity>, innings: InningsEntity): Int? {
        if (!isFinalInnings(match, innings)) return null
        return if (match.matchType == MatchType.MINI_TEST_2_INNINGS) {
            val a1 = CricketRules.inningsTotal(allInnings.find { it.inningsIndex == 1 })
            val b1 = CricketRules.inningsTotal(allInnings.find { it.inningsIndex == 2 })
            val a2 = CricketRules.inningsTotal(allInnings.find { it.inningsIndex == 3 })
            a1 + a2 - b1 + 1
        } else {
            CricketRules.inningsTotal(allInnings.find { it.inningsIndex == 1 }) + 1
        }
    }

    fun progress(match: MatchEntity, innings: InningsEntity, allInnings: List<InningsEntity>): InningsProgress {
        val ended = isInningsOver(match, innings)
        val total = CricketRules.inningsTotal(innings)

        if (isFinalInnings(match, innings)) {
            val target = target(match, allInnings, innings) ?: Int.MAX_VALUE
            return when {
                total >= target -> InningsProgress(isInningsComplete = true, isMatchComplete = true)
                ended -> InningsProgress(isInningsComplete = true, isMatchComplete = true)
                else -> InningsProgress(isInningsComplete = false, isMatchComplete = false)
            }
        }

        if (match.matchType == MatchType.MINI_TEST_2_INNINGS && innings.inningsIndex == 3 && ended) {
            // Innings defeat: the side batting first and third is still behind after both innings.
            val a1 = CricketRules.inningsTotal(allInnings.find { it.inningsIndex == 1 })
            val b1 = CricketRules.inningsTotal(allInnings.find { it.inningsIndex == 2 })
            val a2 = total
            return InningsProgress(isInningsComplete = true, isMatchComplete = a1 + a2 < b1)
        }

        return InningsProgress(isInningsComplete = ended, isMatchComplete = false)
    }

    /** Kept for compatibility with older callers. Returns (isInningsComplete, isMatchComplete). */
    fun checkInningsAndMatchCompletion(
        match: MatchEntity,
        currentInnings: InningsEntity,
        allInnings: List<InningsEntity>,
        battingTeam: TeamEntity,
        maxWickets: Int
    ): Pair<Boolean, Boolean> {
        val p = progress(match, currentInnings.copy(maxWickets = maxWickets), allInnings)
        return Pair(p.isInningsComplete, p.isMatchComplete)
    }

    /** Lead or trail line for a two-innings match, from the batting side's point of view. */
    fun calculateMiniTestLeadOrTrail(inningsList: List<InningsEntity>, team1: TeamEntity, team2: TeamEntity): String? {
        val inn1 = inningsList.find { it.inningsIndex == 1 } ?: return null
        val inn2 = inningsList.find { it.inningsIndex == 2 } ?: return null
        val inn3 = inningsList.find { it.inningsIndex == 3 }
        val inn4 = inningsList.find { it.inningsIndex == 4 }
        fun name(id: Long) = if (id == team1.id) team1.shortName else team2.shortName

        val a1 = CricketRules.inningsTotal(inn1)
        val b1 = CricketRules.inningsTotal(inn2)
        if (inn4 != null) return null // the chase line covers this
        if (inn3 == null) {
            val diff = b1 - a1
            return when {
                diff > 0 -> "${name(inn2.battingTeamId)} lead by ${CricketRules.plural(diff, "run")}"
                diff < 0 -> "${name(inn2.battingTeamId)} trail by ${CricketRules.plural(-diff, "run")}"
                else -> "Scores level"
            }
        }
        val diff = a1 + CricketRules.inningsTotal(inn3) - b1
        return when {
            diff > 0 -> "${name(inn3.battingTeamId)} lead by ${CricketRules.plural(diff, "run")}"
            diff < 0 -> "${name(inn3.battingTeamId)} trail by ${CricketRules.plural(-diff, "run")}"
            else -> "Scores level"
        }
    }

    /** Older signature used by the original unit test. */
    fun calculateMiniTestLeadOrTrail(inningsList: List<InningsEntity>, team1Id: Long, team2Id: Long): String? {
        val inn1 = inningsList.find { it.inningsIndex == 1 } ?: return null
        val inn2 = inningsList.find { it.inningsIndex == 2 } ?: return "1st innings in progress"
        val diff = CricketRules.inningsTotal(inn1) - CricketRules.inningsTotal(inn2)
        return when {
            diff > 0 -> "1st Innings Lead: $diff runs"
            diff < 0 -> "1st Innings Trail: ${-diff} runs"
            else -> "Scores Level after 1st Innings"
        }
    }

    /** Works out the winner and the result line once a match is complete on the field. */
    fun result(match: MatchEntity, team1: TeamEntity, team2: TeamEntity, allInnings: List<InningsEntity>): MatchResult {
        fun name(id: Long) = if (id == team1.id) team1.name else team2.name
        val inn1 = allInnings.find { it.inningsIndex == 1 } ?: return MatchResult(null, "No result")
        val inn2 = allInnings.find { it.inningsIndex == 2 } ?: return MatchResult(null, "No result")

        if (match.matchType != MatchType.MINI_TEST_2_INNINGS) {
            val r1 = CricketRules.inningsTotal(inn1)
            val r2 = CricketRules.inningsTotal(inn2)
            return when {
                r2 > r1 -> {
                    val wicketsLeft = (inn2.maxWickets - inn2.wickets).coerceAtLeast(1)
                    val ballsLeft = (maxBalls(match) - inn2.ballsBowled).coerceAtLeast(0)
                    val tail = if (ballsLeft > 0) " (${CricketRules.plural(ballsLeft, "ball")} left)" else ""
                    MatchResult(inn2.battingTeamId, "${name(inn2.battingTeamId)} won by ${CricketRules.plural(wicketsLeft, "wicket")}$tail")
                }
                r1 > r2 -> MatchResult(inn1.battingTeamId, "${name(inn1.battingTeamId)} won by ${CricketRules.plural(r1 - r2, "run")}")
                else -> MatchResult(null, "Match tied", isTie = true)
            }
        }

        // Two innings each: A bats 1st and 3rd, B bats 2nd and 4th.
        val teamA = inn1.battingTeamId
        val teamB = inn2.battingTeamId
        val a1 = CricketRules.inningsTotal(inn1)
        val b1 = CricketRules.inningsTotal(inn2)
        val inn3 = allInnings.find { it.inningsIndex == 3 }
        val inn4 = allInnings.find { it.inningsIndex == 4 }
        val a2 = CricketRules.inningsTotal(inn3)

        if (inn3 != null && inn4 == null && a1 + a2 < b1 && isInningsOver(match, inn3)) {
            return MatchResult(teamB, "${name(teamB)} won by an innings and ${CricketRules.plural(b1 - a1 - a2, "run")}")
        }
        if (inn4 == null) return MatchResult(null, "Match drawn", isDraw = true)

        val target = a1 + a2 - b1 + 1
        val b2 = CricketRules.inningsTotal(inn4)
        val allOut = inn4.wickets >= inn4.maxWickets
        return when {
            b2 >= target -> {
                val wicketsLeft = (inn4.maxWickets - inn4.wickets).coerceAtLeast(1)
                MatchResult(teamB, "${name(teamB)} won by ${CricketRules.plural(wicketsLeft, "wicket")}")
            }
            allOut && b2 == target - 1 -> MatchResult(null, "Match tied", isTie = true)
            allOut -> MatchResult(teamA, "${name(teamA)} won by ${CricketRules.plural(target - 1 - b2, "run")}")
            else -> MatchResult(null, "Match drawn", isDraw = true)
        }
    }

    /** Older signature: (winnerTeamId, summary). */
    fun generateResultSummary(
        match: MatchEntity,
        team1: TeamEntity,
        team2: TeamEntity,
        allInnings: List<InningsEntity>
    ): Pair<Long?, String> {
        val r = result(match, team1, team2, allInnings)
        return Pair(r.winnerTeamId, r.summary)
    }
}
