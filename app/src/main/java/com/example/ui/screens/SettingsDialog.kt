package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ThemeMode

@Composable
fun SettingsDialog(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text("Appearance", style = MaterialTheme.typography.titleSmall)
                for (mode in ThemeMode.values()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onThemeChange(mode) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = themeMode == mode, onClick = { onThemeChange(mode) })
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Scoring tips", style = MaterialTheme.typography.titleSmall)
                Text(
                    "For extras, tap Wd, Nb, Bye or LB first, then the runs. Undo works across overs and innings, " +
                        "and reopens a finished match. Everything is saved on your phone as you go, so you can close the app mid-match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
