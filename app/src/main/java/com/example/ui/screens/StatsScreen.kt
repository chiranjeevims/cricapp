package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TeamEntity
import com.example.domain.PlayerCareerStats
import com.example.domain.TeamStatsSummary
import com.example.ui.components.CardSurface
import com.example.ui.components.ChoicePill
import com.example.ui.components.EmptyState
import com.example.ui.components.FieldLabel
import com.example.ui.components.FullScreenDialog
import com.example.ui.components.LoadingBox
import com.example.ui.components.PlayerAvatar
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SegmentedControl
import com.example.ui.components.StatCell
import com.example.ui.components.TeamBadge
import com.example.ui.components.ThinDivider
import com.example.ui.components.format1
import com.example.ui.components.format2
import com.example.ui.components.teamColor
import com.example.ui.theme.LocalCricPalette
import com.example.ui.theme.NumberStyle
import com.example.ui.viewmodels.StatsBundle
import com.example.ui.viewmodels.StatsViewModel

@Composable
fun StatsScreen(vm: StatsViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var profile by remember { mutableStateOf<PlayerCareerStats?>(null) }
    var teamProfile by remember { mutableStateOf<TeamStatsSummary?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Stats", subtitle = "Career records across every match")
        SegmentedControl(
            listOf("Batting", "Bowling", "Fielding", "Teams"),
            tab,
            { tab = it; sort = 0 },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        val s = stats
        if (s == null) {
            LoadingBox()
        } else {
            when (tab) {
                0 -> BattingBoard(s, sort, { sort = it }) { profile = it }
                1 -> BowlingBoard(s, sort, { sort = it }) { profile = it }
                2 -> FieldingBoard(s) { profile = it }
                else -> TeamsBoard(s) { teamProfile = it }
            }
        }
    }

    val s = stats
    profile?.let { p -> if (s != null) PlayerProfileDialog(p, s.playerTeams[p.player.id].orEmpty()) { profile = null } }
    teamProfile?.let { t -> if (s != null) TeamProfileDialog(t, s) { teamProfile = null } }
}

@Composable
private fun SortChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { i, o -> ChoicePill(o, i == selected, { onSelect(i) }) }
    }
}

