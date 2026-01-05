@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.dueckis.kawaiiraweditor

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.widget.Toast
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
import com.dueckis.kawaiiraweditor.data.decoding.decodePreviewBytesForTagging
import com.dueckis.kawaiiraweditor.data.immich.ImmichLoginResult
import com.dueckis.kawaiiraweditor.data.immich.buildImmichCallbackUrl
import com.dueckis.kawaiiraweditor.data.immich.buildImmichMobileRedirectUri
import com.dueckis.kawaiiraweditor.data.immich.completeImmichOAuth
import com.dueckis.kawaiiraweditor.data.immich.loginImmich
import com.dueckis.kawaiiraweditor.data.immich.parseImmichOAuthParams
import com.dueckis.kawaiiraweditor.data.immich.startImmichOAuth
import com.dueckis.kawaiiraweditor.data.model.GalleryItem
import com.dueckis.kawaiiraweditor.data.model.EditorPanelTab
import com.dueckis.kawaiiraweditor.data.model.Screen
import com.dueckis.kawaiiraweditor.data.model.updateById
import com.dueckis.kawaiiraweditor.data.model.updateByIds
import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder
import com.dueckis.kawaiiraweditor.data.permissions.maybeRequestPostNotificationsPermission
import com.dueckis.kawaiiraweditor.data.preferences.AppPreferences
import com.dueckis.kawaiiraweditor.data.storage.ProjectStorage
import com.dueckis.kawaiiraweditor.data.media.decodeToBitmap
import com.dueckis.kawaiiraweditor.data.media.parseRawMetadataForSearch
import com.dueckis.kawaiiraweditor.domain.ai.ClipAutoTagger
import com.dueckis.kawaiiraweditor.ui.editor.EditorScreen
import com.dueckis.kawaiiraweditor.ui.gallery.GalleryScreen
import com.dueckis.kawaiiraweditor.ui.settings.SettingsScreen
import com.dueckis.kawaiiraweditor.ui.tutorial.TutorialDialog
import com.dueckis.kawaiiraweditor.ui.tutorial.TutorialOption
import com.dueckis.kawaiiraweditor.ui.tutorial.TutorialStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun KawaiiApp(
    pendingProjectToOpen: String? = null,
    pendingImmichOAuthRedirect: String? = null,
    onProjectOpened: () -> Unit = {},
    onImmichOAuthHandled: () -> Unit = {}
) {
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
    var editorInitialTab by remember { mutableStateOf<EditorPanelTab?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var editorDismissProgressTarget by remember { mutableFloatStateOf(0f) }
    val editorDismissProgress = remember { Animatable(0f) }
    var lowQualityPreviewEnabled by remember { mutableStateOf(appPreferences.isLowQualityPreviewEnabled()) }
    var automaticTaggingEnabled by remember { mutableStateOf(appPreferences.isAutomaticTaggingEnabled()) }
    var aiAssistanceLevel by remember { mutableStateOf(appPreferences.getAiAssistanceLevel()) }
    var toneCurveProfileSwitcherEnabled by remember {
        mutableStateOf(appPreferences.isToneCurveProfileSwitcherEnabled())
    }
    var openEditorOnImportEnabled by remember { mutableStateOf(appPreferences.isOpenEditorOnImportEnabled()) }
    var immichServerUrl by remember { mutableStateOf(appPreferences.getImmichServerUrl()) }
    var immichAuthMode by remember { mutableStateOf(appPreferences.getImmichAuthMode()) }
    var immichLoginEmail by remember { mutableStateOf(appPreferences.getImmichLoginEmail()) }
    var immichAccessToken by remember { mutableStateOf(appPreferences.getImmichAccessToken()) }
    var immichApiKey by remember { mutableStateOf(appPreferences.getImmichApiKey()) }
    var immichDescriptionSyncEnabled by remember {
        mutableStateOf(appPreferences.isImmichDescriptionSyncEnabled())
    }

    val tutorialSteps = remember { buildTutorialSteps() }
    var tutorialCompleted by remember { mutableStateOf(appPreferences.isTutorialCompleted()) }
    var tutorialStepIndex by remember { mutableIntStateOf(0) }
    val tutorialAnswers = remember { mutableStateMapOf<String, String>() }

    appPreferences.ensureDefaultMaskRenameTagsSeeded()
    var maskRenameTags by remember { mutableStateOf(appPreferences.getMaskRenameTags()) }

    BackHandler(enabled = currentScreen == Screen.Settings) {
        currentScreen = Screen.Gallery
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun requestNotificationPermissionIfNeeded() {
        maybeRequestPostNotificationsPermission(context) { permission ->
            notificationPermissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(Unit) {
        requestNotificationPermissionIfNeeded()
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
        galleryItems = galleryItems.updateById(projectId) { item -> item.copy(rawMetadata = map) }
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
                    galleryItems = galleryItems.updateById(projectId) { item -> item.copy(tags = tags) }
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

    LaunchedEffect(pendingProjectToOpen, galleryItems) {
        if (pendingProjectToOpen != null && galleryItems.isNotEmpty()) {
            val target = galleryItems.firstOrNull { it.projectId == pendingProjectToOpen }
            if (target != null) {
                editorInitialTab = null
                selectedItem = target
                currentScreen = Screen.Editor
                editorDismissProgress.snapTo(0f)
                onProjectOpened()
            }
        }
    }

    LaunchedEffect(pendingImmichOAuthRedirect) {
        val redirectUrl = pendingImmichOAuthRedirect?.trim().orEmpty()
        if (redirectUrl.isBlank()) return@LaunchedEffect

        val server = immichServerUrl.trim()
        val state = appPreferences.getImmichOAuthState()
        val verifier = appPreferences.getImmichOAuthVerifier()
        val params = parseImmichOAuthParams(redirectUrl)
        var callbackUrl = ""
        var result: ImmichLoginResult? = null
        var errorMessage: String? = null

        if (server.isBlank()) {
            errorMessage = "Immich server URL is missing."
        } else if (state.isBlank() || verifier.isBlank()) {
            errorMessage = "Immich login expired. Try again."
        } else {
            val callbackState = params.state
            val callbackError = params.errorDescription ?: params.error
            val callbackCode = params.code
            if (!callbackError.isNullOrBlank()) {
                errorMessage = "Immich login failed: $callbackError"
            } else if (callbackCode.isNullOrBlank()) {
                errorMessage = "Immich login failed: missing authorization code."
            } else if (callbackState != null && callbackState != state) {
                errorMessage = "Immich login failed: state mismatch."
            } else {
                callbackUrl = buildImmichCallbackUrl(server, redirectUrl)
                result = completeImmichOAuth(server, callbackUrl, state, verifier)
                val token = result?.accessToken
                if (!token.isNullOrBlank()) {
                    immichAccessToken = token
                    appPreferences.setImmichAccessToken(token)
                    val email = result?.userEmail
                    if (!email.isNullOrBlank()) {
                        immichLoginEmail = email
                        appPreferences.setImmichLoginEmail(email)
                    }
                    Toast.makeText(context, "Logged in to Immich.", Toast.LENGTH_SHORT).show()
                } else {
                    errorMessage = result?.errorMessage ?: "Immich login failed."
                }
            }
        }

        appPreferences.clearImmichOAuthPending()
        onImmichOAuthHandled()
        if (errorMessage != null) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
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
                    rawMetadata = metadata.rawMetadata ?: emptyMap(),
                    createdAt = metadata.createdAt,
                    modifiedAt = metadata.modifiedAt,
                    immichAssetId = metadata.immichAssetId,
                    immichAlbumId = metadata.immichAlbumId,
                    editsUpdatedAtMs = metadata.editsUpdatedAtMs ?: metadata.modifiedAt,
                    immichSidecarUpdatedAtMs = metadata.immichSidecarUpdatedAtMs ?: 0L
                )
            }
        }

        val missingTagIds = projects.filter { it.tags.isNullOrEmpty() }.map { it.id }
        if (missingTagIds.isNotEmpty() && automaticTaggingEnabled) {
            requestNotificationPermissionIfNeeded()
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
            editorInitialTab = null
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
                        openEditorOnImportEnabled = openEditorOnImportEnabled,
                        immichDescriptionSyncEnabled = immichDescriptionSyncEnabled,
                        immichServerUrl = immichServerUrl,
                        immichAuthMode = immichAuthMode,
                        immichAccessToken = immichAccessToken,
                        immichApiKey = immichApiKey,
                    isTaggingInFlight = ::isTaggingInFlight,
                    onTaggingInFlightChange = ::setTaggingInFlight,
                    tagProgressFor = ::tagProgressFor,
                    onTagProgressChange = ::setTagProgress,
                    onMetadataChanged = { projectId, rawMetadata ->
                        galleryItems = galleryItems.updateById(projectId) { item ->
                            item.copy(rawMetadata = rawMetadata)
                        }
                    },
                    onOpenItem = { item, initialTab ->
                        if (currentScreen == Screen.Editor) return@GalleryScreen
                        coroutineScope.launch {
                            editorDismissProgressTarget = 0f
                            editorDismissProgress.snapTo(1f)
                            editorInitialTab = initialTab
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
                        galleryItems = galleryItems.updateById(projectId) { item ->
                            item.copy(thumbnail = thumbnail)
                        }
                    },
                    onTagsChanged = { projectId, tags ->
                        galleryItems = galleryItems.updateById(projectId) { item -> item.copy(tags = tags) }
                    },
                    onRatingChangeMany = { projectIds, rating ->
                        coroutineScope.launch {
                            val ids = projectIds.toSet()
                            withContext(Dispatchers.IO) {
                                ids.forEach { id -> storage.setRating(id, rating) }
                            }
                            galleryItems = galleryItems.updateByIds(ids) { item ->
                                item.copy(rating = rating.coerceIn(0, 5))
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
                            aiMaskingEnabled = aiAssistanceLevel != AppPreferences.AiAssistanceLevel.None,
                            toneCurveProfileSwitcherEnabled = toneCurveProfileSwitcherEnabled,
                            immichDescriptionSyncEnabled = immichDescriptionSyncEnabled,
                            initialPanelTab = editorInitialTab ?: EditorPanelTab.Adjustments,
                            maskRenameTags = maskRenameTags,
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
                        aiAssistanceLevel = aiAssistanceLevel,
                        toneCurveProfileSwitcherEnabled = toneCurveProfileSwitcherEnabled,
                        openEditorOnImportEnabled = openEditorOnImportEnabled,
                        maskRenameTags = maskRenameTags,
                        onMaskRenameTagsChange = { tags ->
                            maskRenameTags = tags
                            appPreferences.setMaskRenameTags(tags)
                        },
                        onLowQualityPreviewEnabledChange = { enabled ->
                            lowQualityPreviewEnabled = enabled
                            appPreferences.setLowQualityPreviewEnabled(enabled)
                        },
                        onAutomaticTaggingEnabledChange = { enabled ->
                            automaticTaggingEnabled = enabled
                            appPreferences.setAutomaticTaggingEnabled(enabled)
                        },
                        onAiAssistanceLevelChange = { level ->
                            aiAssistanceLevel = level
                            appPreferences.setAiAssistanceLevel(level)
                        },
                        onToneCurveProfileSwitcherEnabledChange = { enabled ->
                            toneCurveProfileSwitcherEnabled = enabled
                            appPreferences.setToneCurveProfileSwitcherEnabled(enabled)
                        },
                        onOpenEditorOnImportEnabledChange = { enabled ->
                            openEditorOnImportEnabled = enabled
                            appPreferences.setOpenEditorOnImportEnabled(enabled)
                        },
                        immichDescriptionSyncEnabled = immichDescriptionSyncEnabled,
                        onImmichDescriptionSyncEnabledChange = { enabled ->
                            immichDescriptionSyncEnabled = enabled
                            appPreferences.setImmichDescriptionSyncEnabled(enabled)
                        },
                        immichServerUrl = immichServerUrl,
                        immichAuthMode = immichAuthMode,
                        immichLoginEmail = immichLoginEmail,
                        immichAccessToken = immichAccessToken,
                        immichApiKey = immichApiKey,
                        onImmichServerUrlChange = { url ->
                            immichServerUrl = url
                            appPreferences.setImmichServerUrl(url)
                        },
                        onImmichAuthModeChange = { mode ->
                            immichAuthMode = mode
                            appPreferences.setImmichAuthMode(mode)
                        },
                        onImmichLoginEmailChange = { email ->
                            immichLoginEmail = email
                            appPreferences.setImmichLoginEmail(email)
                        },
                        onImmichAccessTokenChange = { token ->
                            immichAccessToken = token
                            appPreferences.setImmichAccessToken(token)
                        },
                        onImmichLogin = { serverUrl, email, password ->
                            loginImmich(serverUrl, email, password)
                        },
                        onImmichOAuthStart = { serverUrl ->
                            appPreferences.clearImmichOAuthPending()
                            val usedRedirect = if (serverUrl.isNotBlank()) {
                                "${serverUrl.trimEnd('/')}/api/oauth/mobile-redirect"
                            } else {
                                buildImmichMobileRedirectUri(serverUrl)
                            }
                            val result = startImmichOAuth(serverUrl, usedRedirect)
                            val state = result?.state
                            val verifier = result?.codeVerifier
                            if (!result?.authorizationUrl.isNullOrBlank() && !state.isNullOrBlank() && !verifier.isNullOrBlank()) {
                                appPreferences.setImmichOAuthPending(state, verifier)
                            }
                            result
                        },
                        onImmichApiKeyChange = { key ->
                            immichApiKey = key
                            appPreferences.setImmichApiKey(key)
                        },
                        onBackClick = { currentScreen = Screen.Gallery }
                    )
                }

                val activeTutorialStep = if (!tutorialCompleted) {
                    tutorialSteps.getOrNull(tutorialStepIndex)
                } else {
                    null
                }

                if (activeTutorialStep != null) {
                    if (activeTutorialStep.id == TUTORIAL_STEP_AI_USAGE && tutorialAnswers[activeTutorialStep.id] == null) {
                        tutorialAnswers[activeTutorialStep.id] = aiLevelToOptionId(aiAssistanceLevel)
                    }
                    val selectedOptionId = tutorialAnswers[activeTutorialStep.id]
                    TutorialDialog(
                        step = activeTutorialStep,
                        selectedOptionId = selectedOptionId,
                        continueEnabled = selectedOptionId != null || activeTutorialStep !is TutorialStep.MultipleChoice,
                        onOptionSelected = { optionId ->
                            tutorialAnswers[activeTutorialStep.id] = optionId
                            if (activeTutorialStep.id == TUTORIAL_STEP_AI_USAGE) {
                                val level = aiOptionIdToLevel(optionId)
                                aiAssistanceLevel = level
                                appPreferences.setAiAssistanceLevel(level)
                            }
                        },
                        onContinue = {
                            val nextIndex = tutorialStepIndex + 1
                            if (nextIndex >= tutorialSteps.size) {
                                appPreferences.setTutorialCompleted(true)
                                tutorialStepIndex = 0
                                tutorialCompleted = true
                                tutorialAnswers.clear()
                            } else {
                                tutorialStepIndex = nextIndex
                            }
                        },
                        onBack = if (tutorialStepIndex > 0) {
                            { tutorialStepIndex-- }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

private const val TUTORIAL_STEP_CREDITS = "tutorial_credits"
private const val TUTORIAL_STEP_AI_USAGE = "tutorial_ai_usage"
private const val OPTION_AI_NONE = "ai_none"
private const val OPTION_AI_SELECTIONS = "ai_selections"
private const val OPTION_AI_ALL = "ai_all"

private fun buildTutorialSteps(): List<TutorialStep> = listOf(
    TutorialStep.Info(
        id = TUTORIAL_STEP_CREDITS,
        title = "Welcome to Kawaii RAW Editor",
        body = listOf(
            "Kawaii RAW Editor builds on the open-source RapidRAW project.",
            "RAW decoding and processing is powered by the rawler library.",
            "Huge thanks to both communities for making this possible."
        ),
        continueLabel = "Next"
    ),
    TutorialStep.MultipleChoice(
        id = TUTORIAL_STEP_AI_USAGE,
        title = "Choose AI Assistance",
        body = listOf(
            "How much AI assistance would you like to enable by default?"
        ),
        options = listOf(
            TutorialOption(
                id = OPTION_AI_NONE,
                title = "None",
                description = "Disable AI-assisted features."
            ),
            TutorialOption(
                id = OPTION_AI_SELECTIONS,
                title = "Only for selections",
                description = "Allow AI selection helpers while keeping other tools manual."
            ),
            TutorialOption(
                id = OPTION_AI_ALL,
                title = "All",
                description = "Enable AI assistance wherever it is available."
            )
        ),
        continueLabel = "Save"
    )
)

private fun aiOptionIdToLevel(optionId: String): AppPreferences.AiAssistanceLevel = when (optionId) {
    OPTION_AI_NONE -> AppPreferences.AiAssistanceLevel.None
    OPTION_AI_SELECTIONS -> AppPreferences.AiAssistanceLevel.SelectionsOnly
    OPTION_AI_ALL -> AppPreferences.AiAssistanceLevel.All
    else -> AppPreferences.AiAssistanceLevel.All
}

private fun aiLevelToOptionId(level: AppPreferences.AiAssistanceLevel): String = when (level) {
    AppPreferences.AiAssistanceLevel.None -> OPTION_AI_NONE
    AppPreferences.AiAssistanceLevel.SelectionsOnly -> OPTION_AI_SELECTIONS
    AppPreferences.AiAssistanceLevel.All -> OPTION_AI_ALL
}
