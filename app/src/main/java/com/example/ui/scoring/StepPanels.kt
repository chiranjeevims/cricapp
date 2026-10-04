package com.example.ui.scoring

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MatchStatus
import com.example.data.model.PlayerEntity
import com.example.data.model.TeamEntity
import com.example.data.model.TossDecision
import com.example.domain.BowlerFigures
import com.example.domain.CricketRules
import com.example.domain.CricketScoringEngine
import com.example.domain.LiveScoreState
import com.example.domain.ScorecardBuilder
import com.example.ui.components.BallChip
import com.example.ui.components.CardSurface
import com.example.ui.components.ChoicePill
import com.example.ui.components.FieldLabel
import com.example.ui.components.InfoNote
import com.example.ui.components.SegmentedControl
import com.example.ui.components.TeamBadge
import com.example.ui.theme.LocalCricPalette
import kotlin.random.Random

/** Problems that stop a side from batting or bowling, in plain words. */
fun squadProblem(team: TeamEntity, squad: List<PlayerEntity>): String? = when {
    squad.isEmpty() -> "${team.name} has no players yet. Add players in Squads first."
    !team.isSoloTeam && squad.size < 2 -> "${team.name} needs at least 2 players, or switch it to a solo team in Squads."
    else -> null
}

@Composable
fun PlayerPills(
    players: List<PlayerEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    disabledIds: Set<Long> = emptySet()
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (pl in players) {
            ChoicePill(
                text = pl.name,
                selected = pl.id == selectedId,
                enabled = pl.id !in disabledIds,
                onClick = { onSelect(pl.id) }
            )
        }
    }
}

