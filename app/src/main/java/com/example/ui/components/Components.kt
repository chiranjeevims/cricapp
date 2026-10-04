package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BallEntity
import com.example.data.model.ExtraType
import com.example.data.model.MatchStatus
import com.example.data.model.TeamEntity
import com.example.domain.ScorecardBuilder
import com.example.ui.theme.BarlowCondensed
import com.example.ui.theme.LocalCricPalette
import com.example.ui.theme.NumberStyle
import com.example.ui.theme.parseTeamColor
import java.util.Locale

// ------------------------------------------------------------------ Identity

fun teamColor(team: TeamEntity?): Color = parseTeamColor(team?.primaryColorHex, team?.id ?: 0L)

fun contentColorOn(background: Color): Color = if (background.luminance() > 0.55f) Color(0xFF111831) else Color.White

@Composable
fun TeamBadge(team: TeamEntity?, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val bg = teamColor(team)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = (team?.shortName ?: "?").take(3).uppercase(Locale.getDefault()),
            color = contentColorOn(bg),
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
            maxLines = 1
        )
    }
}

fun initials(name: String): String {
    val parts = name.replace("'", " ").split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.getDefault())
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale.getDefault())
    }
}

@Composable
fun PlayerAvatar(name: String, color: Color, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials(name),
            color = color,
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp
        )
    }
}

// ------------------------------------------------------------------ Structure

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) trailing()
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 14.dp, bottom = 8.dp)
    )
}

@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val color = MaterialTheme.colorScheme.surface
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(modifier = Modifier.padding(14.dp), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(modifier = Modifier.padding(14.dp), content = content)
        }
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant)
}

// ------------------------------------------------------------------ Inputs

@Composable
fun ChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.primary else scheme.surface,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, scheme.outline)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leading != null) leading()
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (!enabled) scheme.onSurface.copy(alpha = 0.38f) else Color.Unspecified,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) scheme.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onValueChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease")
        }
        Text(
            text = if (suffix.isEmpty()) "$value" else "$value $suffix",
            style = NumberStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(if (suffix.isEmpty()) 44.dp else 84.dp)
        )
        IconButton(onClick = { onValueChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
}

// ------------------------------------------------------------------ Status & feedback

@Composable
fun StatusTag(status: MatchStatus, modifier: Modifier = Modifier) {
    val palette = LocalCricPalette.current
    val scheme = MaterialTheme.colorScheme
    val (label, color) = when (status) {
        MatchStatus.IN_PROGRESS -> "Live" to palette.wicket
        MatchStatus.SCHEDULED -> "Upcoming" to scheme.onSurfaceVariant
        MatchStatus.COMPLETED -> "Result" to scheme.primary
        MatchStatus.ABANDONED_RAIN -> "No result" to scheme.onSurfaceVariant
        MatchStatus.DECLARED_WINNER -> "Awarded" to palette.accentText
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (status == MatchStatus.IN_PROGRESS) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, tone: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tone.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(tone))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = LocalCricPalette.current.wicket,
                    contentColor = Color.White
                ) else ButtonDefaults.buttonColors()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}

/** Full-screen sheet used for editors and setup flows. */
@Composable
fun FullScreenDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrollable: Boolean = true,
    bottomBar: (@Composable () -> Unit)? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    headerActions()
                }
                val body = Modifier.weight(1f).fillMaxWidth()
                Column(
                    modifier = if (scrollable) body.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)
                    else body,
                    content = content
                )
                if (bottomBar != null) {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                        Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            bottomBar()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = NumberStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = valueColor)
    }
}

// ------------------------------------------------------------------ Balls

data class BallLook(val background: Color, val content: Color, val label: String)

@Composable
fun ballLook(ball: BallEntity): BallLook {
    val p = LocalCricPalette.current
    val label = ScorecardBuilder.ballLabel(ball)
    return when {
        ball.isPenaltyRun -> BallLook(p.extra.copy(alpha = 0.18f), p.extra, label)
        ball.isWicket -> BallLook(p.wicket, Color.White, label)
        ball.runsOffBat == 6 -> BallLook(p.six, Color(0xFF2E1B00), label)
        ball.runsOffBat == 4 -> BallLook(p.four, Color.White, label)
        ball.extraType == ExtraType.WIDE || ball.extraType == ExtraType.NO_BALL -> BallLook(p.extra, Color.White, label)
        ball.extraType == ExtraType.BYE || ball.extraType == ExtraType.LEG_BYE -> BallLook(p.extra.copy(alpha = 0.18f), p.extra, label)
        ball.runsOffBat == 0 -> BallLook(p.keyNeutral, p.dot, "•")
        else -> BallLook(p.keyNeutral, p.onKeyNeutral, label)
    }
}

@Composable
fun BallChip(ball: BallEntity, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val look = ballLook(ball)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(look.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = look.label,
            color = look.content,
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = when {
                look.label.length >= 3 -> (size.value * 0.34f).sp
                look.label.length == 2 -> (size.value * 0.42f).sp
                else -> (size.value * 0.5f).sp
            },
            maxLines = 1
        )
    }
}

fun Double.format1(): String = String.format(Locale.US, "%.1f", this)
fun Double.format2(): String = String.format(Locale.US, "%.2f", this)
