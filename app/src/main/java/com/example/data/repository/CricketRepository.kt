package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.model.BallEntity
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType
import com.example.data.model.GroundEntity
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchStage
import com.example.data.model.MatchStatus
import com.example.data.model.MatchType
import com.example.data.model.PlayerEntity
import com.example.data.model.TeamEntity
import com.example.data.model.TeamPlayerCrossRef
import com.example.data.model.TossDecision
import com.example.data.model.TournamentEntity
import com.example.data.model.TournamentFormat
import com.example.data.model.TournamentStatus
import com.example.data.model.TournamentTeamCrossRef
import com.example.domain.CreaseLogic
import com.example.domain.Crease
import com.example.domain.CricketRules
import com.example.domain.CricketScoringEngine
import com.example.domain.DeliveryInput
import com.example.domain.LiveScoreState
import com.example.domain.LiveStateBuilder
import com.example.domain.PlayerOfTheMatchEngine
import com.example.domain.ScorecardBuilder
import com.example.domain.TournamentProgression
import com.example.domain.TournamentScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of an action, with a short message for the snackbar. */
data class ActionResult(val ok: Boolean, val message: String? = null) {
    companion object {
        val OK = ActionResult(true)
        fun done(message: String) = ActionResult(true, message)
        fun fail(message: String) = ActionResult(false, message)
    }
}

data class TeamDeleteCheck(val matchCount: Int, val tournamentNames: List<String>) {
    val blocked: Boolean get() = tournamentNames.isNotEmpty()
}

enum class KnockoutDecision(val label: String, val phrase: String) {
    SUPER_OVER("Super over", "won the super over"),
    BOWL_OUT("Bowl-out", "won the bowl-out"),
    COIN_TOSS("Coin toss", "won the coin toss"),
    HIGHER_SEED("Higher seed", "go through as the higher seed")
}

class CricketRepository(private val db: AppDatabase) {

    private val playerDao get() = db.playerDao()
    private val teamDao get() = db.teamDao()
    private val groundDao get() = db.groundDao()
    private val tournamentDao get() = db.tournamentDao()
    private val matchDao get() = db.matchDao()
    private val inningsDao get() = db.inningsDao()
    private val ballDao get() = db.ballDao()

    companion object {
        /** One writer at a time: rapid taps can never record the same ball twice or race an undo. */
        private val writeLock = Mutex()
    }

    // ---------------------------------------------------------------- Observed data

    fun getAllPlayers(): Flow<List<PlayerEntity>> = playerDao.getAllPlayers()
    fun getAllTeams(): Flow<List<TeamEntity>> = teamDao.getAllTeams()
    fun getAllGrounds(): Flow<List<GroundEntity>> = groundDao.getAllGrounds()
    fun getAllTournaments(): Flow<List<TournamentEntity>> = tournamentDao.getAllTournaments()
    fun getAllMatches(): Flow<List<MatchEntity>> = matchDao.getAllMatches()
    fun getAllInnings(): Flow<List<InningsEntity>> = inningsDao.getAllInnings()
    fun getAllBalls(): Flow<List<BallEntity>> = ballDao.getAllBalls()
    fun getAllTeamPlayerRefs(): Flow<List<TeamPlayerCrossRef>> = teamDao.getAllTeamPlayerCrossRefs()
    fun getAllTournamentTeamRefs(): Flow<List<TournamentTeamCrossRef>> = tournamentDao.getAllTournamentTeamCrossRefs()

    fun getMatchFlow(id: Long): Flow<MatchEntity?> = matchDao.getMatchFlowById(id)
    fun getInningsForMatchFlow(matchId: Long): Flow<List<InningsEntity>> = inningsDao.getInningsForMatch(matchId)
    fun getBallsForMatchFlow(matchId: Long): Flow<List<BallEntity>> = ballDao.getBallsForMatch(matchId)
    fun getPlayersForTeam(teamId: Long): Flow<List<PlayerEntity>> = teamDao.getPlayersForTeam(teamId)

    fun getTournamentFlow(id: Long): Flow<TournamentEntity?> = tournamentDao.getTournamentFlowById(id)
    fun getTeamsForTournament(id: Long): Flow<List<TeamEntity>> = tournamentDao.getTeamsForTournament(id)
    fun getMatchesForTournament(id: Long): Flow<List<MatchEntity>> = matchDao.getMatchesForTournament(id)
    fun getInningsForTournament(id: Long): Flow<List<InningsEntity>> = inningsDao.getInningsForTournament(id)
    fun getBallsForTournament(id: Long): Flow<List<BallEntity>> = ballDao.getBallsForTournament(id)

