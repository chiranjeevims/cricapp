package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GroundEntity
import com.example.data.model.PlayerEntity
import com.example.data.model.PlayerRole
import com.example.data.model.TeamEntity
import com.example.data.repository.TeamDeleteCheck
import com.example.ui.components.CardSurface
import com.example.ui.components.ChoicePill
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.FieldLabel
import com.example.ui.components.FullScreenDialog
import com.example.ui.components.InfoNote
import com.example.ui.components.LoadingBox
import com.example.ui.components.PlayerAvatar
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SegmentedControl
import com.example.ui.components.Stepper
import com.example.ui.components.TeamBadge
import com.example.ui.components.ThinDivider
import com.example.ui.components.contentColorOn
import com.example.ui.components.teamColor
import com.example.ui.scoring.PlayerPickerDialog
import com.example.ui.theme.LocalCricPalette
import com.example.ui.theme.TeamColorOptions
import com.example.ui.theme.parseTeamColor
import com.example.ui.viewmodels.GroundListItem
import com.example.ui.viewmodels.PlayerListItem
import com.example.ui.viewmodels.SquadsViewModel
import com.example.ui.viewmodels.TeamListItem

private sealed interface SquadsDialog {
    data class TeamEditor(val teamId: Long?) : SquadsDialog
    data class PlayerEditor(val player: PlayerEntity?) : SquadsDialog
    data class GroundEditor(val ground: GroundEntity?) : SquadsDialog
}

