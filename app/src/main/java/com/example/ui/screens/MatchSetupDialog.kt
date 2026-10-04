package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.data.model.GroundEntity
import com.example.data.model.MatchType
import com.example.data.model.TossDecision
import com.example.ui.components.ChoicePill
import com.example.ui.components.EmptyState
import com.example.ui.components.FieldLabel
import com.example.ui.components.FullScreenDialog
import com.example.ui.components.InfoNote
import com.example.ui.components.SegmentedControl
import com.example.ui.components.Stepper
import com.example.ui.components.TeamBadge
import com.example.ui.scoring.squadProblem
import com.example.ui.theme.LocalCricPalette
import com.example.ui.viewmodels.TeamListItem
import kotlin.random.Random

@Composable
fun MatchSetupDialog(
    teams: List<TeamListItem>,
    grounds: List<GroundEntity>,
    onDismiss: () -> Unit,
    onCreateTeams: () -> Unit,
    onStart: (
        team1Id: Long,
        team2Id: Long,
        groundId: Long?,
        matchType: MatchType,
        overs: Int,
        ballsPerOver: Int,
        soloWickets1: Int,
        soloWickets2: Int,
        tossWinnerId: Long,
        tossDecision: TossDecision
    ) -> Unit
) {
    var format by remember { mutableStateOf(MatchType.STANDARD_LIMITED_OVERS) }
    var team1Id by remember { mutableStateOf(teams.getOrNull(0)?.team?.id) }
    var team2Id by remember { mutableStateOf(teams.getOrNull(1)?.team?.id) }
    var overs by remember { mutableIntStateOf(10) }
    var ballsPerOver by remember { mutableIntStateOf(6) }
    var groundId by remember { mutableStateOf<Long?>(null) }
    var solo1 by remember { mutableIntStateOf(3) }
    var solo2 by remember { mutableIntStateOf(3) }
    var tossWinner by remember { mutableStateOf<Long?>(null) }
    var decision by remember { mutableStateOf(TossDecision.BAT) }
    var coinNote by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }

    val t1 = teams.find { it.team.id == team1Id }
    val t2 = teams.find { it.team.id == team2Id }
    val problems = listOfNotNull(
        t1?.let { squadProblem(it.team, it.players) },
        t2?.let { squadProblem(it.team, it.players) }
    )
    val ready = t1 != null && t2 != null && t1.team.id != t2.team.id && tossWinner != null && problems.isEmpty() && !starting

    val startBar: (@Composable () -> Unit)? = if (teams.size >= 2) {
        {
            Button(
                onClick = {
                    val winner = tossWinner
                    if (t1 != null && t2 != null && winner != null) {
                        starting = true
                        onStart(t1.team.id, t2.team.id, groundId, format, overs, ballsPerOver, solo1, solo2, winner, decision)
                    }
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (tossWinner == null) "Do the toss to start" else "Start match") }
        }
    } else null

    FullScreenDialog(
        title = "New match",
        onDismiss = onDismiss,
        bottomBar = startBar
    ) {
        if (teams.size < 2) {
            EmptyState(
                icon = Icons.Default.Groups,
                title = "You need two teams",
                message = "Create teams and add players in Squads, then come back to start a match.",
                actionLabel = "Go to Squads",
                onAction = onCreateTeams
            )
            return@FullScreenDialog
        }

        FieldLabel("Format")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in MatchType.values()) {
                ChoicePill(f.label, format == f, {
                    format = f
                    overs = when (f) {
                        MatchType.BOX_GULLY -> 6
                        MatchType.SOLO_DUEL -> 5
                        MatchType.MINI_TEST_2_INNINGS -> 8
                        MatchType.STANDARD_LIMITED_OVERS -> 10
                    }
                })
            }
        }
        Text(format.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

        FieldLabel("Home side")
        TeamPicker(teams, team1Id, excludeId = null) { id ->
            team1Id = id
            if (team2Id == id) team2Id = teams.firstOrNull { it.team.id != id }?.team?.id
            tossWinner = null
            coinNote = null
        }
        FieldLabel("Away side")
        TeamPicker(teams, team2Id, excludeId = team1Id) { id ->
            team2Id = id
            tossWinner = null
            coinNote = null
        }

        if (t1?.team?.isSoloTeam == true) {
            FieldLabel("${t1.team.name}: lives (wickets before out)")
            Stepper(solo1, { solo1 = it }, 2..10)
        }
        if (t2?.team?.isSoloTeam == true) {
            FieldLabel("${t2.team.name}: lives (wickets before out)")
            Stepper(solo2, { solo2 = it }, 2..10)
        }

        FieldLabel(if (format == MatchType.MINI_TEST_2_INNINGS) "Overs per innings (two innings each)" else "Overs per innings")
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
            FieldLabel("Ground")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill("Not set", groundId == null, { groundId = null })
                for (g in grounds) ChoicePill(g.name, groundId == g.id, { groundId = g.id })
            }
        }

        if (t1 != null && t2 != null) {
            FieldLabel("Toss")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in listOf(t1, t2)) {
                    ChoicePill(
                        t.team.name,
                        tossWinner == t.team.id,
                        { tossWinner = t.team.id; coinNote = null },
                        leading = { TeamBadge(t.team, size = 22.dp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            TextButton(onClick = {
                val pick = if (Random.nextBoolean()) t1 else t2
                tossWinner = pick.team.id
                coinNote = "The coin says ${pick.team.name}"
            }) { Text("Flip a coin") }
            coinNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalCricPalette.current.accentText) }
            FieldLabel("Toss winner chose to")
            SegmentedControl(listOf("Bat first", "Bowl first"), if (decision == TossDecision.BAT) 0 else 1, {
                decision = if (it == 0) TossDecision.BAT else TossDecision.BOWL
            })
        }

        for (p in problems) {
            Spacer(Modifier.height(10.dp))
            InfoNote(p, tone = LocalCricPalette.current.wicket)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TeamPicker(teams: List<TeamListItem>, selectedId: Long?, excludeId: Long?, onSelect: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in teams) {
            if (t.team.id == excludeId) continue
            ChoicePill(
                text = t.team.name,
                selected = t.team.id == selectedId,
                onClick = { onSelect(t.team.id) },
                leading = { TeamBadge(t.team, size = 22.dp) }
            )
        }
    }
    val chosen = teams.find { it.team.id == selectedId }
    if (chosen != null) {
        Text(
            if (chosen.team.isSoloTeam) "Solo side: ${chosen.players.firstOrNull()?.name ?: "no player yet"}"
            else "${chosen.players.size} players",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
