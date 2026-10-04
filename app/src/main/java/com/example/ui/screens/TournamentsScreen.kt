package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GroundEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchStage
import com.example.data.model.MatchStatus
import com.example.data.model.TeamEntity
import com.example.data.model.TournamentFormat
import com.example.data.model.TournamentStatus
import com.example.data.repository.KnockoutDecision
import com.example.domain.PlayerCareerStats
import com.example.domain.PointsTableRow
import com.example.ui.components.CardSurface
import com.example.ui.components.ChoicePill
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.FieldLabel
import com.example.ui.components.FullScreenDialog
import com.example.ui.components.InfoNote
import com.example.ui.components.LoadingBox
import com.example.ui.components.MatchRow
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SectionTitle
import com.example.ui.components.SegmentedControl
import com.example.ui.components.Stepper
import com.example.ui.components.TeamBadge
import com.example.ui.components.ThinDivider
import com.example.ui.components.format1
import com.example.ui.components.format2
import com.example.ui.scoring.SettleDialog
import com.example.ui.scoring.squadProblem
import com.example.ui.theme.LocalCricPalette
import com.example.ui.theme.NumberStyle
import com.example.ui.viewmodels.MatchListItem
import com.example.ui.viewmodels.TeamListItem
import com.example.ui.viewmodels.TournamentDetail
import com.example.ui.viewmodels.TournamentListItem
import com.example.ui.viewmodels.TournamentViewModel

@Composable
fun TournamentsScreen(
    vm: TournamentViewModel,
    teams: List<TeamListItem>,
    grounds: List<GroundEntity>,
    onOpenMatch: (Long) -> Unit,
    createRequested: Boolean,
    onCreateRequestHandled: () -> Unit
) {
    val list by vm.tournaments.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(createRequested) {
        if (createRequested) {
            vm.clearSelection()
            showCreate = true
            onCreateRequestHandled()
        }
    }

    if (selectedId != null) {
        BackHandler { vm.clearSelection() }
        val d = detail
        if (d == null || d.tournament.id != selectedId) {
            Column {
                ScreenHeader(title = "Tournament", onBack = { vm.clearSelection() })
                LoadingBox()
            }
        } else {
            TournamentDetailView(d, teams, vm, onOpenMatch)
        }
    } else {
        TournamentList(list, onOpen = { vm.select(it) }, onCreate = { showCreate = true })
    }

    if (showCreate) {
        CreateTournamentDialog(
            teams = teams,
            grounds = grounds,
            onDismiss = { showCreate = false },
            onCreate = { name, format, ids, encounters, overs, bpo, ground ->
                vm.createTournament(name, format, ids, encounters, overs, bpo, ground)
                showCreate = false
            }
        )
    }
}

@Composable
private fun TournamentList(list: List<TournamentListItem>?, onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Tournaments", subtitle = "Leagues, knockouts and playoffs") {
            FilledTonalButton(onClick = onCreate, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("New", modifier = Modifier.padding(start = 6.dp))
            }
        }
        when {
            list == null -> LoadingBox()
            list.isEmpty() -> EmptyState(
                icon = Icons.Default.EmojiEvents,
                title = "No tournaments yet",
                message = "Pick the teams and the format. Fixtures, the points table and the playoffs are worked out for you.",
                actionLabel = "New tournament",
                onAction = onCreate
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.tournament.id }) { item -> TournamentCard(item) { onOpen(item.tournament.id) } }
            }
        }
    }
}

@Composable
private fun TournamentCard(item: TournamentListItem, onClick: () -> Unit) {
    val t = item.tournament
    val palette = LocalCricPalette.current
    CardSurface(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${t.format.label}, ${item.teamCount} teams, ${t.oversPerInnings} overs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (item.live > 0) "${item.live} live" else t.status.label,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    item.live > 0 -> palette.wicket
                    t.status == TournamentStatus.COMPLETED -> palette.win
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { if (item.total == 0) 0f else item.played / item.total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (t.status == TournamentStatus.COMPLETED) palette.win else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${item.played} of ${item.total} matches played", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (item.champion != null) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = palette.accentText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(item.champion.name, style = MaterialTheme.typography.labelLarge, color = palette.accentText)
            }
        }
    }
}

private sealed interface FixtureDialog {
    data class Actions(val match: MatchEntity) : FixtureDialog
    data class ConfirmReset(val match: MatchEntity) : FixtureDialog
    data class ConfirmDelete(val match: MatchEntity) : FixtureDialog
    data class Settle(val match: MatchEntity, val team: TeamEntity) : FixtureDialog
    data object Rename : FixtureDialog
    data object DeleteTournament : FixtureDialog
}

