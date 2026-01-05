@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.dueckis.kawaiiraweditor.ui.editor.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dueckis.kawaiiraweditor.data.immich.ImmichAuthMode
import com.dueckis.kawaiiraweditor.data.immich.ImmichConfig
import com.dueckis.kawaiiraweditor.data.immich.ImmichUploadResult
import com.dueckis.kawaiiraweditor.data.immich.addImmichAssetsToAlbum
import com.dueckis.kawaiiraweditor.data.immich.uploadImmichAsset
import com.dueckis.kawaiiraweditor.data.media.ExportImageFormat
import com.dueckis.kawaiiraweditor.data.media.decodeToBitmap
import com.dueckis.kawaiiraweditor.data.media.exportReplayVideo
import com.dueckis.kawaiiraweditor.data.media.saveBitmapToPictures
import com.dueckis.kawaiiraweditor.data.media.saveJpegToPictures
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder
import com.dueckis.kawaiiraweditor.data.preferences.AppPreferences
import com.dueckis.kawaiiraweditor.data.preferences.ReplayExportQuality
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun encodeExportBytes(
    fullBytes: ByteArray,
    options: ExportOptions,
    allowPassthroughJpeg: Boolean
): ByteArray? {
    if (allowPassthroughJpeg && options.format == ExportImageFormat.Jpeg && options.quality == 96) return fullBytes
    val bitmap = fullBytes.decodeToBitmap() ?: return null
    val output = ByteArrayOutputStream()
    val compressFormat =
        when (options.format) {
            ExportImageFormat.Jpeg -> Bitmap.CompressFormat.JPEG
            ExportImageFormat.Png -> Bitmap.CompressFormat.PNG
            ExportImageFormat.Webp ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                else Bitmap.CompressFormat.WEBP
        }
    val ok = bitmap.compress(compressFormat, options.quality.coerceIn(1, 100), output)
    bitmap.recycle()
    val encoded = if (ok) output.toByteArray() else return null
    if (encoded.isEmpty()) return null
    val valid =
        when (options.format) {
            ExportImageFormat.Jpeg -> encoded.size >= 2 && encoded[0] == 0xFF.toByte() && encoded[1] == 0xD8.toByte()
            ExportImageFormat.Png -> encoded.size >= 8 && encoded[0] == 0x89.toByte() && encoded[1] == 0x50.toByte()
            ExportImageFormat.Webp -> encoded.size >= 12 && encoded.copyOfRange(0, 4).contentEquals("RIFF".toByteArray())
        }
    return if (valid) encoded else null
}

private fun buildXmpSidecarForIridis(editsJson: String): ByteArray {
    val encoded = Base64.encodeToString(editsJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    val xml =
        """
        |<?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
        |<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="KawaiiRawEditor">
        |  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        |    <rdf:Description
        |      xmlns:krwe="https://kawaiiraweditor.dueckis.com/ns/1.0/"
        |      krwe:EditsJsonBase64="$encoded" />
        |  </rdf:RDF>
        |</x:xmpmeta>
        |<?xpacket end="w"?>
        |
        """.trimMargin()
    return xml.toByteArray(Charsets.UTF_8)
}

private fun buildExportFileName(format: ExportImageFormat): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "IRIDIS_$stamp.${format.extension}"
}

private fun buildExportFileName(
    sourceFileName: String?,
    format: ExportImageFormat
): String {
    val base = sourceFileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return if (base == null) {
        buildExportFileName(format)
    } else {
        "${base}_EDIT_$stamp.${format.extension}"
    }
}

private fun buildImmichUploadErrorMessage(result: ImmichUploadResult): String {
    val status = result.statusCode?.let { "HTTP $it" }
    val base = result.errorMessage?.takeIf { it.isNotBlank() } ?: "Immich upload failed."
    val body = result.responseBody?.takeIf { it.isNotBlank() }?.take(400)
    return when {
        status != null && body != null -> "$base ($status)\n$body"
        status != null -> "$base ($status)"
        body != null -> "$base\n$body"
        else -> base
    }
}