@Composable
private fun LeaderTable(
    headers: List<String>,
    rows: List<PlayerCareerStats>,
    teamsOf: (PlayerCareerStats) -> String,
    values: (PlayerCareerStats) -> List<String>,
    highlight: Int,
    onOpen: (PlayerCareerStats) -> Unit,
    emptyTitle: String,
    emptyMessage: String
) {
    if (rows.isEmpty()) {
        EmptyState(icon = Icons.Default.Leaderboard, title = emptyTitle, message = emptyMessage)
        return
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val palette = LocalCricPalette.current
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Player", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(3.2f).padding(start = 30.dp))
                for (h in headers) {
                    Text(h, style = MaterialTheme.typography.labelMedium, color = muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
            ThinDivider()
        }
        itemsIndexed(rows, key = { _, r -> r.player.id }) { index, r ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(r) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(3.2f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}",
                        style = NumberStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = if (index < 3) palette.accentText else muted,
                        modifier = Modifier.width(30.dp)
                    )
                    Column {
                        Text(r.player.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val team = teamsOf(r)
                        if (team.isNotBlank()) Text(team, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
                    }
                }
                values(r).forEachIndexed { i, v ->
                    Text(
                        v,
                        style = NumberStyle.copy(fontSize = if (i == highlight) 18.sp else 16.sp, fontWeight = if (i == highlight) FontWeight.Bold else FontWeight.Medium),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            ThinDivider()
        }
    }
}

private fun teamLabel(s: StatsBundle, p: PlayerCareerStats): String = s.playerTeams[p.player.id].orEmpty().joinToString { it.shortName }

@Composable
private fun BattingBoard(s: StatsBundle, sort: Int, onSort: (Int) -> Unit, onOpen: (PlayerCareerStats) -> Unit) {
    val options = listOf("Runs", "Average", "Strike rate", "Sixes", "Fifties")
    Column {
        SortChips(options, sort, onSort)
        val batted = s.players.filter { it.hasBatted }
        val rows = when (sort) {
            1 -> batted.filter { it.innings >= 2 }.sortedByDescending { it.battingAverage }
            2 -> batted.filter { it.ballsFaced >= 10 }.sortedByDescending { it.strikeRate }
            3 -> batted.sortedByDescending { it.sixes }
            4 -> batted.sortedWith(compareByDescending<PlayerCareerStats> { it.fifties + it.hundreds * 2 }.thenByDescending { it.runs })
            else -> batted.sortedWith(compareByDescending<PlayerCareerStats> { it.runs }.thenByDescending { it.strikeRate })
        }
        LeaderTable(
            headers = listOf("Inn", "Runs", "HS", "Avg", "SR"),
            rows = rows,
            teamsOf = { teamLabel(s, it) },
            values = { listOf("${it.innings}", "${it.runs}", it.highestScoreDisplay, it.battingAverage.format1(), it.strikeRate.format1()) },
            highlight = when (sort) {
                1 -> 3
                2 -> 4
                else -> 1
            },
            onOpen = onOpen,
            emptyTitle = "No batting records yet",
            emptyMessage = if (sort == 1) "Averages show once a player has batted twice." else if (sort == 2) "Strike rates show after 10 balls faced." else "Score a match ball by ball and the leaders appear here."
        )
    }
}

@Composable
private fun BowlingBoard(s: StatsBundle, sort: Int, onSort: (Int) -> Unit, onOpen: (PlayerCareerStats) -> Unit) {
    val options = listOf("Wickets", "Economy", "Average", "Best figures")
    Column {
        SortChips(options, sort, onSort)
        val bowled = s.players.filter { it.hasBowled }
        val rows = when (sort) {
            1 -> bowled.filter { it.ballsBowled >= 12 }.sortedBy { it.economyRate }
            2 -> bowled.filter { it.wickets > 0 }.sortedBy { it.bowlingAverage }
            3 -> bowled.filter { it.bestBowlingWickets > 0 }.sortedWith(compareByDescending<PlayerCareerStats> { it.bestBowlingWickets }.thenBy { it.bestBowlingRuns })
            else -> bowled.sortedWith(compareByDescending<PlayerCareerStats> { it.wickets }.thenBy { it.economyRate })
        }
        LeaderTable(
            headers = listOf("Ov", "Wkts", "Best", "Econ"),
            rows = rows,
            teamsOf = { teamLabel(s, it) },
            values = { listOf(it.oversBowledDisplay, "${it.wickets}", it.bestBowlingDisplay, it.economyRate.format2()) },
            highlight = when (sort) {
                1 -> 3
                3 -> 2
                else -> 1
            },
            onOpen = onOpen,
            emptyTitle = "No bowling records yet",
            emptyMessage = if (sort == 1) "Economy rates show after 2 overs bowled." else "Score a match ball by ball and the leaders appear here."
        )
    }
}

@Composable
private fun FieldingBoard(s: StatsBundle, onOpen: (PlayerCareerStats) -> Unit) {
    val rows = s.players.filter { it.catches + it.runOuts + it.stumpings > 0 }
        .sortedByDescending { it.catches + it.runOuts + it.stumpings }
    LeaderTable(
        headers = listOf("Ct", "RO", "St", "Total"),
        rows = rows,
        teamsOf = { teamLabel(s, it) },
        values = { listOf("${it.catches}", "${it.runOuts}", "${it.stumpings}", "${it.catches + it.runOuts + it.stumpings}") },
        highlight = 3,
        onOpen = onOpen,
        emptyTitle = "No fielding records yet",
        emptyMessage = "Choose the fielder when you record a catch, run out or stumping."
    )
}

@Composable
private fun TeamsBoard(s: StatsBundle, onOpen: (TeamStatsSummary) -> Unit) {
    val rows = s.teams.sortedWith(compareByDescending<TeamStatsSummary> { it.titles }.thenByDescending { it.won }.thenByDescending { it.winPercentage })
    if (rows.isEmpty()) {
        EmptyState(icon = Icons.Default.Leaderboard, title = "No teams yet", message = "Create teams in Squads.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(rows, key = { _, t -> t.team.id }) { _, t ->
            CardSurface(onClick = { onOpen(t) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(t.team, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t.team.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Played ${t.matchesPlayed}, won ${t.won}, lost ${t.lost}" + if (t.tied + t.noResult > 0) ", other ${t.tied + t.noResult}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${t.winPercentage.toInt()}%", style = NumberStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
                        Text(
                            if (t.titles > 0) "${t.titles} title${if (t.titles > 1) "s" else ""}" else "win rate",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (t.titles > 0) LocalCricPalette.current.accentText else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatGrid(cells: List<Pair<String, String>>) {
    for (row in cells.chunked(3)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            for ((label, value) in row) StatCell(label, value, Modifier.weight(1f))
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PlayerProfileDialog(p: PlayerCareerStats, teams: List<TeamEntity>, onDismiss: () -> Unit) {
    FullScreenDialog(title = p.player.name, subtitle = p.player.role.label, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            PlayerAvatar(p.player.name, teamColor(teams.firstOrNull()), size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    listOf(p.player.battingStyle, p.player.bowlingStyle).filter { it.isNotBlank() && it != "None" }.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (teams.isEmpty()) "Not in a team" else teams.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!p.hasAnyRecord) {
            Text("No match records yet.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
            return@FullScreenDialog
        }
        CardSurface(modifier = Modifier.padding(top = 8.dp)) {
            Text("Batting", style = MaterialTheme.typography.titleMedium)
            StatGrid(
                listOf(
                    "Matches" to "${p.matches}", "Innings" to "${p.innings}", "Runs" to "${p.runs}",
                    "Average" to p.battingAverage.format1(), "Strike rate" to p.strikeRate.format1(), "Highest" to p.highestScoreDisplay,
                    "50s" to "${p.fifties}", "100s" to "${p.hundreds}", "Not outs" to "${p.notOuts}",
                    "Fours" to "${p.fours}", "Sixes" to "${p.sixes}", "Ducks" to "${p.ducks}"
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        CardSurface {
            Text("Bowling", style = MaterialTheme.typography.titleMedium)
            StatGrid(
                listOf(
                    "Overs" to p.oversBowledDisplay, "Wickets" to "${p.wickets}", "Best" to p.bestBowlingDisplay,
                    "Economy" to p.economyRate.format2(), "Average" to if (p.wickets > 0) p.bowlingAverage.format1() else "-", "Maidens" to "${p.maidens}",
                    "Dot balls" to "${p.dotBalls}", "3 wickets" to "${p.threeWicketHauls}", "5 wickets" to "${p.fiveWicketHauls}"
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        CardSurface {
            Text("Fielding and awards", style = MaterialTheme.typography.titleMedium)
            StatGrid(
                listOf(
                    "Catches" to "${p.catches}", "Run outs" to "${p.runOuts}", "Stumpings" to "${p.stumpings}",
                    "Player of the match" to "${p.playerOfMatchAwards}"
                )
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TeamProfileDialog(t: TeamStatsSummary, s: StatsBundle, onDismiss: () -> Unit) {
    val h2h = remember(t, s) { s.headToHead(t.team) }
    FullScreenDialog(title = t.team.name, subtitle = if (t.team.isSoloTeam) "Solo side" else "Team record", onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            TeamBadge(t.team, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                if (t.titles > 0) "${t.titles} tournament title${if (t.titles > 1) "s" else ""}" else "No titles yet",
                style = MaterialTheme.typography.titleMedium,
                color = if (t.titles > 0) LocalCricPalette.current.accentText else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CardSurface {
            StatGrid(
                listOf(
                    "Played" to "${t.matchesPlayed}", "Won" to "${t.won}", "Lost" to "${t.lost}",
                    "Tied" to "${t.tied}", "No result" to "${t.noResult}", "Win rate" to "${t.winPercentage.toInt()}%",
                    "Best total" to "${t.highestTotal}", "Lowest total" to (t.lowestTotal?.toString() ?: "-"), "Runs" to "${t.totalRunsScored}",
                    "Wickets taken" to "${t.totalWicketsTaken}"
                )
            )
        }
        FieldLabel("Head to head")
        if (h2h.isEmpty()) {
            Text("No finished matches yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (h in h2h) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(h.opponent, size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("v ${h.opponent.name}", style = MaterialTheme.typography.titleSmall)
                    Text("${h.played} played", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${h.won}-${h.lost}" + if (h.other > 0) "-${h.other}" else "", style = NumberStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))
            }
            ThinDivider()
        }
        Text("Won, lost and other results.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
    }
}
