package com.example.ui.scoring

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MatchType
import com.example.domain.BatterScore
import com.example.domain.BowlerFigures
import com.example.domain.CricketRules
import com.example.domain.LiveScoreState
import com.example.domain.ScorecardBuilder
import com.example.ui.components.BallChip
import com.example.ui.components.CardSurface
import com.example.ui.components.TeamBadge
import com.example.ui.components.ThinDivider
import com.example.ui.components.format1
import com.example.ui.components.format2
import com.example.ui.theme.BarlowCondensed
import com.example.ui.theme.LocalCricPalette
import com.example.ui.theme.NumberStyle

fun inningsLabel(index: Int): String = when (index) {
    1 -> "1st innings"
    2 -> "2nd innings"
    3 -> "3rd innings"
    else -> "${index}th innings"
}

/** The scoreboard: the one loud element on the screen. */
@Composable
fun ScorePanel(live: LiveScoreState, modifier: Modifier = Modifier) {
    val p = LocalCricPalette.current
    val match = live.match
    val inn = live.currentInnings
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = p.panel,
        contentColor = p.onPanel
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(live.battingTeam, size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (inn == null) "${live.team1.name} v ${live.team2.name}" else live.battingTeam.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = p.onPanel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (inn == null) "Toss to come" else inningsLabel(inn.inningsIndex),
                    style = MaterialTheme.typography.labelMedium,
                    color = p.onPanelMuted
                )
            }

            if (inn != null) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "${live.totalRuns}/${live.wickets}",
                        style = MaterialTheme.typography.displayLarge,
                        color = p.onPanel
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.padding(bottom = 10.dp)) {
                        Text(
                            text = "${CricketRules.oversText(live.legalBalls, match.ballsPerOver)} ov",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            color = p.onPanel
                        )
                        Text("of ${match.totalOversPerInnings}", style = MaterialTheme.typography.labelSmall, color = p.onPanelMuted)
                    }
                    Spacer(Modifier.weight(1f))
                    if (live.isSoloBatting) {
                        LivesIndicator(lost = live.wickets, total = live.maxWicketsAllowed, modifier = Modifier.padding(bottom = 14.dp))
                    }
                }
            }

            val headline = when {
                live.isFinished -> live.resultSummary
                live.isMatchComplete -> live.liveResult?.summary
                inn == null -> null
                live.targetRuns != null && !live.isInningsComplete -> {
                    val need = live.runsNeeded ?: 0
                    if (need <= 0) "Scores level" else "Need ${CricketRules.plural(need, "run")} from ${CricketRules.plural(live.ballsRemaining, "ball")}"
                }
                live.leadOrTrailSummary != null -> live.leadOrTrailSummary
                live.projectedScore != null -> "Projected score ${live.projectedScore}"
                else -> null
            }
            if (!headline.isNullOrBlank()) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = p.accent,
                    modifier = Modifier.padding(top = if (inn == null) 8.dp else 0.dp)
                )
            }

            if (inn != null) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.panelHigh))
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    PanelStat("Run rate", live.currentRunRate.format2(), Modifier.weight(1f))
                    when {
                        live.requiredRunRate != null && !live.isInningsComplete ->
                            PanelStat("Required", live.requiredRunRate.format2(), Modifier.weight(1f))
                        live.targetRuns != null -> PanelStat("Target", "${live.targetRuns}", Modifier.weight(1f))
                        else -> PanelStat("Extras", "${extrasTotal(live)}", Modifier.weight(1f))
                    }
                    PanelStat("Partnership", "${live.partnershipRuns} (${live.partnershipBalls})", Modifier.weight(1.2f))
                }
            }

            val others = live.allInnings.filter { it.id != inn?.id }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    for (o in others) {
                        val team = if (o.battingTeamId == live.team1.id) live.team1 else live.team2
                        Text(
                            text = "${team.shortName} ${o.runs + o.penaltyRuns}/${o.wickets} (${CricketRules.oversText(o.ballsBowled, match.ballsPerOver)})",
                            style = MaterialTheme.typography.labelLarge,
                            color = p.onPanelMuted
                        )
                    }
                }
            }
        }
    }
}

private fun extrasTotal(live: LiveScoreState): Int =
    live.inningsBalls.filter { !it.isPenaltyRun }.sumOf { it.extraRuns } + (live.currentInnings?.penaltyRuns ?: 0)

@Composable
private fun PanelStat(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalCricPalette.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.onPanelMuted)
        Text(value, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = p.onPanel)
    }
}