@Composable
internal fun ExportButton(
    modifier: Modifier = Modifier,
    label: String = "Export",
    sessionHandle: Long,
    adjustments: AdjustmentState,
    masks: List<MaskState>,
    originImmichAssetId: String? = null,
    originImmichAlbumId: String? = null,
    sourceFileName: String? = null,
    isExporting: Boolean,
    nativeDispatcher: CoroutineDispatcher,
    context: Context,
    onExportStart: () -> Unit,
    onExportComplete: (Boolean, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val appPreferences = remember(context) { AppPreferences(context) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDestinationDialog by remember { mutableStateOf(false) }
    var showImmichAlbumDialog by remember { mutableStateOf(false) }
    var pendingOptions by remember { mutableStateOf<ExportOptions?>(null) }
    var pendingImmichConfig by remember { mutableStateOf<ImmichConfig?>(null) }
    var lastChosenImmichAlbumId by remember { mutableStateOf(originImmichAlbumId) }
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
    var exportProgressMessage by remember { mutableStateOf<String?>(null) }
    var showReplayQualityDialog by remember { mutableStateOf(false) }
    var replayQualitySelection by remember { mutableStateOf(appPreferences.getReplayExportQuality()) }

    fun startReplayExport() {
        if (isExporting || sessionHandle == 0L) return
        showExportDialog = false
        showDestinationDialog = false
        showImmichAlbumDialog = false
        val currentAdjustments = adjustments
        val currentMasks = masks
        val replayQuality = appPreferences.getReplayExportQuality()
        replayQualitySelection = replayQuality
        showReplayQualityDialog = false
        exportProgressMessage = "Creating replay video..."
        pendingImmichConfig = null
        pendingOptions = null
        onExportStart()

        coroutineScope.launch {
            try {
                val startRealtime = SystemClock.elapsedRealtime()
                var lastUpdateMs = startRealtime
                val result = withContext(Dispatchers.IO) {
                    exportReplayVideo(
                        context = context,
                        sessionHandle = sessionHandle,
                        adjustments = currentAdjustments,
                        masks = currentMasks,
                        nativeDispatcher = nativeDispatcher,
                        maxDimension = replayQuality.maxDimension,
                        onProgress = { current, total ->
                            if (total <= 0) return@exportReplayVideo
                            val now = SystemClock.elapsedRealtime()
                            val shouldUpdate = current == total || current == 1 || now - lastUpdateMs >= 120L
                            if (!shouldUpdate) return@exportReplayVideo
                            lastUpdateMs = now
                            val elapsedMs = (now - startRealtime).coerceAtLeast(1L)
                            val remainingFrames = (total - current).coerceAtLeast(0)
                            val etaMs = if (current > 0 && remainingFrames > 0) {
                                (elapsedMs.toDouble() / current.toDouble() * remainingFrames.toDouble()).toLong().coerceAtLeast(0L)
                            } else {
                                0L
                            }
                            val percent = ((current.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                            val etaText = formatEta(etaMs)
                            coroutineScope.launch {
                                exportProgressMessage = "Rendering replay... $percent% (ETA $etaText)"
                            }
                        }
                    )
                }
                exportProgressMessage = null
                if (result.success && result.uri != null) {
                    val shareShown = tryShareReplayVideo(context, result.uri)
                    val message = if (shareShown) {
                        "Saved to ${result.uri}"
                    } else {
                        "Saved to ${result.uri}. Unable to open share sheet."
                    }
                    onExportComplete(true, message)
                } else {
                    onExportComplete(false, result.errorMessage ?: "Replay export failed.")
                }
            } finally {
                exportProgressMessage = null
            }
        }
    }

    fun promptReplayQualitySelection() {
        if (isExporting || sessionHandle == 0L) return
        showExportDialog = false
        showDestinationDialog = false
        showImmichAlbumDialog = false
        replayQualitySelection = appPreferences.getReplayExportQuality()
        showReplayQualityDialog = true
    }

    LaunchedEffect(originImmichAlbumId) {
        if (!originImmichAlbumId.isNullOrBlank()) {
            lastChosenImmichAlbumId = originImmichAlbumId
        }
    }

    fun resolveImmichConfig(): ImmichConfig? {
        val serverUrl = appPreferences.getImmichServerUrl()
        val authMode = appPreferences.getImmichAuthMode()
        val accessToken = appPreferences.getImmichAccessToken()
        val apiKey = appPreferences.getImmichApiKey()
        val configured =
            when (authMode) {
                ImmichAuthMode.Login -> serverUrl.isNotBlank() && accessToken.isNotBlank()
                ImmichAuthMode.ApiKey -> serverUrl.isNotBlank() && apiKey.isNotBlank()
            }
        if (!configured) return null
        return ImmichConfig(
            serverUrl = serverUrl,
            authMode = authMode,
            apiKey = apiKey,
            accessToken = accessToken
        )
    }

    fun startExport(
        destination: ExportDestination,
        options: ExportOptions,
        immichConfig: ImmichConfig?,
        targetImmichAlbumId: String?
    ) {
        if (isExporting || sessionHandle == 0L) return
        val currentAdjustments = adjustments
        val currentMasks = masks
        onExportStart()
        exportProgressMessage =
            if (destination == ExportDestination.Immich) "Uploading to Immich..." else "Saving export..."

        coroutineScope.launch {
            try {
                val currentJson = withContext(Dispatchers.Default) {
                    currentAdjustments.toJson(currentMasks)
                }

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
                    onExportComplete(false, "Export failed (Out of Memory). Try Low RAM Mode.")
                    return@launch
                }

                when (destination) {
                    ExportDestination.Local -> {
                        val savedUri = withContext(Dispatchers.IO) {
                            if (options.format == ExportImageFormat.Jpeg && options.quality == 96) {
                                saveJpegToPictures(context, fullBytes)
                            } else {
                                val bitmap = withContext(Dispatchers.Default) {
                                    fullBytes.decodeToBitmap()
                                } ?: return@withContext null

                                saveBitmapToPictures(
                                    context,
                                    bitmap,
                                    options.format,
                                    options.quality
                                ).also { bitmap.recycle() }
                            }
                        }

                        if (savedUri != null) {
                            onExportComplete(true, "Saved to $savedUri")
                        } else {
                            onExportComplete(false, "Export failed: could not save image.")
                        }
                    }

                    ExportDestination.Immich -> {
                        val config = immichConfig
                        if (config == null) {
                            onExportComplete(false, "Immich is not configured.")
                            return@launch
                        }
                        val selectedAlbumId = targetImmichAlbumId?.takeIf { it.isNotBlank() }
                        val exportBytes = withContext(Dispatchers.Default) {
                            encodeExportBytes(fullBytes, options, allowPassthroughJpeg = false)
                        }
                        if (exportBytes == null) {
                            onExportComplete(false, "Export failed: could not encode image.")
                            return@launch
                        }
                        val fileName = buildExportFileName(sourceFileName, options.format)
                        val upload = uploadImmichAsset(
                            config = config,
                            bytes = exportBytes,
                            fileName = fileName,
                            mimeType = options.format.mimeType
                        )
                        val uploadedAssetId = upload.assetId
                        if (!uploadedAssetId.isNullOrBlank()) {
                            if (!selectedAlbumId.isNullOrBlank()) {
                                runCatching {
                                    addImmichAssetsToAlbum(
                                        config = config,
                                        albumId = selectedAlbumId,
                                        assetIds = listOf(uploadedAssetId)
                                    )
                                }
                            }

                            val xmpFileName = "$fileName.xmp"
                            val xmpBytes = buildXmpSidecarForIridis(currentJson)
                            val xmpUpload =
                                runCatching {
                                    uploadImmichAsset(
                                        config = config,
                                        bytes = xmpBytes,
                                        fileName = xmpFileName,
                                        mimeType = "application/octet-stream"
                                    )
                                }.getOrDefault(ImmichUploadResult(assetId = null, errorMessage = "XMP upload failed."))
                            val xmpAssetId = xmpUpload.assetId
                            if (!xmpAssetId.isNullOrBlank() && !selectedAlbumId.isNullOrBlank()) {
                                runCatching {
                                    addImmichAssetsToAlbum(
                                        config = config,
                                        albumId = selectedAlbumId,
                                        assetIds = listOf(xmpAssetId)
                                    )
                                }
                            }

                            onExportComplete(true, "Uploaded to Immich.")
                        } else {
                            onExportComplete(false, buildImmichUploadErrorMessage(upload))
                        }
                    }
                }
            } finally {
                exportProgressMessage = null
                showExportDialog = false
            }
        }
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
            isLoading = isExporting,
            onExportReplay = if (isExporting) null else ::promptReplayQualitySelection,
            onDismissRequest = {
                if (!isExporting) {
                    showExportDialog = false
                }
            },
            onConfirm = { options ->
                lastOptions = options
                if (isExporting || sessionHandle == 0L) return@ExportOptionsDialog
                pendingOptions = options
                val immichConfig = resolveImmichConfig()
                pendingImmichConfig = immichConfig
                showExportDialog = false
                if (immichConfig == null) {
                    showDestinationDialog = false
                    showImmichAlbumDialog = false
                    startExport(ExportDestination.Local, options, null, null)
                } else {
                    showDestinationDialog = true
                }
            }
        )
    }

    if (showDestinationDialog) {
        val immichConfig = pendingImmichConfig ?: resolveImmichConfig()
        val immichAuthMode = appPreferences.getImmichAuthMode()
        ExportDestinationDialog(
            immichEnabled = immichConfig != null,
            immichAuthMode = immichAuthMode,
            onDismissRequest = { showDestinationDialog = false },
            onSelectDestination = { destination ->
                val options = pendingOptions ?: lastOptions
                pendingOptions = options
                showDestinationDialog = false
                when (destination) {
                    ExportDestination.Local -> {
                        pendingImmichConfig = null
                        showImmichAlbumDialog = false
                        startExport(ExportDestination.Local, options, null, null)
                    }

                    ExportDestination.Immich -> {
                        if (immichConfig == null) {
                            onExportComplete(false, "Immich is not configured.")
                        } else {
                            pendingImmichConfig = immichConfig
                            showImmichAlbumDialog = true
                        }
                    }
                }
            }
        )
    }

    if (showImmichAlbumDialog) {
        val config = pendingImmichConfig ?: resolveImmichConfig()
        if (config == null) {
            showImmichAlbumDialog = false
            onExportComplete(false, "Immich is not configured.")
        } else {
            ImmichAlbumPickerDialog(
                config = config,
                initialSelectionId = lastChosenImmichAlbumId,
                onDismissRequest = {
                    showImmichAlbumDialog = false
                    showDestinationDialog = true
                },
                onAlbumSelected = { albumId ->
                    val options = pendingOptions ?: lastOptions
                    lastChosenImmichAlbumId = albumId
                    pendingImmichConfig = config
                    showImmichAlbumDialog = false
                    startExport(ExportDestination.Immich, options, config, albumId)
                }
            )
        }
    }

    if (showReplayQualityDialog) {
        ReplayQualityDialog(
            current = replayQualitySelection,
            isLoading = isExporting,
            onConfirm = { quality ->
                appPreferences.setReplayExportQuality(quality)
                replayQualitySelection = quality
                showReplayQualityDialog = false
                startReplayExport()
            },
            onDismissRequest = {
                if (!isExporting) {
                    showReplayQualityDialog = false
                }
            }
        )
    }

    if (isExporting && !showExportDialog && !showDestinationDialog && !showImmichAlbumDialog && !showReplayQualityDialog) {
        val message = exportProgressMessage
            ?: if (pendingImmichConfig != null) "Uploading to Immich..." else "Saving export..."
        ExportProgressDialog(message = message)
    }
}

private fun tryShareReplayVideo(context: Context, uri: Uri): Boolean {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "IRIDIS Replay", uri)
    }
    val chooser = Intent.createChooser(shareIntent, "Share replay")
    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        ContextCompat.startActivity(context, chooser, null)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: Throwable) {
        false
    }
}

@Composable
private fun ExportProgressDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        dismissButton = {},
        title = { Text("Exporting") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(message)
            }
        }
    )
}

private fun formatEta(etaMs: Long): String {
    if (etaMs <= 500L) return "<1s"
    val seconds = (etaMs / 1000L).coerceAtLeast(1L)
    val minutes = seconds / 60L
    val remainingSeconds = (seconds % 60L).toInt()
    return if (minutes > 0) {
        String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
    } else {
        "${remainingSeconds}s"
    }
}
