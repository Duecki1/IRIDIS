@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.dueckis.kawaiiraweditor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import com.dueckis.kawaiiraweditor.data.decoding.decodePreviewBytesForTagging
import com.dueckis.kawaiiraweditor.data.model.GalleryItem
import com.dueckis.kawaiiraweditor.data.model.Screen
import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder
import com.dueckis.kawaiiraweditor.data.preferences.AppPreferences
import com.dueckis.kawaiiraweditor.data.storage.ProjectStorage
import com.dueckis.kawaiiraweditor.data.media.decodeToBitmap
import com.dueckis.kawaiiraweditor.data.media.parseRawMetadataForSearch
import com.dueckis.kawaiiraweditor.domain.ai.ClipAutoTagger
import com.dueckis.kawaiiraweditor.ui.editor.EditorScreen
import com.dueckis.kawaiiraweditor.ui.gallery.GalleryScreen
import com.dueckis.kawaiiraweditor.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun KawaiiApp() {
    val context = LocalContext.current
    val storage = remember { ProjectStorage(context) }
    val appPreferences = remember(context) { AppPreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    val tagger = remember { ClipAutoTagger(context) }
    val metadataDispatcher = remember { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
    DisposableEffect(metadataDispatcher) {
        onDispose { metadataDispatcher.close() }
    }

    var currentScreen by remember { mutableStateOf(Screen.Gallery) }
    var galleryItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var selectedItem by remember { mutableStateOf<GalleryItem?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var editorDismissProgressTarget by remember { mutableFloatStateOf(0f) }
    val editorDismissProgress = remember { Animatable(0f) }
    var lowQualityPreviewEnabled by remember { mutableStateOf(appPreferences.isLowQualityPreviewEnabled()) }
    var automaticTaggingEnabled by remember { mutableStateOf(appPreferences.isAutomaticTaggingEnabled()) }
    var environmentMaskingEnabled by remember { mutableStateOf(appPreferences.isEnvironmentMaskingEnabled()) }

    BackHandler(enabled = currentScreen == Screen.Settings) {
        currentScreen = Screen.Gallery
    }

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

    val tagBackfillQueue = remember { Channel<String>(capacity = Channel.UNLIMITED) }
    val tagBackfillQueued = remember { mutableStateMapOf<String, Boolean>() }
    val tagBackfillInFlight = remember { mutableStateMapOf<String, Boolean>() }
    val tagProgressById = remember { mutableStateMapOf<String, Float>() }
    val metadataBackfillQueue = remember { Channel<String>(capacity = Channel.UNLIMITED) }
    val metadataBackfillQueued = remember { mutableStateMapOf<String, Boolean>() }
    val metadataBackfillInFlight = remember { mutableStateMapOf<String, Boolean>() }

    fun setTaggingInFlight(projectId: String, inFlight: Boolean) {
        if (inFlight) {
            tagBackfillInFlight[projectId] = true
            if (tagProgressById[projectId] == null) tagProgressById[projectId] = 0f
        } else {
            tagBackfillInFlight.remove(projectId)
            tagProgressById.remove(projectId)
        }
    }

    fun isTaggingInFlight(projectId: String): Boolean = tagBackfillInFlight[projectId] == true
    fun tagProgressFor(projectId: String): Float? = tagProgressById[projectId]
    fun setTagProgress(projectId: String, progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        coroutineScope.launch {
            if (!isTaggingInFlight(projectId)) return@launch
            tagProgressById[projectId] = clamped
        }
    }

    fun setMetadataInFlight(projectId: String, inFlight: Boolean) {
        if (inFlight) {
            metadataBackfillInFlight[projectId] = true
        } else {
            metadataBackfillInFlight.remove(projectId)
        }
    }

    fun isMetadataInFlight(projectId: String): Boolean = metadataBackfillInFlight[projectId] == true

    suspend fun computeAndPersistRawMetadata(projectId: String, rawBytes: ByteArray) {
        val json = withContext(metadataDispatcher) {
            val handle = runCatching { LibRawDecoder.createSession(rawBytes) }.getOrDefault(0L)
            if (handle == 0L) return@withContext ""
            try {
                runCatching { LibRawDecoder.getMetadataJsonFromSession(handle) }.getOrDefault("")
            } finally {
                LibRawDecoder.releaseSession(handle)
            }
        }
        val map = parseRawMetadataForSearch(json)
        if (map.isEmpty()) return
        withContext(Dispatchers.IO) { storage.setRawMetadata(projectId, map) }
        galleryItems =
            galleryItems.map { item -> if (item.projectId == projectId) item.copy(rawMetadata = map) else item }
    }

    LaunchedEffect(Unit) {
        if (automaticTaggingEnabled) {
            for (projectId in tagBackfillQueue) {
                tagBackfillQueued.remove(projectId)
                setTaggingInFlight(projectId, true)
                try {
                    val projectMeta = withContext(Dispatchers.IO) {
                        storage.getAllProjects().firstOrNull { it.id == projectId }
                    } ?: continue
                    if (!projectMeta.tags.isNullOrEmpty() && !projectMeta.rawMetadata.isNullOrEmpty()) continue

                    val rawBytes = withContext(Dispatchers.IO) { storage.loadRawBytes(projectId) } ?: continue

                    if (projectMeta.rawMetadata.isNullOrEmpty()) {
                        runCatching { computeAndPersistRawMetadata(projectId, rawBytes) }
                    }

                    val tags = withContext(Dispatchers.Default) {
                        runCatching {
                            val previewBytes = decodePreviewBytesForTagging(rawBytes, lowQualityPreviewEnabled)
                            val bmp = previewBytes?.decodeToBitmap()
                            if (bmp == null) emptyList()
                            else tagger.generateTags(bmp, onProgress = { p -> setTagProgress(projectId, p) })
                        }.getOrDefault(emptyList())
                    }
                    if (tags.isEmpty()) continue
                    withContext(Dispatchers.IO) { storage.setTags(projectId, tags) }
                    galleryItems =
                        galleryItems.map { item -> if (item.projectId == projectId) item.copy(tags = tags) else item }
                } finally {
                    setTaggingInFlight(projectId, false)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        for (projectId in metadataBackfillQueue) {
            metadataBackfillQueued.remove(projectId)
            setMetadataInFlight(projectId, true)
            try {
                val projectMeta = withContext(Dispatchers.IO) {
                    storage.getAllProjects().firstOrNull { it.id == projectId }
                } ?: continue
                if (!projectMeta.rawMetadata.isNullOrEmpty()) continue

                val rawBytes = withContext(Dispatchers.IO) { storage.loadRawBytes(projectId) } ?: continue
                runCatching { computeAndPersistRawMetadata(projectId, rawBytes) }
            } finally {
                setMetadataInFlight(projectId, false)
            }
        }
    }

    LaunchedEffect(refreshTrigger) {
        val projects = withContext(Dispatchers.IO) { storage.getAllProjects() }
        galleryItems = withContext(Dispatchers.IO) {
            projects.map { metadata ->
                val thumbnailBytes = storage.loadThumbnail(metadata.id)
                val thumbnail = thumbnailBytes?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                GalleryItem(
                    projectId = metadata.id,
                    fileName = metadata.fileName,
                    thumbnail = thumbnail,
                    rating = metadata.rating,
                    tags = metadata.tags ?: emptyList(),
                    rawMetadata = metadata.rawMetadata ?: emptyMap()
                )
            }
        }

        val missingTagIds = projects.filter { it.tags.isNullOrEmpty() }.map { it.id }
        if (missingTagIds.isNotEmpty() && automaticTaggingEnabled) {
            maybeRequestNotificationPermission()
        }
        missingTagIds.forEach { id ->
            if (!isTaggingInFlight(id) && tagBackfillQueued[id] != true) {
                tagBackfillQueued[id] = true
                tagBackfillQueue.trySend(id)
            }
        }

        val missingMetadataIds = projects.filter { it.rawMetadata.isNullOrEmpty() }.map { it.id }
        missingMetadataIds.forEach { id ->
            if (!isMetadataInFlight(id) && metadataBackfillQueued[id] != true) {
                metadataBackfillQueued[id] = true
                metadataBackfillQueue.trySend(id)
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { editorDismissProgressTarget.coerceIn(0f, 1f) }.collect { target ->
            editorDismissProgress.snapTo(target)
        }
    }

    fun requestExitEditor(animated: Boolean) {
        if (currentScreen != Screen.Editor) return
        coroutineScope.launch {
            if (animated) {
                editorDismissProgressTarget = editorDismissProgress.value
                editorDismissProgress.animateTo(
                    1f,
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                )
            } else {
                editorDismissProgress.snapTo(1f)
            }
            currentScreen = Screen.Gallery
            selectedItem = null
            refreshTrigger++
            editorDismissProgressTarget = 0f
            editorDismissProgress.snapTo(0f)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx().coerceAtLeast(1f) }
            val editorVisible = currentScreen == Screen.Editor && selectedItem != null
            val dismissProgress = if (editorVisible) editorDismissProgress.value.coerceIn(0f, 1f) else 1f
            val editorTranslationPx = dismissProgress * widthPx

            Box(modifier = Modifier.fillMaxSize()) {
                GalleryScreen(
                    items = galleryItems,
                    tagger = tagger,
                    lowQualityPreviewEnabled = lowQualityPreviewEnabled,
                    automaticTaggingEnabled = automaticTaggingEnabled,
                    isTaggingInFlight = ::isTaggingInFlight,
                    onTaggingInFlightChange = ::setTaggingInFlight,
                    tagProgressFor = ::tagProgressFor,
                    onTagProgressChange = ::setTagProgress,
                    onMetadataChanged = { projectId, rawMetadata ->
                        galleryItems =
                            galleryItems.map { item ->
                                if (item.projectId == projectId) item.copy(rawMetadata = rawMetadata) else item
                            }
                    },
                    onOpenItem = { item ->
                        if (currentScreen == Screen.Editor) return@GalleryScreen
                        coroutineScope.launch {
                            editorDismissProgressTarget = 0f
                            editorDismissProgress.snapTo(1f)
                            selectedItem = item
                            currentScreen = Screen.Editor
                            editorDismissProgress.animateTo(
                                0f,
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                            )
                        }
                    },
                    onAddClick = { newItem ->
                        galleryItems = galleryItems + newItem
                    },
                    onThumbnailReady = { projectId, thumbnail ->
                        galleryItems =
                            galleryItems.map { item ->
                                if (item.projectId == projectId) item.copy(thumbnail = thumbnail) else item
                            }
                    },
                    onTagsChanged = { projectId, tags ->
                        galleryItems =
                            galleryItems.map { item -> if (item.projectId == projectId) item.copy(tags = tags) else item }
                    },
                    onRatingChangeMany = { projectIds, rating ->
                        coroutineScope.launch {
                            val ids = projectIds.toSet()
                            withContext(Dispatchers.IO) {
                                ids.forEach { id -> storage.setRating(id, rating) }
                            }
                            galleryItems = galleryItems.map { item ->
                                if (item.projectId !in ids) item else item.copy(rating = rating.coerceIn(0, 5))
                            }
                        }
                    },
                    onDeleteMany = { projectIds ->
                        coroutineScope.launch {
                            val ids = projectIds.toSet()
                            withContext(Dispatchers.IO) {
                                ids.forEach { id -> storage.deleteProject(id) }
                            }
                            galleryItems = galleryItems.filterNot { it.projectId in ids }
                        }
                    },
                    onOpenSettings = { if (currentScreen == Screen.Gallery) currentScreen = Screen.Settings },
                    onRequestRefresh = { refreshTrigger++ }
                )

                if (editorVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(editorVisible) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                    )
                }

                if (editorVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(translationX = editorTranslationPx)
                    ) {
                        EditorScreen(
                            galleryItem = selectedItem,
                            lowQualityPreviewEnabled = lowQualityPreviewEnabled,
                            environmentMaskingEnabled = environmentMaskingEnabled,
                            onBackClick = { requestExitEditor(animated = true) },
                            onPredictiveBackProgress = { progress ->
                                editorDismissProgressTarget = progress.coerceIn(0f, 1f)
                            },
                            onPredictiveBackCancelled = {
                                coroutineScope.launch {
                                    editorDismissProgressTarget = editorDismissProgress.value
                                    editorDismissProgress.animateTo(
                                        0f,
                                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                    )
                                    editorDismissProgressTarget = 0f
                                }
                            },
                            onPredictiveBackCommitted = {
                                requestExitEditor(animated = true)
                            }
                        )
                    }
                }

                if (currentScreen == Screen.Settings) {
                    SettingsScreen(
                        lowQualityPreviewEnabled = lowQualityPreviewEnabled,
                        automaticTaggingEnabled = automaticTaggingEnabled,
                        environmentMaskingEnabled = environmentMaskingEnabled,
                        onLowQualityPreviewEnabledChange = { enabled ->
                            lowQualityPreviewEnabled = enabled
                            appPreferences.setLowQualityPreviewEnabled(enabled)
                        },
                        onAutomaticTaggingEnabledChange = { enabled ->
                            automaticTaggingEnabled = enabled
                            appPreferences.setAutomaticTaggingEnabled(enabled)
                        },
                        onEnvironmentMaskingEnabledChange = { enabled ->
                            environmentMaskingEnabled = enabled
                            appPreferences.setEnvironmentMaskingEnabled(enabled)
                        },
                        onBackClick = { currentScreen = Screen.Gallery }
                    )
                }
            }
        }
    }
}
