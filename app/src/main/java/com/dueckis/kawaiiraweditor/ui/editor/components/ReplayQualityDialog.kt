package com.dueckis.kawaiiraweditor.ui.editor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.preferences.ReplayExportQuality

@Composable
internal fun ReplayQualityDialog(
    current: ReplayExportQuality,
    isLoading: Boolean,
    onConfirm: (ReplayExportQuality) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selected by remember(current) { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismissRequest() },
        title = { Text("Replay quality") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ReplayExportQuality.values().forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == quality,
                            onClick = { selected = quality },
                            enabled = !isLoading
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(text = quality.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = quality.description,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = { if (!isLoading) onConfirm(selected) }
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isLoading,
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
        }
    )
}
