@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dueckis.kawaiiraweditor.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.media.ExportImageFormat
import com.dueckis.kawaiiraweditor.ui.components.doubleTapSliderThumbToReset

internal data class ExportOptions(
    val format: ExportImageFormat,
    val quality: Int,
    val resizeLongEdgePx: Int?,
    val dontEnlarge: Boolean,
    val lowRamMode: Boolean = false
)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExportOptionsDialog(
    initial: ExportOptions,
    isLoading: Boolean, // Added parameter
    onDismissRequest: () -> Unit,
    onConfirm: (ExportOptions) -> Unit
) {
    var format by remember(initial) { mutableStateOf(initial.format) }
    var quality by remember(initial) { mutableIntStateOf(initial.quality.coerceIn(1, 100)) }
    var resizeEnabled by remember(initial) { mutableStateOf(initial.resizeLongEdgePx != null) }
    var longEdgeText by remember(initial) { mutableStateOf((initial.resizeLongEdgePx ?: 2048).toString()) }
    var dontEnlarge by remember(initial) { mutableStateOf(initial.dontEnlarge) }
    var lowRamMode by remember(initial) { mutableStateOf(initial.lowRamMode) }

    val longEdgeValue = longEdgeText.toIntOrNull()?.coerceIn(64, 20000)
    val isLongEdgeValid = !resizeEnabled || longEdgeValue != null

    AlertDialog(
        // Prevent dismissal via back-press or clicking outside while loading
        onDismissRequest = { if (!isLoading) onDismissRequest() },
        title = { Text("Export") },
        text = {
            Box(contentAlignment = Alignment.Center) {
                // Main Content
                Column(
                    // Slightly fade content when loading to draw focus to the indicator
                    modifier = Modifier.alpha(if (isLoading) 0.5f else 1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("File format", style = MaterialTheme.typography.bodyMedium)
                        ExportFormatDropdown(
                            value = format,
                            onValueChange = { format = it },
                            enabled = !isLoading // Disable dropdown
                        )
                    }

                    if (format != ExportImageFormat.Png) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Quality", style = MaterialTheme.typography.bodyMedium)
                                Text("$quality", style = MaterialTheme.typography.bodyMedium)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .doubleTapSliderThumbToReset(
                                        value = quality.toFloat(),
                                        valueRange = 1f..100f,
                                        onReset = { if (!isLoading) quality = 90 }
                                    )
                            ) {
                                Slider(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = quality.toFloat(),
                                    onValueChange = { quality = it.toInt().coerceIn(1, 100) },
                                    valueRange = 1f..100f,
                                    enabled = !isLoading // Disable slider
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Resize", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = resizeEnabled,
                            onCheckedChange = { resizeEnabled = it },
                            enabled = !isLoading // Disable switch
                        )
                    }

                    if (resizeEnabled) {
                        OutlinedTextField(
                            value = longEdgeText,
                            onValueChange = { longEdgeText = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Long edge (px)") },
                            isError = !isLongEdgeValid,
                            enabled = !isLoading, // Disable textfield
                            supportingText = {
                                if (!isLongEdgeValid) Text("Enter a number (e.g. 2048)")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Don't enlarge", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = dontEnlarge,
                                onCheckedChange = { dontEnlarge = it },
                                enabled = !isLoading
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Low RAM / Hardware Mode", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = lowRamMode,
                            onCheckedChange = { lowRamMode = it },
                            enabled = !isLoading
                        )
                    }
                }

                // Show Loading Indicator overlaying the content
                if (isLoading) {
                    androidx.compose.material3.LoadingIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isLongEdgeValid && !isLoading, // Disable button
                onClick = {
                    onConfirm(
                        ExportOptions(
                            format = format,
                            quality = quality.coerceIn(1, 100),
                            resizeLongEdgePx = if (resizeEnabled) longEdgeValue else null,
                            dontEnlarge = dontEnlarge,
                            lowRamMode = lowRamMode
                        )
                    )
                }
            ) {
                Text(if (isLoading) "Exporting..." else "Export")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isLoading, // Disable cancel button
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExportFormatDropdown(
    value: ExportImageFormat,
    onValueChange: (ExportImageFormat) -> Unit,
    enabled: Boolean // Added parameter
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(36.dp),
            enabled = enabled // Apply enabled state
        ) {
            Text(value.label)
            Spacer(Modifier.width(6.dp))
            Text(".${value.extension}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            ExportImageFormat.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.label} (.${option.extension})") },
                    onClick = {
                        expanded = false
                        onValueChange(option)
                    }
                )
            }
        }
    }
}