    suspend fun getPlayersForTeamSync(teamId: Long): List<PlayerEntity> = teamDao.getPlayersForTeamSync(teamId)
    suspend fun getTeamPlayerRefs(teamId: Long): List<TeamPlayerCrossRef> = teamDao.getTeamPlayerCrossRefs(teamId)
    suspend fun getMatchById(id: Long): MatchEntity? = matchDao.getMatchById(id)

    // ---------------------------------------------------------------- Players

    suspend fun savePlayer(player: PlayerEntity): Long =
        if (player.id == 0L) playerDao.insertPlayer(player) else {
            playerDao.updatePlayer(player); player.id
        }

    suspend fun deletePlayer(player: PlayerEntity): ActionResult = writeLock.withLock {
        val records = ballDao.countBallsInvolvingPlayer(player.id)
        if (records > 0) {
            return@withLock ActionResult.fail("${player.name} has match records, so they can't be deleted. Remove them from their team instead.")
        }
        playerDao.deletePlayer(player)
        ActionResult.done("${player.name} deleted")
    }

    // ---------------------------------------------------------------- Teams

    suspend fun createTeam(team: TeamEntity, playerIds: List<Long>): Long = db.withTransaction {
        val id = teamDao.insertTeam(team)
        teamDao.insertTeamPlayerCrossRefs(playerIds.mapIndexed { i, pid ->
            TeamPlayerCrossRef(teamId = id, playerId = pid, isCaptain = i == 0, isWicketKeeper = team.isSoloTeam)
        })
        id
    }

    suspend fun updateTeam(team: TeamEntity) = teamDao.updateTeam(team)

    suspend fun addPlayersToTeam(teamId: Long, playerIds: List<Long>) = db.withTransaction {
        val existing = teamDao.getTeamPlayerCrossRefs(teamId).map { it.playerId }.toSet()
        val hasCaptain = teamDao.getTeamPlayerCrossRefs(teamId).any { it.isCaptain }
        val fresh = playerIds.filter { it !in existing }
        teamDao.insertTeamPlayerCrossRefs(fresh.mapIndexed { i, pid ->
            TeamPlayerCrossRef(teamId = teamId, playerId = pid, isCaptain = !hasCaptain && i == 0)
        })
    }

    suspend fun removePlayerFromTeam(teamId: Long, playerId: Long) = teamDao.removePlayerFromTeam(teamId, playerId)

    suspend fun setCaptain(teamId: Long, playerId: Long) = db.withTransaction {
        teamDao.clearCaptain(teamId)
        teamDao.setCaptain(teamId, playerId)
    }

    suspend fun toggleWicketKeeper(teamId: Long, playerId: Long) = db.withTransaction {
        val isKeeper = teamDao.getTeamPlayerCrossRefs(teamId).any { it.playerId == playerId && it.isWicketKeeper }
        teamDao.clearWicketKeeper(teamId)
        if (!isKeeper) teamDao.setWicketKeeper(teamId, playerId)
    }

    suspend fun checkTeamDeletion(teamId: Long): TeamDeleteCheck = TeamDeleteCheck(
        matchCount = matchDao.countMatchesForTeam(teamId),
        tournamentNames = tournamentDao.getTournamentsForTeam(teamId).map { it.name }
    )

    /** Deletes a team and its matches. Teams that are part of a tournament are kept. */
    suspend fun deleteTeam(team: TeamEntity): ActionResult = writeLock.withLock {
        val check = checkTeamDeletion(team.id)
        if (check.blocked) {
            return@withLock ActionResult.fail("${team.name} plays in ${check.tournamentNames.joinToString()}. Delete that tournament first.")
        }
        db.withTransaction {
            matchDao.getMatchesForTeamSync(team.id).forEach { matchDao.deleteMatchById(it.id) }
            teamDao.deleteTeam(team)
        }
        ActionResult.done("${team.name} deleted")
    }

    // ---------------------------------------------------------------- Grounds

