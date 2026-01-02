package com.dueckis.kawaiiraweditor.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.immich.ImmichAuthMode

@Composable
internal fun ExportDestinationDialog(
    immichEnabled: Boolean,
    immichAuthMode: ImmichAuthMode?,
    onDismissRequest: () -> Unit,
    onSelectDestination: (ExportDestination) -> Unit
) {
    val immichHint =
        when (immichAuthMode) {
            ImmichAuthMode.Login -> "Sign in to Immich in settings."
            ImmichAuthMode.ApiKey -> "Add your Immich API key in settings."
            null -> "Configure Immich in settings."
        }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Save to") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onSelectDestination(ExportDestination.Local) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Local device")
                }
                OutlinedButton(
                    onClick = { onSelectDestination(ExportDestination.Immich) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = immichEnabled
                ) {
                    Text("Immich")
                }
                if (!immichEnabled) {
                    Text(
                        text = immichHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        }
    )
}
