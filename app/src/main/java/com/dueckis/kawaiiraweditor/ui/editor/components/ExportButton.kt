@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.dueckis.kawaiiraweditor.ui.editor.components

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dueckis.kawaiiraweditor.data.preferences.AppPreferences
import com.dueckis.kawaiiraweditor.data.immich.ImmichAuthMode
import com.dueckis.kawaiiraweditor.data.immich.ImmichConfig
import com.dueckis.kawaiiraweditor.data.immich.addImmichAssetsToAlbum
import com.dueckis.kawaiiraweditor.data.immich.ImmichUploadResult
import com.dueckis.kawaiiraweditor.data.immich.uploadImmichAsset
import com.dueckis.kawaiiraweditor.data.media.ExportImageFormat
import com.dueckis.kawaiiraweditor.data.media.decodeToBitmap
import com.dueckis.kawaiiraweditor.data.media.saveBitmapToPictures
import com.dueckis.kawaiiraweditor.data.media.saveJpegToPictures
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ExportDestination {
    Local,
    Immich
}

@Composable
private fun ExportDestinationDialog(
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
    // Quick sanity check for common formats.
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
    var showExportDialog by remember { mutableStateOf(false) }
    var showDestinationDialog by remember { mutableStateOf(false) }
    var pendingOptions by remember { mutableStateOf<ExportOptions?>(null) }
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
    val appPreferences = remember(context) { AppPreferences(context) }

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

    fun startExport(destination: ExportDestination, options: ExportOptions, immichConfig: ImmichConfig?) {
        if (isExporting || sessionHandle == 0L) return
        val currentAdjustments = adjustments
        val currentMasks = masks
        onExportStart()

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
                                )
                                    .also { bitmap.recycle() }
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
                        val exportBytes = withContext(Dispatchers.Default) {
                            // Always re-encode for Immich to maximize compatibility with server-side decoders.
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
                            val albumId = originImmichAlbumId
                            if (!albumId.isNullOrBlank()) {
                                runCatching {
                                    addImmichAssetsToAlbum(
                                        config = config,
                                        albumId = albumId,
                                        assetIds = listOf(uploadedAssetId)
                                    )
                                }
                            }

                            // Upload XMP sidecar (Immich-supported) with our edits embedded.
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
                            if (!xmpAssetId.isNullOrBlank() && !albumId.isNullOrBlank()) {
                                runCatching {
                                    addImmichAssetsToAlbum(
                                        config = config,
                                        albumId = albumId,
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
            isLoading = isExporting, // This should trigger a LoadingIndicator inside your Dialog UI
            onDismissRequest = {
                // Prevent dismissing if an export is currently in progress
                if (!isExporting) {
                    showExportDialog = false
                }
            },
            onConfirm = { options ->
                lastOptions = options
                if (isExporting || sessionHandle == 0L) return@ExportOptionsDialog
                pendingOptions = options
                val immichConfig = resolveImmichConfig()
                if (immichConfig == null) {
                    startExport(ExportDestination.Local, options, null)
                } else {
                    showDestinationDialog = true
                }
            }
        )
    }

    if (showDestinationDialog) {
        val immichConfig = resolveImmichConfig()
        val immichAuthMode = appPreferences.getImmichAuthMode()
        ExportDestinationDialog(
            immichEnabled = immichConfig != null,
            immichAuthMode = immichAuthMode,
            onDismissRequest = { showDestinationDialog = false },
            onSelectDestination = { destination ->
                val options = pendingOptions ?: lastOptions
                showDestinationDialog = false
                startExport(destination, options, immichConfig)
            }
        )
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
