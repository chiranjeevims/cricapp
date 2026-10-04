package com.example.ui.scoring

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.CricketRules
import com.example.domain.InningsCard
import com.example.domain.LiveScoreState
import com.example.domain.MatchFormat
import com.example.domain.ScorecardBuilder
import com.example.ui.components.BallChip
import com.example.ui.components.CardSurface
import com.example.ui.components.FullScreenDialog
import com.example.ui.components.SegmentedControl
import com.example.ui.components.ThinDivider
import com.example.ui.components.format1
import com.example.ui.components.format2
import com.example.ui.theme.LocalCricPalette
import com.example.ui.theme.NumberStyle

fun shareScorecard(context: Context, live: LiveScoreState, venue: String?) {
    val text = ScorecardBuilder.shareText(
        match = live.match,
        team1 = live.team1,
        team2 = live.team2,
        innings = live.allInnings,
        balls = live.allBalls,
        players = live.playersById,
        resultLine = live.resultSummary,
        potm = live.match.potmPlayerId?.let { live.playersById[it] },
        venue = venue
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${live.team1.name} vs ${live.team2.name}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share scorecard"))
}

@Composable
fun ScorecardDialog(live: LiveScoreState, venue: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val innings = live.allInnings
    var tab by remember { mutableIntStateOf((innings.size - 1).coerceAtLeast(0)) }
    val bpo = live.match.ballsPerOver
    val cards = remember(live.allBalls, innings) {
        val byInnings = live.allBalls.groupBy { it.inningsId }
        innings.map { inn ->
            val squad = if (inn.battingTeamId == live.team1.id) live.squad1 else live.squad2
            val crease = if (inn.id == live.currentInnings?.id) listOf(live.striker?.id, live.nonStriker?.id) else emptyList()
            ScorecardBuilder.build(inn, byInnings[inn.id].orEmpty(), bpo, squad.map { it.id }, crease)
        }
    }
    fun name(id: Long?) = id?.let { live.playersById[it]?.name } ?: "Unknown"

    FullScreenDialog(
        title = "Scorecard",
        subtitle = "${live.team1.name} v ${live.team2.name}",
        onDismiss = onDismiss,
        headerActions = {
            IconButton(onClick = { shareScorecard(context, live, venue) }) {
                Icon(Icons.Default.Share, contentDescription = "Share scorecard")
            }
        }
    ) {
        if (live.resultSummary.isNotBlank()) {
            Text(live.resultSummary, style = MaterialTheme.typography.titleMedium, color = LocalCricPalette.current.win)
            Spacer(Modifier.height(10.dp))
        }
        if (cards.isEmpty()) {
            Text("No balls bowled yet.", style = MaterialTheme.typography.bodyLarge)
            MatchInfo(live, venue)
            return@FullScreenDialog
        }
        if (cards.size > 1) {
            SegmentedControl(
                options = cards.map { c ->
                    val t = if (c.innings.battingTeamId == live.team1.id) live.team1 else live.team2
                    if (isTwoInnings(live)) "${t.shortName} ${if (c.innings.inningsIndex <= 2) 1 else 2}" else t.shortName
                },
                selectedIndex = tab.coerceIn(0, cards.size - 1),
                onSelect = { tab = it }
            )
            Spacer(Modifier.height(12.dp))
        }
        val card = cards[tab.coerceIn(0, cards.size - 1)]
        val team = if (card.innings.battingTeamId == live.team1.id) live.team1 else live.team2

        Row(verticalAlignment = Alignment.Bottom) {
            Text(team.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("${card.total}/${card.wickets}", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(8.dp))
            Text("(${CricketRules.oversText(card.legalBalls, bpo)} ov)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(8.dp))

        CardSurface {
            TableHeader("Batter", listOf("R", "B", "4s", "6s", "SR"), firstWeight = 3f)
            for (b in card.batters) {
                TableRow(
                    name = b.playerId.let { name(it) },
                    marker = null,
                    bold = !b.isOut,
                    values = listOf("${b.runs}${if (b.isOut) "" else "*"}", "${b.balls}", "${b.fours}", "${b.sixes}", b.strikeRate.format1()),
                    subtitle = ScorecardBuilder.dismissalText(b) { name(it) },
                    firstWeight = 3f
                )
            }
            ThinDivider(Modifier.padding(vertical = 6.dp))
            val e = card.extras
            Row {
                Text("Extras", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${e.total}", style = NumberStyle.copy(fontWeight = FontWeight.Bold))
            }
            Text(
                "wd ${e.wides}, nb ${e.noBalls}, b ${e.byes}, lb ${e.legByes}, pen ${e.penalty}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.padding(top = 6.dp)) {
                Text("Total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "${card.total}/${card.wickets} (${CricketRules.oversText(card.legalBalls, bpo)} ov, RR ${CricketRules.runRate(card.total, card.legalBalls, bpo).format2()})",
                    style = NumberStyle.copy(fontWeight = FontWeight.Bold)
                )
            }
            if (card.didNotBat.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Did not bat: ${card.didNotBat.joinToString { name(it) }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (card.fallOfWickets.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            CardSurface {
                Text("Fall of wickets", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    card.fallOfWickets.joinToString(", ") {
                        "${it.wicketNumber}-${it.score} (${name(it.playerId)}, ${CricketRules.oversText(it.legalBalls, bpo)} ov)"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        CardSurface {
            TableHeader("Bowler", listOf("O", "M", "R", "W", "Econ"), firstWeight = 3f)
            for (bw in card.bowlers) {
                val extras = listOfNotNull(
                    if (bw.wides > 0) "${bw.wides} wd" else null,
                    if (bw.noBalls > 0) "${bw.noBalls} nb" else null
                ).joinToString(", ").ifBlank { null }
                TableRow(
                    name = name(bw.playerId),
                    marker = null,
                    bold = bw.wickets > 0,
                    values = listOf(CricketRules.oversText(bw.legalBalls, bpo), "${bw.maidens}", "${bw.runs}", "${bw.wickets}", bw.economy(bpo).format2()),
                    subtitle = extras,
                    firstWeight = 3f,
                    emphasiseFirstValue = false
                )
            }
        }

        if (card.overs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            CardSurface {
                Text("Runs per over", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                RunsPerOverChart(card)
                Text(
                    "Red dots mark wickets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            CardSurface {
                Text("Over by over", style = MaterialTheme.typography.titleSmall)
                for (o in card.overs.reversed()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.width(96.dp)) {
                            Text("Over ${o.overIndex + 1}", style = MaterialTheme.typography.labelLarge)
                            Text(name(o.bowlerId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Row(
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) { for (b in o.balls) BallChip(b, size = 26.dp) }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 6.dp)) {
                            Text("${o.runs}", style = NumberStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                            Text("${o.cumulativeRuns}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MatchInfo(live, venue)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RunsPerOverChart(card: InningsCard) {
    val p = LocalCricPalette.current
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bar = MaterialTheme.colorScheme.primary
    val maxRuns = (card.overs.maxOfOrNull { it.runs } ?: 0).coerceAtLeast(6)
    Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
        val n = card.overs.size.coerceAtLeast(6)
        val slot = size.width / n
        val barWidth = slot * 0.62f
        val top = 12.dp.toPx()
        val usable = size.height - top
        for (g in listOf(0.5f, 1f)) {
            val y = size.height - usable * g
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        card.overs.forEachIndexed { i, o ->
            val h = usable * (o.runs / maxRuns.toFloat())
            val x = i * slot + (slot - barWidth) / 2f
            drawRect(
                color = if (o.runs >= 10) p.accent else bar,
                topLeft = Offset(x, size.height - h),
                size = Size(barWidth, h.coerceAtLeast(2f))
            )
            for (w in 0 until o.wickets) {
                drawCircle(
                    color = p.wicket,
                    radius = 3.5.dp.toPx(),
                    center = Offset(x + barWidth / 2f, size.height - h - (6 + w * 9).dp.toPx())
                )
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("Over 1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("Max $maxRuns", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MatchInfo(live: LiveScoreState, venue: String?) {
    val m = live.match
    CardSurface {
        Text("Match info", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        InfoLine("Format", "${m.matchType.label}, ${m.totalOversPerInnings} overs, ${m.ballsPerOver}-ball overs")
        if (m.stage.isKnockout || m.tournamentId != null) InfoLine("Stage", m.stage.label)
        if (venue != null) InfoLine("Venue", venue)
        InfoLine("Date", MatchFormat.longDate(m.createdAt))
        val tossWinner = m.tossWinnerTeamId?.let { if (it == live.team1.id) live.team1 else live.team2 }
        if (tossWinner != null && m.tossDecision != null) {
            InfoLine("Toss", "${tossWinner.name}, chose to ${if (m.tossDecision == com.example.data.model.TossDecision.BAT) "bat" else "bowl"}")
        }
        m.potmPlayerId?.let { live.playersById[it] }?.let { InfoLine("Player of the match", it.name) }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(130.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
