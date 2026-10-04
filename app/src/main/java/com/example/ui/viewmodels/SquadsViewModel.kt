package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.GroundEntity
import com.example.data.model.PlayerEntity
import com.example.data.model.TeamEntity
import com.example.data.repository.CricketRepository
import com.example.data.repository.TeamDeleteCheck
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

data class TeamListItem(
    val team: TeamEntity,
    val players: List<PlayerEntity>,
    val captainId: Long?,
    val keeperId: Long?,
    val matchesPlayed: Int
)

data class PlayerListItem(val player: PlayerEntity, val teams: List<TeamEntity>)

data class GroundListItem(val ground: GroundEntity, val matches: Int, val averageFirstInnings: Int?)

/** Teams, the player pool and grounds. */
class SquadsViewModel(private val repository: CricketRepository) : ViewModel() {

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    val teams: StateFlow<List<TeamListItem>?> = combine(
        repository.getAllTeams(),
        repository.getAllPlayers(),
        repository.getAllTeamPlayerRefs(),
        repository.getAllMatches()
    ) { teams, players, refs, matches ->
        val playerMap = players.associateBy { it.id }
        val refsByTeam = refs.groupBy { it.teamId }
        teams.map { team ->
            val teamRefs = refsByTeam[team.id].orEmpty()
            val roster = teamRefs
                .sortedWith(compareByDescending<com.example.data.model.TeamPlayerCrossRef> { it.isCaptain }
                    .thenBy { playerMap[it.playerId]?.name?.lowercase() ?: "" })
                .mapNotNull { playerMap[it.playerId] }
            TeamListItem(
                team = team,
                players = roster,
                captainId = teamRefs.firstOrNull { it.isCaptain }?.playerId,
                keeperId = teamRefs.firstOrNull { it.isWicketKeeper }?.playerId,
                matchesPlayed = matches.count { (it.team1Id == team.id || it.team2Id == team.id) && it.status.isFinished }
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val players: StateFlow<List<PlayerListItem>?> = combine(
        repository.getAllPlayers(),
        repository.getAllTeams(),
        repository.getAllTeamPlayerRefs()
    ) { players, teams, refs ->
        val teamMap = teams.associateBy { it.id }
        val teamsByPlayer = refs.groupBy { it.playerId }
        players.map { p -> PlayerListItem(p, teamsByPlayer[p.id].orEmpty().mapNotNull { teamMap[it.teamId] }) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val grounds: StateFlow<List<GroundListItem>?> = combine(
        repository.getAllGrounds(),
        repository.getAllMatches(),
        repository.getAllInnings()
    ) { grounds, matches, innings ->
        val firstInnings = innings.filter { it.inningsIndex == 1 && it.ballsBowled > 0 }.associateBy { it.matchId }
        grounds.map { g ->
            val ms = matches.filter { it.groundId == g.id && it.status.isFinished }
            val firsts = ms.mapNotNull { firstInnings[it.id] }.map { it.runs + it.penaltyRuns }
            GroundListItem(g, ms.size, if (firsts.isEmpty()) null else firsts.average().toInt())
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ------------------------------------------------------------ Teams

    fun createTeam(team: TeamEntity, playerIds: List<Long>) {
        viewModelScope.launch {
            repository.createTeam(team, playerIds)
            _events.send("${team.name} created")
        }
    }

    fun updateTeam(team: TeamEntity) {
        viewModelScope.launch { repository.updateTeam(team) }
    }

    fun addPlayersToTeam(teamId: Long, playerIds: List<Long>) {
        viewModelScope.launch { repository.addPlayersToTeam(teamId, playerIds) }
    }

    fun removePlayerFromTeam(teamId: Long, playerId: Long) {
        viewModelScope.launch { repository.removePlayerFromTeam(teamId, playerId) }
    }

    fun setCaptain(teamId: Long, playerId: Long) {
        viewModelScope.launch { repository.setCaptain(teamId, playerId) }
    }

    fun toggleKeeper(teamId: Long, playerId: Long) {
        viewModelScope.launch { repository.toggleWicketKeeper(teamId, playerId) }
    }

    fun checkTeamDeletion(teamId: Long, onResult: (TeamDeleteCheck) -> Unit) {
        viewModelScope.launch { onResult(repository.checkTeamDeletion(teamId)) }
    }

    fun deleteTeam(team: TeamEntity, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val r = repository.deleteTeam(team)
            r.message?.let { _events.send(it) }
            if (r.ok) onDeleted()
        }
    }

    // ------------------------------------------------------------ Players

    /** Creates or updates a player; new players can go straight into a team. */
    fun savePlayer(player: PlayerEntity, addToTeamId: Long? = null) {
        viewModelScope.launch {
            val id = repository.savePlayer(player)
            if (addToTeamId != null) repository.addPlayersToTeam(addToTeamId, listOf(id))
            if (player.id == 0L) _events.send("${player.name} added")
        }
    }

    fun deletePlayer(player: PlayerEntity) {
        viewModelScope.launch { repository.deletePlayer(player).message?.let { _events.send(it) } }
    }

    // ------------------------------------------------------------ Grounds

    fun saveGround(ground: GroundEntity) {
        viewModelScope.launch { repository.saveGround(ground) }
    }

    fun deleteGround(ground: GroundEntity) {
        viewModelScope.launch {
            repository.deleteGround(ground)
            _events.send("${ground.name} deleted")
        }
    }
}
