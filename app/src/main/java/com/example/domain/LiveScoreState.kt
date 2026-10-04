package com.example.domain

import com.example.data.model.BallEntity
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchType
import com.example.data.model.PlayerEntity
import com.example.data.model.TeamEntity

/** Everything the scoring console needs, rebuilt from the database after every change. */
data class LiveScoreState(
    val match: MatchEntity,
    val team1: TeamEntity,
    val team2: TeamEntity,
    val squad1: List<PlayerEntity>,
    val squad2: List<PlayerEntity>,
    val playersById: Map<Long, PlayerEntity>,
    /** Innings rows with runs, wickets and balls recomputed from their deliveries. */
    val allInnings: List<InningsEntity>,
    val allBalls: List<BallEntity>,
    val currentInnings: InningsEntity?,
    val battingTeam: TeamEntity,
    val bowlingTeam: TeamEntity,
    val battingSquad: List<PlayerEntity>,
    val bowlingSquad: List<PlayerEntity>,
    val striker: PlayerEntity?,
    val nonStriker: PlayerEntity?,
    val bowler: PlayerEntity?,
    val inningsBalls: List<BallEntity>,
    val totalRuns: Int,
    val wickets: Int,
    val legalBalls: Int,
    val maxWicketsAllowed: Int,
    val maxBalls: Int,
    val currentOverBalls: List<BallEntity>,
    val previousOverBalls: List<BallEntity>,
    val currentRunRate: Double,
    val targetRuns: Int?,
    val runsNeeded: Int?,
    val ballsRemaining: Int,
    val requiredRunRate: Double?,
    val leadOrTrailSummary: String?,
    val partnershipRuns: Int,
    val partnershipBalls: Int,
    val isSoloBatting: Boolean,
    val isSoloBowling: Boolean,
    val isInningsComplete: Boolean,
    val isMatchComplete: Boolean,
    val needsToss: Boolean,
    val needsOpeners: Boolean,
    val needsNewBowler: Boolean,
    val availableBatters: List<PlayerEntity>,
    val previousBowlerId: Long?,
    val liveResult: MatchResult?,
    val lastBall: BallEntity?
) {
    val isFinished: Boolean get() = match.status.isFinished

    /** Ball-by-ball input is allowed. */
    val canScore: Boolean
        get() = !isFinished && currentInnings != null && !isInningsComplete && !needsOpeners &&
            !needsNewBowler && striker != null && bowler != null

    /** Text shown in the result banner: the saved result once finished, otherwise the live one. */
    val resultSummary: String
        get() = if (isFinished) match.resultSummary else liveResult?.summary ?: ""

    val needsWinnerDecision: Boolean
        get() = match.stage.isKnockout && isFinished && match.winnerTeamId == null

    val currentOverIndex: Int get() = legalBalls / match.ballsPerOver.coerceAtLeast(1)

    val projectedScore: Int?
        get() {
            val inn = currentInnings ?: return null
            if (targetRuns != null || legalBalls == 0 || isInningsComplete) return null
            if (match.matchType == MatchType.MINI_TEST_2_INNINGS && inn.inningsIndex > 1) return null
            return totalRuns + (currentRunRate * (maxBalls - legalBalls) / match.ballsPerOver.coerceAtLeast(1)).toInt()
        }
}

object LiveStateBuilder {

