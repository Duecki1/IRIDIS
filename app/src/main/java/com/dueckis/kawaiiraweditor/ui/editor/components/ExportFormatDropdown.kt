package com.dueckis.kawaiiraweditor.ui.editor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.media.ExportImageFormat

@Composable
internal fun ExportFormatDropdown(
    value: ExportImageFormat,
    onValueChange: (ExportImageFormat) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.height(36.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(36.dp),
            enabled = enabled
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
