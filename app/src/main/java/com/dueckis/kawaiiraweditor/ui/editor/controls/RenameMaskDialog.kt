package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun RenameMaskDialog(
    currentName: String,
    tagPresets: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(currentName) { mutableStateOf(currentName) }
    var userTyped by remember { mutableStateOf(false) }

    val base = remember(tagPresets) {
        tagPresets.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    val isGenericMaskName = remember(text) {
        Regex("^Mask\\s*\\d+$", RegexOption.IGNORE_CASE).matches(text.trim())
    }

    val query = remember(text, userTyped, isGenericMaskName) {
        if (!userTyped || isGenericMaskName) "" else text.trim()
    }

    val suggestions = remember(query, base) {
        if (query.isBlank()) base else base.filter { it.contains(query, ignoreCase = true) }.ifEmpty { base }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename mask") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        userTyped = true
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                if (base.isEmpty()) {
                    Text(
                        "No tags saved yet. Add some in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Tap a tag or type your own:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        suggestions.forEach { tag ->
                            SuggestionChip(
                                onClick = {
                                    val v = tag.trim()
                                    if (v.isNotEmpty()) onConfirm(v)
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val v = text.trim()
                    if (v.isNotEmpty()) onConfirm(v)
                }
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