    suspend fun saveGround(ground: GroundEntity): Long =
        if (ground.id == 0L) groundDao.insertGround(ground) else {
            groundDao.updateGround(ground); ground.id
        }

    suspend fun deleteGround(ground: GroundEntity) = groundDao.deleteGround(ground)

    // ---------------------------------------------------------------- Tournaments

    suspend fun createTournament(
        name: String,
        format: TournamentFormat,
        orderedTeamIds: List<Long>,
        encountersPerOpponent: Int,
        oversPerInnings: Int,
        ballsPerOver: Int,
        groundId: Long?
    ): Long = writeLock.withLock {
        val id = db.withTransaction {
            val tid = tournamentDao.insertTournament(
                TournamentEntity(
                    name = name.trim(),
                    format = format,
                    encountersPerOpponent = encountersPerOpponent,
                    oversPerInnings = oversPerInnings,
                    ballsPerOver = ballsPerOver,
                    status = TournamentStatus.UPCOMING
                )
            )
            tournamentDao.insertTournamentTeamCrossRefs(orderedTeamIds.map { TournamentTeamCrossRef(tid, it) })
            val created = tournamentDao.getTournamentById(tid)!!
            val byId = teamDao.getTeamsByIds(orderedTeamIds).associateBy { it.id }
            val teams = orderedTeamIds.mapNotNull { byId[it] }
            matchDao.insertMatches(TournamentScheduler.generateInitialFixtures(created, teams, groundId))
            tid
        }
        syncTournamentLocked(id)
        id
    }

    suspend fun renameTournament(id: Long, name: String) {
        val t = tournamentDao.getTournamentById(id) ?: return
        tournamentDao.updateTournament(t.copy(name = name.trim()))
    }

    suspend fun deleteTournament(id: Long): ActionResult = writeLock.withLock {
        val t = tournamentDao.getTournamentById(id) ?: return@withLock ActionResult.fail("Tournament not found")
        db.withTransaction {
            matchDao.deleteMatchesForTournament(id)
            tournamentDao.deleteTournament(t)
        }
        ActionResult.done("${t.name} deleted")
    }

    suspend fun syncTournament(id: Long) = writeLock.withLock { syncTournamentLocked(id) }

    private suspend fun syncTournamentLocked(id: Long) {
        db.withTransaction {
            val t = tournamentDao.getTournamentById(id) ?: return@withTransaction
            val teams = tournamentDao.getTeamsForTournamentSync(id)
            val matches = matchDao.getMatchesForTournamentSync(id)
            val innings = inningsDao.getInningsForTournamentSync(id).groupBy { it.matchId }
            val plan = TournamentProgression.plan(t, teams, matches, innings)
            plan.matchIdsToDelete.forEach { matchDao.deleteMatchById(it) }
            if (plan.matchesToInsert.isNotEmpty()) matchDao.insertMatches(plan.matchesToInsert)
            if (t.status != plan.status || t.winnerTeamId != plan.championTeamId) {
                tournamentDao.updateTournament(t.copy(status = plan.status, winnerTeamId = plan.championTeamId))
            }
        }
    }

    // ---------------------------------------------------------------- Match setup

    suspend fun createMatch(
        team1Id: Long,
        team2Id: Long,
        groundId: Long?,
        matchType: MatchType,
        overs: Int,
        ballsPerOver: Int,
        soloWickets1: Int,
        soloWickets2: Int,
        tossWinnerId: Long?,
        tossDecision: TossDecision?
    ): Long = writeLock.withLock {
        db.withTransaction {
            val match = MatchEntity(
                tournamentId = null,
                stage = MatchStage.STANDALONE,
                groundId = groundId,
                team1Id = team1Id,
                team2Id = team2Id,
                matchType = matchType,
                totalOversPerInnings = overs,
                ballsPerOver = ballsPerOver,
                soloPlayer1Wickets = soloWickets1,
                soloPlayer2Wickets = soloWickets2,
                tossWinnerTeamId = tossWinnerId,
                tossDecision = tossDecision,
                currentInningsIndex = 1,
                status = if (tossWinnerId != null) MatchStatus.IN_PROGRESS else MatchStatus.SCHEDULED
            )
            val id = matchDao.insertMatch(match)
            if (tossWinnerId != null) insertFirstInnings(match.copy(id = id))
            id
        }
    }

