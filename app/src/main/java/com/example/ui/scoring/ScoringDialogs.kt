package com.example.ui.scoring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DismissalType
import com.example.data.model.PlayerEntity
import com.example.data.model.TeamEntity
import com.example.data.repository.KnockoutDecision
import com.example.domain.LiveScoreState
import com.example.domain.WicketInput
import com.example.ui.components.ChoicePill
import com.example.ui.components.FieldLabel
import com.example.ui.components.InfoNote
import com.example.ui.components.SegmentedControl
import com.example.ui.components.Stepper
import com.example.ui.components.TeamBadge
import com.example.ui.theme.LocalCricPalette

@Composable
fun WicketDialog(
    live: LiveScoreState,
    onConfirm: (WicketInput, runsCompleted: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val p = LocalCricPalette.current
    var dismissal by remember { mutableStateOf(DismissalType.BOWLED) }
    var outId by remember { mutableStateOf(live.striker?.id) }
    var runs by remember { mutableIntStateOf(0) }
    var fielder by remember { mutableStateOf<Long?>(null) }
    var nextBatter by remember { mutableStateOf(live.availableBatters.firstOrNull()?.id) }

    val endsInnings = live.wickets + 1 >= live.maxWicketsAllowed
    val needsNext = !live.isSoloBatting && !endsInnings && live.availableBatters.isNotEmpty()
    val types = listOf(
        DismissalType.BOWLED, DismissalType.CAUGHT, DismissalType.LBW, DismissalType.RUN_OUT,
        DismissalType.STUMPED, DismissalType.HIT_WICKET, DismissalType.RETIRED
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wicket", style = MaterialTheme.typography.headlineSmall, color = p.wicket) },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                if (live.isSoloBatting) {
                    InfoNote("Solo batter: a life is lost but ${live.striker?.name ?: "the batter"} keeps batting.", tone = p.accentText)
                    Spacer(Modifier.height(4.dp))
                }
                FieldLabel("How out")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in types.take(4)) ChoicePill(t.label, dismissal == t, { dismissal = t })
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in types.drop(4)) ChoicePill(t.label, dismissal == t, { dismissal = t })
                }

                if (dismissal == DismissalType.RUN_OUT) {
                    if (!live.isSoloBatting && live.nonStriker != null) {
                        FieldLabel("Who is out")
                        val names = listOfNotNull(live.striker, live.nonStriker)
                        SegmentedControl(
                            options = names.map { it.name },
                            selectedIndex = names.indexOfFirst { it.id == outId }.coerceAtLeast(0),
                            onSelect = { outId = names[it].id }
                        )
                    }
                    FieldLabel("Runs completed before the run out")
                    SegmentedControl(listOf("0", "1", "2", "3"), runs, { runs = it })
                }

                if (dismissal == DismissalType.CAUGHT || dismissal == DismissalType.RUN_OUT || dismissal == DismissalType.STUMPED) {
                    FieldLabel(if (dismissal == DismissalType.STUMPED) "Wicket-keeper (optional)" else "Fielder (optional)")
                    PlayerPills(live.bowlingSquad, fielder, { fielder = if (fielder == it) null else it })
                }

                if (!live.isSoloBatting) {
                    when {
                        endsInnings -> {
                            Spacer(Modifier.height(12.dp))
                            InfoNote("This is the last wicket. The innings will end.", tone = p.wicket)
                        }
                        live.availableBatters.isEmpty() -> {
                            Spacer(Modifier.height(12.dp))
                            InfoNote("Nobody is left to bat, so the innings will end.", tone = p.wicket)
                        }
                        else -> {
                            FieldLabel("Next batter in")
                            PlayerPills(live.availableBatters, nextBatter, { nextBatter = it })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val out = if (dismissal == DismissalType.RUN_OUT) outId else live.striker?.id
                    onConfirm(
                        WicketInput(dismissal, out, fielder, if (needsNext) nextBatter else null),
                        if (dismissal == DismissalType.RUN_OUT) runs else 0
                    )
                },
                enabled = !needsNext || nextBatter != null,
                colors = ButtonDefaults.buttonColors(containerColor = p.wicket, contentColor = Color.White)
            ) { Text("Confirm wicket") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

data class ToolAction(val icon: ImageVector, val title: String, val detail: String?, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun ToolsDialog(actions: List<ToolAction>, onDismiss: () -> Unit) {
    val p = LocalCricPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match tools", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                for (a in actions) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                a.onClick()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(a.icon, contentDescription = null, tint = if (a.destructive) p.wicket else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(a.title, style = MaterialTheme.typography.titleSmall, color = if (a.destructive) p.wicket else MaterialTheme.colorScheme.onSurface)
                            if (a.detail != null) {
                                Text(a.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun NumberDialog(
    title: String,
    message: String,
    initial: Int,
    range: IntRange,
    confirmLabel: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    suffix: String = ""
) {
    var value by remember { mutableIntStateOf(initial.coerceIn(range)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Stepper(value = value, onValueChange = { value = it }, range = range, suffix = suffix, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(value)
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Change who is batting and bowling without recording a ball: substitutes, retired hurt, corrections. */
@Composable
fun CreaseDialog(
    live: LiveScoreState,
    onSave: (striker: Long?, nonStriker: Long?, bowler: Long?) -> Unit,
    onAddPlayers: (TeamEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val dismissed = remember(live.inningsBalls) { live.inningsBalls.filter { it.isWicket }.mapNotNull { it.outPlayerId }.toSet() }
    val batters = live.battingSquad.filter { it.id !in dismissed }
    var striker by remember { mutableStateOf(live.striker?.id) }
    var nonStriker by remember { mutableStateOf(live.nonStriker?.id) }
    var bowler by remember { mutableStateOf(live.bowler?.id) }
    val valid = striker != null && (live.isSoloBatting || (nonStriker != null && nonStriker != striker))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change players", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "For substitutes, a batter retiring hurt, or fixing a mistake. No ball is recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FieldLabel(if (live.isSoloBatting) "Batter" else "On strike")
                PlayerPills(batters, striker, { id -> striker = id; if (nonStriker == id) nonStriker = null })
                if (!live.isSoloBatting) {
                    FieldLabel("Non-striker")
                    PlayerPills(batters, nonStriker, { nonStriker = it }, disabledIds = setOfNotNull(striker))
                }
                FieldLabel("Bowler")
                PlayerPills(live.bowlingSquad, bowler, { bowler = it })
                FieldLabel("Someone arrived late?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in listOf(live.battingTeam, live.bowlingTeam)) {
                        OutlinedButton(onClick = { onAddPlayers(t) }, modifier = Modifier.weight(1f)) {
                            Text("Add to ${t.shortName}", maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(striker, if (live.isSoloBatting) null else nonStriker, bowler)
                onDismiss()
            }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AwardDialog(live: LiveScoreState, onAward: (Long, String) -> Unit, onDismiss: () -> Unit) {
    var winner by remember { mutableStateOf<Long?>(null) }
    val reasons = listOf("walkover", "forfeit", "umpires' decision", "bad light")
    var reason by remember { mutableStateOf(reasons[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Award the match", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text("The winner gets 2 points. Scores so far are kept.", style = MaterialTheme.typography.bodyMedium)
                FieldLabel("Winner")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in listOf(live.team1, live.team2)) {
                        ChoicePill(t.name, winner == t.id, { winner = t.id }, leading = { TeamBadge(t, size = 22.dp) }, modifier = Modifier.weight(1f))
                    }
                }
                FieldLabel("Reason")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in reasons) ChoicePill(r.replaceFirstChar { it.uppercase() }, reason == r, { reason = r })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                winner?.let { onAward(it, reason) }
                onDismiss()
            }, enabled = winner != null) { Text("Award") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SettleDialog(team: TeamEntity, onSettle: (KnockoutDecision) -> Unit, onDismiss: () -> Unit) {
    var decision by remember { mutableStateOf(KnockoutDecision.SUPER_OVER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${team.name} go through", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text("How was it decided?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                for (d in KnockoutDecision.values()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { decision = d }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(selected = decision == d, onClick = { decision = d })
                        Text(d.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSettle(decision)
                onDismiss()
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PotmDialog(live: LiveScoreState, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player of the match", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                for ((team, squad) in listOf(live.team1 to live.squad1, live.team2 to live.squad2)) {
                    FieldLabel(team.name)
                    for (pl in squad) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onPick(pl.id)
                                onDismiss()
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(selected = live.match.potmPlayerId == pl.id, onClick = {
                                onPick(pl.id)
                                onDismiss()
                            })
                            Text(pl.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** Multi-select picker over the player pool, with search. */
@Composable
fun PlayerPickerDialog(
    title: String,
    players: List<PlayerEntity>,
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Add",
    emptyMessage: String = "Everyone in the player pool is already in this team. Add new players in Squads.",
    singleChoice: Boolean = false
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val shown = players.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                if (players.isEmpty()) {
                    Text(emptyMessage, style = MaterialTheme.typography.bodyMedium)
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search players") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        for (pl in shown) {
                            val checked = pl.id in selected
                            val toggle = {
                                selected = when {
                                    singleChoice -> setOf(pl.id)
                                    checked -> selected - pl.id
                                    else -> selected + pl.id
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { toggle() })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pl.role.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (shown.isEmpty()) {
                            Text("No players match \"$query\"", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(selected.toList())
                onDismiss()
            }, enabled = selected.isNotEmpty()) {
                Text(if (selected.size > 1) "$confirmLabel ${selected.size}" else confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