@Composable
private fun LivesIndicator(lost: Int, total: Int, modifier: Modifier = Modifier) {
    val p = LocalCricPalette.current
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text("Lives", style = MaterialTheme.typography.labelSmall, color = p.onPanelMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total.coerceAtMost(10)) { i ->
                Box(
                    Modifier.size(9.dp).clip(CircleShape)
                        .background(if (i < total - lost) p.accent else p.panelHigh)
                )
            }
        }
    }
}

/** The balls of the over in progress (or the last over between overs). */
@Composable
fun OverStrip(live: LiveScoreState, modifier: Modifier = Modifier) {
    val showingLast = live.currentOverBalls.isEmpty() && live.previousOverBalls.isNotEmpty()
    val balls = if (showingLast) live.previousOverBalls else live.currentOverBalls
    val overNumber = if (showingLast) live.currentOverIndex else live.currentOverIndex + 1
    CardSurface(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(64.dp)) {
                Text(if (showingLast) "Last over" else "This over", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Over $overNumber", style = MaterialTheme.typography.titleSmall)
            }
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (balls.isEmpty()) {
                    Text("Waiting for the first ball", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for (b in balls) BallChip(b)
            }
            if (balls.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "${balls.sumOf { it.runsOffBat + it.extraRuns }}",
                    style = NumberStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/** Batters at the crease and the current bowler with their figures. */
@Composable
fun CreaseCard(live: LiveScoreState, modifier: Modifier = Modifier) {
    val inn = live.currentInnings ?: return
    val bpo = live.match.ballsPerOver
    val card = remember(live.inningsBalls, live.striker?.id, live.nonStriker?.id) {
        ScorecardBuilder.build(inn, live.inningsBalls, bpo, atCreaseIds = listOf(live.striker?.id, live.nonStriker?.id))
    }
    val batters = card.batters.associateBy { it.playerId }
    val bowlers = card.bowlers.associateBy { it.playerId }
    val palette = LocalCricPalette.current
    CardSurface(modifier = modifier) {
        TableHeader("Batter", listOf("R", "B", "4s", "6s", "SR"))
        val atCrease = listOfNotNull(live.striker, live.nonStriker)
        if (atCrease.isEmpty()) {
            Text("No batters at the crease", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        }
        for (player in atCrease) {
            val s = batters[player.id] ?: BatterScore(player.id)
            val onStrike = player.id == live.striker?.id
            TableRow(
                name = player.name,
                marker = if (onStrike) palette.accent else null,
                bold = onStrike,
                values = listOf("${s.runs}", "${s.balls}", "${s.fours}", "${s.sixes}", s.strikeRate.format1())
            )
        }
        ThinDivider(Modifier.padding(vertical = 8.dp))
        TableHeader("Bowler", listOf("O", "M", "R", "W", "Econ"))
        val bowler = live.bowler
        if (bowler == null) {
            Text("Choose the next bowler", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        } else {
            val f = bowlers[bowler.id] ?: BowlerFigures(bowler.id)
            TableRow(
                name = bowler.name,
                marker = null,
                bold = true,
                values = listOf(CricketRules.oversText(f.legalBalls, bpo), "${f.maidens}", "${f.runs}", "${f.wickets}", f.economy(bpo).format2())
            )
        }
    }
}

@Composable
fun TableHeader(first: String, columns: List<String>, modifier: Modifier = Modifier, firstWeight: Float = 2.6f) {
    Row(modifier = modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(first, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(firstWeight))
        for (c in columns) {
            Text(c, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun TableRow(
    name: String,
    marker: Color?,
    bold: Boolean,
    values: List<String>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    firstWeight: Float = 2.6f,
    emphasiseFirstValue: Boolean = true
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(firstWeight), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(marker ?: Color.Transparent))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
        values.forEachIndexed { i, v ->
            Text(
                v,
                style = NumberStyle.copy(fontSize = 17.sp, fontWeight = if (i == 0 && emphasiseFirstValue) FontWeight.Bold else FontWeight.Medium),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** The last few overs, newest first. */
@Composable
fun RecentOvers(live: LiveScoreState, modifier: Modifier = Modifier) {
    val inn = live.currentInnings ?: return
    val card = remember(live.inningsBalls) { ScorecardBuilder.build(inn, live.inningsBalls, live.match.ballsPerOver) }
    val overs = card.overs.takeLast(4).reversed()
    if (overs.isEmpty()) return
    CardSurface(modifier = modifier) {
        Text("Recent overs", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        for (o in overs) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.width(92.dp)) {
                    Text("Over ${o.overIndex + 1}", style = MaterialTheme.typography.labelLarge)
                    Text(
                        live.playersById[o.bowlerId]?.name ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (b in o.balls) BallChip(b, size = 26.dp)
                }
                Text("${o.runs}", style = NumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

fun isTwoInnings(live: LiveScoreState): Boolean = live.match.matchType == MatchType.MINI_TEST_2_INNINGS
