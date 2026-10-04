package com.example.ui.scoring

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExtraType
import com.example.data.model.GroundEntity
import com.example.data.model.MatchStage
import com.example.data.model.MatchStatus
import com.example.data.model.PlayerEntity
import com.example.data.model.TeamEntity
import com.example.domain.LiveScoreState
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingBox
import com.example.ui.viewmodels.MatchViewModel
import com.example.ui.viewmodels.ScoringUiState

private sealed interface ScoringDialog {
    data object Wicket : ScoringDialog
    data object Tools : ScoringDialog
    data object Scorecard : ScoringDialog
    data object Penalty : ScoringDialog
    data object OtherRuns : ScoringDialog
    data object Overs : ScoringDialog
    data object Crease : ScoringDialog
    data object Award : ScoringDialog
    data object Potm : ScoringDialog
    data object ConfirmAbandon : ScoringDialog
    data object ConfirmEndInnings : ScoringDialog
    data object ConfirmReset : ScoringDialog
    data object ConfirmDelete : ScoringDialog
    data class AddToSquad(val team: TeamEntity) : ScoringDialog
    data class Settle(val team: TeamEntity) : ScoringDialog
}

@Composable
fun ScoringScreen(
    vm: MatchViewModel,
    allPlayers: List<PlayerEntity>,
    grounds: List<GroundEntity>,
    onBack: () -> Unit,
    onOpenMatch: (Long) -> Unit
) {
    BackHandler { onBack() }
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val view = LocalView.current
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<ScoringDialog?>(null) }
    var pendingExtra by remember { mutableStateOf<ExtraType?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }
    // Keep the screen awake while scoring.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    val live = (ui as? ScoringUiState.Ready)?.live
    val venue = live?.match?.groundId?.let { id -> grounds.find { it.id == id } }?.let { "${it.name}, ${it.city}" }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (live != null) "${live.team1.shortName} v ${live.team2.shortName}" else "Match",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1
                    )
                    if (live != null) {
                        Text(
                            text = if (live.match.stage != MatchStage.STANDALONE) live.match.stage.label
                            else "${live.match.matchType.label}, ${live.match.totalOversPerInnings} overs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (live != null) {
                    IconButton(onClick = { dialog = ScoringDialog.Scorecard }) {
                        Icon(Icons.Default.Assessment, contentDescription = "Scorecard")
                    }
                    IconButton(onClick = { dialog = ScoringDialog.Tools }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Match tools")
                    }
                }
            }
        },
        bottomBar = {
            if (live != null && live.canScore) {
                ScoringKeypad(
                    pendingExtra = pendingExtra,
                    onPendingExtra = { tap(); pendingExtra = it },
                    onRuns = { r ->
                        tap()
                        val extra = pendingExtra
                        if (extra == null) vm.scoreRuns(r) else vm.scoreExtra(extra, r)
                        pendingExtra = null
                    },
                    onWicket = { tap(); dialog = ScoringDialog.Wicket },
                    onUndo = { tap(); pendingExtra = null; vm.undo() },
                    onSwap = { tap(); vm.swapStrike() },
                    onMore = { dialog = ScoringDialog.Tools },
                    canSwap = !live.isSoloBatting && live.nonStriker != null,
                    canUndo = live.lastBall != null
                )
            }
        }
    ) { padding ->
        when (val state = ui) {
            is ScoringUiState.Loading -> LoadingBox(Modifier.padding(padding))
            is ScoringUiState.Missing -> EmptyState(
                icon = Icons.Default.WarningAmber,
                title = "Match not found",
                message = "It may have been deleted.",
                actionLabel = "Go back",
                onAction = onBack,
                modifier = Modifier.padding(padding)
            )
            is ScoringUiState.Ready -> ScoringContent(
                live = state.live,
                modifier = Modifier.padding(padding),
                onStartMatch = { winner, decision -> vm.startMatch(winner, decision) },
                onOpeners = { s, ns, b -> vm.setOpeners(s, ns, b) },
                onBowler = { tap(); vm.selectBowler(it) },
                onUndo = { vm.undo() },
                onNextInnings = { vm.startNextInnings() },
                onScorecard = { dialog = ScoringDialog.Scorecard },
                onShare = { shareScorecard(context, state.live, venue) },
                onRematch = { vm.rematch(onOpenMatch) },
                onReopen = { vm.reopen() },
                onPotm = { dialog = ScoringDialog.Potm },
                onSettle = { dialog = ScoringDialog.Settle(it) }
            )
        }
    }

    val l = live ?: return
    when (val d = dialog) {
        null -> {}
        ScoringDialog.Wicket -> WicketDialog(
            live = l,
            onConfirm = { w, runs ->
                vm.recordWicket(w, runs)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        ScoringDialog.Tools -> ToolsDialog(actions = toolActions(l, setDialog = { dialog = it }, vm = vm, onShare = { shareScorecard(context, l, venue) }, onRematch = { vm.rematch(onOpenMatch) }), onDismiss = { if (dialog == ScoringDialog.Tools) dialog = null })
        ScoringDialog.Scorecard -> ScorecardDialog(l, venue, onDismiss = { dialog = null })
        ScoringDialog.Penalty -> NumberDialog(
            title = "Penalty runs",
            message = "Added to ${l.battingTeam.name}'s total as extras. No ball is bowled.",
            initial = 5,
            range = 1..20,
            confirmLabel = "Add runs",
            onConfirm = { vm.addPenalty(it) },
            onDismiss = { dialog = null }
        )
        ScoringDialog.OtherRuns -> NumberDialog(
            title = "Runs off the bat",
            message = "For overthrows and all-run fives. Odd numbers change the strike.",
            initial = 5,
            range = 5..9,
            confirmLabel = "Record",
            onConfirm = { vm.scoreRuns(it) },
            onDismiss = { dialog = null }
        )
        ScoringDialog.Overs -> NumberDialog(
            title = "Overs per innings",
            message = "Use this when a match is shortened, for example by rain.",
            initial = l.match.totalOversPerInnings,
            range = 1..100,
            confirmLabel = "Save",
            onConfirm = { vm.changeOvers(it) },
            onDismiss = { dialog = null },
            suffix = "overs"
        )
        ScoringDialog.Crease -> CreaseDialog(
            live = l,
            onSave = { s, ns, b -> vm.updateCrease(s, ns, b) },
            onAddPlayers = { dialog = ScoringDialog.AddToSquad(it) },
            onDismiss = { if (dialog == ScoringDialog.Crease) dialog = null }
        )
        is ScoringDialog.AddToSquad -> {
            val inSquad = (if (d.team.id == l.team1.id) l.squad1 else l.squad2).map { it.id }.toSet()
            PlayerPickerDialog(
                title = "Add to ${d.team.name}",
                players = allPlayers.filter { it.id !in inSquad },
                onConfirm = { ids -> vm.addPlayersToSquad(d.team.id, ids) },
                onDismiss = { dialog = ScoringDialog.Crease }
            )
        }
        ScoringDialog.Award -> AwardDialog(l, onAward = { id, reason -> vm.declareWinner(id, reason) }, onDismiss = { dialog = null })
        ScoringDialog.Potm -> PotmDialog(l, onPick = { vm.setPlayerOfMatch(it) }, onDismiss = { dialog = null })
        is ScoringDialog.Settle -> SettleDialog(d.team, onSettle = { vm.settleKnockout(d.team.id, it) }, onDismiss = { dialog = null })
        ScoringDialog.ConfirmAbandon -> ConfirmDialog(
            title = "Abandon the match?",
            message = "It will count as no result: 1 point each in a league. You can reopen it later.",
            confirmLabel = "Abandon",
            destructive = true,
            onConfirm = { vm.abandon() },
            onDismiss = { dialog = null }
        )
        ScoringDialog.ConfirmEndInnings -> ConfirmDialog(
            title = "End this innings now?",
            message = "Use this for a declaration or when the batting side can't continue. You can undo it.",
            confirmLabel = "End innings",
            onConfirm = { vm.endInnings() },
            onDismiss = { dialog = null }
        )
        ScoringDialog.ConfirmReset -> ConfirmDialog(
            title = "Reset the match?",
            message = "Every ball will be cleared and the match goes back to the toss. This can't be undone.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = { vm.reset() },
            onDismiss = { dialog = null }
        )
        ScoringDialog.ConfirmDelete -> ConfirmDialog(
            title = "Delete this match?",
            message = if (l.match.tournamentId != null) "The fixture and all its scores will be removed from the tournament. This can't be undone."
            else "The match and all its scores will be removed. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deleteMatch(onDeleted = onBack) },
            onDismiss = { dialog = null }
        )
    }
}

private fun toolActions(
    l: LiveScoreState,
    setDialog: (ScoringDialog?) -> Unit,
    vm: MatchViewModel,
    onShare: () -> Unit,
    onRematch: () -> Unit
): List<ToolAction> {
    val open = !l.isFinished
    val started = l.currentInnings != null
    val list = mutableListOf<ToolAction>()
    if (open && started) {
        list += ToolAction(Icons.Default.Groups, "Change players", "Substitute, retire hurt or fix the crease") { setDialog(ScoringDialog.Crease) }
        list += ToolAction(Icons.Default.Add, "Penalty runs", "Award runs to the batting side") { setDialog(ScoringDialog.Penalty) }
    }
    if (l.canScore) {
        list += ToolAction(Icons.Default.Edit, "5 or more runs", "Overthrows and all-run fives") { setDialog(ScoringDialog.OtherRuns) }
    }
    if (open) {
        list += ToolAction(Icons.Default.Timer, "Change overs", "Now ${l.match.totalOversPerInnings} overs per innings") { setDialog(ScoringDialog.Overs) }
    }
    if (open && started && !l.isInningsComplete) {
        list += ToolAction(Icons.Default.Flag, "End innings", "Declare or close the innings early") { setDialog(ScoringDialog.ConfirmEndInnings) }
    }
    if (open) {
        list += ToolAction(Icons.Default.Gavel, "Award the match", "Walkover or forfeit, winner gets 2 points") { setDialog(ScoringDialog.Award) }
        list += ToolAction(Icons.Default.Block, "Abandon", "Rain or bad light: no result") { setDialog(ScoringDialog.ConfirmAbandon) }
    }
    if (l.match.status == MatchStatus.DECLARED_WINNER || l.match.status == MatchStatus.ABANDONED_RAIN) {
        list += ToolAction(Icons.Default.Refresh, "Reopen match", "Clear the result and carry on scoring") { vm.reopen() }
    }
    if (l.isFinished && l.match.status != MatchStatus.ABANDONED_RAIN) {
        list += ToolAction(Icons.Default.Star, "Player of the match", l.match.potmPlayerId?.let { l.playersById[it]?.name }) { setDialog(ScoringDialog.Potm) }
    }
    list += ToolAction(Icons.Default.Share, "Share scorecard", null) { onShare() }
    if (l.isFinished) list += ToolAction(Icons.Default.Replay, "Rematch", "Same teams and settings") { onRematch() }
    list += ToolAction(Icons.Default.Refresh, "Reset match", "Clear every ball and start again", destructive = true) { setDialog(ScoringDialog.ConfirmReset) }
    list += ToolAction(Icons.Default.Delete, "Delete match", null, destructive = true) { setDialog(ScoringDialog.ConfirmDelete) }
    return list
}

@Composable
private fun ScoringContent(
    live: LiveScoreState,
    modifier: Modifier,
    onStartMatch: (Long, com.example.data.model.TossDecision) -> Unit,
    onOpeners: (Long, Long?, Long) -> Unit,
    onBowler: (Long) -> Unit,
    onUndo: () -> Unit,
    onNextInnings: () -> Unit,
    onScorecard: () -> Unit,
    onShare: () -> Unit,
    onRematch: () -> Unit,
    onReopen: () -> Unit,
    onPotm: () -> Unit,
    onSettle: (TeamEntity) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScorePanel(live)
        val showResult = live.isFinished || live.isMatchComplete
        when {
            showResult -> ResultPanel(
                live = live,
                onScorecard = onScorecard,
                onShare = onShare,
                onRematch = onRematch,
                onUndo = onUndo,
                onReopen = onReopen,
                onChoosePotm = onPotm,
                onSettle = onSettle
            )
            live.needsToss -> TossPanel(live, onStart = onStartMatch)
            live.needsOpeners -> OpenersPanel(live, onConfirm = onOpeners, onUndo = onUndo)
            live.isInningsComplete -> InningsBreakPanel(live, onNext = onNextInnings, onUndo = onUndo)
            live.needsNewBowler -> NextBowlerPanel(live, onSelect = onBowler, onUndo = onUndo)
        }
        if (live.currentInnings != null && !showResult) {
            if (live.canScore) OverStrip(live)
            if (!live.needsOpeners && !live.isInningsComplete) CreaseCard(live)
            RecentOvers(live)
        }
        Spacer(Modifier.height(8.dp))
    }
}
