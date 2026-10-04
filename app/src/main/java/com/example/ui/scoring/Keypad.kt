package com.example.ui.scoring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExtraType
import com.example.ui.theme.BarlowCondensed
import com.example.ui.theme.LocalCricPalette

/**
 * Scoring keypad. Tap a run to record it. For extras, tap Wd / Nb / Bye / LB first and then the runs:
 * Wd then 0 is a plain wide, Nb then 4 is a no-ball hit for four.
 */
@Composable
fun ScoringKeypad(
    pendingExtra: ExtraType?,
    onPendingExtra: (ExtraType?) -> Unit,
    onRuns: (Int) -> Unit,
    onWicket: () -> Unit,
    onUndo: () -> Unit,
    onSwap: () -> Unit,
    onMore: () -> Unit,
    canSwap: Boolean,
    canUndo: Boolean,
    modifier: Modifier = Modifier
) {
    val p = LocalCricPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pendingExtra != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.accent.copy(alpha = 0.16f))
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (pendingExtra) {
                            ExtraType.WIDE -> "Wide: tap the runs taken (0 for just the wide)"
                            ExtraType.NO_BALL -> "No-ball: tap the runs off the bat"
                            ExtraType.BYE -> "Byes: tap the runs taken"
                            else -> "Leg byes: tap the runs taken"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onPendingExtra(null) }) { Text("Cancel") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in listOf(0, 1, 2, 3, 4, 6)) {
                    val (bg, fg) = when (r) {
                        4 -> p.four to Color.White
                        6 -> p.six to Color(0xFF2E1B00)
                        else -> p.keyNeutral to p.onKeyNeutral
                    }
                    val disabled = r == 0 && (pendingExtra == ExtraType.BYE || pendingExtra == ExtraType.LEG_BYE)
                    Key(
                        label = "$r",
                        background = bg,
                        content = fg,
                        enabled = !disabled,
                        height = 58.dp,
                        fontSize = 28,
                        onClick = { onRuns(r) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExtraKey("Wd", ExtraType.WIDE, pendingExtra, onPendingExtra)
                ExtraKey("Nb", ExtraType.NO_BALL, pendingExtra, onPendingExtra)
                ExtraKey("Bye", ExtraType.BYE, pendingExtra, onPendingExtra)
                ExtraKey("LB", ExtraType.LEG_BYE, pendingExtra, onPendingExtra)
                Key(
                    label = "Out",
                    background = p.wicket,
                    content = Color.White,
                    weight = 1.5f,
                    enabled = pendingExtra == null,
                    onClick = onWicket
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionKey(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = canUndo, onClick = onUndo)
                ActionKey(Icons.Default.SwapHoriz, "Swap strike", enabled = canSwap, onClick = onSwap)
                ActionKey(Icons.Default.MoreHoriz, "More", enabled = true, onClick = onMore)
            }
        }
    }
}

@Composable
private fun RowScope.ExtraKey(label: String, type: ExtraType, pending: ExtraType?, onPending: (ExtraType?) -> Unit) {
    val p = LocalCricPalette.current
    val active = pending == type
    Key(
        label = label,
        background = if (active) p.accent else p.extra.copy(alpha = if (p.isDark) 0.30f else 0.14f),
        content = if (active) Color(0xFF2E1B00) else p.extra,
        fontSize = 20,
        onClick = { onPending(if (active) null else type) }
    )
}

@Composable
private fun RowScope.Key(
    label: String,
    background: Color,
    content: Color,
    onClick: () -> Unit,
    weight: Float = 1f,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 52.dp,
    fontSize: Int = 22
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = content,
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp
        )
    }
}

@Composable
private fun RowScope.ActionKey(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .weight(1f)
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHigh)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.onSurface)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
    }
}
