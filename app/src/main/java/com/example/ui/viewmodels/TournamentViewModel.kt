@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BallEntity
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchStage
import com.example.data.model.MatchStatus
import com.example.data.model.PlayerEntity
import com.example.data.model.TeamEntity
import com.example.data.model.TournamentEntity
import com.example.data.model.TournamentFormat
import com.example.data.repository.ActionResult
import com.example.data.repository.CricketRepository
import com.example.data.repository.KnockoutDecision
import com.example.domain.NrrEngine
import com.example.domain.PlayerCareerStats
import com.example.domain.PointsTableRow
import com.example.domain.StatsAggregator
import com.example.domain.TeamStatsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TournamentListItem(
    val tournament: TournamentEntity,
    val teamCount: Int,
    val played: Int,
    val total: Int,
    val live: Int,
    val champion: TeamEntity?
)

data class TournamentDetail(
    val tournament: TournamentEntity,
    val teams: List<TeamEntity>,
    val matches: List<MatchEntity>,
    val inningsByMatch: Map<Long, List<InningsEntity>>,
    val standings: List<PointsTableRow>,
    val teamStats: List<TeamStatsSummary>,
    val playerStats: List<PlayerCareerStats>,
    val champion: TeamEntity?,
    val played: Int,
    val total: Int,
    val qualifiers: Int
) {
    val hasTable: Boolean get() = tournament.format != TournamentFormat.KNOCKOUT
    val teamsById: Map<Long, TeamEntity> get() = teams.associateBy { it.id }
}

private data class TournamentRaw(
    val tournament: TournamentEntity?,
    val teams: List<TeamEntity>,
    val matches: List<MatchEntity>,
    val innings: List<InningsEntity>
)

/** Tournaments list and the selected tournament's table, fixtures and stats, kept live from the database. */
class TournamentViewModel(private val repository: CricketRepository) : ViewModel() {

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    val tournaments: StateFlow<List<TournamentListItem>?> = combine(
        repository.getAllTournaments(),
        repository.getAllMatches(),
        repository.getAllTournamentTeamRefs(),
        repository.getAllTeams()
    ) { tournaments, matches, refs, teams ->
        val teamMap = teams.associateBy { it.id }
        tournaments.map { t ->
            val ms = matches.filter { it.tournamentId == t.id }
            TournamentListItem(
                tournament = t,
                teamCount = refs.count { it.tournamentId == t.id },
                played = ms.count { it.status.isFinished },
                total = ms.size,
                live = ms.count { it.status == MatchStatus.IN_PROGRESS },
                champion = t.winnerTeamId?.let { teamMap[it] }
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = _selectedId.asStateFlow()

    val detail: StateFlow<TournamentDetail?> = _selectedId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else {
                val raw = combine(
                    repository.getTournamentFlow(id),
                    repository.getTeamsForTournament(id),
                    repository.getMatchesForTournament(id),
                    repository.getInningsForTournament(id)
                ) { t, teams, matches, innings -> TournamentRaw(t, teams, matches, innings) }
                combine(
                    raw,
                    repository.getBallsForTournament(id),
                    repository.getAllPlayers()
                ) { r, balls, players -> buildDetail(r, balls, players) }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun buildDetail(r: TournamentRaw, balls: List<BallEntity>, players: List<PlayerEntity>): TournamentDetail? {
        val t = r.tournament ?: return null
        val inningsByMatch = r.innings.groupBy { it.matchId }
        val league = r.matches.filter { it.stage == MatchStage.LEAGUE }
        val standings = if (t.format == TournamentFormat.KNOCKOUT) emptyList()
        else NrrEngine.calculateStandings(r.teams, league, inningsByMatch)
        val playerStats = StatsAggregator.aggregatePlayerStats(players, r.matches, r.innings, balls).filter { it.hasAnyRecord }
        return TournamentDetail(
            tournament = t,
            teams = r.teams,
            matches = r.matches,
            inningsByMatch = inningsByMatch,
            standings = standings,
            teamStats = StatsAggregator.aggregateTeamStats(r.teams, r.matches, r.innings),
            playerStats = playerStats,
            champion = t.winnerTeamId?.let { id -> r.teams.find { it.id == id } },
            played = r.matches.count { it.status.isFinished },
            total = r.matches.size,
            qualifiers = when {
                t.format != TournamentFormat.LEAGUE_AND_KNOCKOUT -> 0
                r.teams.size >= 4 -> 4
                else -> 2
            }
        )
    }

    fun select(id: Long) {
        _selectedId.value = id
        viewModelScope.launch { repository.syncTournament(id) }
    }

    fun clearSelection() {
        _selectedId.value = null
    }

    fun createTournament(
        name: String,
        format: TournamentFormat,
        teamIds: List<Long>,
        encounters: Int,
        overs: Int,
        ballsPerOver: Int,
        groundId: Long?
    ) {
        viewModelScope.launch {
            val id = repository.createTournament(name, format, teamIds, encounters, overs, ballsPerOver, groundId)
            _selectedId.value = id
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { repository.renameTournament(id, name) }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val r = repository.deleteTournament(id)
            if (r.ok && _selectedId.value == id) _selectedId.value = null
            r.message?.let { _events.send(it) }
        }
    }

    private fun act(block: suspend () -> ActionResult) {
        viewModelScope.launch { block().message?.let { _events.send(it) } }
    }

    fun awardMatch(matchId: Long, winnerId: Long) = act { repository.declareWinner(matchId, winnerId, "walkover") }
    fun abandonMatch(matchId: Long) = act { repository.abandonMatch(matchId) }
    fun reopenMatch(matchId: Long) = act { repository.reopenMatch(matchId) }
    fun resetMatch(matchId: Long) = act { repository.resetMatch(matchId) }
    fun deleteMatch(matchId: Long) = act { repository.deleteMatch(matchId) }
    fun settleKnockout(matchId: Long, winnerId: Long, decision: KnockoutDecision) =
        act { repository.settleKnockout(matchId, winnerId, decision) }
}