@Composable
fun TossPanel(live: LiveScoreState, onStart: (Long, TossDecision) -> Unit, modifier: Modifier = Modifier) {
    var winner by remember(live.match.id) { mutableStateOf<Long?>(null) }
    var decision by remember(live.match.id) { mutableStateOf(TossDecision.BAT) }
    var coinNote by remember(live.match.id) { mutableStateOf<String?>(null) }
    val problems = listOfNotNull(squadProblem(live.team1, live.squad1), squadProblem(live.team2, live.squad2))

    CardSurface(modifier = modifier) {
        Text("Toss", style = MaterialTheme.typography.titleLarge)
        Text("Who won the toss?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in listOf(live.team1, live.team2)) {
                ChoicePill(
                    text = t.name,
                    selected = winner == t.id,
                    onClick = { winner = t.id },
                    leading = { TeamBadge(t, size = 22.dp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        TextButton(onClick = {
            val pick = if (Random.nextBoolean()) live.team1 else live.team2
            winner = pick.id
            coinNote = "The coin says ${pick.name}"
        }) { Text("Flip a coin") }
        coinNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalCricPalette.current.accentText) }
        FieldLabel("They chose to")
        SegmentedControl(
            options = listOf("Bat first", "Bowl first"),
            selectedIndex = if (decision == TossDecision.BAT) 0 else 1,
            onSelect = { decision = if (it == 0) TossDecision.BAT else TossDecision.BOWL }
        )
        for (p in problems) {
            Spacer(Modifier.height(10.dp))
            InfoNote(p, tone = LocalCricPalette.current.wicket)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { winner?.let { onStart(it, decision) } },
            enabled = winner != null && problems.isEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Start match") }
    }
}

@Composable
fun OpenersPanel(
    live: LiveScoreState,
    onConfirm: (striker: Long, nonStriker: Long?, bowler: Long) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inn = live.currentInnings ?: return
    val batters = live.battingSquad
    val bowlers = live.bowlingSquad
    var striker by remember(inn.id) { mutableStateOf(batters.getOrNull(0)?.id) }
    var nonStriker by remember(inn.id) { mutableStateOf(if (live.isSoloBatting) null else batters.getOrNull(1)?.id) }
    var bowler by remember(inn.id) { mutableStateOf(bowlers.firstOrNull()?.id) }
    val problem = squadProblem(live.battingTeam, batters) ?: if (bowlers.isEmpty()) "${live.bowlingTeam.name} has no players to bowl." else null
    val valid = problem == null && striker != null && bowler != null &&
        (live.isSoloBatting || (nonStriker != null && nonStriker != striker))

    CardSurface(modifier = modifier) {
        Text("${inningsLabel(inn.inningsIndex).replaceFirstChar { it.uppercase() }}: ${live.battingTeam.name} bat", style = MaterialTheme.typography.titleLarge)
        if (live.targetRuns != null) {
            Text(
                "Target ${live.targetRuns} from ${live.match.totalOversPerInnings} overs",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalCricPalette.current.accentText
            )
        }
        if (problem != null) {
            Spacer(Modifier.height(10.dp))
            InfoNote(problem, tone = LocalCricPalette.current.wicket)
        } else {
            FieldLabel(if (live.isSoloBatting) "Batter" else "On strike")
            PlayerPills(batters, striker, { id -> striker = id; if (nonStriker == id) nonStriker = null })
            if (!live.isSoloBatting) {
                FieldLabel("Non-striker")
                PlayerPills(batters, nonStriker, { nonStriker = it }, disabledIds = setOfNotNull(striker))
            }
            FieldLabel("Opening bowler")
            PlayerPills(bowlers, bowler, { bowler = it })
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onConfirm(striker!!, nonStriker, bowler!!) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Start innings") }
        if (inn.inningsIndex > 1) {
            TextButton(onClick = onUndo, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Back to the previous innings")
            }
        }
    }
}

@Composable
fun NextBowlerPanel(live: LiveScoreState, onSelect: (Long) -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val inn = live.currentInnings ?: return
    val bpo = live.match.ballsPerOver
    val figures = remember(live.inningsBalls) {
        ScorecardBuilder.build(inn, live.inningsBalls, bpo).bowlers.associateBy { it.playerId }
    }
    val lastOver = live.previousOverBalls
    val blockPrevious = !live.isSoloBowling && live.bowlingSquad.size > 1
    CardSurface(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("End of over ${live.currentOverIndex}", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${lastOver.sumOf { it.runsOffBat + it.extraRuns }} runs, ${lastOver.count { it.isWicket }} wickets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) { for (b in lastOver) BallChip(b, size = 28.dp) }
        Text("Who bowls the next over?", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        for (pl in live.bowlingSquad) {
            val f = figures[pl.id] ?: BowlerFigures(pl.id)
            val blocked = blockPrevious && pl.id == live.previousBowlerId
            Surface(
                onClick = { onSelect(pl.id) },
                enabled = !blocked,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            pl.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (blocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (blocked) "Bowled the last over" else pl.bowlingStyle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (f.legalBalls == 0 && f.runs == 0) "Yet to bowl"
                        else "${CricketRules.oversText(f.legalBalls, bpo)}-${f.maidens}-${f.runs}-${f.wickets}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        TextButton(onClick = onUndo, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Undo last ball") }
    }
}

@Composable
fun InningsBreakPanel(live: LiveScoreState, onNext: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val inn = live.currentInnings ?: return
    val match = live.match
    val nextIndex = inn.inningsIndex + 1
    val nextBatting = live.bowlingTeam
    val nextIsFinal = nextIndex >= CricketScoringEngine.maxInnings(match)
    val target = if (nextIsFinal) {
        if (isTwoInnings(live)) {
            val a1 = CricketRules.inningsTotal(live.allInnings.find { it.inningsIndex == 1 })
            val b1 = CricketRules.inningsTotal(live.allInnings.find { it.inningsIndex == 2 })
            a1 + live.totalRuns - b1 + 1
        } else live.totalRuns + 1
    } else null

    CardSurface(modifier = modifier) {
        Text("Innings break", style = MaterialTheme.typography.titleLarge)
        Text(
            "${live.battingTeam.name} made ${live.totalRuns}/${live.wickets} in ${CricketRules.oversText(live.legalBalls, match.ballsPerOver)} overs",
            style = MaterialTheme.typography.bodyLarge
        )
        when {
            target != null -> Text(
                "${nextBatting.name} need $target to win",
                style = MaterialTheme.typography.titleMedium,
                color = LocalCricPalette.current.accentText,
                modifier = Modifier.padding(top = 4.dp)
            )
            live.leadOrTrailSummary != null -> Text(
                live.leadOrTrailSummary,
                style = MaterialTheme.typography.titleMedium,
                color = LocalCricPalette.current.accentText,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Start ${inningsLabel(nextIndex)}")
        }
        TextButton(onClick = onUndo, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Undo last ball") }
    }
}

@Composable
fun ResultPanel(
    live: LiveScoreState,
    onScorecard: () -> Unit,
    onShare: () -> Unit,
    onRematch: () -> Unit,
    onUndo: () -> Unit,
    onReopen: () -> Unit,
    onChoosePotm: () -> Unit,
    onSettle: (TeamEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCricPalette.current
    val potm = live.match.potmPlayerId?.let { live.playersById[it] }
    val winner = live.match.winnerTeamId?.let { if (it == live.team1.id) live.team1 else live.team2 }
    CardSurface(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = p.accentText)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        winner != null -> "${winner.name} win"
                        live.match.status == MatchStatus.ABANDONED_RAIN -> "No result"
                        live.resultSummary.startsWith("Match tied") -> "Match tied"
                        live.resultSummary.startsWith("Match drawn") -> "Match drawn"
                        else -> "Match over"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                Text(live.resultSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (live.needsWinnerDecision) {
            Spacer(Modifier.height(12.dp))
            InfoNote("This is a knockout match, so someone has to go through. Who won the super over, bowl-out or toss?", tone = p.accentText)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in listOf(live.team1, live.team2)) {
                    OutlinedButton(onClick = { onSettle(t) }, modifier = Modifier.weight(1f)) {
                        Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (live.isFinished && live.match.status != MatchStatus.ABANDONED_RAIN) {
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = onChoosePotm,
                shape = RoundedCornerShape(12.dp),
                color = p.accent.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = p.accentText)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Player of the match", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(potm?.name ?: "Tap to choose", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onScorecard, modifier = Modifier.weight(1f)) { Text("Scorecard") }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            TextButton(onClick = onRematch, modifier = Modifier.weight(1f)) { Text("Rematch") }
            when (live.match.status) {
                MatchStatus.DECLARED_WINNER, MatchStatus.ABANDONED_RAIN ->
                    TextButton(onClick = onReopen, modifier = Modifier.weight(1f)) { Text("Reopen match") }
                else -> if (live.currentInnings != null) {
                    TextButton(onClick = onUndo, modifier = Modifier.weight(1f)) { Text("Undo last ball") }
                }
            }
        }
    }
}

@Composable
fun MiniTeamRow(team: TeamEntity, trailing: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        TeamBadge(team, size = 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(team.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.labelLarge, color = color)
    }
}
