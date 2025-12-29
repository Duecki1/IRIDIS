@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.dueckis.kawaiiraweditor.ui.editor.components

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder
import com.dueckis.kawaiiraweditor.data.media.ExportImageFormat
import com.dueckis.kawaiiraweditor.data.media.decodeJpegToBitmapSampled
import com.dueckis.kawaiiraweditor.data.media.decodeToBitmap
import com.dueckis.kawaiiraweditor.data.media.saveBitmapToPictures
import com.dueckis.kawaiiraweditor.data.media.saveJpegToPictures
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
    var showExportDialog by remember { mutableStateOf(false) }
    var lastOptions by remember {
        mutableStateOf(
            ExportOptions(
                format = ExportImageFormat.Jpeg,
                quality = 90,
                resizeLongEdgePx = null,
                dontEnlarge = true
            )
        )
    }

    FilledTonalButton(
        modifier = modifier,
        onClick = {
            if (isExporting || sessionHandle == 0L) return@FilledTonalButton
            showExportDialog = true
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

    if (showExportDialog) {
        ExportOptionsDialog(
            initial = lastOptions,
            onDismissRequest = { showExportDialog = false },
            onConfirm = { options ->
                showExportDialog = false
                lastOptions = options
                if (isExporting || sessionHandle == 0L) return@ExportOptionsDialog

                val currentAdjustments = adjustments
                val currentMasks = masks
                onExportStart()
                coroutineScope.launch {
                    val currentJson = withContext(Dispatchers.Default) { currentAdjustments.toJson(currentMasks) }

                    // CALCULATE MAX DIMENSION
                    // If user requested resize, pass that. Otherwise 0 (Full Res).
                    val maxDim = options.resizeLongEdgePx ?: 0

                    val fullBytes = withContext(nativeDispatcher) {
                        runCatching {
                            LibRawDecoder.exportFromSession(
                                sessionHandle,
                                currentJson,
                                maxDim,
                                options.lowRamMode
                            )
                        }.getOrNull()
                    }
                    if (fullBytes == null) {
                        onExportComplete(false, "Export failed (Out of Memory). Try enabling Low RAM Mode.")
                        return@launch
                    }

                    val savedUri =
                        withContext(Dispatchers.IO) {
                            // Bytes are already resized/processed by Native if needed
                            if (options.format == ExportImageFormat.Jpeg && options.quality == 96) {
                                saveJpegToPictures(context, fullBytes)
                            } else {
                                // Only need to decode for format conversion (PNG/WebP) or if quality changed
                                // Note: Native export is always JPEG Q96 based on lib.rs
                                val bitmap = withContext(Dispatchers.Default) {
                                    fullBytes.decodeToBitmap() 
                                } ?: return@withContext null

                                saveBitmapToPictures(context, bitmap, options.format, options.quality).also { bitmap.recycle() }
                            }
                        }

                    if (savedUri != null) {
                        onExportComplete(true, "Saved to $savedUri")
                    } else {
                        onExportComplete(false, "Export failed: could not save image.")
                    }
                }
            }
        )
    }
}