    /** Same teams and settings, toss still to be done. */
    suspend fun createRematch(matchId: Long): Long? = writeLock.withLock {
        val m = matchDao.getMatchById(matchId) ?: return@withLock null
        matchDao.insertMatch(
            m.copy(
                id = 0,
                tournamentId = null,
                stage = MatchStage.STANDALONE,
                roundIndex = 1,
                matchNumberInRound = 1,
                tossWinnerTeamId = null,
                tossDecision = null,
                currentInningsIndex = 1,
                status = MatchStatus.SCHEDULED,
                winnerTeamId = null,
                potmPlayerId = null,
                resultSummary = "",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun insertFirstInnings(match: MatchEntity) {
        val battingId = LiveStateBuilder.firstBattingTeamId(match)
        val bowlingId = if (battingId == match.team1Id) match.team2Id else match.team1Id
        inningsDao.insertInnings(
            InningsEntity(
                matchId = match.id,
                inningsIndex = 1,
                battingTeamId = battingId,
                bowlingTeamId = bowlingId,
                maxWickets = maxWicketsFor(match, battingId)
            )
        )
    }

    private suspend fun maxWicketsFor(match: MatchEntity, battingTeamId: Long): Int {
        val team = teamDao.getTeamById(battingTeamId)
        val squad = teamDao.getPlayersForTeamSync(battingTeamId).size
        return CricketRules.maxWicketsFor(team?.isSoloTeam == true, CricketRules.soloLivesFor(match, battingTeamId), squad)
    }

    // ---------------------------------------------------------------- Live match snapshot

    suspend fun loadSnapshot(matchId: Long): LiveScoreState? = db.withTransaction { loadSnapshotInternal(matchId) }

    private suspend fun loadSnapshotInternal(matchId: Long): LiveScoreState? {
        val match = matchDao.getMatchById(matchId) ?: return null
        val team1 = teamDao.getTeamById(match.team1Id) ?: TeamEntity(id = match.team1Id, name = "Deleted team", shortName = "DEL")
        val team2 = teamDao.getTeamById(match.team2Id) ?: TeamEntity(id = match.team2Id, name = "Deleted team", shortName = "DEL")
        return LiveStateBuilder.build(
            match = match,
            team1 = team1,
            team2 = team2,
            squad1 = teamDao.getPlayersForTeamSync(team1.id),
            squad2 = teamDao.getPlayersForTeamSync(team2.id),
            allPlayers = playerDao.getAllPlayersSync(),
            rawInnings = inningsDao.getInningsForMatchSync(matchId),
            balls = ballDao.getBallsForMatchSync(matchId)
        )
    }

    /** Runs a scoring change atomically, then saves the result if the match just ended. */
    private suspend fun mutate(matchId: Long, block: suspend (LiveScoreState) -> ActionResult): ActionResult =
        writeLock.withLock {
            val result = db.withTransaction {
                val snap = loadSnapshotInternal(matchId) ?: return@withTransaction ActionResult.fail("Match not found")
                block(snap)
            }
            finalizeIfNeededLocked(matchId)
            matchDao.getMatchById(matchId)?.tournamentId?.let { syncTournamentLocked(it) }
            result
        }

    /**
     * Run once at start-up. Saves results for matches that finished on the field but were never
     * marked complete (older versions only did that from the Done button), then brings every
     * tournament's table, bracket and champion up to date.
     */
    suspend fun repairOnLaunch() = writeLock.withLock {
        for (m in matchDao.getAllMatchesSync()) {
            if (!m.status.isFinished && inningsDao.getInningsForMatchSync(m.id).isNotEmpty()) {
                finalizeIfNeededLocked(m.id)
            }
        }
        for (t in tournamentDao.getAllTournamentsSync()) syncTournamentLocked(t.id)
    }

    private suspend fun finalizeIfNeededLocked(matchId: Long) {
        db.withTransaction {
            val s = loadSnapshotInternal(matchId) ?: return@withTransaction
            val match = s.match
            if (!s.isMatchComplete || match.status.isFinished) return@withTransaction
            val result = s.liveResult ?: CricketScoringEngine.result(match, s.team1, s.team2, s.allInnings)
            val winningSide = when (result.winnerTeamId) {
                s.team1.id -> s.squad1.map { it.id }.toSet()
                s.team2.id -> s.squad2.map { it.id }.toSet()
                else -> emptySet()
            }
            val potm = PlayerOfTheMatchEngine.determinePlayerOfTheMatch(s.allBalls, match.ballsPerOver, winningSide)
            s.currentInnings?.let { inn ->
                if (!inn.isCompleted) inningsDao.updateInnings(inn.copy(isCompleted = true))
            }
            matchDao.updateMatch(
                match.copy(
                    status = MatchStatus.COMPLETED,
                    winnerTeamId = result.winnerTeamId,
                    resultSummary = result.summary,
                    potmPlayerId = potm
                )
            )
        }
    }

    // ---------------------------------------------------------------- Scoring actions

    suspend fun startMatch(matchId: Long, tossWinnerId: Long, decision: TossDecision): ActionResult = mutate(matchId) { s ->
        if (s.currentInnings != null) return@mutate ActionResult.fail("This match has already started")
        val updated = s.match.copy(
            tossWinnerTeamId = tossWinnerId,
            tossDecision = decision,
            status = MatchStatus.IN_PROGRESS,
            currentInningsIndex = 1
        )
        matchDao.updateMatch(updated)
        insertFirstInnings(updated)
        ActionResult.OK
    }

    suspend fun setOpeners(matchId: Long, strikerId: Long, nonStrikerId: Long?, bowlerId: Long): ActionResult = mutate(matchId) { s ->
        val inn = s.currentInnings ?: return@mutate ActionResult.fail("Do the toss first")
        if (!s.isSoloBatting && (nonStrikerId == null || nonStrikerId == strikerId)) {
            return@mutate ActionResult.fail("Pick two different batters")
        }
        inningsDao.updateInnings(
            inn.copy(
                strikerId = strikerId,
                nonStrikerId = if (s.isSoloBatting) null else nonStrikerId,
                bowlerId = bowlerId
            )
        )
        if (s.match.status == MatchStatus.SCHEDULED) matchDao.updateMatch(s.match.copy(status = MatchStatus.IN_PROGRESS))
        ActionResult.OK
    }

    suspend fun recordDelivery(matchId: Long, input: DeliveryInput): ActionResult = mutate(matchId) { s ->
        val inn = s.currentInnings
        val striker = s.striker
        val bowler = s.bowler
        if (!s.canScore || inn == null || striker == null || bowler == null) {
            return@mutate ActionResult.fail(
                when {
                    s.isFinished -> "This match is finished"
                    s.needsNewBowler -> "Choose the bowler for the next over"
                    s.needsOpeners -> "Choose the opening batters and bowler"
                    else -> "This innings is over"
                }
            )
        }
        val wicket = input.wicket
        val allOutAfter = wicket != null && s.wickets + 1 >= s.maxWicketsAllowed
        if (wicket != null && !s.isSoloBatting && !allOutAfter && wicket.newBatterId == null && s.availableBatters.isNotEmpty()) {
            return@mutate ActionResult.fail("Choose the next batter")
        }

        val bpo = s.match.ballsPerOver.coerceAtLeast(1)
        val legalBefore = s.legalBalls
        val legal = CricketRules.isLegalBall(input.extraType)
        val ball = BallEntity(
            matchId = s.match.id,
            inningsId = inn.id,
            overIndex = legalBefore / bpo,
            ballInOver = legalBefore % bpo + if (legal) 1 else 0,
            legalBallNumber = legalBefore + if (legal) 1 else 0,
            strikerPlayerId = striker.id,
            nonStrikerPlayerId = s.nonStriker?.id,
            bowlerPlayerId = bowler.id,
            runsOffBat = input.runsOffBat,
            extraType = input.extraType,
            extraRuns = input.extraRuns,
            isWicket = wicket != null,
            dismissalType = wicket?.dismissal ?: DismissalType.NONE,
            outPlayerId = wicket?.let { it.outPlayerId ?: striker.id },
            fielderPlayerId = wicket?.fielderId,
            isPenaltyRun = false,
            commentary = ""
        )
        ballDao.insertBall(ball.copy(commentary = ScorecardBuilder.ballLabel(ball)))

        val next = CreaseLogic.afterDelivery(
            crease = Crease(striker.id, s.nonStriker?.id, bowler.id),
            input = input,
            legalBallsBefore = legalBefore,
            ballsPerOver = bpo,
            wicketsBefore = s.wickets,
            maxWickets = s.maxWicketsAllowed,
            isSoloBatting = s.isSoloBatting,
            keepBowlerAtOverEnd = s.bowlingSquad.size <= 1
        )
        val totals = CricketRules.totals(ballDao.getBallsForInningsSync(inn.id))
        // Not all out but nobody left to come in (e.g. players removed mid-match): the innings ends.
        val noOneLeft = wicket != null && !s.isSoloBatting && next.strikerId == null && totals.wickets < s.maxWicketsAllowed
        inningsDao.updateInnings(
            inn.copy(
                runs = totals.runs,
                wickets = totals.wickets,
                ballsBowled = totals.legalBalls,
                strikerId = next.strikerId,
                nonStrikerId = next.nonStrikerId,
                bowlerId = next.bowlerId,
                maxWickets = s.maxWicketsAllowed,
                isCompleted = inn.isCompleted || noOneLeft
            )
        )
        if (s.match.status == MatchStatus.SCHEDULED) matchDao.updateMatch(s.match.copy(status = MatchStatus.IN_PROGRESS))
        ActionResult.OK
    }

    suspend fun addPenaltyRuns(matchId: Long, runs: Int): ActionResult = mutate(matchId) { s ->
        val inn = s.currentInnings ?: return@mutate ActionResult.fail("The match hasn't started")
        if (s.isFinished) return@mutate ActionResult.fail("This match is finished")
        if (runs <= 0) return@mutate ActionResult.fail("Enter at least 1 run")
        val bpo = s.match.ballsPerOver.coerceAtLeast(1)
        val strikerId = s.striker?.id ?: s.lastBall?.strikerPlayerId ?: s.battingSquad.firstOrNull()?.id ?: 0L
        val bowlerId = s.bowler?.id ?: s.lastBall?.bowlerPlayerId ?: s.bowlingSquad.firstOrNull()?.id ?: 0L
        ballDao.insertBall(
            BallEntity(
                matchId = s.match.id,
                inningsId = inn.id,
                overIndex = s.legalBalls / bpo,
                ballInOver = s.legalBalls % bpo,
                legalBallNumber = s.legalBalls,
                strikerPlayerId = strikerId,
                nonStrikerPlayerId = s.nonStriker?.id,
                bowlerPlayerId = bowlerId,
                extraType = ExtraType.PENALTY,
                extraRuns = runs,
                isPenaltyRun = true,
                commentary = "P$runs"
            )
        )
        inningsDao.updateInnings(inn.copy(penaltyRuns = inn.penaltyRuns + runs))
        ActionResult.done("+$runs penalty runs to ${s.battingTeam.shortName}")
    }

    suspend fun undoLastBall(matchId: Long): ActionResult = mutate(matchId) { s ->
        val match = s.match
        if (match.status == MatchStatus.DECLARED_WINNER || match.status == MatchStatus.ABANDONED_RAIN) {
            return@mutate ActionResult.fail("Reopen the match before undoing")
        }
        val inn = s.currentInnings ?: return@mutate ActionResult.fail("Nothing to undo")
        val last = s.inningsBalls.lastOrNull()
        val reopened = match.copy(status = MatchStatus.IN_PROGRESS, winnerTeamId = null, resultSummary = "", potmPlayerId = null)

        if (last == null) {
            if (inn.inningsIndex > 1) {
                inningsDao.deleteInningsById(inn.id)
                s.allInnings.find { it.inningsIndex == inn.inningsIndex - 1 }?.let {
                    inningsDao.updateInnings(it.copy(isCompleted = false))
                }
                matchDao.updateMatch(reopened.copy(currentInningsIndex = inn.inningsIndex - 1))
                return@mutate ActionResult.done("Back to innings ${inn.inningsIndex - 1}")
            }
            if (inn.strikerId != null || inn.bowlerId != null) {
                inningsDao.updateInnings(inn.copy(strikerId = null, nonStrikerId = null, bowlerId = null))
                return@mutate ActionResult.done("Openers cleared")
            }
            return@mutate ActionResult.fail("Nothing to undo")
        }

        ballDao.deleteBallById(last.id)
        val totals = CricketRules.totals(ballDao.getBallsForInningsSync(inn.id))
        val restored = if (last.isPenaltyRun) {
            inn.copy(penaltyRuns = (inn.penaltyRuns - last.extraRuns).coerceAtLeast(0))
        } else {
            inn.copy(
                strikerId = last.strikerPlayerId,
                nonStrikerId = if (s.isSoloBatting) null else last.nonStrikerPlayerId,
                bowlerId = last.bowlerPlayerId
            )
        }
        inningsDao.updateInnings(
            restored.copy(
                runs = totals.runs,
                wickets = totals.wickets,
                ballsBowled = totals.legalBalls,
                isCompleted = false
            )
        )
        if (match.status == MatchStatus.COMPLETED) matchDao.updateMatch(reopened)
        ActionResult.done("Undone: ${ScorecardBuilder.ballLabel(last)}")
    }

    suspend fun selectBowler(matchId: Long, bowlerId: Long): ActionResult = mutate(matchId) { s ->
        val inn = s.currentInnings ?: return@mutate ActionResult.fail("The match hasn't started")
        if (!s.isSoloBowling && s.bowlingSquad.size > 1 && bowlerId == s.previousBowlerId) {
            return@mutate ActionResult.fail("A bowler can't bowl two overs in a row")
        }
        inningsDao.updateInnings(
            inn.copy(
                bowlerId = bowlerId,
                // Persist the crease too, so matches from the old version pick up the current batters.
                strikerId = s.striker?.id ?: inn.strikerId,
                nonStrikerId = s.nonStriker?.id ?: inn.nonStrikerId
            )
        )
        ActionResult.OK
    }

    suspend fun swapStrike(matchId: Long): ActionResult = mutate(matchId) { s ->
        val inn = s.currentInnings ?: return@mutate ActionResult.fail("The match hasn't started")
        if (s.isSoloBatting || s.nonStriker == null) return@mutate ActionResult.fail("Only one batter at the crease")
        inningsDao.updateInnings(
            inn.copy(strikerId = s.nonStriker.id, nonStrikerId = s.striker?.id, bowlerId = s.bowler?.id)
        )
        ActionResult.done("Strike changed: ${s.nonStriker.name} on strike")
    }

    /** Replace the batters or bowler (substitutes, retired hurt, corrections). */
    suspend fun updateCrease(matchId: Long, strikerId: Long?, nonStrikerId: Long?, bowlerId: Long?): ActionResult = mutate(matchId) { s ->
        val inn = s.currentInnings ?: return@mutate ActionResult.fail("The match hasn't started")
        if (!s.isSoloBatting && strikerId != null && strikerId == nonStrikerId) {
            return@mutate ActionResult.fail("Striker and non-striker must be different players")
        }
        inningsDao.updateInnings(
            inn.copy(
                strikerId = strikerId,
                nonStrikerId = if (s.isSoloBatting) null else nonStrikerId,
                bowlerId = bowlerId
            )
        )
        ActionResult.done("Players updated")
    }

    suspend fun endInnings(matchId: Long): ActionResult = mutate(matchId) { s ->
        val inn = s.currentInnings ?: return@mutate ActionResult.fail("The match hasn't started")
        if (s.isFinished) return@mutate ActionResult.fail("This match is finished")
        inningsDao.updateInnings(inn.copy(isCompleted = true))
        ActionResult.done("Innings ${inn.inningsIndex} ended")
    }

    suspend fun startNextInnings(matchId: Long): ActionResult = mutate(matchId) { s ->
        val inn = s.currentInnings ?: return@mutate ActionResult.fail("The match hasn't started")
        if (!s.isInningsComplete || s.isMatchComplete || s.isFinished) return@mutate ActionResult.fail("The innings isn't over yet")
        val nextIndex = inn.inningsIndex + 1
        if (nextIndex > CricketScoringEngine.maxInnings(s.match)) return@mutate ActionResult.fail("No more innings in this format")
        if (s.allInnings.any { it.inningsIndex == nextIndex }) return@mutate ActionResult.OK
        inningsDao.updateInnings(inn.copy(isCompleted = true))
        inningsDao.insertInnings(
            InningsEntity(
                matchId = s.match.id,
                inningsIndex = nextIndex,
                battingTeamId = inn.bowlingTeamId,
                bowlingTeamId = inn.battingTeamId,
                maxWickets = maxWicketsFor(s.match, inn.bowlingTeamId)
            )
        )
        matchDao.updateMatch(s.match.copy(currentInningsIndex = nextIndex, status = MatchStatus.IN_PROGRESS))
        ActionResult.OK
    }

    suspend fun changeOvers(matchId: Long, overs: Int): ActionResult = mutate(matchId) { s ->
        if (s.isFinished) return@mutate ActionResult.fail("This match is finished")
        val minOvers = (s.legalBalls + s.match.ballsPerOver - 1) / s.match.ballsPerOver.coerceAtLeast(1)
        val value = overs.coerceIn(maxOf(1, minOvers), 100)
        matchDao.updateMatch(s.match.copy(totalOversPerInnings = value))
        ActionResult.done("Now $value overs per innings")
    }

    suspend fun declareWinner(matchId: Long, winnerTeamId: Long, reason: String): ActionResult = mutate(matchId) { s ->
        val name = if (winnerTeamId == s.team1.id) s.team1.name else s.team2.name
        matchDao.updateMatch(
            s.match.copy(
                status = MatchStatus.DECLARED_WINNER,
                winnerTeamId = winnerTeamId,
                resultSummary = "$name won ($reason)"
            )
        )
        ActionResult.done("$name awarded the match")
    }

    suspend fun abandonMatch(matchId: Long): ActionResult = mutate(matchId) { s ->
        matchDao.updateMatch(
            s.match.copy(
                status = MatchStatus.ABANDONED_RAIN,
                winnerTeamId = null,
                potmPlayerId = null,
                resultSummary = "No result (match abandoned)"
            )
        )
        ActionResult.done("Match abandoned: 1 point each")
    }

    /** Knockout matches need a winner even after a tie or a washout. */
    suspend fun settleKnockout(matchId: Long, winnerTeamId: Long, decision: KnockoutDecision): ActionResult = mutate(matchId) { s ->
        if (!s.match.status.isFinished) return@mutate ActionResult.fail("Finish the match first")
        val name = if (winnerTeamId == s.team1.id) s.team1.name else s.team2.name
        val base = s.match.resultSummary.substringBefore(" · ").ifBlank { "Match tied" }
        matchDao.updateMatch(
            s.match.copy(winnerTeamId = winnerTeamId, resultSummary = "$base · $name ${decision.phrase}")
        )
        ActionResult.done("$name go through")
    }

    /** Undo an awarded result or abandonment and carry on scoring. */
    suspend fun reopenMatch(matchId: Long): ActionResult = mutate(matchId) { s ->
        if (!s.match.status.isFinished) return@mutate ActionResult.fail("The match is still open")
        val hasInnings = s.allInnings.isNotEmpty()
        matchDao.updateMatch(
            s.match.copy(
                status = if (hasInnings) MatchStatus.IN_PROGRESS else MatchStatus.SCHEDULED,
                winnerTeamId = null,
                resultSummary = "",
                potmPlayerId = null
            )
        )
        s.currentInnings?.let { if (it.isCompleted && !s.isMatchComplete) inningsDao.updateInnings(it.copy(isCompleted = false)) }
        ActionResult.done("Match reopened")
    }

    /** Clear every ball and start again from the toss. */
    suspend fun resetMatch(matchId: Long): ActionResult = mutate(matchId) { s ->
        inningsDao.deleteInningsForMatch(matchId)
        matchDao.updateMatch(
            s.match.copy(
                status = MatchStatus.SCHEDULED,
                tossWinnerTeamId = null,
                tossDecision = null,
                currentInningsIndex = 1,
                winnerTeamId = null,
                potmPlayerId = null,
                resultSummary = ""
            )
        )
        ActionResult.done("Match reset")
    }

    suspend fun setPlayerOfMatch(matchId: Long, playerId: Long?): ActionResult = mutate(matchId) { s ->
        matchDao.updateMatch(s.match.copy(potmPlayerId = playerId))
        ActionResult.done("Player of the match updated")
    }

    suspend fun deleteMatch(matchId: Long): ActionResult = writeLock.withLock {
        val m = matchDao.getMatchById(matchId) ?: return@withLock ActionResult.fail("Match not found")
        matchDao.deleteMatchById(matchId)
        m.tournamentId?.let { syncTournamentLocked(it) }
        ActionResult.done("Match deleted")
    }

    /** Adds players from the pool to a team while a match is on. */
    suspend fun addPlayersToSquadDuringMatch(teamId: Long, playerIds: List<Long>) = addPlayersToTeam(teamId, playerIds)
}
