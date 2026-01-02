@file:OptIn(
    androidx.compose.material.ExperimentalMaterialApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.dueckis.kawaiiraweditor.ui.gallery

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Parcelable
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.decoding.decodePreviewBytesForTagging
import com.dueckis.kawaiiraweditor.data.immich.ImmichAlbum
import com.dueckis.kawaiiraweditor.data.immich.ImmichAsset
import com.dueckis.kawaiiraweditor.data.immich.ImmichAuthMode
import com.dueckis.kawaiiraweditor.data.immich.ImmichConfig
import com.dueckis.kawaiiraweditor.data.immich.downloadImmichOriginal
import com.dueckis.kawaiiraweditor.data.immich.downloadImmichThumbnail
import com.dueckis.kawaiiraweditor.data.immich.fetchImmichAlbumAssets
import com.dueckis.kawaiiraweditor.data.immich.fetchImmichAlbums
import com.dueckis.kawaiiraweditor.data.media.decodeToBitmap
import com.dueckis.kawaiiraweditor.data.media.displayNameForUri
import com.dueckis.kawaiiraweditor.data.media.parseRawMetadataForSearch
import com.dueckis.kawaiiraweditor.data.media.saveJpegToPictures
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.AiEnvironmentCategory
import com.dueckis.kawaiiraweditor.data.model.EditorPanelTab
import com.dueckis.kawaiiraweditor.data.model.GalleryItem
import com.dueckis.kawaiiraweditor.data.model.MaskTransformState
import com.dueckis.kawaiiraweditor.data.model.SubMaskType
import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder
import com.dueckis.kawaiiraweditor.data.permissions.maybeRequestPostNotificationsPermission
import com.dueckis.kawaiiraweditor.data.storage.ProjectStorage
import com.dueckis.kawaiiraweditor.domain.ai.AiEnvironmentMaskGenerator
import com.dueckis.kawaiiraweditor.domain.ai.ClipAutoTagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.min

private data class AiMaskCopyInfo(
    val hasAiSubject: Boolean,
    val environmentCategories: List<String>
) {
    val hasAiEnvironment: Boolean
        get() = environmentCategories.isNotEmpty()
}

private data class EnvironmentMaskUpdate(
    val dataByCategory: Map<String, String>,
    val baseWidthPx: Int,
    val baseHeightPx: Int,
    val hadFailures: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryScreen(
    items: List<GalleryItem>,
    tagger: ClipAutoTagger,
    lowQualityPreviewEnabled: Boolean,
    automaticTaggingEnabled: Boolean,
    openEditorOnImportEnabled: Boolean,
    immichDescriptionSyncEnabled: Boolean,
    immichServerUrl: String,
    immichAuthMode: ImmichAuthMode,
    immichAccessToken: String,
    immichApiKey: String,
    isTaggingInFlight: (String) -> Boolean,
    onTaggingInFlightChange: (String, Boolean) -> Unit,
    tagProgressFor: (String) -> Float?,
    onTagProgressChange: (String, Float) -> Unit,
    onMetadataChanged: (String, Map<String, String>) -> Unit,
    onOpenItem: (GalleryItem, EditorPanelTab?) -> Unit,
    onAddClick: (GalleryItem) -> Unit,
    onThumbnailReady: (String, Bitmap) -> Unit,
    onTagsChanged: (String, List<String>) -> Unit,
    onRatingChangeMany: (List<String>, Int) -> Unit,
    onDeleteMany: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestRefresh: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val storage = remember { ProjectStorage(context) }
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    var isPullRefreshing by remember { mutableStateOf(false) }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun requestNotificationPermissionIfNeeded() {
        maybeRequestPostNotificationsPermission(context) { permission ->
            notificationPermissionLauncher.launch(permission)
        }
    }

    suspend fun importBytes(
        name: String,
        bytes: ByteArray,
        openAfterImport: Boolean,
        immichAssetId: String? = null,
        immichAlbumId: String? = null
    ) {
        val projectId = withContext(Dispatchers.IO) {
            storage.importRawFile(
                name,
                bytes,
                immichAssetId = immichAssetId,
                immichAlbumId = immichAlbumId
            )
        }
        val now = System.currentTimeMillis()
        val item = GalleryItem(
            projectId = projectId,
            fileName = name,
            thumbnail = null,
            tags = emptyList(),
            rawMetadata = emptyMap(),
            createdAt = now,
            modifiedAt = now,
            immichAssetId = immichAssetId,
            immichAlbumId = immichAlbumId,
            editsUpdatedAtMs = now,
            immichSidecarUpdatedAtMs = 0L
        )
        onAddClick(item)
        if (openAfterImport) {
            onOpenItem(item, null)
        }
        coroutineScope.launch {
            val previewBytes = withContext(Dispatchers.Default) {
                runCatching { decodePreviewBytesForTagging(bytes, lowQualityPreviewEnabled) }.getOrNull()
            }
            val previewBitmap = previewBytes?.decodeToBitmap() ?: return@launch

            val maxSize = 1024
            val scale = min(maxSize.toFloat() / previewBitmap.width, maxSize.toFloat() / previewBitmap.height)
            val scaledWidth = (previewBitmap.width * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (previewBitmap.height * scale).toInt().coerceAtLeast(1)
            val thumbnail =
                if (previewBitmap.width == scaledWidth && previewBitmap.height == scaledHeight) previewBitmap
                else Bitmap.createScaledBitmap(previewBitmap, scaledWidth, scaledHeight, true)

            withContext(Dispatchers.IO) {
                val outputStream = ByteArrayOutputStream()
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                storage.saveThumbnail(projectId, outputStream.toByteArray())
            }
            onThumbnailReady(projectId, thumbnail)
            if (thumbnail != previewBitmap) previewBitmap.recycle()

            if (automaticTaggingEnabled) {
                requestNotificationPermissionIfNeeded()
                onTaggingInFlightChange(projectId, true)
                try {
                    val tags = withContext(Dispatchers.Default) {
                        runCatching {
                            tagger.generateTags(thumbnail, onProgress = { p -> onTagProgressChange(projectId, p) })
                        }.getOrDefault(emptyList())
                    }
                    withContext(Dispatchers.IO) { storage.setTags(projectId, tags) }
                    onTagsChanged(projectId, tags)
                } finally {
                    onTaggingInFlightChange(projectId, false)
                }
            }
        }

        if (automaticTaggingEnabled) {
            coroutineScope.launch {
                val meta = withContext(Dispatchers.Default) {
                    runCatching {
                        val handle = LibRawDecoder.createSession(bytes)
                        if (handle == 0L) return@runCatching emptyMap<String, String>()
                        try {
                            val json = LibRawDecoder.getMetadataJsonFromSession(handle)
                            parseRawMetadataForSearch(json)
                        } finally {
                            LibRawDecoder.releaseSession(handle)
                        }
                    }.getOrDefault(emptyMap())
                }
                if (meta.isNotEmpty()) {
                    withContext(Dispatchers.IO) { storage.setRawMetadata(projectId, meta) }
                    onMetadataChanged(projectId, meta)
                }
            }
        }
    }

    suspend fun importUri(uri: Uri, openAfterImport: Boolean) {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        if (bytes != null) {
            val name = displayNameForUri(context, uri)
            importBytes(name, bytes, openAfterImport)
        }
    }

    val activity = context as? Activity
    val intent = activity?.intent

    LaunchedEffect(intent) {
        if (intent == null) return@LaunchedEffect

        val action = intent.action
        val type = intent.type
        val urisToImport = mutableListOf<Uri>()

        if (Intent.ACTION_VIEW == action && intent.data != null) {
            urisToImport.add(intent.data!!)
        }
        else if (Intent.ACTION_SEND == action && type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
                urisToImport.add(it)
            }
        }
        else if (Intent.ACTION_SEND_MULTIPLE == action && type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.let { list ->
                list.filterIsInstance<Uri>().forEach { urisToImport.add(it) }
            }
        }

        if (urisToImport.isNotEmpty()) {
            var opened = false
            urisToImport.forEach { uri ->
                val openThis = openEditorOnImportEnabled && !opened
                importUri(uri, openAfterImport = openThis)
                if (openThis) opened = true
            }
            intent.action = null
            intent.data = null
        }
    }

    val columns = when {
        screenWidthDp >= 900 -> 5
        screenWidthDp >= 600 -> 4
        screenWidthDp >= 400 -> 3
        else -> 2
    }

    val mimeTypes = arrayOf("image/x-sony-arw", "image/*")
    val immichConfigured = remember(immichServerUrl, immichAuthMode, immichAccessToken, immichApiKey) {
        when (immichAuthMode) {
            ImmichAuthMode.Login -> immichServerUrl.isNotBlank() && immichAccessToken.isNotBlank()
            ImmichAuthMode.ApiKey -> immichServerUrl.isNotBlank() && immichApiKey.isNotBlank()
        }
    }
    val immichConfig =
        remember(immichServerUrl, immichAuthMode, immichAccessToken, immichApiKey) {
            ImmichConfig(
                serverUrl = immichServerUrl,
                authMode = immichAuthMode,
                apiKey = immichApiKey,
                accessToken = immichAccessToken
            )
        }
    var gallerySource by rememberSaveable { mutableStateOf(GallerySource.Local) }
    var immichAlbums by remember { mutableStateOf<List<ImmichAlbum>>(emptyList()) }
    var immichSelectedAlbum by remember { mutableStateOf<ImmichAlbum?>(null) }
    var immichAlbumAssets by remember { mutableStateOf<List<ImmichAsset>>(emptyList()) }
    var immichLoading by remember { mutableStateOf(false) }
    var immichError by remember { mutableStateOf<String?>(null) }
    var immichRefreshTrigger by remember { mutableIntStateOf(0) }
    val immichDownloadInFlight = remember { mutableStateMapOf<String, Boolean>() }
    val immichThumbs = remember { mutableStateMapOf<String, Bitmap>() }
    val immichThumbInFlight = remember { mutableStateMapOf<String, Boolean>() }

    suspend fun loadImmichGallery() {
        immichError = null
        if (!immichConfigured) {
            immichLoading = false
            return
        }
        immichLoading = true
        if (immichSelectedAlbum == null) {
            val loaded = fetchImmichAlbums(immichConfig)
            immichAlbums = loaded ?: emptyList()
            if (loaded == null) {
                immichError = "Could not load Immich albums."
            }
        } else {
            immichAlbumAssets = emptyList()
            val loaded = fetchImmichAlbumAssets(immichConfig, immichSelectedAlbum!!.id)
            immichAlbumAssets = loaded ?: emptyList()
            if (loaded == null) {
                immichError = "Could not load Immich album."
            }
        }
        immichLoading = false
    }

    var queryText by rememberSaveable { mutableStateOf("") }
    val queryLower = remember(queryText) { queryText.trim().lowercase(Locale.US) }
    var gallerySortField by rememberSaveable { mutableStateOf(GallerySortField.Date) }
    var gallerySortOrder by rememberSaveable { mutableStateOf(GallerySortOrder.Desc) }

    fun matchesQuery(item: GalleryItem): Boolean {
        if (queryLower.isBlank()) return true
        if (item.fileName.lowercase(Locale.US).contains(queryLower)) return true
        if (item.tags.any { it.lowercase(Locale.US).contains(queryLower) }) return true
        if (item.rawMetadata.any { (k, v) ->
                k.lowercase(Locale.US).contains(queryLower) || v.lowercase(Locale.US).contains(queryLower)
            }
        ) return true
        return false
    }

    fun matchesImmichAlbumQuery(album: ImmichAlbum): Boolean {
        if (queryLower.isBlank()) return true
        return album.name.lowercase(Locale.US).contains(queryLower)
    }

    fun matchesImmichAssetQuery(asset: ImmichAsset): Boolean {
        if (queryLower.isBlank()) return true
        return asset.fileName.lowercase(Locale.US).contains(queryLower)
    }

    fun parseIsoToEpoch(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            val format = java.text.SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val parsed = runCatching { format.parse(iso)?.time ?: 0L }.getOrDefault(0L)
            if (parsed != 0L) return parsed
        }
        return 0L
    }

    val filteredLocalItems = remember(items, queryLower) {
        if (queryLower.isBlank()) items else items.filter(::matchesQuery)
    }
    val filteredImmichAlbums = remember(immichAlbums, queryLower) {
        if (queryLower.isBlank()) immichAlbums else immichAlbums.filter(::matchesImmichAlbumQuery)
    }
    val filteredImmichAssets = remember(immichAlbumAssets, queryLower) {
        if (queryLower.isBlank()) immichAlbumAssets else immichAlbumAssets.filter(::matchesImmichAssetQuery)
    }
    val sortedLocalItems = remember(filteredLocalItems, gallerySortField, gallerySortOrder) {
        when (gallerySortField) {
            GallerySortField.Name -> {
                if (gallerySortOrder == GallerySortOrder.Asc) {
                    filteredLocalItems.sortedBy { it.fileName.lowercase(Locale.US) }
                } else {
                    filteredLocalItems.sortedByDescending { it.fileName.lowercase(Locale.US) }
                }
            }
            GallerySortField.Date -> {
                if (gallerySortOrder == GallerySortOrder.Asc) {
                    filteredLocalItems.sortedBy { it.createdAt }
                } else {
                    filteredLocalItems.sortedByDescending { it.createdAt }
                }
            }
            GallerySortField.Changed -> {
                if (gallerySortOrder == GallerySortOrder.Asc) {
                    filteredLocalItems.sortedBy { it.modifiedAt }
                } else {
                    filteredLocalItems.sortedByDescending { it.modifiedAt }
                }
            }
        }
    }
    val sortedImmichAlbums = remember(filteredImmichAlbums, gallerySortField, gallerySortOrder) {
        when (gallerySortField) {
            GallerySortField.Name -> {
                if (gallerySortOrder == GallerySortOrder.Asc) {
                    filteredImmichAlbums.sortedBy { it.name.lowercase(Locale.US) }
                } else {
                    filteredImmichAlbums.sortedByDescending { it.name.lowercase(Locale.US) }
                }
            }
            GallerySortField.Date -> {
                if (gallerySortOrder == GallerySortOrder.Asc) {
                    filteredImmichAlbums.sortedBy { parseIsoToEpoch(it.createdAt) }
                } else {
                    filteredImmichAlbums.sortedByDescending { parseIsoToEpoch(it.createdAt) }
                }
            }
            GallerySortField.Changed -> {
                if (gallerySortOrder == GallerySortOrder.Asc) {
                    filteredImmichAlbums.sortedBy { parseIsoToEpoch(it.lastModifiedAssetTimestamp ?: it.updatedAt) }
                } else {
                    filteredImmichAlbums.sortedByDescending {
                        parseIsoToEpoch(it.lastModifiedAssetTimestamp ?: it.updatedAt)
                    }
                }
            }
        }
    }
    val sortedImmichAssets = remember(filteredImmichAssets, gallerySortField, gallerySortOrder) {
        when (gallerySortField) {
            GallerySortField.Name -> {
                if (gallerySortOrder == GallerySortOrder.Asc) {
                    filteredImmichAssets.sortedBy { it.fileName.lowercase(Locale.US) }
                } else {
                    filteredImmichAssets.sortedByDescending { it.fileName.lowercase(Locale.US) }
                }
            }
            GallerySortField.Date -> {
                if (gallerySortOrder == GallerySortOrder.Asc) {
                    filteredImmichAssets.sortedBy { parseIsoToEpoch(it.createdAt) }
                } else {
                    filteredImmichAssets.sortedByDescending { parseIsoToEpoch(it.createdAt) }
                }
            }
            GallerySortField.Changed -> {
                if (gallerySortOrder == GallerySortOrder.Asc) {
                    filteredImmichAssets.sortedBy { parseIsoToEpoch(it.updatedAt) }
                } else {
                    filteredImmichAssets.sortedByDescending { parseIsoToEpoch(it.updatedAt) }
                }
            }
        }
    }

    val pickRaw = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            importUri(uri, openAfterImport = openEditorOnImportEnabled)
        }
    }

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isBulkExporting by remember { mutableStateOf(false) }
    var isPastingAdjustments by remember { mutableStateOf(false) }
    var copiedAdjustmentsJson by remember { mutableStateOf<String?>(null) }
    var pasteProgressMessage by remember { mutableStateOf<String?>(null) }
    var pendingPasteIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPasteAdjustmentsJson by remember { mutableStateOf<String?>(null) }
    var pendingPasteEnvironmentCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPasteHasSubjectMasks by remember { mutableStateOf(false) }
    var pendingPasteOpenTarget by remember { mutableStateOf<GalleryItem?>(null) }
    var pendingRunEnvironmentMasks by remember { mutableStateOf(false) }
    var showEnvironmentMaskDialog by remember { mutableStateOf(false) }
    var showSubjectMaskDialog by remember { mutableStateOf(false) }
    var bulkExportDone by remember { mutableIntStateOf(0) }
    var bulkExportTotal by remember { mutableIntStateOf(0) }
    var bulkExportStatus by remember { mutableStateOf<String?>(null) }
    val bulkExportProgressAnim = remember { Animatable(0f) }
    val selectedItems = remember(items, selectedIds) { items.filter { it.projectId in selectedIds } }
    val uniformRating = remember(selectedItems) {
        selectedItems.map { it.rating }.distinct().singleOrNull()
    }

    val localItemByImmichAssetId = remember(items) {
        items.asSequence()
            .mapNotNull { item -> item.immichAssetId?.let { it to item } }
            .toMap()
    }

    var showSelectionInfo by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectionInfoMetadataJson by remember { mutableStateOf<String?>(null) }
    val selectionInfoTarget = remember(selectedItems) { selectedItems.singleOrNull() }
    val metadataDispatcher = remember { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
    DisposableEffect(metadataDispatcher) {
        onDispose { metadataDispatcher.close() }
    }

    LaunchedEffect(gallerySource) {
        if (gallerySource != GallerySource.Local) {
            selectedIds = emptySet()
            showSelectionInfo = false
            showDeleteDialog = false
        }
        if (gallerySource != GallerySource.Immich) {
            immichSelectedAlbum = null
            immichAlbumAssets = emptyList()
            immichError = null
        }
    }

    fun parseAiMaskCopyInfo(adjustmentsJson: String): AiMaskCopyInfo {
        return runCatching {
            val obj = JSONObject(adjustmentsJson)
            val masksArr = obj.optJSONArray("masks") ?: return@runCatching AiMaskCopyInfo(false, emptyList())
            var hasSubject = false
            val envCategories = linkedSetOf<String>()
            for (i in 0 until masksArr.length()) {
                val maskObj = masksArr.optJSONObject(i) ?: continue
                val subMasksArr = maskObj.optJSONArray("subMasks") ?: continue
                for (j in 0 until subMasksArr.length()) {
                    val subObj = subMasksArr.optJSONObject(j) ?: continue
                    when (subObj.optString("type")) {
                        SubMaskType.AiSubject.id -> hasSubject = true
                        SubMaskType.AiEnvironment.id -> {
                            val params = subObj.optJSONObject("parameters")
                            val category = AiEnvironmentCategory.fromId(params?.optString("category")).id
                            envCategories.add(category)
                        }
                    }
                }
            }
            AiMaskCopyInfo(hasSubject, envCategories.toList())
        }.getOrDefault(AiMaskCopyInfo(false, emptyList()))
    }

    fun updateEnvironmentMasksJson(
        adjustmentsJson: String,
        environmentMaskDataByCategory: Map<String, String>,
        baseTransform: MaskTransformState?,
        baseWidthPx: Int?,
        baseHeightPx: Int?,
        clearMissing: Boolean
    ): String {
        return runCatching {
            val obj = JSONObject(adjustmentsJson)
            val masksArr = obj.optJSONArray("masks") ?: return@runCatching adjustmentsJson
            for (i in 0 until masksArr.length()) {
                val maskObj = masksArr.optJSONObject(i) ?: continue
                val subMasksArr = maskObj.optJSONArray("subMasks") ?: continue
                for (j in 0 until subMasksArr.length()) {
                    val subObj = subMasksArr.optJSONObject(j) ?: continue
                    if (subObj.optString("type") != SubMaskType.AiEnvironment.id) continue
                    val params = subObj.optJSONObject("parameters") ?: JSONObject().also { subObj.put("parameters", it) }
                    val categoryId = AiEnvironmentCategory.fromId(params.optString("category")).id
                    val dataUrl = environmentMaskDataByCategory[categoryId]
                    if (dataUrl != null) {
                        params.put("maskDataBase64", dataUrl)
                        baseTransform?.let { params.put("baseTransform", it.toJsonObject()) }
                        if (baseWidthPx != null && baseHeightPx != null) {
                            params.put("baseWidthPx", baseWidthPx)
                            params.put("baseHeightPx", baseHeightPx)
                        }
                    } else if (clearMissing) {
                        params.remove("maskDataBase64")
                        params.remove("baseTransform")
                        params.remove("baseWidthPx")
                        params.remove("baseHeightPx")
                    }
                }
            }
            obj.toString()
        }.getOrDefault(adjustmentsJson)
    }

    fun startBulkExport(projectIds: List<String>) {
        if (isBulkExporting) return
        if (projectIds.isEmpty()) return

        isBulkExporting = true
        bulkExportDone = 0
        bulkExportTotal = projectIds.size

        coroutineScope.launch {
            var successCount = 0
            var failureCount = 0
            bulkExportProgressAnim.snapTo(0f)

            for ((idx, projectId) in projectIds.withIndex()) {
                val total = bulkExportTotal.coerceAtLeast(1)
                val start = idx.toFloat() / total.toFloat()
                val end = (idx + 1).toFloat() / total.toFloat()
                val slot = (end - start).coerceAtLeast(0f)

                if (bulkExportProgressAnim.value < start - 0.0001f) {
                    bulkExportProgressAnim.animateTo(
                        start,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                    )
                }

                val cap = (start + slot * 0.985f).coerceIn(0f, 1f)
                val fakeJob = launch {
                    val t0 = SystemClock.uptimeMillis()
                    while (isActive) {
                        val tSec = (SystemClock.uptimeMillis() - t0).toFloat() / 1000f
                        val frac = 1f - exp(-tSec / 1.4f)
                        val target = (start + (cap - start) * frac).coerceIn(start, cap)
                        val current = bulkExportProgressAnim.value
                        val next = current + (target - current) * 0.22f
                        bulkExportProgressAnim.snapTo(next.coerceIn(start, cap))
                        delay(16)
                    }
                }

                val (rawBytes, adjustmentsJson) = withContext(Dispatchers.IO) {
                    val raw = storage.loadRawBytes(projectId)
                    val adj = storage.loadAdjustments(projectId)
                    raw to adj
                }
                if (rawBytes == null) {
                    failureCount++
                    fakeJob.cancel()
                    fakeJob.join()
                    bulkExportDone = idx + 1
                    bulkExportProgressAnim.animateTo(
                        end,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                    )
                    continue
                }

                val jpegBytes = withContext(Dispatchers.Default) {
                    runCatching { LibRawDecoder.decodeFullRes(rawBytes, adjustmentsJson) }.getOrNull()
                }
                if (jpegBytes == null) {
                    failureCount++
                    fakeJob.cancel()
                    fakeJob.join()
                    bulkExportDone = idx + 1
                    bulkExportProgressAnim.animateTo(
                        end,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                    )
                    continue
                }

                val saved = withContext(Dispatchers.IO) { saveJpegToPictures(context, jpegBytes) }
                if (saved == null) failureCount++ else successCount++

                fakeJob.cancel()
                fakeJob.join()
                bulkExportDone = idx + 1
                bulkExportProgressAnim.animateTo(
                    end,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                )
            }

            isBulkExporting = false
            bulkExportStatus =
                if (failureCount == 0) "Exported $successCount JPEG(s)."
                else "Exported $successCount JPEG(s), $failureCount failed."
        }
    }

    suspend fun pasteAdjustments(
        projectIds: List<String>,
        adjustmentsJson: String,
        regenerateEnvironmentMasks: Boolean,
        environmentCategories: List<String>
    ) {
        if (isBulkExporting || isPastingAdjustments) return
        if (projectIds.isEmpty()) return
        isPastingAdjustments = true

        val uniqueEnvironmentCategories = environmentCategories.distinct()
        val shouldRegenerateEnvironment = regenerateEnvironmentMasks && uniqueEnvironmentCategories.isNotEmpty()
        val environmentGenerator =
            if (shouldRegenerateEnvironment) AiEnvironmentMaskGenerator(context) else null
        val baseTransform = if (shouldRegenerateEnvironment) MaskTransformState() else null
        val baseJson = if (shouldRegenerateEnvironment) AdjustmentState().toJson(emptyList()) else ""
        var successCount = 0
        var failureCount = 0
        var environmentFailureCount = 0

        try {
            for ((index, projectId) in projectIds.withIndex()) {
                pasteProgressMessage =
                    if (shouldRegenerateEnvironment) {
                        "Regenerating environment masks (${index + 1}/${projectIds.size})..."
                    } else {
                        "Pasting adjustments (${index + 1}/${projectIds.size})..."
                    }

                val rawBytes = withContext(Dispatchers.IO) { storage.loadRawBytes(projectId) }
                if (rawBytes == null) {
                    failureCount++
                    continue
                }

                var effectiveJson = adjustmentsJson
                if (shouldRegenerateEnvironment && environmentGenerator != null) {
                    val envUpdate =
                        withContext(Dispatchers.Default) {
                            val previewBytes =
                                runCatching { LibRawDecoder.decode(rawBytes, baseJson) }.getOrNull()
                            val bitmap = previewBytes?.decodeToBitmap() ?: return@withContext null
                            try {
                                val dataByCategory = mutableMapOf<String, String>()
                                var hadFailures = false
                                val baseWidth = bitmap.width.coerceAtLeast(1)
                                val baseHeight = bitmap.height.coerceAtLeast(1)
                                for (categoryId in uniqueEnvironmentCategories) {
                                    val category = AiEnvironmentCategory.fromId(categoryId)
                                    val dataUrl =
                                        runCatching {
                                            environmentGenerator.generateEnvironmentMaskDataUrl(bitmap, category)
                                        }.getOrNull()
                                    if (dataUrl == null) {
                                        hadFailures = true
                                    } else {
                                        dataByCategory[category.id] = dataUrl
                                    }
                                }
                                EnvironmentMaskUpdate(
                                    dataByCategory = dataByCategory,
                                    baseWidthPx = baseWidth,
                                    baseHeightPx = baseHeight,
                                    hadFailures = hadFailures
                                )
                            } finally {
                                bitmap.recycle()
                            }
                        }

                    if (envUpdate == null) {
                        environmentFailureCount++
                        effectiveJson =
                            updateEnvironmentMasksJson(
                                adjustmentsJson = adjustmentsJson,
                                environmentMaskDataByCategory = emptyMap(),
                                baseTransform = null,
                                baseWidthPx = null,
                                baseHeightPx = null,
                                clearMissing = true
                            )
                    } else {
                        if (envUpdate.hadFailures) environmentFailureCount++
                        effectiveJson =
                            updateEnvironmentMasksJson(
                                adjustmentsJson = adjustmentsJson,
                                environmentMaskDataByCategory = envUpdate.dataByCategory,
                                baseTransform = baseTransform,
                                baseWidthPx = envUpdate.baseWidthPx,
                                baseHeightPx = envUpdate.baseHeightPx,
                                clearMissing = true
                            )
                    }
                }

                withContext(Dispatchers.IO) { storage.saveAdjustments(projectId, effectiveJson) }

                val previewBytes = withContext(Dispatchers.Default) {
                    runCatching { LibRawDecoder.decode(rawBytes, effectiveJson) }.getOrNull()
                }
                val previewBitmap = previewBytes?.decodeToBitmap()
                if (previewBitmap == null) {
                    failureCount++
                    continue
                }

                withContext(Dispatchers.IO) {
                    val maxSize = 1024
                    val scale = min(maxSize.toFloat() / previewBitmap.width, maxSize.toFloat() / previewBitmap.height)
                    val scaledWidth = (previewBitmap.width * scale).toInt().coerceAtLeast(1)
                    val scaledHeight = (previewBitmap.height * scale).toInt().coerceAtLeast(1)
                    val thumbnail =
                        if (previewBitmap.width == scaledWidth && previewBitmap.height == scaledHeight) previewBitmap
                        else Bitmap.createScaledBitmap(previewBitmap, scaledWidth, scaledHeight, true)

                    val outputStream = ByteArrayOutputStream()
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    storage.saveThumbnail(projectId, outputStream.toByteArray())

                    if (thumbnail != previewBitmap) thumbnail.recycle()
                    previewBitmap.recycle()
                }

                successCount++
            }
        } finally {
            isPastingAdjustments = false
            pasteProgressMessage = null
        }

        onRequestRefresh()
        val msg = buildString {
            when {
                failureCount == 0 -> append("Pasted adjustments to $successCount image(s).")
                successCount == 0 -> append("Paste failed.")
                else -> append("Pasted to $successCount image(s), $failureCount failed.")
            }
            if (environmentFailureCount > 0) {
                if (isNotEmpty()) append(" ")
                append("Environment masks failed on $environmentFailureCount image(s).")
            }
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun launchPaste(
        regenerateEnvironmentMasks: Boolean,
        openMasksAfterPaste: Boolean
    ) {
        val ids = pendingPasteIds
        val json = pendingPasteAdjustmentsJson
        val envCategories = if (regenerateEnvironmentMasks) pendingPasteEnvironmentCategories else emptyList()
        val openTarget = if (openMasksAfterPaste) pendingPasteOpenTarget else null

        pendingPasteIds = emptyList()
        pendingPasteAdjustmentsJson = null
        pendingPasteEnvironmentCategories = emptyList()
        pendingPasteHasSubjectMasks = false
        pendingPasteOpenTarget = null
        pendingRunEnvironmentMasks = false
        showEnvironmentMaskDialog = false
        showSubjectMaskDialog = false

        if (json == null || ids.isEmpty()) return

        coroutineScope.launch {
            pasteAdjustments(
                projectIds = ids,
                adjustmentsJson = json,
                regenerateEnvironmentMasks = regenerateEnvironmentMasks,
                environmentCategories = envCategories
            )
            if (openTarget != null) {
                onOpenItem(openTarget, EditorPanelTab.Masks)
            }
        }
    }

    suspend fun refreshLocalPreviews() {
        val projectIds = items.map { it.projectId }
        for (projectId in projectIds) {
            val rawBytes = withContext(Dispatchers.IO) { storage.loadRawBytes(projectId) } ?: continue
            val adjustmentsJson = withContext(Dispatchers.IO) { storage.loadAdjustments(projectId) }
            val previewBytes = withContext(Dispatchers.Default) {
                runCatching { LibRawDecoder.decode(rawBytes, adjustmentsJson) }.getOrNull()
            }
            val previewBitmap = previewBytes?.decodeToBitmap() ?: continue

            val maxSize = 1024
            val scale = min(maxSize.toFloat() / previewBitmap.width, maxSize.toFloat() / previewBitmap.height)
            val scaledWidth = (previewBitmap.width * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (previewBitmap.height * scale).toInt().coerceAtLeast(1)
            val thumbnail =
                if (previewBitmap.width == scaledWidth && previewBitmap.height == scaledHeight) previewBitmap
                else Bitmap.createScaledBitmap(previewBitmap, scaledWidth, scaledHeight, true)

            withContext(Dispatchers.IO) {
                val outputStream = ByteArrayOutputStream()
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                storage.saveThumbnail(projectId, outputStream.toByteArray())
            }
            onThumbnailReady(projectId, thumbnail)
            if (thumbnail != previewBitmap) thumbnail.recycle()
            previewBitmap.recycle()
        }
        onRequestRefresh()
    }

    suspend fun ensureImmichThumbnail(assetId: String) {
        if (!immichConfigured) return
        if (immichThumbs.containsKey(assetId) || immichThumbInFlight[assetId] == true) return
        immichThumbInFlight[assetId] = true
        val bytes = downloadImmichThumbnail(immichConfig, assetId)
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bitmap != null) immichThumbs[assetId] = bitmap
        immichThumbInFlight.remove(assetId)
    }

    fun startImmichImport(asset: ImmichAsset) {
        if (!immichConfigured) {
            val message =
                if (immichAuthMode == ImmichAuthMode.Login) {
                    "Log in to Immich in settings first."
                } else {
                    "Configure Immich API key in settings first."
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }
        val existing =
            items.firstOrNull { it.immichAssetId == asset.id }
                ?: storage.findProjectByImmichAssetId(asset.id)?.let { meta ->
                    GalleryItem(
                        projectId = meta.id,
                        fileName = meta.fileName,
                        createdAt = meta.createdAt,
                        modifiedAt = meta.modifiedAt,
                        rating = meta.rating,
                        tags = meta.tags ?: emptyList(),
                        rawMetadata = meta.rawMetadata ?: emptyMap(),
                        immichAssetId = meta.immichAssetId,
                        immichAlbumId = meta.immichAlbumId,
                        editsUpdatedAtMs = meta.editsUpdatedAtMs ?: meta.modifiedAt,
                        immichSidecarUpdatedAtMs = meta.immichSidecarUpdatedAtMs ?: 0L
                    )
                }
        if (existing != null) {
            onOpenItem(existing, null)
            return
        }
        if (immichDownloadInFlight[asset.id] == true) return
        immichDownloadInFlight[asset.id] = true
        coroutineScope.launch {
            try {
                val bytes = downloadImmichOriginal(immichConfig, asset.id)
                if (bytes == null) {
                    Toast.makeText(context, "Immich download failed.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                importBytes(
                    asset.fileName,
                    bytes,
                    openAfterImport = true,
                    immichAssetId = asset.id,
                    immichAlbumId = immichSelectedAlbum?.id
                )
            } finally {
                immichDownloadInFlight.remove(asset.id)
            }
        }
    }

    LaunchedEffect(bulkExportStatus) {
        if (bulkExportStatus == null) return@LaunchedEffect
        Toast.makeText(context, bulkExportStatus, Toast.LENGTH_SHORT).show()
        delay(2500)
        bulkExportStatus = null
    }

    LaunchedEffect(immichServerUrl, immichAuthMode, immichAccessToken, immichApiKey) {
        immichThumbs.clear()
        immichThumbInFlight.clear()
        immichDownloadInFlight.clear()
        immichAlbums = emptyList()
        immichAlbumAssets = emptyList()
        immichSelectedAlbum = null
        immichError = null
    }

    LaunchedEffect(immichConfigured) {
        if (!immichConfigured) {
            if (gallerySource == GallerySource.Immich) gallerySource = GallerySource.Local
            immichSelectedAlbum = null
        }
    }

    LaunchedEffect(
        gallerySource,
        immichSelectedAlbum?.id,
        immichServerUrl,
        immichAuthMode,
        immichAccessToken,
        immichApiKey,
        immichRefreshTrigger
    ) {
        if (gallerySource != GallerySource.Immich) return@LaunchedEffect
        loadImmichGallery()
    }

    val topTags = remember(items) {
        items.flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var isExecutingSearch by remember { mutableStateOf(false) }

    LaunchedEffect(searchActive) {
        if (!searchActive) {
            if (isExecutingSearch) {
                isExecutingSearch = false
            } else {
                queryText = ""
            }
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = gallerySource == GallerySource.Immich && immichSelectedAlbum != null) {
        immichSelectedAlbum = null
        immichAlbumAssets = emptyList()
        immichError = null
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isPullRefreshing,
        onRefresh = {
            if (isPullRefreshing) return@rememberPullRefreshState
            if (gallerySource != GallerySource.Immich) return@rememberPullRefreshState
            coroutineScope.launch {
                isPullRefreshing = true
                try {
                    loadImmichGallery()
                } finally {
                    isPullRefreshing = false
                }
            }
        }
    )

    LaunchedEffect(gallerySource) {
        if (gallerySource != GallerySource.Immich && isPullRefreshing) {
            isPullRefreshing = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DockedSearchBar(
                            modifier = Modifier
                                .weight(1f)
                                .semantics { traversalIndex = 0f },
                            inputField = {
                                SearchBarDefaults.InputField(
                                    query = queryText,
                                    onQueryChange = { queryText = it },
                                    onSearch = {
                                        isExecutingSearch = true
                                        searchActive = false
                                    },
                                    expanded = searchActive,
                                    onExpandedChange = { searchActive = it },
                                    placeholder = { Text("Search RAWs") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                    trailingIcon = {
                                        if (queryText.isNotBlank()) {
                                            IconButton(onClick = { queryText = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear")
                                            }
                                        }
                                    }
                                )
                            },
                            expanded = searchActive,
                            onExpandedChange = { searchActive = it }
                        ) {
                            val showPopularTags =
                                queryText.isBlank() && topTags.isNotEmpty() && gallerySource == GallerySource.Local
                            val showSearchResults = queryText.isNotBlank()

                            if (showPopularTags) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        text = "Popular tags",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(topTags.size) { index ->
                                            val tag = topTags[index]
                                            InputChip(
                                                selected = false,
                                                onClick = {
                                                    queryText = tag
                                                    isExecutingSearch = true
                                                    searchActive = false
                                                },
                                                label = { Text(tag) },
                                                colors = InputChipDefaults.inputChipColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                                )
                                            )
                                        }
                                    }
                                }
                            } else if (showSearchResults) {
                                Column(Modifier.verticalScroll(rememberScrollState())) {
                                    if (gallerySource == GallerySource.Immich) {
                                        val matches =
                                            if (immichSelectedAlbum == null) {
                                                remember(queryLower, immichAlbums) {
                                                    immichAlbums.filter(::matchesImmichAlbumQuery).take(10)
                                                }
                                            } else {
                                                remember(queryLower, immichAlbumAssets) {
                                                    immichAlbumAssets.filter(::matchesImmichAssetQuery).take(10)
                                                }
                                            }
                                        if (matches.isNotEmpty()) {
                                            matches.forEach { item ->
                                                ListItem(
                                                    headlineContent = {
                                                        val title =
                                                            if (item is ImmichAlbum) item.name
                                                            else (item as ImmichAsset).fileName
                                                        Text(title)
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            val title =
                                                                if (item is ImmichAlbum) item.name
                                                                else (item as ImmichAsset).fileName
                                                            queryText = title
                                                            isExecutingSearch = true
                                                            searchActive = false
                                                        }
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "No matches found",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else {
                                        val matches = remember(queryLower, items) {
                                            items.filter(::matchesQuery).take(10)
                                        }
                                        if (matches.isNotEmpty()) {
                                            matches.forEach { item ->
                                                ListItem(
                                                    headlineContent = { Text(item.fileName) },
                                                    supportingContent = {
                                                        if (item.tags.isNotEmpty()) {
                                                            Text(
                                                                item.tags.take(4).joinToString(", "),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            queryText = item.fileName
                                                            isExecutingSearch = true
                                                            searchActive = false
                                                        }
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "No matches found",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (searchActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Start typing to search",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                    }
                }

                if (immichConfigured) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InputChip(
                            selected = gallerySource == GallerySource.Local,
                            onClick = { gallerySource = GallerySource.Local },
                            label = { Text("Local") },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                        InputChip(
                            selected = gallerySource == GallerySource.Immich,
                            onClick = { gallerySource = GallerySource.Immich },
                            label = { Text("Immich") },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                }

                val sortMenuExpanded = remember { mutableStateOf(false) }
                val orderMenuExpanded = remember { mutableStateOf(false) }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Box {
                            InputChip(
                                selected = false,
                                onClick = { sortMenuExpanded.value = true },
                                label = { Text("Sort: ${gallerySortField.label}") },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                            DropdownMenu(
                                expanded = sortMenuExpanded.value,
                                onDismissRequest = { sortMenuExpanded.value = false }
                            ) {
                                GallerySortField.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            gallerySortField = option
                                            sortMenuExpanded.value = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Box {
                            InputChip(
                                selected = false,
                                onClick = { orderMenuExpanded.value = true },
                                label = { Text("Order: ${gallerySortOrder.label}") },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                            DropdownMenu(
                                expanded = orderMenuExpanded.value,
                                onDismissRequest = { orderMenuExpanded.value = false }
                            ) {
                                GallerySortOrder.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            gallerySortOrder = option
                                            orderMenuExpanded.value = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (isBulkExporting) {
                    LinearWavyProgressIndicator(
                        progress = { bulkExportProgressAnim.value.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedIds.isEmpty(),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                BottomAppBar(
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { pickRaw.launch(mimeTypes) },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add RAW file")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (gallerySource == GallerySource.Immich) Modifier.pullRefresh(pullRefreshState) else Modifier)
        ) {
            val gridBottomPadding = if (selectedIds.isNotEmpty()) 128.dp else 88.dp

            Column(modifier = Modifier.fillMaxSize()) {
                if (gallerySource == GallerySource.Local) {
                    if (items.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No RAW files yet",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap the + button to add RAW files",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            modifier = Modifier.fillMaxSize(),
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 16.dp,
                                end = 16.dp,
                                bottom = gridBottomPadding
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(sortedLocalItems.size) { index ->
                                val item = sortedLocalItems[index]
                                val isSelected = item.projectId in selectedIds
                                val needsImmichSync =
                                    immichDescriptionSyncEnabled &&
                                        !item.immichAssetId.isNullOrBlank() &&
                                        item.editsUpdatedAtMs > item.immichSidecarUpdatedAtMs
                                val showImmichOriginIcon =
                                    immichDescriptionSyncEnabled && !item.immichAssetId.isNullOrBlank()
                                GalleryItemCard(
                                    item = item,
                                    selected = isSelected,
                                    isProcessing = isTaggingInFlight(item.projectId),
                                    automaticTaggingEnabled = automaticTaggingEnabled,
                                    processingProgress = tagProgressFor(item.projectId),
                                    needsImmichSync = needsImmichSync,
                                    showImmichOriginIcon = showImmichOriginIcon && !needsImmichSync,
                                    onClick = {
                                        if (isBulkExporting) return@GalleryItemCard
                                        if (selectedIds.isEmpty()) {
                                            onOpenItem(item, null)
                                        } else {
                                            selectedIds =
                                                if (isSelected) selectedIds - item.projectId
                                                else selectedIds + item.projectId
                                        }
                                    },
                                    onLongClick = {
                                        if (isBulkExporting) return@GalleryItemCard
                                        selectedIds =
                                            if (isSelected) selectedIds - item.projectId else selectedIds + item.projectId
                                    }
                                )
                            }
                        }
                    }
                } else {
                    val showingImmichAlbums = immichSelectedAlbum == null
                    if (immichSelectedAlbum != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                immichSelectedAlbum = null
                                immichAlbumAssets = emptyList()
                                immichError = null
                            }) { Text("Albums") }
                            Text(
                                text = immichSelectedAlbum?.name.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    when {
                        !immichConfigured -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Immich not configured",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = when (immichAuthMode) {
                                        ImmichAuthMode.Login ->
                                            "Add your server URL and log in from settings to browse Immich."
                                        ImmichAuthMode.ApiKey ->
                                            "Add your server URL and API key in settings to browse Immich."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(onClick = onOpenSettings) { Text("Open settings") }
                            }
                        }
                        immichLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                        immichError != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = immichError ?: "Immich error",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(onClick = { immichRefreshTrigger++ }) { Text("Retry") }
                            }
                        }
                        showingImmichAlbums && sortedImmichAlbums.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (queryLower.isNotBlank()) "No Immich matches"
                                    else "No Immich albums found",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        !showingImmichAlbums && sortedImmichAssets.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (queryLower.isNotBlank()) "No Immich matches"
                                    else "No Immich images found",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            LazyVerticalGrid(
                                modifier = Modifier.fillMaxSize(),
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    top = 16.dp,
                                    end = 16.dp,
                                    bottom = gridBottomPadding
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (showingImmichAlbums) {
                                    items(sortedImmichAlbums.size) { index ->
                                        val album = sortedImmichAlbums[index]
                                        val thumbId = album.thumbnailAssetId
                                        val thumbnail =
                                            thumbId?.let { localItemByImmichAssetId[it]?.thumbnail }
                                                ?: thumbId?.let { immichThumbs[it] }
                                        LaunchedEffect(thumbId, immichConfigured) {
                                            if (immichConfigured && thumbId != null) ensureImmichThumbnail(thumbId)
                                        }
                                        ImmichAlbumCard(
                                            name = album.name,
                                            assetCount = album.assetCount,
                                            thumbnail = thumbnail,
                                            onClick = { immichSelectedAlbum = album }
                                        )
                                    }
                                } else {
                                    items(sortedImmichAssets.size) { index ->
                                        val asset = sortedImmichAssets[index]
                                        val thumbnail = localItemByImmichAssetId[asset.id]?.thumbnail ?: immichThumbs[asset.id]
                                        LaunchedEffect(asset.id, immichConfigured) {
                                            if (immichConfigured) ensureImmichThumbnail(asset.id)
                                        }
                                        ImmichItemCard(
                                            fileName = asset.fileName,
                                            thumbnail = thumbnail,
                                            isDownloading = immichDownloadInFlight[asset.id] == true,
                                            onClick = { startImmichImport(asset) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (gallerySource == GallerySource.Immich) {
                PullRefreshIndicator(
                    refreshing = isPullRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = selectedIds.isNotEmpty() && gallerySource == GallerySource.Local,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedIds.size == 1) {
                                IconButton(
                                    enabled = !isBulkExporting && !isPastingAdjustments,
                                    onClick = {
                                        val id = selectedIds.first()
                                        copiedAdjustmentsJson = storage.loadAdjustments(id)
                                        Toast.makeText(context, "Copied adjustments", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy adjustments")
                                }
                            }

                            IconButton(
                                enabled = !isBulkExporting && !isPastingAdjustments && copiedAdjustmentsJson != null,
                                onClick = {
                                    val json = copiedAdjustmentsJson ?: return@IconButton
                                    val orderedIds = selectedItems.map { it.projectId }
                                    if (orderedIds.isEmpty()) return@IconButton
                                    val maskInfo = parseAiMaskCopyInfo(json)
                                    pendingPasteIds = orderedIds
                                    pendingPasteAdjustmentsJson = json
                                    pendingPasteEnvironmentCategories = maskInfo.environmentCategories
                                    pendingPasteHasSubjectMasks = maskInfo.hasAiSubject
                                    pendingPasteOpenTarget = selectedItems.firstOrNull()
                                    pendingRunEnvironmentMasks = false
                                    when {
                                        maskInfo.hasAiEnvironment -> showEnvironmentMaskDialog = true
                                        maskInfo.hasAiSubject -> showSubjectMaskDialog = true
                                        else -> launchPaste(regenerateEnvironmentMasks = false, openMasksAfterPaste = false)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste adjustments")
                            }
                        }
                    }

                    HorizontalFloatingToolbar(
                        expanded = true,
                        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            toolbarContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        floatingActionButton = {
                            FloatingToolbarDefaults.StandardFloatingActionButton(
                                onClick = {
                                    if (!isBulkExporting) {
                                        startBulkExport(selectedIds.toList())
                                    }
                                },
                                containerColor =
                                    if (isBulkExporting) MaterialTheme.colorScheme.surfaceContainerHighest
                                    else MaterialTheme.colorScheme.primaryContainer,
                                contentColor =
                                    if (isBulkExporting) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = "Export Selection")
                            }
                        }
                    ) {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }

                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        VerticalDivider(
                            modifier = Modifier
                                .height(16.dp)
                                .padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        IconButton(
                            enabled = !isBulkExporting,
                            onClick = {
                                val next = when (uniformRating) {
                                    null -> 1
                                    5 -> 0
                                    else -> (uniformRating + 1).coerceIn(0, 5)
                                }
                                onRatingChangeMany(selectedIds.toList(), next)
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor =
                                    if ((uniformRating ?: 0) > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = if ((uniformRating ?: 0) > 0) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Cycle rating"
                            )
                        }

                        IconButton(
                            enabled = !isBulkExporting && !isPastingAdjustments,
                            onClick = { showDeleteDialog = true }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selection")
                        }

                        if (selectedIds.size == 1) {
                            IconButton(
                                enabled = !isBulkExporting && !isPastingAdjustments,
                                onClick = { showSelectionInfo = true }
                            ) {
                                Icon(Icons.Default.Info, contentDescription = "Selection info")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selectedIds.toList()
                        showDeleteDialog = false
                        showSelectionInfo = false
                        selectedIds = emptySet()
                        if (ids.isNotEmpty()) onDeleteMany(ids)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
            title = { Text("Delete ${selectedIds.size} item(s)?") },
            text = { Text("This will remove the RAW and all edits from this device.") }
        )
    }

    if (showSelectionInfo && selectionInfoTarget != null) {
        LaunchedEffect(showSelectionInfo, selectionInfoTarget.projectId) {
            if (!showSelectionInfo) return@LaunchedEffect
            selectionInfoMetadataJson = null
            val rawBytes = withContext(Dispatchers.IO) { storage.loadRawBytes(selectionInfoTarget.projectId) }
            if (rawBytes == null) {
                selectionInfoMetadataJson = ""
                return@LaunchedEffect
            }
            selectionInfoMetadataJson = withContext(metadataDispatcher) {
                val handle = runCatching { LibRawDecoder.createSession(rawBytes) }.getOrDefault(0L)
                if (handle == 0L) return@withContext ""
                try {
                    runCatching { LibRawDecoder.getMetadataJsonFromSession(handle) }.getOrDefault("")
                } finally {
                    LibRawDecoder.releaseSession(handle)
                }
            }
        }

        val tags = selectionInfoTarget.tags
        val pairs = remember(selectionInfoMetadataJson) {
            val json = selectionInfoMetadataJson ?: return@remember emptyList()
            if (json.isBlank()) return@remember emptyList()
            runCatching {
                val obj = JSONObject(json)
                listOf(
                    "Make" to obj.optString("make"),
                    "Model" to obj.optString("model"),
                    "Lens" to obj.optString("lens"),
                    "ISO" to obj.optString("iso"),
                    "Exposure" to obj.optString("exposureTime"),
                    "Aperture" to obj.optString("fNumber"),
                    "Focal Length" to obj.optString("focalLength"),
                    "Date" to obj.optString("dateTimeOriginal"),
                ).filter { it.second.isNotBlank() && it.second != "null" }
            }.getOrDefault(emptyList())
        }

        AlertDialog(
            onDismissRequest = { showSelectionInfo = false },
            confirmButton = {
                TextButton(onClick = { showSelectionInfo = false }) { Text("Close") }
            },
            title = { Text("Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tags", style = MaterialTheme.typography.titleSmall)
                        if (tags.isEmpty()) {
                            Text(
                                text = "No tags yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = tags.take(25).joinToString(" \u2022 "),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Metadata", style = MaterialTheme.typography.titleSmall)
                        when {
                            selectionInfoMetadataJson == null -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LoadingIndicator(
                                        modifier = Modifier.height(18.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Reading metadata\u2026")
                                }
                            }

                            pairs.isEmpty() -> {
                                Text(
                                    text = "No metadata available.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            else -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pairs.forEach { (k, v) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(v, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    } else if (showSelectionInfo && selectionInfoTarget == null) {
        showSelectionInfo = false
    }

    if (showEnvironmentMaskDialog) {
        val count = pendingPasteIds.size
        val message =
            if (count == 1) {
                "These adjustments include environment masks. Regenerate them now?"
            } else {
                "These adjustments include environment masks. Regenerate them for $count images?"
            }
        AlertDialog(
            onDismissRequest = {
                showEnvironmentMaskDialog = false
                pendingRunEnvironmentMasks = false
                if (pendingPasteHasSubjectMasks) showSubjectMaskDialog = true
                else launchPaste(regenerateEnvironmentMasks = false, openMasksAfterPaste = false)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRunEnvironmentMasks = true
                        showEnvironmentMaskDialog = false
                        if (pendingPasteHasSubjectMasks) showSubjectMaskDialog = true
                        else launchPaste(regenerateEnvironmentMasks = true, openMasksAfterPaste = false)
                    }
                ) { Text("Run") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingRunEnvironmentMasks = false
                        showEnvironmentMaskDialog = false
                        if (pendingPasteHasSubjectMasks) showSubjectMaskDialog = true
                        else launchPaste(regenerateEnvironmentMasks = false, openMasksAfterPaste = false)
                    }
                ) { Text("Skip") }
            },
            title = { Text("Regenerate environment masks?") },
            text = { Text(message) }
        )
    }

    if (showSubjectMaskDialog) {
        val count = pendingPasteIds.size
        val message =
            if (count <= 1) {
                "These adjustments include a subject mask. Open the editor in the Masking tab to redo it?"
            } else {
                "These adjustments include subject masks. Open the editor in the Masking tab for the first selected image to redo them?"
            }
        AlertDialog(
            onDismissRequest = {
                showSubjectMaskDialog = false
                launchPaste(regenerateEnvironmentMasks = pendingRunEnvironmentMasks, openMasksAfterPaste = false)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSubjectMaskDialog = false
                        launchPaste(regenerateEnvironmentMasks = pendingRunEnvironmentMasks, openMasksAfterPaste = true)
                    }
                ) { Text("Open masks") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSubjectMaskDialog = false
                        launchPaste(regenerateEnvironmentMasks = pendingRunEnvironmentMasks, openMasksAfterPaste = false)
                    }
                ) { Text("Not now") }
            },
            title = { Text("Redo subject masks?") },
            text = { Text(message) }
        )
    }

    if (pasteProgressMessage != null) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text(pasteProgressMessage ?: "") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(
                        modifier = Modifier.height(18.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Please wait...")
                }
            }
        )
    }
}