@Composable
fun SquadsScreen(vm: SquadsViewModel) {
    val teams by vm.teams.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val grounds by vm.grounds.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<SquadsDialog?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Squads", subtitle = "Teams, the player pool and grounds") {
            FilledTonalButton(
                onClick = {
                    dialog = when (tab) {
                        0 -> SquadsDialog.TeamEditor(null)
                        1 -> SquadsDialog.PlayerEditor(null)
                        else -> SquadsDialog.GroundEditor(null)
                    }
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    when (tab) {
                        0 -> "Team"
                        1 -> "Player"
                        else -> "Ground"
                    },
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        SegmentedControl(
            options = listOf("Teams", "Players", "Grounds"),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        when (tab) {
            0 -> TeamsList(teams, onOpen = { dialog = SquadsDialog.TeamEditor(it) }, onCreate = { dialog = SquadsDialog.TeamEditor(null) })
            1 -> PlayersList(players, onOpen = { dialog = SquadsDialog.PlayerEditor(it) }, onCreate = { dialog = SquadsDialog.PlayerEditor(null) })
            else -> GroundsList(grounds, onOpen = { dialog = SquadsDialog.GroundEditor(it) }, onCreate = { dialog = SquadsDialog.GroundEditor(null) })
        }
    }

    when (val d = dialog) {
        null -> {}
        is SquadsDialog.TeamEditor -> TeamEditorDialog(
            item = d.teamId?.let { id -> teams.orEmpty().find { it.team.id == id } },
            allPlayers = players.orEmpty().map { it.player },
            vm = vm,
            onDismiss = { dialog = null }
        )
        is SquadsDialog.PlayerEditor -> PlayerEditorDialog(
            player = d.player,
            teams = teams.orEmpty().map { it.team },
            onSave = { p, teamId -> vm.savePlayer(p, teamId) },
            onDelete = { vm.deletePlayer(it) },
            onDismiss = { dialog = null }
        )
        is SquadsDialog.GroundEditor -> GroundEditorDialog(
            ground = d.ground,
            onSave = { vm.saveGround(it) },
            onDelete = { vm.deleteGround(it) },
            onDismiss = { dialog = null }
        )
    }
}

@Composable
private fun TeamsList(teams: List<TeamListItem>?, onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    when {
        teams == null -> LoadingBox()
        teams.isEmpty() -> EmptyState(
            icon = Icons.Default.Groups,
            title = "No teams yet",
            message = "Create a team, give it a colour and pick its players from the pool.",
            actionLabel = "New team",
            onAction = onCreate
        )
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(teams, key = { it.team.id }) { item ->
                CardSurface(onClick = { onOpen(item.team.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamBadge(item.team, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.team.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val captain = item.players.find { it.id == item.captainId }
                            Text(
                                when {
                                    item.team.isSoloTeam -> "Solo side, ${item.team.defaultSoloWickets} lives" + (item.players.firstOrNull()?.let { ": ${it.name}" } ?: "")
                                    item.players.isEmpty() -> "No players yet"
                                    captain != null -> "${item.players.size} players, captain ${captain.name}"
                                    else -> "${item.players.size} players"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.players.isEmpty()) LocalCricPalette.current.wicket else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${item.matchesPlayed}", style = MaterialTheme.typography.titleMedium)
                            Text("played", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayersList(players: List<PlayerListItem>?, onOpen: (PlayerEntity) -> Unit, onCreate: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    when {
        players == null -> LoadingBox()
        players.isEmpty() -> EmptyState(
            icon = Icons.Default.Person,
            title = "No players yet",
            message = "Add players once and pick them for any team.",
            actionLabel = "New player",
            onAction = onCreate
        )
        else -> Column {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search ${players.size} players") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            val shown = players.filter { query.isBlank() || it.player.name.contains(query.trim(), ignoreCase = true) }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
                items(shown, key = { it.player.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(item.player) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerAvatar(item.player.name, teamColor(item.teams.firstOrNull()), size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.player.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(item.player.role.label, item.teams.joinToString { it.shortName }.ifBlank { "No team" }).joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (item.player.jerseyNumber > 0) {
                            Text("#${item.player.jerseyNumber}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ThinDivider()
                }
            }
        }
    }
}

@Composable
private fun GroundsList(grounds: List<GroundListItem>?, onOpen: (GroundEntity) -> Unit, onCreate: () -> Unit) {
    when {
        grounds == null -> LoadingBox()
        grounds.isEmpty() -> EmptyState(
            icon = Icons.Default.Stadium,
            title = "No grounds yet",
            message = "Add the grounds you play at to track scores by venue.",
            actionLabel = "New ground",
            onAction = onCreate
        )
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(grounds, key = { it.ground.id }) { item ->
                CardSurface(onClick = { onOpen(item.ground) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Stadium, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.ground.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${item.ground.city}, ${item.ground.pitchType.lowercase()} pitch, ${item.ground.boundarySizeMeters} m boundary",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (item.matches == 0) "No matches played here yet"
                                else "${item.matches} matches" + (item.averageFirstInnings?.let { ", average first innings $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Team editor

@Composable
private fun TeamEditorDialog(
    item: TeamListItem?,
    allPlayers: List<PlayerEntity>,
    vm: SquadsViewModel,
    onDismiss: () -> Unit
) {
    val existing = item?.team
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var short by remember { mutableStateOf(existing?.shortName ?: "") }
    var color by remember { mutableStateOf(existing?.primaryColorHex ?: TeamColorOptions[allPlayers.size % TeamColorOptions.size]) }
    var solo by remember { mutableStateOf(existing?.isSoloTeam ?: false) }
    var lives by remember { mutableIntStateOf(existing?.defaultSoloWickets ?: 3) }
    var newSquad by remember { mutableStateOf(listOf<Long>()) }
    var showPicker by remember { mutableStateOf(false) }
    var deleteCheck by remember { mutableStateOf<TeamDeleteCheck?>(null) }

    val squad: List<PlayerEntity> = if (item != null) item.players else newSquad.mapNotNull { id -> allPlayers.find { it.id == id } }
    val shortCode = short.ifBlank { name.filter { it.isLetterOrDigit() }.take(3) }.uppercase()
    val valid = name.isNotBlank() && (!solo || squad.size <= 1)

    fun buildTeam() = TeamEntity(
        id = existing?.id ?: 0,
        name = name.trim(),
        shortName = shortCode.ifBlank { "TEAM" },
        primaryColorHex = color,
        isSoloTeam = solo,
        defaultSoloWickets = lives
    )

    FullScreenDialog(
        title = if (existing == null) "New team" else existing.name,
        onDismiss = onDismiss,
        bottomBar = {
            Button(
                onClick = {
                    if (existing == null) vm.createTeam(buildTeam(), newSquad) else vm.updateTeam(buildTeam())
                    onDismiss()
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (existing == null) "Create team" else "Save changes") }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            TeamBadge(buildTeam(), size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    label = { Text("Team name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        OutlinedTextField(
            value = short,
            onValueChange = { short = it.filter { c -> c.isLetterOrDigit() }.take(4).uppercase() },
            label = { Text("Short code") },
            placeholder = { Text(shortCode.ifBlank { "e.g. RCB" }) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
        FieldLabel("Colour")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (hex in TeamColorOptions) {
                val c = parseTeamColor(hex)
                val selected = hex.equals(color, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c)
                        .then(if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                        .clickable { color = hex },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = contentColorOn(c), modifier = Modifier.size(20.dp))
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Solo side", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "One player bats and bowls for the whole side. Their score carries on through each lost life.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = solo, onCheckedChange = { solo = it })
                }
                if (solo) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Text("Lives", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Stepper(lives, { lives = it }, 2..10)
                    }
                    if (squad.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        InfoNote("A solo side needs exactly one player. Remove the others first.", tone = LocalCricPalette.current.wicket)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("Squad (${squad.size})", modifier = Modifier.weight(1f))
            TextButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Add players", modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (squad.isEmpty()) {
            Text(
                if (solo) "Pick the solo player." else "Pick at least two players. You can add more at any time, even during a match.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        for (pl in squad) {
            val isCaptain = item != null && item.captainId == pl.id
            val isKeeper = item != null && item.keeperId == pl.id
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(pl.name, parseTeamColor(color), size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(pl.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pl.role.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (existing != null) {
                    RoleToggle("C", isCaptain) { vm.setCaptain(existing.id, pl.id) }
                    Spacer(Modifier.width(6.dp))
                    RoleToggle("WK", isKeeper) { vm.toggleKeeper(existing.id, pl.id) }
                }
                IconButton(onClick = {
                    if (existing != null) vm.removePlayerFromTeam(existing.id, pl.id) else newSquad = newSquad - pl.id
                }) { Icon(Icons.Default.Close, contentDescription = "Remove ${pl.name}") }
            }
        }

        if (existing != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { vm.checkTeamDeletion(existing.id) { deleteCheck = it } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete team", color = LocalCricPalette.current.wicket) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showPicker) {
        val inSquad = squad.map { it.id }.toSet()
        PlayerPickerDialog(
            title = "Add players",
            players = allPlayers.filter { it.id !in inSquad },
            singleChoice = solo,
            emptyMessage = "Everyone in the player pool is already in this team. Create new players on the Players tab.",
            onConfirm = { ids ->
                if (existing != null) vm.addPlayersToTeam(existing.id, if (solo) ids.take(1) else ids)
                else newSquad = if (solo) ids.take(1) else (newSquad + ids).distinct()
            },
            onDismiss = { showPicker = false }
        )
    }

    deleteCheck?.let { check ->
        if (check.blocked) {
            ConfirmDialog(
                title = "Can't delete this team",
                message = "${existing?.name} plays in ${check.tournamentNames.joinToString()}. Delete the tournament first, then the team.",
                confirmLabel = "OK",
                onConfirm = {},
                onDismiss = { deleteCheck = null },
                dismissLabel = "Close"
            )
        } else {
            ConfirmDialog(
                title = "Delete ${existing?.name}?",
                message = if (check.matchCount > 0) "Its ${check.matchCount} matches and their scores will be deleted too. Players stay in the pool."
                else "Players stay in the pool.",
                confirmLabel = "Delete",
                destructive = true,
                onConfirm = { existing?.let { vm.deleteTeam(it, onDeleted = onDismiss) } },
                onDismiss = { deleteCheck = null }
            )
        }
    }
}

@Composable
private fun RoleToggle(label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) scheme.onPrimary else scheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ Player editor

@Composable
private fun PlayerEditorDialog(
    player: PlayerEntity?,
    teams: List<TeamEntity>,
    onSave: (PlayerEntity, Long?) -> Unit,
    onDelete: (PlayerEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(player?.name ?: "") }
    var role by remember { mutableStateOf(player?.role ?: PlayerRole.ALL_ROUNDER) }
    var batting by remember { mutableStateOf(player?.battingStyle ?: "Right-hand bat") }
    var bowling by remember { mutableStateOf(player?.bowlingStyle ?: "Right-arm medium") }
    var jersey by remember { mutableStateOf((player?.jerseyNumber ?: 0).let { if (it == 0) "" else "$it" }) }
    var addTo by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val bowlingOptions = listOf("Right-arm fast", "Right-arm medium", "Right-arm off-break", "Right-arm leg-break", "Left-arm fast", "Left-arm medium", "Left-arm orthodox", "Left-arm wrist spin", "None")

    FullScreenDialog(
        title = if (player == null) "New player" else player.name,
        onDismiss = onDismiss,
        bottomBar = {
            Button(
                onClick = {
                    onSave(
                        PlayerEntity(
                            id = player?.id ?: 0,
                            name = name.trim(),
                            role = role,
                            battingStyle = batting,
                            bowlingStyle = bowling,
                            jerseyNumber = jersey.toIntOrNull() ?: 0
                        ),
                        if (player == null) addTo else null
                    )
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (player == null) "Add player" else "Save changes") }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = jersey,
            onValueChange = { jersey = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text("Shirt number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
        FieldLabel("Role")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in PlayerRole.values()) ChoicePill(r.label, role == r, { role = r })
        }
        FieldLabel("Bats")
        SegmentedControl(
            listOf("Right-handed", "Left-handed"),
            if (batting.startsWith("Left", ignoreCase = true)) 1 else 0,
            { batting = if (it == 1) "Left-hand bat" else "Right-hand bat" }
        )
        FieldLabel("Bowls")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (b in bowlingOptions) ChoicePill(b, bowling == b, { bowling = b })
        }
        if (player == null && teams.isNotEmpty()) {
            FieldLabel("Add to a team (optional)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill("No team", addTo == null, { addTo = null })
                for (t in teams.filter { !it.isSoloTeam }) {
                    ChoicePill(t.name, addTo == t.id, { addTo = t.id }, leading = { TeamBadge(t, size = 20.dp) })
                }
            }
        }
        if (player != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete player", color = LocalCricPalette.current.wicket)
            }
            Text(
                "Players who have batted, bowled or fielded in a match can't be deleted, so their records stay complete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete && player != null) {
        ConfirmDialog(
            title = "Delete ${player.name}?",
            message = "They will be removed from the pool and from every team.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                onDelete(player)
                onDismiss()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

// ------------------------------------------------------------------ Ground editor

@Composable
private fun GroundEditorDialog(
    ground: GroundEntity?,
    onSave: (GroundEntity) -> Unit,
    onDelete: (GroundEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(ground?.name ?: "") }
    var city by remember { mutableStateOf(ground?.city ?: "") }
    var pitch by remember { mutableStateOf(ground?.pitchType ?: "Turf") }
    var boundary by remember { mutableIntStateOf(ground?.boundarySizeMeters ?: 60) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pitches = listOf("Turf", "Matting", "Cement", "Box Astro-Turf", "Grass", "Tennis-ball ground")

    FullScreenDialog(
        title = if (ground == null) "New ground" else ground.name,
        onDismiss = onDismiss,
        bottomBar = {
            Button(
                onClick = {
                    onSave(
                        GroundEntity(
                            id = ground?.id ?: 0,
                            name = name.trim(),
                            city = city.trim().ifBlank { "Local" },
                            pitchType = pitch,
                            boundarySizeMeters = boundary
                        )
                    )
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (ground == null) "Add ground" else "Save changes") }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(48) },
            label = { Text("Ground name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = city,
            onValueChange = { city = it.take(40) },
            label = { Text("Town or area") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
        FieldLabel("Pitch")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (pt in pitches) ChoicePill(pt, pitch == pt, { pitch = pt })
        }
        FieldLabel("Boundary: $boundary m")
        Slider(
            value = boundary.toFloat(),
            onValueChange = { boundary = it.toInt() },
            valueRange = 20f..90f,
            steps = 13
        )
        if (ground != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete ground", color = LocalCricPalette.current.wicket)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete && ground != null) {
        ConfirmDialog(
            title = "Delete ${ground.name}?",
            message = "Matches played here are kept but will no longer show a ground.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                onDelete(ground)
                onDismiss()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