@Composable
private fun TournamentDetailView(
    d: TournamentDetail,
    squads: List<TeamListItem>,
    vm: TournamentViewModel,
    onOpenMatch: (Long) -> Unit
) {
    val t = d.tournament
    val tabs = if (d.hasTable) listOf("Table", "Fixtures", "Stats") else listOf("Fixtures", "Stats")
    var tab by rememberSaveable(t.id) { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<FixtureDialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    val teamMap = d.teamsById

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = t.name,
            subtitle = "${t.format.label}, ${t.oversPerInnings} overs, ${d.played} of ${d.total} played",
            onBack = { vm.clearSelection() }
        ) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Tournament options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menu = false; dialog = FixtureDialog.Rename }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete tournament") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menu = false; dialog = FixtureDialog.DeleteTournament }
                    )
                }
            }
        }
        SegmentedControl(tabs, tab.coerceIn(0, tabs.size - 1), { tab = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

        val current = tabs[tab.coerceIn(0, tabs.size - 1)]
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (d.champion != null) {
                item { ChampionBanner(d.champion, t.name) }
            }
            val pending = d.matches.filter { it.stage.isKnockout && it.status.isFinished && it.winnerTeamId == null }
            if (pending.isNotEmpty()) {
                item {
                    InfoNote(
                        "${pending.size} knockout match${if (pending.size > 1) "es need" else " needs"} a winner. Open it under Fixtures to choose who goes through.",
                        tone = LocalCricPalette.current.accentText
                    )
                }
            }
            when (current) {
                "Table" -> standingsSection(d)
                "Fixtures" -> fixturesSection(d, teamMap) { dialog = FixtureDialog.Actions(it) }
                else -> statsSection(d, squads)
            }
        }
    }

    when (val dd = dialog) {
        null -> {}
        is FixtureDialog.Actions -> FixtureActionsDialog(
            match = dd.match,
            teamMap = teamMap,
            hasInnings = !d.inningsByMatch[dd.match.id].isNullOrEmpty(),
            onOpen = { dialog = null; onOpenMatch(dd.match.id) },
            onAward = { id -> dialog = null; vm.awardMatch(dd.match.id, id) },
            onAbandon = { dialog = null; vm.abandonMatch(dd.match.id) },
            onReopen = { dialog = null; vm.reopenMatch(dd.match.id) },
            onSettle = { team -> dialog = FixtureDialog.Settle(dd.match, team) },
            onReset = { dialog = FixtureDialog.ConfirmReset(dd.match) },
            onDelete = { dialog = FixtureDialog.ConfirmDelete(dd.match) },
            onDismiss = { dialog = null }
        )
        is FixtureDialog.ConfirmReset -> ConfirmDialog(
            title = "Reset this match?",
            message = "All its balls and the result are cleared, and it goes back to unplayed.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = { vm.resetMatch(dd.match.id) },
            onDismiss = { dialog = null }
        )
        is FixtureDialog.ConfirmDelete -> ConfirmDialog(
            title = "Delete this fixture?",
            message = if (dd.match.stage.isKnockout) "It will be recreated automatically when the bracket needs it."
            else "The two teams won't meet in this round. Its scores are removed from the table.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deleteMatch(dd.match.id) },
            onDismiss = { dialog = null }
        )
        is FixtureDialog.Settle -> SettleDialog(
            team = dd.team,
            onSettle = { decision: KnockoutDecision -> vm.settleKnockout(dd.match.id, dd.team.id, decision) },
            onDismiss = { dialog = null }
        )
        FixtureDialog.Rename -> RenameDialog(t.name, onSave = { vm.rename(t.id, it) }, onDismiss = { dialog = null })
        FixtureDialog.DeleteTournament -> ConfirmDialog(
            title = "Delete ${t.name}?",
            message = "All ${d.total} fixtures and their scores will be deleted. Teams and players are kept.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.delete(t.id) },
            onDismiss = { dialog = null }
        )
    }
}

@Composable
private fun ChampionBanner(team: TeamEntity, tournamentName: String) {
    val p = LocalCricPalette.current
    Surface(shape = RoundedCornerShape(18.dp), color = p.panel, contentColor = p.onPanel, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = p.panel)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("$tournamentName champions", style = MaterialTheme.typography.labelMedium, color = p.onPanelMuted)
                Text(team.name, style = MaterialTheme.typography.headlineMedium, color = p.onPanel)
            }
            TeamBadge(team, size = 40.dp)
        }
    }
}

