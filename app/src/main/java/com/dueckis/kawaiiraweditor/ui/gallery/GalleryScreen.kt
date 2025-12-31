@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.dueckis.kawaiiraweditor.ui.gallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
import com.dueckis.kawaiiraweditor.data.decoding.decodePreviewBytesForTagging
import com.dueckis.kawaiiraweditor.data.media.decodeToBitmap
import com.dueckis.kawaiiraweditor.data.media.displayNameForUri
import com.dueckis.kawaiiraweditor.data.media.parseRawMetadataForSearch
import com.dueckis.kawaiiraweditor.data.media.saveJpegToPictures
import com.dueckis.kawaiiraweditor.data.model.GalleryItem
import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder
import com.dueckis.kawaiiraweditor.data.storage.ProjectStorage
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryScreen(
    items: List<GalleryItem>,
    tagger: ClipAutoTagger,
    lowQualityPreviewEnabled: Boolean,
    automaticTaggingEnabled: Boolean,
    isTaggingInFlight: (String) -> Boolean,
    onTaggingInFlightChange: (String, Boolean) -> Unit,
    tagProgressFor: (String) -> Float?,
    onTagProgressChange: (String, Float) -> Unit,
    onMetadataChanged: (String, Map<String, String>) -> Unit,
    onOpenItem: (GalleryItem) -> Unit,
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

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // --- 1. REUSABLE IMPORT LOGIC ---
    suspend fun importUri(uri: Uri) {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        if (bytes != null) {
            val name = displayNameForUri(context, uri)
            val projectId = withContext(Dispatchers.IO) {
                storage.importRawFile(name, bytes)
            }
            val item = GalleryItem(
                projectId = projectId,
                fileName = name,
                thumbnail = null,
                tags = emptyList(),
                rawMetadata = emptyMap()
            )
            onAddClick(item)

            // Start background processing (thumbnails, tagging, metadata)
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
                    maybeRequestNotificationPermission()
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
    }

    // --- 2. HANDLE INTENTS (Open With / Share) ---
    val activity = context as? Activity
    val intent = activity?.intent

    LaunchedEffect(intent) {
        if (intent == null) return@LaunchedEffect

        val action = intent.action
        val type = intent.type
        val urisToImport = mutableListOf<Uri>()

        // Case A: "Open with" (Single file via Data)
        if (Intent.ACTION_VIEW == action && intent.data != null) {
            urisToImport.add(intent.data!!)
        }
        // Case B: "Share" (Single image)
        else if (Intent.ACTION_SEND == action && type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
                urisToImport.add(it)
            }
        }
        // Case C: "Share Multiple"
        else if (Intent.ACTION_SEND_MULTIPLE == action && type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.let { list ->
                list.filterIsInstance<Uri>().forEach { urisToImport.add(it) }
            }
        }

        if (urisToImport.isNotEmpty()) {
            urisToImport.forEach { uri ->
                importUri(uri)
            }
            // Clear the action so we don't re-import on rotation
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

    var queryText by rememberSaveable { mutableStateOf("") }
    val queryLower = remember(queryText) { queryText.trim().lowercase(Locale.US) }

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

    val filteredItems = remember(items, queryLower) {
        if (queryLower.isBlank()) items else items.filter(::matchesQuery)
    }

    // --- 3. UPDATED FILE PICKER TO USE SHARED LOGIC ---
    val pickRaw = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            importUri(uri)
        }
    }

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isBulkExporting by remember { mutableStateOf(false) }
    var isPastingAdjustments by remember { mutableStateOf(false) }
    var copiedAdjustmentsJson by remember { mutableStateOf<String?>(null) }
    var bulkExportDone by remember { mutableIntStateOf(0) }
    var bulkExportTotal by remember { mutableIntStateOf(0) }
    var bulkExportStatus by remember { mutableStateOf<String?>(null) }
    val bulkExportProgressAnim = remember { Animatable(0f) }
    val selectedItems = remember(items, selectedIds) { items.filter { it.projectId in selectedIds } }
    val uniformRating = remember(selectedItems) {
        selectedItems.map { it.rating }.distinct().singleOrNull()
    }

    var showSelectionInfo by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectionInfoMetadataJson by remember { mutableStateOf<String?>(null) }
    val selectionInfoTarget = remember(selectedItems) { selectedItems.singleOrNull() }
    val metadataDispatcher = remember { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
    DisposableEffect(metadataDispatcher) {
        onDispose { metadataDispatcher.close() }
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

    fun startPasteAdjustments(projectIds: List<String>, adjustmentsJson: String) {
        if (isBulkExporting || isPastingAdjustments) return
        if (projectIds.isEmpty()) return
        isPastingAdjustments = true

        coroutineScope.launch {
            var successCount = 0
            var failureCount = 0

            for (projectId in projectIds) {
                val rawBytes = withContext(Dispatchers.IO) { storage.loadRawBytes(projectId) }
                if (rawBytes == null) {
                    failureCount++
                    continue
                }

                withContext(Dispatchers.IO) { storage.saveAdjustments(projectId, adjustmentsJson) }

                val previewBytes = withContext(Dispatchers.Default) {
                    runCatching { LibRawDecoder.decode(rawBytes, adjustmentsJson) }.getOrNull()
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

            isPastingAdjustments = false
            onRequestRefresh()
            val msg =
                when {
                    failureCount == 0 -> "Pasted adjustments to $successCount image(s)."
                    successCount == 0 -> "Paste failed."
                    else -> "Pasted to $successCount image(s), $failureCount failed."
                }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(bulkExportStatus) {
        if (bulkExportStatus == null) return@LaunchedEffect
        Toast.makeText(context, bulkExportStatus, Toast.LENGTH_SHORT).show()
        delay(2500)
        bulkExportStatus = null
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
                isExecutingSearch = false // Reset flag, but don't clear text
            } else {
                queryText = "" // User cancelled, so clear text
            }
            focusManager.clearFocus()
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
                                .fillMaxWidth()
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
                            val showPopularTags = queryText.isBlank() && topTags.isNotEmpty()
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
                                val matches = remember(queryLower, items) {
                                    items.filter(::matchesQuery).take(10)
                                }
                                Column(Modifier.verticalScroll(rememberScrollState())) {
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
        ) {
            val gridBottomPadding = if (selectedIds.isNotEmpty()) 128.dp else 88.dp

            Column(modifier = Modifier.fillMaxSize()) {
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
                        items(filteredItems.size) { index ->
                            val item = filteredItems[index]
                            val isSelected = item.projectId in selectedIds
                            GalleryItemCard(
                                item = item,
                                selected = isSelected,
                                isProcessing = isTaggingInFlight(item.projectId),
                                automaticTaggingEnabled = automaticTaggingEnabled,
                                processingProgress = tagProgressFor(item.projectId),
                                onClick = {
                                    if (isBulkExporting) return@GalleryItemCard
                                    if (selectedIds.isEmpty()) {
                                        onOpenItem(item)
                                    } else {
                                        selectedIds =
                                            if (isSelected) selectedIds - item.projectId else selectedIds + item.projectId
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
            }

            // Multi-selection Floating Toolbar
            AnimatedVisibility(
                visible = selectedIds.isNotEmpty(),
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
                                    startPasteAdjustments(selectedIds.toList(), json)
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
}