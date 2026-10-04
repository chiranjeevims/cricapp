package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchStage
import com.example.data.model.MatchStatus
import com.example.data.model.MatchType
import com.example.data.model.TeamEntity
import com.example.domain.CricketRules
import com.example.domain.MatchFormat
import com.example.ui.theme.BarlowCondensed
import com.example.ui.theme.LocalCricPalette
import com.example.ui.theme.NumberStyle
import com.example.ui.viewmodels.MatchListItem

/** "Summer Cup, semi-final 1" or "3 Oct, limited overs, 10 overs". */
fun matchMeta(item: MatchListItem): String {
    val m = item.match
    val tour = item.tournament
    return if (tour != null) {
        val stage = if (m.stage == MatchStage.LEAGUE) "league round ${m.roundIndex}" else m.stage.label.lowercase()
        "${tour.name}, $stage"
    } else {
        "${MatchFormat.shortDate(m.createdAt)}, ${m.matchType.label.lowercase()}, ${m.totalOversPerInnings} overs"
    }
}

/** "Need 34 from 52 balls" for a live one-innings chase, null otherwise. */
fun liveEquation(match: MatchEntity, innings: List<InningsEntity>): String? {
    if (match.status != MatchStatus.IN_PROGRESS || match.matchType == MatchType.MINI_TEST_2_INNINGS) return null
    val first = innings.find { it.inningsIndex == 1 } ?: return null
    val second = innings.find { it.inningsIndex == 2 } ?: return null
    val need = first.runs + first.penaltyRuns + 1 - (second.runs + second.penaltyRuns)
    val balls = match.totalOversPerInnings * match.ballsPerOver - second.ballsBowled
    if (need <= 0 || balls <= 0) return null
    return "Need ${CricketRules.plural(need, "run")} from ${CricketRules.plural(balls, "ball")}"
}

@Composable
private fun TeamScoreLine(team: TeamEntity?, score: String?, emphasise: Boolean, muted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamBadge(team, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = team?.name ?: "Deleted team",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasise) FontWeight.Bold else FontWeight.SemiBold,
            color = if (muted) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (score != null) {
            Text(
                text = score,
                style = NumberStyle.copy(fontSize = 18.sp, fontWeight = if (emphasise) FontWeight.Bold else FontWeight.Medium),
                color = if (muted) scheme.onSurfaceVariant else scheme.onSurface
            )
        }
    }
}

@Composable
fun MatchRow(
    item: MatchListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    meta: String = matchMeta(item)
) {
    val m = item.match
    val palette = LocalCricPalette.current
    val scheme = MaterialTheme.colorScheme
    val decided = m.status.isFinished && m.winnerTeamId != null
    CardSurface(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StatusTag(m.status)
            if (onDelete != null) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Match options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete match") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TeamScoreLine(item.team1, item.scoreFor(m.team1Id), emphasise = decided && m.winnerTeamId == m.team1Id, muted = decided && m.winnerTeamId != m.team1Id)
        TeamScoreLine(item.team2, item.scoreFor(m.team2Id), emphasise = decided && m.winnerTeamId == m.team2Id, muted = decided && m.winnerTeamId != m.team2Id)
        val line = when (m.status) {
            MatchStatus.IN_PROGRESS -> liveEquation(m, item.innings) ?: "In progress"
            MatchStatus.SCHEDULED -> if (item.ground != null) "At ${item.ground.name}" else null
            else -> m.resultSummary.ifBlank { m.status.label }
        }
        if (line != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = when {
                    m.status == MatchStatus.IN_PROGRESS -> palette.accentText
                    decided -> palette.win
                    else -> scheme.onSurfaceVariant
                }
            )
        }
    }
}

/** Scoreboard-style card for matches being played right now. */
@Composable
fun LiveMatchCard(item: MatchListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalCricPalette.current
    val m = item.match
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = p.panel,
        contentColor = p.onPanel
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(p.wicket))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = matchMeta(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = p.onPanelMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("Resume", style = MaterialTheme.typography.labelLarge, color = p.accent)
            }
            Spacer(Modifier.height(10.dp))
            for (team in listOf(item.team1 to m.team1Id, item.team2 to m.team2Id)) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(team.first, size = 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        team.first?.name ?: "Team",
                        style = MaterialTheme.typography.titleMedium,
                        color = p.onPanel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        item.scoreFor(team.second) ?: "Yet to bat",
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (item.scoreFor(team.second) != null) 24.sp else 15.sp,
                        color = if (item.scoreFor(team.second) != null) p.onPanel else p.onPanelMuted
                    )
                }
            }
            val eq = liveEquation(m, item.innings)
            if (eq != null) {
                Spacer(Modifier.height(8.dp))
                Text(eq, style = MaterialTheme.typography.titleSmall, color = p.accent)
            }
        }
    }
}

@Composable
fun FixtureLine(
    team1: TeamEntity?,
    team2: TeamEntity?,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TeamBadge(team1, size = 24.dp)
        Text(team1?.shortName ?: "?", style = MaterialTheme.typography.titleSmall)
        Text("v", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TeamBadge(team2, size = 24.dp)
        Text(team2?.shortName ?: "?", style = MaterialTheme.typography.titleSmall)
    }
}