    fun build(
        match: MatchEntity,
        team1: TeamEntity,
        team2: TeamEntity,
        squad1: List<PlayerEntity>,
        squad2: List<PlayerEntity>,
        allPlayers: List<PlayerEntity>,
        rawInnings: List<InningsEntity>,
        balls: List<BallEntity>
    ): LiveScoreState {
        val bpo = match.ballsPerOver.coerceAtLeast(1)
        val playersById = HashMap<Long, PlayerEntity>()
        for (p in allPlayers) playersById[p.id] = p
        for (p in squad1) playersById[p.id] = p
        for (p in squad2) playersById[p.id] = p

        val ballsByInnings = balls.groupBy { it.inningsId }
        val sortedInnings = rawInnings.sortedBy { it.inningsIndex }
        val current0 = sortedInnings.find { it.inningsIndex == match.currentInningsIndex } ?: sortedInnings.lastOrNull()

        fun teamOf(id: Long) = if (id == team1.id) team1 else team2
        fun squadOf(id: Long) = if (id == team1.id) squad1 else squad2

        // Normalise every innings from its deliveries so totals can never drift from the ball log.
        val innings = sortedInnings.map { inn ->
            val t = CricketRules.totals(ballsByInnings[inn.id].orEmpty())
            val battingTeam = teamOf(inn.battingTeamId)
            val maxW = if (inn.id == current0?.id && !inn.isCompleted) {
                CricketRules.maxWicketsFor(
                    battingTeam.isSoloTeam,
                    CricketRules.soloLivesFor(match, inn.battingTeamId),
                    squadOf(inn.battingTeamId).size
                )
            } else inn.maxWickets
            inn.copy(runs = t.runs, wickets = t.wickets, ballsBowled = t.legalBalls, maxWickets = maxW)
        }
        val current = innings.find { it.id == current0?.id }

        val battingTeamId = current?.battingTeamId ?: firstBattingTeamId(match)
        val battingTeam = teamOf(battingTeamId)
        val bowlingTeam = if (battingTeam.id == team1.id) team2 else team1
        val battingSquad = squadOf(battingTeam.id)
        val bowlingSquad = squadOf(bowlingTeam.id)
        val isSoloBatting = battingTeam.isSoloTeam
        val isSoloBowling = bowlingTeam.isSoloTeam

        val inningsBalls = if (current != null) ballsByInnings[current.id].orEmpty() else emptyList()
        val deliveries = inningsBalls.filter { !it.isPenaltyRun }
        val lastDelivery = deliveries.lastOrNull()
        val legalBalls = current?.ballsBowled ?: 0
        val wickets = current?.wickets ?: 0
        val totalRuns = CricketRules.inningsTotal(current)
        val maxWickets = current?.maxWickets ?: 10
        val maxBalls = CricketScoringEngine.maxBalls(match)

        val overJustCompleted = lastDelivery != null && CricketRules.isLegalBall(lastDelivery.extraType) &&
            legalBalls > 0 && legalBalls % bpo == 0

        // Crease state. Matches scored with the old version have no stored crease, so fall back to the last ball.
        val legacy = current != null && current.strikerId == null && deliveries.isNotEmpty()
        val strikerId = if (legacy) lastDelivery?.strikerPlayerId else current?.strikerId
        val nonStrikerId = when {
            isSoloBatting -> null
            legacy -> lastDelivery?.nonStrikerPlayerId
            else -> current?.nonStrikerId
        }
        val bowlerId = when {
            legacy -> if (overJustCompleted) null else lastDelivery?.bowlerPlayerId
            else -> current?.bowlerId
        }

        val progress = if (current != null) CricketScoringEngine.progress(match, current, innings)
        else InningsProgress(isInningsComplete = false, isMatchComplete = false)

        val finished = match.status.isFinished
        val needsToss = current == null && !finished
        val needsOpeners = current != null && !finished && !progress.isInningsComplete && deliveries.isEmpty() &&
            (current.strikerId == null || current.bowlerId == null || (!isSoloBatting && current.nonStrikerId == null))
        val needsNewBowler = current != null && !finished && !progress.isInningsComplete &&
            deliveries.isNotEmpty() && bowlerId == null

        val dismissed = deliveries.filter { it.isWicket }.mapNotNull { it.outPlayerId }.toSet()
        val availableBatters = if (isSoloBatting) emptyList() else battingSquad.filter {
            it.id !in dismissed && it.id != strikerId && it.id != nonStrikerId
        }

        val currentOverIndex = legalBalls / bpo
        val currentOverBalls = deliveries.filter { it.overIndex == currentOverIndex }
        val previousOverBalls = if (currentOverIndex > 0) deliveries.filter { it.overIndex == currentOverIndex - 1 } else emptyList()

        val crr = CricketRules.runRate(totalRuns, legalBalls, bpo)
        val target = if (current != null) CricketScoringEngine.target(match, innings, current) else null
        val runsNeeded = target?.let { (it - totalRuns).coerceAtLeast(0) }
        val ballsRemaining = (maxBalls - legalBalls).coerceAtLeast(0)
        val rrr = if (runsNeeded != null && ballsRemaining > 0) runsNeeded * bpo.toDouble() / ballsRemaining else null

        val leadOrTrail = if (match.matchType == MatchType.MINI_TEST_2_INNINGS) {
            CricketScoringEngine.calculateMiniTestLeadOrTrail(innings, team1, team2)
        } else null

        // Current partnership: everything since the last wicket in this innings.
        val lastWicketIdx = deliveries.indexOfLast { it.isWicket }
        val partnershipBalls = deliveries.drop(lastWicketIdx + 1)
        val partnershipRuns = partnershipBalls.sumOf { it.runsOffBat + it.extraRuns }
        val partnershipLegal = partnershipBalls.count { CricketRules.isLegalBall(it.extraType) }

        val liveResult = if (progress.isMatchComplete) CricketScoringEngine.result(match, team1, team2, innings) else null

        return LiveScoreState(
            match = match,
            team1 = team1,
            team2 = team2,
            squad1 = squad1,
            squad2 = squad2,
            playersById = playersById,
            allInnings = innings,
            allBalls = balls,
            currentInnings = current,
            battingTeam = battingTeam,
            bowlingTeam = bowlingTeam,
            battingSquad = battingSquad,
            bowlingSquad = bowlingSquad,
            striker = strikerId?.let { playersById[it] },
            nonStriker = nonStrikerId?.let { playersById[it] },
            bowler = bowlerId?.let { playersById[it] },
            inningsBalls = inningsBalls,
            totalRuns = totalRuns,
            wickets = wickets,
            legalBalls = legalBalls,
            maxWicketsAllowed = maxWickets,
            maxBalls = maxBalls,
            currentOverBalls = currentOverBalls,
            previousOverBalls = previousOverBalls,
            currentRunRate = crr,
            targetRuns = target,
            runsNeeded = runsNeeded,
            ballsRemaining = ballsRemaining,
            requiredRunRate = rrr,
            leadOrTrailSummary = leadOrTrail,
            partnershipRuns = partnershipRuns,
            partnershipBalls = partnershipLegal,
            isSoloBatting = isSoloBatting,
            isSoloBowling = isSoloBowling,
            isInningsComplete = progress.isInningsComplete,
            isMatchComplete = progress.isMatchComplete,
            needsToss = needsToss,
            needsOpeners = needsOpeners,
            needsNewBowler = needsNewBowler,
            availableBatters = availableBatters,
            previousBowlerId = if (overJustCompleted) lastDelivery?.bowlerPlayerId else null,
            liveResult = liveResult,
            lastBall = inningsBalls.lastOrNull()
        )
    }

    /** Which side bats first, from the toss (team 1 if no toss was recorded). */
    fun firstBattingTeamId(match: MatchEntity): Long {
        val tossWinner = match.tossWinnerTeamId ?: return match.team1Id
        val other = if (tossWinner == match.team1Id) match.team2Id else match.team1Id
        return if (match.tossDecision == com.example.data.model.TossDecision.BOWL) other else tossWinner
    }
}
