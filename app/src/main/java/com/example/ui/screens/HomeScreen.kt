package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MatchStatus
import com.example.ui.components.CardSurface
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.FixtureLine
import com.example.ui.components.LiveMatchCard
import com.example.ui.components.LoadingBox
import com.example.ui.components.MatchRow
import com.example.ui.components.SectionTitle
import com.example.ui.components.matchMeta
import com.example.ui.theme.LocalCricPalette
import com.example.ui.viewmodels.MatchListItem
import com.example.ui.viewmodels.MatchesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    vm: MatchesViewModel,
    onNewMatch: () -> Unit,
    onNewTournament: () -> Unit,
    onOpenMatch: (Long) -> Unit,
    onSeeAllMatches: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val matches by vm.matches.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<MatchListItem?>(null) }
    val all = matches
    val live = all.orEmpty().filter { it.match.status == MatchStatus.IN_PROGRESS }
    val upcoming = all.orEmpty().filter { it.match.status == MatchStatus.SCHEDULED }.sortedWith(
        compareBy<MatchListItem> { it.tournament == null }.thenBy { it.match.roundIndex }.thenBy { it.match.matchNumberInRound }
    )
    val recent = all.orEmpty().filter { it.match.status.isFinished }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CricScore", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onNewMatch, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New match")
                }
                OutlinedButton(onClick = onNewTournament, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New tournament", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (all == null) {
            item { LoadingBox() }
        } else if (all.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.SportsCricket,
                    title = "Score your first match",
                    message = "Pick two teams, do the toss and score ball by ball. Four sample teams are ready in Squads.",
                    actionLabel = "New match",
                    onAction = onNewMatch
                )
            }
        }

        if (live.isNotEmpty()) {
            item { SectionTitle("Live now") }
            items(live, key = { "live-${it.match.id}" }) { item ->
                LiveMatchCard(item, onClick = { onOpenMatch(item.match.id) })
            }
        }

        if (upcoming.isNotEmpty()) {
            item { SectionTitle("Up next") }
            items(upcoming.take(3), key = { "next-${it.match.id}" }) { item ->
                CardSurface(onClick = { onOpenMatch(item.match.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            FixtureLine(item.team1, item.team2)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                matchMeta(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text("Start", style = MaterialTheme.typography.labelLarge, color = LocalCricPalette.current.accentText)
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item {
                SectionTitle("Recent results") {
                    TextButton(onClick = onSeeAllMatches) { Text("See all") }
                }
            }
            items(recent.take(5), key = { "recent-${it.match.id}" }) { item ->
                MatchRow(item, onClick = { onOpenMatch(item.match.id) }, onDelete = { deleteTarget = item })
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete this match?",
            message = "${target.team1?.name ?: "Team"} v ${target.team2?.name ?: "Team"} and all its scores will be removed. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deleteMatch(target.match.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}
