@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ExtraType
import com.example.data.model.TossDecision
import com.example.data.repository.ActionResult
import com.example.data.repository.CricketRepository
import com.example.data.repository.KnockoutDecision
import com.example.domain.DeliveryInput
import com.example.domain.LiveScoreState
import com.example.domain.WicketInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ScoringUiState {
    data object Loading : ScoringUiState
    data object Missing : ScoringUiState
    data class Ready(val live: LiveScoreState) : ScoringUiState
}

/** Drives the live scoring console. Every change is written to the database and the state is re-read from it. */
class MatchViewModel(private val repository: CricketRepository) : ViewModel() {

    private val matchId = MutableStateFlow<Long?>(null)
    val activeMatchId: StateFlow<Long?> = matchId.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    val uiState: StateFlow<ScoringUiState> = matchId
        .flatMapLatest { id ->
            if (id == null) flowOf<ScoringUiState>(ScoringUiState.Loading)
            else flow<ScoringUiState> {
                emit(ScoringUiState.Loading)
                val first = repository.getMatchById(id)
                if (first == null) {
                    emit(ScoringUiState.Missing)
                } else {
                    val triggers: Flow<Unit> = merge(
                        repository.getMatchFlow(id).map { },
                        repository.getInningsForMatchFlow(id).map { },
                        repository.getBallsForMatchFlow(id).map { },
                        repository.getPlayersForTeam(first.team1Id).map { },
                        repository.getPlayersForTeam(first.team2Id).map { },
                        repository.getAllTeams().map { },
                        repository.getAllPlayers().map { }
                    )
                    emitAll(
                        triggers.conflate().mapLatest<Unit, ScoringUiState> {
                            val snap = repository.loadSnapshot(id)
                            if (snap == null) ScoringUiState.Missing else ScoringUiState.Ready(snap)
                        }
                    )
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoringUiState.Loading)

    fun open(id: Long) {
        matchId.value = id
    }

    fun close() {
        matchId.value = null
    }

    private fun act(showSuccess: Boolean = false, block: suspend (Long) -> ActionResult) {
        val id = matchId.value ?: return
        viewModelScope.launch {
            val result = block(id)
            val msg = result.message
            if (msg != null && (!result.ok || showSuccess)) _events.send(msg)
        }
    }

    fun scoreRuns(runs: Int) = act { repository.recordDelivery(it, DeliveryInput(runs, ExtraType.NONE, 0)) }

    /** Wide: runs taken are added to the 1 wide. No-ball: runs are off the bat. Byes/leg byes: runs taken. */
    fun scoreExtra(type: ExtraType, runs: Int) = act {
        val input = when (type) {
            ExtraType.WIDE -> DeliveryInput(0, ExtraType.WIDE, 1 + runs)
            ExtraType.NO_BALL -> DeliveryInput(runs, ExtraType.NO_BALL, 1)
            ExtraType.BYE, ExtraType.LEG_BYE -> DeliveryInput(0, type, runs)
            else -> DeliveryInput(runs, ExtraType.NONE, 0)
        }
        repository.recordDelivery(it, input)
    }

    fun recordWicket(wicket: WicketInput, runsCompleted: Int) =
        act { repository.recordDelivery(it, DeliveryInput(runsCompleted, ExtraType.NONE, 0, wicket)) }

    fun undo() = act(showSuccess = true) { repository.undoLastBall(it) }
    fun addPenalty(runs: Int) = act(showSuccess = true) { repository.addPenaltyRuns(it, runs) }
    fun selectBowler(playerId: Long) = act { repository.selectBowler(it, playerId) }
    fun swapStrike() = act(showSuccess = true) { repository.swapStrike(it) }
    fun setOpeners(strikerId: Long, nonStrikerId: Long?, bowlerId: Long) = act { repository.setOpeners(it, strikerId, nonStrikerId, bowlerId) }
    fun startMatch(tossWinnerId: Long, decision: TossDecision) = act { repository.startMatch(it, tossWinnerId, decision) }
    fun updateCrease(strikerId: Long?, nonStrikerId: Long?, bowlerId: Long?) =
        act(showSuccess = true) { repository.updateCrease(it, strikerId, nonStrikerId, bowlerId) }
    fun endInnings() = act(showSuccess = true) { repository.endInnings(it) }
    fun startNextInnings() = act { repository.startNextInnings(it) }
    fun changeOvers(overs: Int) = act(showSuccess = true) { repository.changeOvers(it, overs) }
    fun declareWinner(teamId: Long, reason: String) = act(showSuccess = true) { repository.declareWinner(it, teamId, reason) }
    fun abandon() = act(showSuccess = true) { repository.abandonMatch(it) }
    fun settleKnockout(teamId: Long, decision: KnockoutDecision) = act(showSuccess = true) { repository.settleKnockout(it, teamId, decision) }
    fun reopen() = act(showSuccess = true) { repository.reopenMatch(it) }
    fun reset() = act(showSuccess = true) { repository.resetMatch(it) }
    fun setPlayerOfMatch(playerId: Long?) = act(showSuccess = true) { repository.setPlayerOfMatch(it, playerId) }

    fun addPlayersToSquad(teamId: Long, playerIds: List<Long>) {
        viewModelScope.launch {
            repository.addPlayersToSquadDuringMatch(teamId, playerIds)
            _events.send(if (playerIds.size == 1) "Player added to the squad" else "${playerIds.size} players added to the squad")
        }
    }

    fun rematch(onCreated: (Long) -> Unit) {
        val id = matchId.value ?: return
        viewModelScope.launch {
            val newId = repository.createRematch(id)
            if (newId != null) onCreated(newId)
        }
    }

    fun deleteMatch(onDeleted: () -> Unit) {
        val id = matchId.value ?: return
        viewModelScope.launch {
            val r = repository.deleteMatch(id)
            if (r.ok) onDeleted() else r.message?.let { _events.send(it) }
        }
    }
}