private fun LazyListScope.standingsSection(d: TournamentDetail) {
    item {
        if (d.standings.isEmpty()) {
            Text("No teams in this tournament.", style = MaterialTheme.typography.bodyMedium)
        } else {
            StandingsTable(d.standings, d.qualifiers)
        }
    }
    item {
        Text(
            buildString {
                append("Win 2 points, tie or no result 1. Teams level on points are split by net run rate.")
                if (d.qualifiers == 4) append(" The top 4 play the semi-finals: 1st v 4th and 2nd v 3rd.")
                if (d.qualifiers == 2) append(" The top 2 meet in the final.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun StandingsTable(rows: List<PointsTableRow>, qualifiers: Int) {
    val p = LocalCricPalette.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    CardSurface {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Text("Team", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(3f))
            for (h in listOf("P", "W", "L", "T", "NR")) {
                Text(h, style = MaterialTheme.typography.labelMedium, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
            }
            Text("Pts", style = MaterialTheme.typography.labelMedium, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("NRR", style = MaterialTheme.typography.labelMedium, color = muted, textAlign = TextAlign.End, modifier = Modifier.weight(1.6f))
        }
        rows.forEachIndexed { index, row ->
            val qualified = index < qualifiers
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(3f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(if (qualified) p.accent else MaterialTheme.colorScheme.surface))
                    Spacer(Modifier.width(6.dp))
                    Text("${index + 1}", style = NumberStyle.copy(fontSize = 15.sp), color = muted, modifier = Modifier.width(16.dp))
                    TeamBadge(row.team, size = 24.dp)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(row.team.shortName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        if (row.form.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
                                for (f in row.form) {
                                    Box(
                                        Modifier.size(6.dp).clip(CircleShape).background(
                                            when (f) {
                                                'W' -> p.win
                                                'L' -> p.wicket
                                                else -> p.dot
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                for (v in listOf(row.played, row.won, row.lost, row.tied, row.noResult)) {
                    Text("$v", style = NumberStyle.copy(fontSize = 16.sp), textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
                }
                Text("${row.points}", style = NumberStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text(row.nrrFormatted, style = NumberStyle.copy(fontSize = 15.sp), textAlign = TextAlign.End, modifier = Modifier.weight(1.6f))
            }
            if (qualified && index == qualifiers - 1 && index < rows.size - 1) {
                ThinDivider(Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

private fun roundTitle(matches: List<MatchEntity>): String {
    val first = matches.first()
    return when (first.stage) {
        MatchStage.LEAGUE -> "Round ${first.roundIndex}"
        MatchStage.SEMI_FINAL_1, MatchStage.SEMI_FINAL_2 -> "Semi-finals"
        MatchStage.QUARTER_FINAL -> "Quarter-finals"
        MatchStage.FINAL -> "Final"
        MatchStage.KNOCKOUT_ROUND -> "Knockout round"
        MatchStage.STANDALONE -> "Matches"
    }
}

private fun LazyListScope.fixturesSection(d: TournamentDetail, teamMap: Map<Long, TeamEntity>, onClick: (MatchEntity) -> Unit) {
    if (d.matches.isEmpty()) {
        item { Text("No fixtures yet.", style = MaterialTheme.typography.bodyMedium) }
        return
    }
    val rounds = d.matches.groupBy { it.roundIndex }.toSortedMap().values.toList()
    for (round in rounds) {
        item(key = "round-${round.first().roundIndex}") {
            val done = round.count { it.status.isFinished }
            SectionTitle(roundTitle(round)) {
                Text("$done/${round.size} played", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(round, key = { it.id }) { m ->
            val item = MatchListItem(m, teamMap[m.team1Id], teamMap[m.team2Id], d.inningsByMatch[m.id].orEmpty(), null, d.tournament)
            MatchRow(
                item = item,
                onClick = { onClick(m) },
                meta = if (m.stage.isKnockout) m.stage.label else "Match ${m.matchNumberInRound}"
            )
        }
    }
    if (d.tournament.format == TournamentFormat.LEAGUE_AND_KNOCKOUT && d.matches.none { it.stage.isKnockout }) {
        item {
            InfoNote(
                if (d.qualifiers == 4) "Semi-finals appear here when every league match has a result." else "The final appears here when every league match has a result."
            )
        }
    }
}

private fun LazyListScope.statsSection(d: TournamentDetail, squads: List<TeamListItem>) {
    val teamOf = HashMap<Long, String>()
    val ids = d.teams.map { it.id }.toSet()
    for (s in squads) if (s.team.id in ids) for (pl in s.players) teamOf[pl.id] = s.team.shortName
    val stats = d.playerStats
    if (stats.isEmpty()) {
        item {
            EmptyState(
                icon = Icons.Default.EmojiEvents,
                title = "No stats yet",
                message = "Leaders appear here once matches are scored ball by ball."
            )
        }
        return
    }
    fun label(s: PlayerCareerStats) = teamOf[s.player.id]?.let { "${s.player.name} ($it)" } ?: s.player.name

    item {
        LeaderCard("Most runs", stats.filter { it.runs > 0 }.sortedByDescending { it.runs }.take(5)) { s ->
            Triple(label(s), "${s.runs}", "${s.innings} inns, SR ${s.strikeRate.format1()}, HS ${s.highestScoreDisplay}")
        }
    }
    item {
        LeaderCard("Most wickets", stats.filter { it.wickets > 0 }.sortedWith(compareByDescending<PlayerCareerStats> { it.wickets }.thenBy { it.economyRate }).take(5)) { s ->
            Triple(label(s), "${s.wickets}", "Best ${s.bestBowlingDisplay}, econ ${s.economyRate.format2()}")
        }
    }
    item {
        LeaderCard("Most sixes", stats.filter { it.sixes > 0 }.sortedByDescending { it.sixes }.take(3)) { s ->
            Triple(label(s), "${s.sixes}", "${s.fours} fours")
        }
    }
    item {
        LeaderCard("Player of the match awards", stats.filter { it.playerOfMatchAwards > 0 }.sortedByDescending { it.playerOfMatchAwards }.take(3)) { s ->
            Triple(label(s), "${s.playerOfMatchAwards}", "${s.matches} matches")
        }
    }
    item {
        CardSurface {
            Text("Team records", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            for (ts in d.teamStats.sortedByDescending { it.won }) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(ts.team, size = 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ts.team.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Played ${ts.matchesPlayed}, won ${ts.won}, lost ${ts.lost}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${ts.highestTotal}", style = NumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                        Text("best total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderCard(title: String, rows: List<PlayerCareerStats>, describe: (PlayerCareerStats) -> Triple<String, String, String>) {
    if (rows.isEmpty()) return
    val p = LocalCricPalette.current
    CardSurface {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        rows.forEachIndexed { i, s ->
            val (name, value, detail) = describe(s)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${i + 1}",
                    style = NumberStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = if (i == 0) p.accentText else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value, style = NumberStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun FixtureActionsDialog(
    match: MatchEntity,
    teamMap: Map<Long, TeamEntity>,
    hasInnings: Boolean,
    onOpen: () -> Unit,
    onAward: (Long) -> Unit,
    onAbandon: () -> Unit,
    onReopen: () -> Unit,
    onSettle: (TeamEntity) -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val t1 = teamMap[match.team1Id]
    val t2 = teamMap[match.team2Id]
    val p = LocalCricPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${t1?.name ?: "?"} v ${t2?.name ?: "?"}", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    if (match.status.isFinished) match.resultSummary.ifBlank { match.status.label } else match.stage.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ActionRow(
                    when {
                        match.status == MatchStatus.SCHEDULED && !hasInnings -> "Start scoring"
                        match.status.isFinished -> "Open match and scorecard"
                        else -> "Resume scoring"
                    },
                    onClick = onOpen
                )
                if (!match.status.isFinished) {
                    if (t1 != null) ActionRow("Award walkover to ${t1.name}", onClick = { onAward(t1.id) })
                    if (t2 != null) ActionRow("Award walkover to ${t2.name}", onClick = { onAward(t2.id) })
                    ActionRow("No result (abandoned)", onClick = onAbandon)
                }
                if (match.status == MatchStatus.DECLARED_WINNER || match.status == MatchStatus.ABANDONED_RAIN) {
                    ActionRow("Reopen match", onClick = onReopen)
                }
                if (match.stage.isKnockout && match.status.isFinished && match.winnerTeamId == null) {
                    if (t1 != null) ActionRow("${t1.name} go through", onClick = { onSettle(t1) })
                    if (t2 != null) ActionRow("${t2.name} go through", onClick = { onSettle(t2) })
                }
                if (hasInnings || match.status.isFinished) ActionRow("Reset match", color = p.wicket, onClick = onReset)
                ActionRow("Delete fixture", color = p.wicket, onClick = onDelete)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ActionRow(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)
    )
}

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename tournament", style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            Button(onClick = { onSave(name); onDismiss() }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreateTournamentDialog(
    teams: List<TeamListItem>,
    grounds: List<GroundEntity>,
    onDismiss: () -> Unit,
    onCreate: (name: String, format: TournamentFormat, teamIds: List<Long>, encounters: Int, overs: Int, ballsPerOver: Int, groundId: Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(TournamentFormat.LEAGUE_AND_KNOCKOUT) }
    var picked by remember { mutableStateOf(teams.filter { !it.team.isSoloTeam }.map { it.team.id }) }
    var encounters by remember { mutableIntStateOf(1) }
    var overs by remember { mutableIntStateOf(10) }
    var ballsPerOver by remember { mutableIntStateOf(6) }
    var groundId by remember { mutableStateOf<Long?>(null) }

    val n = picked.size
    val leagueMatches = n * (n - 1) / 2 * encounters
    val summary = when (format) {
        TournamentFormat.ROUND_ROBIN -> "$leagueMatches league matches. The team top of the table wins."
        TournamentFormat.LEAGUE_AND_KNOCKOUT -> if (n >= 4) "$leagueMatches league matches, then two semi-finals and a final."
        else "$leagueMatches league matches, then a final between the top two."
        TournamentFormat.KNOCKOUT -> {
            var size = 1
            while (size < n) size *= 2
            val byes = size - n
            "${n - 1} matches in total." + if (byes > 0) " $byes top seed${if (byes > 1) "s get byes" else " gets a bye"} into round two." else ""
        }
    }
    val problems = teams.filter { it.team.id in picked }.mapNotNull { squadProblem(it.team, it.players) }
    val valid = name.isNotBlank() && n >= 2

    FullScreenDialog(
        title = "New tournament",
        onDismiss = onDismiss,
        bottomBar = {
            Button(
                onClick = { onCreate(name, format, picked, if (format == TournamentFormat.KNOCKOUT) 1 else encounters, overs, ballsPerOver, groundId) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (n < 2) "Pick at least 2 teams" else "Create and schedule") }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Tournament name") },
            placeholder = { Text("Sunday League 2026") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        FieldLabel("Format")
        for (f in TournamentFormat.values()) {
            val selected = f == format
            Surface(
                onClick = { format = f },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(f.label, style = MaterialTheme.typography.titleMedium)
                    Text(f.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("Teams ($n picked)", modifier = Modifier.weight(1f))
            TextButton(onClick = { picked = if (n == teams.size) emptyList() else teams.map { it.team.id } }) {
                Text(if (n == teams.size) "Clear" else "Select all")
            }
        }
        if (format == TournamentFormat.KNOCKOUT) {
            Text("Teams are seeded in the order you pick them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (teams.isEmpty()) {
            Text("Create teams in Squads first.", style = MaterialTheme.typography.bodyMedium)
        }
        for (t in teams) {
            val idx = picked.indexOf(t.team.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { picked = if (idx >= 0) picked - t.team.id else picked + t.team.id }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = idx >= 0, onCheckedChange = { picked = if (it) picked + t.team.id else picked - t.team.id })
                TeamBadge(t.team, size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(t.team.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (t.team.isSoloTeam) "Solo side" else "${t.players.size} players",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (idx >= 0 && format == TournamentFormat.KNOCKOUT) {
                    Text("Seed ${idx + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (format != TournamentFormat.KNOCKOUT) {
            FieldLabel("Each pair of teams plays")
            SegmentedControl(listOf("Once", "Twice", "3 times"), encounters - 1, { encounters = it + 1 })
        }
        FieldLabel("Overs per innings")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper(overs, { overs = it }, 1..50)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (o in listOf(5, 10, 20)) ChoicePill("$o", overs == o, { overs = o })
            }
        }
        FieldLabel("Balls per over")
        SegmentedControl(listOf("4", "5", "6", "8"), listOf(4, 5, 6, 8).indexOf(ballsPerOver).coerceAtLeast(0), {
            ballsPerOver = listOf(4, 5, 6, 8)[it]
        })
        if (grounds.isNotEmpty()) {
            FieldLabel("Home ground")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill("Not set", groundId == null, { groundId = null })
                for (g in grounds) ChoicePill(g.name, groundId == g.id, { groundId = g.id })
            }
        }
        Spacer(Modifier.height(16.dp))
        if (n >= 2) InfoNote(summary)
        for (problem in problems) {
            Spacer(Modifier.height(8.dp))
            InfoNote(problem, tone = LocalCricPalette.current.wicket)
        }
        Spacer(Modifier.height(24.dp))
    }
}
