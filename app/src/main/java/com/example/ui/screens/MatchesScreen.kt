package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MatchStatus
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingBox
import com.example.ui.components.MatchRow
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SegmentedControl
import com.example.ui.viewmodels.MatchListItem
import com.example.ui.viewmodels.MatchesViewModel

@Composable
fun MatchesScreen(
    vm: MatchesViewModel,
    onNewMatch: () -> Unit,
    onOpenMatch: (Long) -> Unit
) {
    val matches by vm.matches.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<MatchListItem?>(null) }
    val filters = listOf("All", "Live", "Upcoming", "Results")

    val shown = matches.orEmpty().filter { item ->
        val statusOk = when (filter) {
            1 -> item.match.status == MatchStatus.IN_PROGRESS
            2 -> item.match.status == MatchStatus.SCHEDULED
            3 -> item.match.status.isFinished
            else -> true
        }
        val q = query.trim()
        val textOk = q.isEmpty() ||
            item.team1?.name?.contains(q, ignoreCase = true) == true ||
            item.team2?.name?.contains(q, ignoreCase = true) == true ||
            item.tournament?.name?.contains(q, ignoreCase = true) == true ||
            item.ground?.name?.contains(q, ignoreCase = true) == true
        statusOk && textOk
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Matches", subtitle = matches?.let { "${it.size} in total" }) {
            FilledTonalButton(onClick = onNewMatch, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("New", modifier = Modifier.padding(start = 6.dp))
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search teams, tournaments or grounds") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SegmentedControl(filters, filter, { filter = it }, modifier = Modifier.padding(top = 10.dp))
        }
        when {
            matches == null -> LoadingBox()
            shown.isEmpty() -> EmptyState(
                icon = Icons.Default.SportsCricket,
                title = if (matches.isNullOrEmpty()) "No matches yet" else "Nothing here",
                message = if (matches.isNullOrEmpty()) "Start a match and it will show up here, live and when it's done."
                else "No matches fit this filter.",
                actionLabel = if (matches.isNullOrEmpty()) "New match" else null,
                onAction = if (matches.isNullOrEmpty()) onNewMatch else null
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown, key = { it.match.id }) { item ->
                    MatchRow(item, onClick = { onOpenMatch(item.match.id) }, onDelete = { deleteTarget = item })
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete this match?",
            message = buildString {
                append("${target.team1?.name ?: "Team"} v ${target.team2?.name ?: "Team"} and all its scores will be removed.")
                if (target.tournament != null) append(" It will also be removed from ${target.tournament.name}.")
                append(" This can't be undone.")
            },
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deleteMatch(target.match.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}
