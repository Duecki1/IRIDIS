@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.dueckis.kawaiiraweditor

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ExportButton(
    modifier: Modifier = Modifier,
    label: String = "Export",
    sessionHandle: Long,
    adjustments: AdjustmentState,
    masks: List<MaskState>,
    isExporting: Boolean,
    nativeDispatcher: CoroutineDispatcher,
    context: Context,
    onExportStart: () -> Unit,
    onExportComplete: (Boolean, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    FilledTonalButton(
        modifier = modifier,
        onClick = {
            if (isExporting || sessionHandle == 0L) return@FilledTonalButton
            val currentAdjustments = adjustments
            val currentMasks = masks
            onExportStart()
            coroutineScope.launch {
                val currentJson = withContext(Dispatchers.Default) { currentAdjustments.toJson(currentMasks) }
                val fullBytes = withContext(nativeDispatcher) {
                    runCatching { LibRawDecoder.decodeFullResFromSession(sessionHandle, currentJson) }.getOrNull()
                }
                if (fullBytes == null) {
                    onExportComplete(false, "Export failed.")
                } else {
                    val savedUri = withContext(Dispatchers.IO) {
                        saveJpegToPictures(context, fullBytes)
                    }
                    if (savedUri != null) {
                        onExportComplete(true, "Saved to $savedUri")
                    } else {
                        onExportComplete(false, "Export failed: could not save JPEG.")
                    }
                }
            }
        },
        enabled = sessionHandle != 0L && !isExporting
    ) {
        if (isExporting) {
            LoadingIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(label)
    }
}
