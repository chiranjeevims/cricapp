package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.GroundEntity
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchType
import com.example.data.model.TeamEntity
import com.example.data.model.TossDecision
import com.example.data.model.TournamentEntity
import com.example.data.repository.CricketRepository
import com.example.domain.MatchFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MatchListItem(
    val match: MatchEntity,
    val team1: TeamEntity?,
    val team2: TeamEntity?,
    val innings: List<InningsEntity>,
    val ground: GroundEntity?,
    val tournament: TournamentEntity?
) {
    fun scoreFor(teamId: Long): String? = MatchFormat.teamScore(match, innings, teamId)
}

/** Match lists for Home and Matches, plus creating and deleting matches. */
class MatchesViewModel(private val repository: CricketRepository) : ViewModel() {

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { repository.repairOnLaunch() }
    }

    /** Null while loading. Newest first. */
    val matches: StateFlow<List<MatchListItem>?> = combine(
        repository.getAllMatches(),
        repository.getAllInnings(),
        repository.getAllTeams(),
        repository.getAllGrounds(),
        repository.getAllTournaments()
    ) { matches, innings, teams, grounds, tournaments ->
        val teamMap = teams.associateBy { it.id }
        val groundMap = grounds.associateBy { it.id }
        val tourMap = tournaments.associateBy { it.id }
        val inningsByMatch = innings.groupBy { it.matchId }
        matches.map { m ->
            MatchListItem(
                match = m,
                team1 = teamMap[m.team1Id],
                team2 = teamMap[m.team2Id],
                innings = inningsByMatch[m.id].orEmpty(),
                ground = m.groundId?.let { groundMap[it] },
                tournament = m.tournamentId?.let { tourMap[it] }
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createMatch(
        team1Id: Long,
        team2Id: Long,
        groundId: Long?,
        matchType: MatchType,
        overs: Int,
        ballsPerOver: Int,
        soloWickets1: Int,
        soloWickets2: Int,
        tossWinnerId: Long?,
        tossDecision: TossDecision?,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.createMatch(
                team1Id, team2Id, groundId, matchType, overs, ballsPerOver,
                soloWickets1, soloWickets2, tossWinnerId, tossDecision
            )
            onCreated(id)
        }
    }

    fun deleteMatch(matchId: Long) {
        viewModelScope.launch {
            repository.deleteMatch(matchId).message?.let { _events.send(it) }
        }
    }
}
