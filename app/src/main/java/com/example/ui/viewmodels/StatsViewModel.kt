package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BallEntity
import com.example.data.model.MatchEntity
import com.example.data.model.TeamEntity
import com.example.data.model.TeamPlayerCrossRef
import com.example.data.model.TournamentEntity
import com.example.data.repository.CricketRepository
import com.example.domain.HeadToHead
import com.example.domain.PlayerCareerStats
import com.example.domain.StatsAggregator
import com.example.domain.TeamStatsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class StatsBundle(
    val players: List<PlayerCareerStats>,
    val teams: List<TeamStatsSummary>,
    val allTeams: List<TeamEntity>,
    val matches: List<MatchEntity>,
    val playerTeams: Map<Long, List<TeamEntity>>
) {
    fun headToHead(team: TeamEntity): List<HeadToHead> = StatsAggregator.headToHead(team, allTeams, matches)
}

private data class StatsExtras(
    val balls: List<BallEntity>,
    val tournaments: List<TournamentEntity>,
    val refs: List<TeamPlayerCrossRef>
)

/** Career leaderboards, recalculated whenever a ball is recorded or a result changes. */
class StatsViewModel(repository: CricketRepository) : ViewModel() {

    private val extras = combine(
        repository.getAllBalls(),
        repository.getAllTournaments(),
        repository.getAllTeamPlayerRefs()
    ) { balls, tournaments, refs -> StatsExtras(balls, tournaments, refs) }

    val stats: StateFlow<StatsBundle?> = combine(
        repository.getAllPlayers(),
        repository.getAllTeams(),
        repository.getAllMatches(),
        repository.getAllInnings(),
        extras
    ) { players, teams, matches, innings, x ->
        val titles = x.tournaments.mapNotNull { it.winnerTeamId }.groupingBy { it }.eachCount()
        val teamMap = teams.associateBy { it.id }
        StatsBundle(
            players = StatsAggregator.aggregatePlayerStats(players, matches, innings, x.balls),
            teams = StatsAggregator.aggregateTeamStats(teams, matches, innings, titles),
            allTeams = teams,
            matches = matches,
            playerTeams = x.refs.groupBy { it.playerId }.mapValues { (_, r) -> r.mapNotNull { teamMap[it.teamId] } }
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
