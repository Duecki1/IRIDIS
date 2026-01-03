package com.dueckis.kawaiiraweditor.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dueckis.kawaiiraweditor.data.model.AdjustmentState
import com.dueckis.kawaiiraweditor.data.model.AiEnvironmentCategory
import com.dueckis.kawaiiraweditor.data.model.BrushLineState
import com.dueckis.kawaiiraweditor.data.model.BrushTool
import com.dueckis.kawaiiraweditor.data.model.CropState
import com.dueckis.kawaiiraweditor.data.model.CurvePointState
import com.dueckis.kawaiiraweditor.data.model.CurvesState
import com.dueckis.kawaiiraweditor.data.model.EditorPanelTab
import com.dueckis.kawaiiraweditor.data.model.GalleryItem
import com.dueckis.kawaiiraweditor.data.model.HslState
import com.dueckis.kawaiiraweditor.data.model.MaskHandle
import com.dueckis.kawaiiraweditor.data.model.MaskPoint
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.model.MaskTapMode
import com.dueckis.kawaiiraweditor.data.model.MaskTransformState
import com.dueckis.kawaiiraweditor.data.model.RadialMaskParametersState
import com.dueckis.kawaiiraweditor.data.model.RenderRequest
import com.dueckis.kawaiiraweditor.data.model.RenderTarget
import com.dueckis.kawaiiraweditor.data.model.SubMaskMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskState
import com.dueckis.kawaiiraweditor.data.model.SubMaskType
import com.dueckis.kawaiiraweditor.data.model.toAdjustmentState
import com.dueckis.kawaiiraweditor.data.model.toMaskTransformState
import com.dueckis.kawaiiraweditor.data.media.decodeToBitmap
import com.dueckis.kawaiiraweditor.data.immich.ImmichConfig
import com.dueckis.kawaiiraweditor.data.immich.IridisSidecarDescription
import com.dueckis.kawaiiraweditor.data.immich.fetchImmichAssetInfo
import com.dueckis.kawaiiraweditor.data.immich.updateImmichAssetDescription
import com.dueckis.kawaiiraweditor.data.native.LibRawDecoder
import com.dueckis.kawaiiraweditor.data.preferences.AppPreferences
import com.dueckis.kawaiiraweditor.data.storage.ProjectStorage
import com.dueckis.kawaiiraweditor.domain.HistogramData
import com.dueckis.kawaiiraweditor.domain.HistogramUtils
import com.dueckis.kawaiiraweditor.domain.ai.AiEnvironmentMaskGenerator
import com.dueckis.kawaiiraweditor.domain.ai.AiSubjectMaskGenerator
import com.dueckis.kawaiiraweditor.domain.ai.ModelInfo
import com.dueckis.kawaiiraweditor.domain.ai.NormalizedPoint
import com.dueckis.kawaiiraweditor.domain.ai.U2NetOnnxSegmenter
import com.dueckis.kawaiiraweditor.domain.ai.missingModels
import com.dueckis.kawaiiraweditor.domain.editor.EditorHistoryEntry
import com.dueckis.kawaiiraweditor.ui.editor.components.ExportButton
import com.dueckis.kawaiiraweditor.ui.editor.controls.AutoCropParams
import com.dueckis.kawaiiraweditor.ui.editor.controls.EditorControlsContent
import com.dueckis.kawaiiraweditor.ui.editor.controls.computeMaxCropNormalized
import com.dueckis.kawaiiraweditor.ui.editor.masking.newSubMaskState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class PendingModelDownloadAction {
    SubjectMask,
    EnvironmentMask,
    EnvironmentDetect,
    SubjectMaskSelect
}

private data class PendingSubjectMaskCreation(
    val createNewMask: Boolean,
    val targetMaskId: String?,
    val mode: SubMaskMode,
    val type: SubMaskType
)

@OptIn(kotlinx.coroutines.FlowPreview::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
internal fun EditorScreen(
    galleryItem: GalleryItem?,
    lowQualityPreviewEnabled: Boolean,
    environmentMaskingEnabled: Boolean,
    immichDescriptionSyncEnabled: Boolean,
    initialPanelTab: EditorPanelTab = EditorPanelTab.Adjustments,
    maskRenameTags: List<String> = emptyList(),
    onBackClick: () -> Unit,
    onPredictiveBackProgress: (Float) -> Unit,
    onPredictiveBackCancelled: () -> Unit,
    onPredictiveBackCommitted: () -> Unit
) {
    val logTag = "EditorScreen"
    if (galleryItem == null) {
        onBackClick()
        return
    }

    val context = LocalContext.current
    val storage = remember { ProjectStorage(context) }
    val appPreferences = remember(context) { AppPreferences(context) }
    data class ImmichSidecarSyncRequest(
        val updatedAtMs: Long,
        val editsJson: String,
        val parentRevisionId: String?,
        val revisionId: String
    )
    val immichSidecarSyncRequests =
        remember(galleryItem.projectId) { Channel<ImmichSidecarSyncRequest>(capacity = Channel.CONFLATED) }
    var lastImmichSidecarSyncRequest by remember(galleryItem.projectId) { mutableStateOf<ImmichSidecarSyncRequest?>(null) }
    var isImmichSidecarSyncing by remember(galleryItem.projectId) { mutableStateOf(false) }
    val immichSyncIdleMs = 30_000L
    val aiFeatherCache = remember(galleryItem.projectId) { LinkedHashMap<String, String>(8, 0.75f, true) }

    fun resolveImmichConfigOrNull(): ImmichConfig? {
        val server = appPreferences.getImmichServerUrl().trim()
        if (server.isBlank()) return null
        val authMode = appPreferences.getImmichAuthMode()
        val apiKey = appPreferences.getImmichApiKey().trim()
        val accessToken = appPreferences.getImmichAccessToken().trim()
        if (authMode == com.dueckis.kawaiiraweditor.data.immich.ImmichAuthMode.ApiKey && apiKey.isBlank()) return null
        if (authMode == com.dueckis.kawaiiraweditor.data.immich.ImmichAuthMode.Login && accessToken.isBlank()) return null
        return ImmichConfig(serverUrl = server, authMode = authMode, apiKey = apiKey, accessToken = accessToken)
    }

    suspend fun fetchRemoteIridisSidecarHistory(
        config: ImmichConfig,
        assetId: String
    ): List<IridisSidecarDescription.IridisSidecarHistoryEntry> {
        val info = fetchImmichAssetInfo(config, assetId) ?: return emptyList()
        val parsed = IridisSidecarDescription.parseHistory(info.description)
        return parsed?.entries.orEmpty()
    }

    suspend fun uploadIridisSidecarToImmich(
        request: ImmichSidecarSyncRequest,
        force: Boolean,
        showToastOnFailure: Boolean
    ): Boolean {
        if (!immichDescriptionSyncEnabled) return false
        if (isImmichSidecarSyncing) return false
        val config = resolveImmichConfigOrNull() ?: return false
        val originImmichAssetId = galleryItem.immichAssetId?.trim().takeUnless { it.isNullOrBlank() } ?: return false
        if (request.editsJson.isBlank()) return false
        if (request.updatedAtMs <= 0L) return false

        val alreadySyncedAtMs =
            withContext(Dispatchers.IO) { storage.getImmichSidecarUpdatedAtMs(galleryItem.projectId) }
        if (!force && request.updatedAtMs <= alreadySyncedAtMs) return true

        val revisionId = request.revisionId.ifBlank {
            IridisSidecarDescription.buildRevisionId(request.updatedAtMs, request.editsJson)
        }
        withContext(Dispatchers.IO) {
            storage.appendEditHistory(
                projectId = galleryItem.projectId,
                entry = ProjectStorage.EditHistoryEntry(
                    id = revisionId,
                    source = ProjectStorage.EditHistorySource.Local,
                    updatedAtMs = request.updatedAtMs,
                    editsJson = request.editsJson,
                    parentId = request.parentRevisionId
                )
            )
        }

        isImmichSidecarSyncing = true
        val info = fetchImmichAssetInfo(config, originImmichAssetId)
        val remoteHistory = IridisSidecarDescription.parseHistory(info?.description)
        val remoteEntries = remoteHistory?.entries.orEmpty()
        if (remoteEntries.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                storage.appendEditHistoryEntries(
                    projectId = galleryItem.projectId,
                    entries = remoteEntries.map {
                        ProjectStorage.EditHistoryEntry(
                            id = it.revisionId,
                            source = ProjectStorage.EditHistorySource.Immich,
                            updatedAtMs = it.updatedAtMs,
                            editsJson = it.editsJson,
                            parentId = it.parentRevisionId
                        )
                    }
                )
            }
        }
        if (remoteEntries.any { it.revisionId == revisionId }) {
            isImmichSidecarSyncing = false
            withContext(Dispatchers.IO) {
                storage.setImmichSidecarInfo(galleryItem.projectId, originImmichAssetId, request.updatedAtMs)
            }
            return true
        }

        val newDescription =
            IridisSidecarDescription.appendHistory(
                info?.description,
                request.editsJson,
                request.updatedAtMs,
                parentRevisionId = request.parentRevisionId,
                revisionId = revisionId
            )
        val result = updateImmichAssetDescription(config, originImmichAssetId, newDescription)
        isImmichSidecarSyncing = false

        if (result.assetId.isNullOrBlank()) {
            val msg = result.errorMessage ?: "Immich sidecar sync failed."
            Log.w(logTag, "Immich sidecar sync failed: $msg")
            if (showToastOnFailure) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
            return false
        }

        withContext(Dispatchers.IO) {
            storage.setImmichSidecarInfo(galleryItem.projectId, originImmichAssetId, request.updatedAtMs)
        }
        return true
    }

    var detectedAiEnvironmentCategories by remember { mutableStateOf<List<AiEnvironmentCategory>?>(null) }
    var isDetectingAiEnvironmentCategories by remember { mutableStateOf(false) }
    LaunchedEffect(galleryItem?.projectId) {
        detectedAiEnvironmentCategories = null
        isDetectingAiEnvironmentCategories = false
    }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var showEditTimelineDialog by remember(galleryItem.projectId) { mutableStateOf(false) }
    var editTimelineEntries by remember(galleryItem.projectId) {
        mutableStateOf<List<ProjectStorage.EditHistoryEntry>>(emptyList())
    }
    var lastSavedEditsJson by remember(galleryItem.projectId) { mutableStateOf<String?>(null) }
    var currentRevisionId by remember(galleryItem.projectId) { mutableStateOf<String?>(null) }
    var sessionRevisionId by remember(galleryItem.projectId) { mutableStateOf<String?>(null) }
    var sessionParentRevisionId by remember(galleryItem.projectId) { mutableStateOf<String?>(null) }
    var baseRevisionId by remember(galleryItem.projectId) { mutableStateOf<String?>(null) }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var sessionHandle by remember { mutableStateOf(0L) }
    var adjustments by remember { mutableStateOf(AdjustmentState()) }
    var masks by remember { mutableStateOf<List<MaskState>>(emptyList()) }
    val maskNumbers = remember { mutableStateMapOf<String, Int>() }

    data class ParsedEdits(
        val adjustments: AdjustmentState,
        val masks: List<MaskState>,
        val detectedAiEnvironmentCategories: List<AiEnvironmentCategory>?
    )

    fun parseEditsJson(raw: String): ParsedEdits? {
        if (raw.isBlank() || raw == "{}") return null
        return runCatching {
            val json = JSONObject(raw)
            val savedDetected = json.optJSONArray("aiEnvironmentDetectedCategories")
            val detected =
                savedDetected?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val rawId = arr.optString(i).orEmpty().trim()
                        if (rawId.isBlank()) null else AiEnvironmentCategory.fromId(rawId)
                    }.distinct()
                }?.takeIf { it.isNotEmpty() }

            fun parseCurvePoints(curvesObj: JSONObject?, key: String): List<CurvePointState> {
                val arr = curvesObj?.optJSONArray(key) ?: return com.dueckis.kawaiiraweditor.data.model.defaultCurvePoints()
                val points =
                    (0 until arr.length()).mapNotNull { idx ->
                        val pObj = arr.optJSONObject(idx) ?: return@mapNotNull null
                        CurvePointState(x = pObj.optDouble("x", 0.0).toFloat(), y = pObj.optDouble("y", 0.0).toFloat())
                    }
                return if (points.size >= 2) points else com.dueckis.kawaiiraweditor.data.model.defaultCurvePoints()
            }

            fun parseCurves(curvesObj: JSONObject?): CurvesState {
                return CurvesState(
                    luma = parseCurvePoints(curvesObj, "luma"),
                    red = parseCurvePoints(curvesObj, "red"),
                    green = parseCurvePoints(curvesObj, "green"),
                    blue = parseCurvePoints(curvesObj, "blue")
                )
            }

            fun parseHsl(obj: JSONObject?): HslState {
                if (obj == null) return HslState()
                fun parseHueSatLum(parent: JSONObject?, key: String): com.dueckis.kawaiiraweditor.data.model.HueSatLumState {
                    val o = parent?.optJSONObject(key) ?: return com.dueckis.kawaiiraweditor.data.model.HueSatLumState()
                    return com.dueckis.kawaiiraweditor.data.model.HueSatLumState(
                        hue = o.optDouble("hue", 0.0).toFloat(),
                        saturation = o.optDouble("saturation", 0.0).toFloat(),
                        luminance = o.optDouble("luminance", 0.0).toFloat()
                    )
                }
                return HslState(
                    reds = parseHueSatLum(obj, "reds"),
                    oranges = parseHueSatLum(obj, "oranges"),
                    yellows = parseHueSatLum(obj, "yellows"),
                    greens = parseHueSatLum(obj, "greens"),
                    aquas = parseHueSatLum(obj, "aquas"),
                    blues = parseHueSatLum(obj, "blues"),
                    purples = parseHueSatLum(obj, "purples"),
                    magentas = parseHueSatLum(obj, "magentas")
                )
            }

            fun parseColorGrading(obj: JSONObject?): com.dueckis.kawaiiraweditor.data.model.ColorGradingState {
                if (obj == null) return com.dueckis.kawaiiraweditor.data.model.ColorGradingState()
                fun parseHueSatLum(parent: JSONObject?, key: String): com.dueckis.kawaiiraweditor.data.model.HueSatLumState {
                    val o = parent?.optJSONObject(key) ?: return com.dueckis.kawaiiraweditor.data.model.HueSatLumState()
                    return com.dueckis.kawaiiraweditor.data.model.HueSatLumState(
                        hue = o.optDouble("hue", 0.0).toFloat(),
                        saturation = o.optDouble("saturation", 0.0).toFloat(),
                        luminance = o.optDouble("luminance", 0.0).toFloat()
                    )
                }
                return com.dueckis.kawaiiraweditor.data.model.ColorGradingState(
                    shadows = parseHueSatLum(obj, "shadows"),
                    midtones = parseHueSatLum(obj, "midtones"),
                    highlights = parseHueSatLum(obj, "highlights"),
                    blending = obj.optDouble("blending", 50.0).toFloat(),
                    balance = obj.optDouble("balance", 0.0).toFloat()
                )
            }

            val parsedCurves = parseCurves(json.optJSONObject("curves"))
            val parsedColorGrading = parseColorGrading(json.optJSONObject("colorGrading"))
            val parsedHsl = parseHsl(json.optJSONObject("hsl"))
            val parsedAspectRatio =
                if (json.has("aspectRatio") && !json.isNull("aspectRatio")) json.optDouble("aspectRatio", 0.0).toFloat() else null
            val parsedCrop =
                json.optJSONObject("crop")?.let { cropObj ->
                    CropState(
                        x = cropObj.optDouble("x", 0.0).toFloat(),
                        y = cropObj.optDouble("y", 0.0).toFloat(),
                        width = cropObj.optDouble("width", 1.0).toFloat(),
                        height = cropObj.optDouble("height", 1.0).toFloat()
                    ).normalized()
                }
            val parsedAdjustments =
                AdjustmentState(
                    rotation = json.optDouble("rotation", 0.0).toFloat(),
                    flipHorizontal = json.optBoolean("flipHorizontal", false),
                    flipVertical = json.optBoolean("flipVertical", false),
                    orientationSteps = json.optInt("orientationSteps", 0),
                    aspectRatio = parsedAspectRatio,
                    crop = parsedCrop,
                    exposure = json.optDouble("exposure", 0.0).toFloat(),
                    brightness = json.optDouble("brightness", 0.0).toFloat(),
                    contrast = json.optDouble("contrast", 0.0).toFloat(),
                    highlights = json.optDouble("highlights", 0.0).toFloat(),
                    shadows = json.optDouble("shadows", 0.0).toFloat(),
                    whites = json.optDouble("whites", 0.0).toFloat(),
                    blacks = json.optDouble("blacks", 0.0).toFloat(),
                    saturation = json.optDouble("saturation", 0.0).toFloat(),
                    temperature = json.optDouble("temperature", 0.0).toFloat(),
                    tint = json.optDouble("tint", 0.0).toFloat(),
                    vibrance = json.optDouble("vibrance", 0.0).toFloat(),
                    clarity = json.optDouble("clarity", 0.0).toFloat(),
                    dehaze = json.optDouble("dehaze", 0.0).toFloat(),
                    structure = json.optDouble("structure", 0.0).toFloat(),
                    centre = json.optDouble("centre", 0.0).toFloat(),
                    vignetteAmount = json.optDouble("vignetteAmount", 0.0).toFloat(),
                    vignetteMidpoint = json.optDouble("vignetteMidpoint", 50.0).toFloat(),
                    vignetteRoundness = json.optDouble("vignetteRoundness", 0.0).toFloat(),
                    vignetteFeather = json.optDouble("vignetteFeather", 50.0).toFloat(),
                    sharpness = json.optDouble("sharpness", 0.0).toFloat(),
                    lumaNoiseReduction = json.optDouble("lumaNoiseReduction", 0.0).toFloat(),
                    colorNoiseReduction = json.optDouble("colorNoiseReduction", 0.0).toFloat(),
                    chromaticAberrationRedCyan = json.optDouble("chromaticAberrationRedCyan", 0.0).toFloat(),
                    chromaticAberrationBlueYellow = json.optDouble("chromaticAberrationBlueYellow", 0.0).toFloat(),
                    toneMapper = json.optString("toneMapper", "basic"),
                    curves = parsedCurves,
                    colorGrading = parsedColorGrading,
                    hsl = parsedHsl
                )

            fun parseMaskTransform(obj: JSONObject?): MaskTransformState? {
                if (obj == null) return null
                val crop =
                    obj.optJSONObject("crop")?.let { cropObj ->
                        CropState(
                            x = cropObj.optDouble("x", 0.0).toFloat(),
                            y = cropObj.optDouble("y", 0.0).toFloat(),
                            width = cropObj.optDouble("width", 1.0).toFloat(),
                            height = cropObj.optDouble("height", 1.0).toFloat()
                        ).normalized()
                    }
                return MaskTransformState(
                    rotation = obj.optDouble("rotation", 0.0).toFloat(),
                    flipHorizontal = obj.optBoolean("flipHorizontal", false),
                    flipVertical = obj.optBoolean("flipVertical", false),
                    orientationSteps = obj.optInt("orientationSteps", 0),
                    crop = crop
                )
            }

            val masksArr = json.optJSONArray("masks") ?: JSONArray()
            val defaultTransform = parsedAdjustments.toMaskTransformState()
            val parsedMasks =
                (0 until masksArr.length()).mapNotNull { idx ->
                    val maskObj = masksArr.optJSONObject(idx) ?: return@mapNotNull null
                    val maskId = maskObj.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                    val maskAdjustmentsObj = maskObj.optJSONObject("adjustments") ?: JSONObject()
                    val maskCurves = parseCurves(maskAdjustmentsObj.optJSONObject("curves"))
                    val maskColorGrading = parseColorGrading(maskAdjustmentsObj.optJSONObject("colorGrading"))
                    val maskHsl = parseHsl(maskAdjustmentsObj.optJSONObject("hsl"))
                    val maskAdjustments =
                        AdjustmentState(
                            exposure = maskAdjustmentsObj.optDouble("exposure", 0.0).toFloat(),
                            brightness = maskAdjustmentsObj.optDouble("brightness", 0.0).toFloat(),
                            contrast = maskAdjustmentsObj.optDouble("contrast", 0.0).toFloat(),
                            highlights = maskAdjustmentsObj.optDouble("highlights", 0.0).toFloat(),
                            shadows = maskAdjustmentsObj.optDouble("shadows", 0.0).toFloat(),
                            whites = maskAdjustmentsObj.optDouble("whites", 0.0).toFloat(),
                            blacks = maskAdjustmentsObj.optDouble("blacks", 0.0).toFloat(),
                            saturation = maskAdjustmentsObj.optDouble("saturation", 0.0).toFloat(),
                            temperature = maskAdjustmentsObj.optDouble("temperature", 0.0).toFloat(),
                            tint = maskAdjustmentsObj.optDouble("tint", 0.0).toFloat(),
                            vibrance = maskAdjustmentsObj.optDouble("vibrance", 0.0).toFloat(),
                            clarity = maskAdjustmentsObj.optDouble("clarity", 0.0).toFloat(),
                            dehaze = maskAdjustmentsObj.optDouble("dehaze", 0.0).toFloat(),
                            structure = maskAdjustmentsObj.optDouble("structure", 0.0).toFloat(),
                            centre = maskAdjustmentsObj.optDouble("centre", 0.0).toFloat(),
                            vignetteAmount = maskAdjustmentsObj.optDouble("vignetteAmount", 0.0).toFloat(),
                            vignetteMidpoint = maskAdjustmentsObj.optDouble("vignetteMidpoint", 50.0).toFloat(),
                            vignetteRoundness = maskAdjustmentsObj.optDouble("vignetteRoundness", 0.0).toFloat(),
                            vignetteFeather = maskAdjustmentsObj.optDouble("vignetteFeather", 50.0).toFloat(),
                            sharpness = maskAdjustmentsObj.optDouble("sharpness", 0.0).toFloat(),
                            lumaNoiseReduction = maskAdjustmentsObj.optDouble("lumaNoiseReduction", 0.0).toFloat(),
                            colorNoiseReduction = maskAdjustmentsObj.optDouble("colorNoiseReduction", 0.0).toFloat(),
                            chromaticAberrationRedCyan = maskAdjustmentsObj.optDouble("chromaticAberrationRedCyan", 0.0).toFloat(),
                            chromaticAberrationBlueYellow = maskAdjustmentsObj.optDouble("chromaticAberrationBlueYellow", 0.0).toFloat(),
                            toneMapper = parsedAdjustments.toneMapper,
                            curves = maskCurves,
                            colorGrading = maskColorGrading,
                            hsl = maskHsl
                        )

                    val subMasksArr = maskObj.optJSONArray("subMasks") ?: JSONArray()
                    val subMasks =
                        (0 until subMasksArr.length()).mapNotNull { sIdx ->
                            val subObj = subMasksArr.optJSONObject(sIdx) ?: return@mapNotNull null
                            val subId = subObj.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                            val subType = subObj.optString("type", SubMaskType.Brush.id).lowercase(Locale.US)
                            val modeStr = subObj.optString("mode", "additive").lowercase(Locale.US)
                            val mode = if (modeStr == "subtractive") SubMaskMode.Subtractive else SubMaskMode.Additive
                            val paramsObj = subObj.optJSONObject("parameters") ?: JSONObject()
                            val visible = subObj.optBoolean("visible", true)

                            when (subType) {
                                SubMaskType.Radial.id ->
                                    SubMaskState(
                                        id = subId,
                                        type = SubMaskType.Radial.id,
                                        visible = visible,
                                        mode = mode,
                                        radial =
                                            RadialMaskParametersState(
                                                centerX = paramsObj.optDouble("centerX", 0.5).toFloat(),
                                                centerY = paramsObj.optDouble("centerY", 0.5).toFloat(),
                                                radiusX = paramsObj.optDouble("radiusX", 0.35).toFloat(),
                                                radiusY = paramsObj.optDouble("radiusY", 0.35).toFloat(),
                                                rotation = paramsObj.optDouble("rotation", 0.0).toFloat(),
                                                feather = paramsObj.optDouble("feather", 0.5).toFloat()
                                            )
                                    )

                                SubMaskType.AiSubject.id ->
                                    SubMaskState(
                                        id = subId,
                                        type = SubMaskType.AiSubject.id,
                                        visible = visible,
                                        mode = mode,
                                        aiSubject =
                                            com.dueckis.kawaiiraweditor.data.model.AiSubjectMaskParametersState(
                                                maskDataBase64 = paramsObj.optString("maskDataBase64").takeIf { it.isNotBlank() },
                                                softness = paramsObj.optDouble("softness", 0.25).toFloat().coerceIn(0f, 1f),
                                                feather = paramsObj.optDouble("feather", 0.0).toFloat().coerceIn(-1f, 1f),
                                                baseTransform = parseMaskTransform(paramsObj.optJSONObject("baseTransform")) ?: defaultTransform,
                                                baseWidthPx = paramsObj.optInt("baseWidthPx", 0).takeIf { it > 0 },
                                                baseHeightPx = paramsObj.optInt("baseHeightPx", 0).takeIf { it > 0 }
                                            )
                                    )

                                SubMaskType.AiEnvironment.id ->
                                    SubMaskState(
                                        id = subId,
                                        type = SubMaskType.AiEnvironment.id,
                                        visible = visible,
                                        mode = mode,
                                        aiEnvironment =
                                            com.dueckis.kawaiiraweditor.data.model.AiEnvironmentMaskParametersState(
                                                category = paramsObj.optString("category", "sky"),
                                                maskDataBase64 = paramsObj.optString("maskDataBase64").takeIf { it.isNotBlank() },
                                                softness = paramsObj.optDouble("softness", 0.25).toFloat().coerceIn(0f, 1f),
                                                feather = paramsObj.optDouble("feather", 0.0).toFloat().coerceIn(-1f, 1f),
                                                baseTransform = parseMaskTransform(paramsObj.optJSONObject("baseTransform")) ?: defaultTransform,
                                                baseWidthPx = paramsObj.optInt("baseWidthPx", 0).takeIf { it > 0 },
                                                baseHeightPx = paramsObj.optInt("baseHeightPx", 0).takeIf { it > 0 }
                                            )
                                    )

                                SubMaskType.Linear.id ->
                                    SubMaskState(
                                        id = subId,
                                        type = SubMaskType.Linear.id,
                                        visible = visible,
                                        mode = mode,
                                        linear =
                                            com.dueckis.kawaiiraweditor.data.model.LinearMaskParametersState(
                                                startX = paramsObj.optDouble("startX", 0.5).toFloat(),
                                                startY = paramsObj.optDouble("startY", 0.2).toFloat(),
                                                endX = paramsObj.optDouble("endX", 0.5).toFloat(),
                                                endY = paramsObj.optDouble("endY", 0.8).toFloat(),
                                                range = paramsObj.optDouble("range", 0.25).toFloat()
                                            )
                                    )

                                else -> {
                                    val linesArr = paramsObj.optJSONArray("lines") ?: JSONArray()
                                    val lines =
                                        (0 until linesArr.length()).mapNotNull { lIdx ->
                                            val lineObj = linesArr.optJSONObject(lIdx) ?: return@mapNotNull null
                                            val pointsArr = lineObj.optJSONArray("points") ?: JSONArray()
                                            val points =
                                                (0 until pointsArr.length()).mapNotNull { pIdx ->
                                                    val pObj = pointsArr.optJSONObject(pIdx) ?: return@mapNotNull null
                                                    MaskPoint(
                                                        x = pObj.optDouble("x", 0.0).toFloat(),
                                                        y = pObj.optDouble("y", 0.0).toFloat(),
                                                        pressure = pObj.optDouble("pressure", 1.0).toFloat().coerceIn(0f, 1f)
                                                    )
                                                }
                                            BrushLineState(
                                                tool = lineObj.optString("tool", "brush"),
                                                brushSize = lineObj.optDouble("brushSize", 50.0).toFloat(),
                                                feather = lineObj.optDouble("feather", 0.5).toFloat(),
                                                order = lineObj.optLong("order", 0L),
                                                points = points
                                            )
                                        }
                                    SubMaskState(
                                        id = subId,
                                        type = SubMaskType.Brush.id,
                                        visible = visible,
                                        mode = mode,
                                        lines = lines
                                    )
                                }
                            }
                        }

                    MaskState(
                        id = maskId,
                        name = maskObj.optString("name", ""),
                        visible = maskObj.optBoolean("visible", true),
                        invert = maskObj.optBoolean("invert", false),
                        opacity = maskObj.optDouble("opacity", 100.0).toFloat(),
                        adjustments = maskAdjustments,
                        subMasks = subMasks
                    )
                }

            ParsedEdits(adjustments = parsedAdjustments, masks = parsedMasks, detectedAiEnvironmentCategories = detected)
        }.getOrNull()
    }

    suspend fun buildCurrentEditsJson(): String {
        return withContext(Dispatchers.Default) {
            val base = adjustments.toJson(masks)
            val obj = JSONObject(base)
            val detected = detectedAiEnvironmentCategories.orEmpty()
            if (detected.isEmpty()) {
                obj.remove("aiEnvironmentDetectedCategories")
            } else {
                val arr = JSONArray()
                detected.forEach { arr.put(it.id) }
                obj.put("aiEnvironmentDetectedCategories", arr)
            }
            obj.toString()
        }
    }
    val currentMaskTransform = adjustments.toMaskTransformState()

    fun assignNumber(maskId: String) {
        if (maskId !in maskNumbers) {
            val next = (maskNumbers.values.maxOrNull() ?: 0) + 1
            maskNumbers[maskId] = next
        }
    }

    var editedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editedViewportBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editedViewportRoi by remember { mutableStateOf<CropState?>(null) }
    val currentViewportRoi = remember { AtomicReference<CropState?>(null) }
    var viewportScale by remember { mutableFloatStateOf(1f) }
    var viewportRoiForDebounce by remember { mutableStateOf<CropState?>(null) }
    var fullPreviewDirtyByViewport by remember { mutableStateOf(false) }

    fun zoomMaxDimensionForScale(scale: Float): Int {
        val minDim = 1280
        val maxDim = 2304
        val minScale = 1.1f
        val maxScale = 5f
        val t = ((scale - minScale) / (maxScale - minScale)).coerceIn(0f, 1f)
        val desired = (minDim + (maxDim - minDim) * t).roundToInt()
        val clamped = desired.coerceIn(minDim, maxDim)
        val step = 128
        return (clamped / step) * step
    }

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var uncroppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editedPreviewRotationDegrees by remember(galleryItem.projectId) { mutableFloatStateOf(0f) }
    var uncroppedPreviewRotationDegrees by remember(galleryItem.projectId) { mutableFloatStateOf(0f) }
    var isComparingOriginal by remember { mutableStateOf(false) }
    var histogramData by remember { mutableStateOf<HistogramData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isGeneratingAiMask by remember { mutableStateOf(false) }
    var isDraggingMaskHandle by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var metadataJson by remember { mutableStateOf<String?>(null) }
    var showAiSubjectOverrideDialog by remember { mutableStateOf(false) }
    var aiSubjectOverrideTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showModelDownloadDialog by remember(galleryItem.projectId) { mutableStateOf(false) }
    var pendingModelDownloadAction by remember(galleryItem.projectId) { mutableStateOf<PendingModelDownloadAction?>(null) }
    var pendingModelDownloadNames by remember(galleryItem.projectId) { mutableStateOf<List<String>>(emptyList()) }
    var pendingSubjectMaskPoints by remember(galleryItem.projectId) { mutableStateOf<List<MaskPoint>>(emptyList()) }
    var pendingSubjectMaskCreation by remember(galleryItem.projectId) { mutableStateOf<PendingSubjectMaskCreation?>(null) }
    val editHistory = remember(galleryItem.projectId) { mutableStateListOf<EditorHistoryEntry>() }

    fun clearModelDownloadState() {
        showModelDownloadDialog = false
        pendingModelDownloadAction = null
        pendingModelDownloadNames = emptyList()
        pendingSubjectMaskPoints = emptyList()
        pendingSubjectMaskCreation = null
    }
    var editHistoryIndex by remember(galleryItem.projectId) { mutableIntStateOf(-1) }
    var isRestoringEditHistory by remember(galleryItem.projectId) { mutableStateOf(false) }
    var isHistoryInteractionActive by remember(galleryItem.projectId) { mutableStateOf(false) }

    PredictiveBackHandler {
        try {
            val shouldAnimate = !showAiSubjectOverrideDialog && !showMetadataDialog && !showModelDownloadDialog
            it.collect { event ->
                if (shouldAnimate) {
                    onPredictiveBackProgress(event.progress)
                }
            }
            when {
                showAiSubjectOverrideDialog -> {
                    showAiSubjectOverrideDialog = false
                    aiSubjectOverrideTarget = null
                }

                showMetadataDialog -> {
                    showMetadataDialog = false
                }

                showModelDownloadDialog -> {
                    clearModelDownloadState()
                }

                else -> {
                    val req = lastImmichSidecarSyncRequest
                    if (req != null) {
                        uploadIridisSidecarToImmich(req, force = true, showToastOnFailure = true)
                    }
                    onPredictiveBackCommitted()
                }
            }
        } catch (_: CancellationException) {
            onPredictiveBackCancelled()
        }
    }

    LaunchedEffect(galleryItem.projectId, galleryItem.immichAssetId) {
        if (!immichDescriptionSyncEnabled) return@LaunchedEffect
        val originImmichAssetId = galleryItem.immichAssetId?.trim().takeUnless { it.isNullOrBlank() } ?: return@LaunchedEffect
        if (originImmichAssetId.isBlank()) return@LaunchedEffect
        immichSidecarSyncRequests
            .receiveAsFlow()
            .debounce(immichSyncIdleMs)
            .collect { request ->
                uploadIridisSidecarToImmich(request, force = false, showToastOnFailure = false)
            }
    }

    var panelTab by remember(galleryItem.projectId) { mutableStateOf(initialPanelTab) }
    val isCropMode = panelTab == EditorPanelTab.CropTransform
    val isCropPreviewActive = isCropMode && !isComparingOriginal
    var selectedMaskId by remember { mutableStateOf<String?>(null) }
    var selectedSubMaskId by remember { mutableStateOf<String?>(null) }
    var isPaintingMask by remember { mutableStateOf(false) }
    var maskTapMode by remember { mutableStateOf(MaskTapMode.None) }
    var brushSize by remember { mutableStateOf(60f) }
    var brushTool by remember { mutableStateOf(BrushTool.Brush) }
    var brushSoftness by remember { mutableStateOf(0.5f) }
    var eraserSoftness by remember { mutableStateOf(0.5f) }
    var showMaskOverlay by remember { mutableStateOf(false) }
    var maskOverlayBlinkKey by remember { mutableStateOf(0L) }
    var maskOverlayBlinkSubMaskId by remember { mutableStateOf<String?>(null) }
    fun requestMaskOverlayBlink(highlightSubMaskId: String?) {
        maskOverlayBlinkSubMaskId = highlightSubMaskId
        maskOverlayBlinkKey++
    }
    val strokeOrder = remember { AtomicLong(0L) }

    var cropDraft by remember(galleryItem.projectId) { mutableStateOf<CropState?>(null) }
    var rotationDraft by remember(galleryItem.projectId) { mutableStateOf<Float?>(null) }
    var isStraightenActive by remember(galleryItem.projectId) { mutableStateOf(false) }
    var isCropGestureActive by remember(galleryItem.projectId) { mutableStateOf(false) }

    val cropBaseBitmap = if (isCropMode) (uncroppedBitmap ?: editedBitmap) else null
    val cropBaseWidthPx = cropBaseBitmap?.width
    val cropBaseHeightPx = cropBaseBitmap?.height
    val cropPreviewBaseRotation =
        if (uncroppedBitmap != null) uncroppedPreviewRotationDegrees else editedPreviewRotationDegrees
    val previewRotationDelta =
        if (!isCropPreviewActive) 0f else ((rotationDraft ?: adjustments.rotation) - cropPreviewBaseRotation)

    val editedBitmapForUi = if (isCropMode) (uncroppedBitmap ?: editedBitmap) else editedBitmap
    val displayBitmap = if (isComparingOriginal) (originalBitmap ?: editedBitmapForUi) else editedBitmapForUi

    fun normalizedCropOrFull(crop: CropState?): CropState {
        val c = crop?.normalized()
        val w = c?.width ?: 1f
        val h = c?.height ?: 1f
        if (c == null || w <= 0.0001f || h <= 0.0001f) return CropState(0f, 0f, 1f, 1f)
        return c
    }

    fun applyOrientationSteps(point: MaskPoint, steps: Int): MaskPoint {
        return when (((steps % 4) + 4) % 4) {
            1 -> MaskPoint(x = 1f - point.y, y = point.x, pressure = point.pressure)
            2 -> MaskPoint(x = 1f - point.x, y = 1f - point.y, pressure = point.pressure)
            3 -> MaskPoint(x = point.y, y = 1f - point.x, pressure = point.pressure)
            else -> point
        }
    }

    fun applyFlips(point: MaskPoint, flipH: Boolean, flipV: Boolean): MaskPoint {
        val x = if (flipH) 1f - point.x else point.x
        val y = if (flipV) 1f - point.y else point.y
        return MaskPoint(x = x, y = y, pressure = point.pressure)
    }

    fun rotatePointNormalized(point: MaskPoint, angleDegrees: Float, widthPx: Float, heightPx: Float): MaskPoint {
        val w = widthPx.coerceAtLeast(1f)
        val h = heightPx.coerceAtLeast(1f)
        val rad = (angleDegrees.toDouble() * PI) / 180.0
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val ux = (point.x - 0.5f) * w
        val uy = (point.y - 0.5f) * h
        val rx = ux * cosA - uy * sinA
        val ry = ux * sinA + uy * cosA
        return MaskPoint(x = (rx / w) + 0.5f, y = (ry / h) + 0.5f, pressure = point.pressure)
    }

    fun remapPointForTransform(
        point: MaskPoint,
        oldCrop: CropState,
        oldRotation: Float,
        oldOrientationSteps: Int,
        oldFlipH: Boolean,
        oldFlipV: Boolean,
        newCrop: CropState,
        newRotation: Float,
        newOrientationSteps: Int,
        newFlipH: Boolean,
        newFlipV: Boolean,
        baseWidthPx: Float,
        baseHeightPx: Float
    ): MaskPoint {
        val baseW = baseWidthPx.coerceAtLeast(1f)
        val baseH = baseHeightPx.coerceAtLeast(1f)
        val oldSteps = ((oldOrientationSteps % 4) + 4) % 4
        val newSteps = ((newOrientationSteps % 4) + 4) % 4
        val oldW = if (oldSteps % 2 == 1) baseH else baseW
        val oldH = if (oldSteps % 2 == 1) baseW else baseH
        val newW = if (newSteps % 2 == 1) baseH else baseW
        val newH = if (newSteps % 2 == 1) baseW else baseH

        val preCrop =
            MaskPoint(
                x = oldCrop.x + point.x * oldCrop.width,
                y = oldCrop.y + point.y * oldCrop.height,
                pressure = point.pressure
            )
        val unrotated = rotatePointNormalized(preCrop, -oldRotation, oldW, oldH)
        val unflipped = applyFlips(unrotated, flipH = oldFlipH, flipV = oldFlipV)
        val canonical = applyOrientationSteps(unflipped, steps = -oldSteps)

        val oriented = applyOrientationSteps(canonical, steps = newSteps)
        val flipped = applyFlips(oriented, flipH = newFlipH, flipV = newFlipV)
        val rotatedNew = rotatePointNormalized(flipped, newRotation, newW, newH)
        val outX = if (newCrop.width <= 0.0001f) rotatedNew.x else (rotatedNew.x - newCrop.x) / newCrop.width
        val outY = if (newCrop.height <= 0.0001f) rotatedNew.y else (rotatedNew.y - newCrop.y) / newCrop.height
        return MaskPoint(x = outX, y = outY, pressure = point.pressure)
    }

    fun decodeDataUrlBitmap(dataUrl: String): Bitmap? {
        val idx = dataUrl.indexOf("base64,")
        if (idx < 0) return null
        val b64 = dataUrl.substring(idx + "base64,".length)
        val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun encodeBitmapToPngDataUrl(bitmap: Bitmap): String? {
        return runCatching {
            val outputStream = java.io.ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) return@runCatching null
            val b64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            "data:image/png;base64,$b64"
        }.getOrNull()
    }

    fun maxFilterU8(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius <= 0) return src
        val tmp = IntArray(src.size)
        val dst = IntArray(src.size)
        val rowDeque = IntArray(width)
        val colDeque = IntArray(height)

        for (y in 0 until height) {
            var head = 0
            var tail = 0
            val row = y * width
            val last = width - 1
            val prefill = minOf(radius, last)
            for (x in 0..prefill) {
                val v = src[row + x]
                while (tail > head && src[row + rowDeque[tail - 1]] <= v) tail--
                rowDeque[tail++] = x
            }
            for (x in 0 until width) {
                val end = x + radius
                if (end <= last && end > prefill) {
                    val v = src[row + end]
                    while (tail > head && src[row + rowDeque[tail - 1]] <= v) tail--
                    rowDeque[tail++] = end
                }
                val start = x - radius
                while (head < tail && rowDeque[head] < start) head++
                tmp[row + x] = src[row + rowDeque[head]]
            }
        }

        for (x in 0 until width) {
            var head = 0
            var tail = 0
            val last = height - 1
            val prefill = minOf(radius, last)
            for (y in 0..prefill) {
                val v = tmp[y * width + x]
                while (tail > head && tmp[colDeque[tail - 1] * width + x] <= v) tail--
                colDeque[tail++] = y
            }
            for (y in 0 until height) {
                val end = y + radius
                if (end <= last && end > prefill) {
                    val v = tmp[end * width + x]
                    while (tail > head && tmp[colDeque[tail - 1] * width + x] <= v) tail--
                    colDeque[tail++] = end
                }
                val start = y - radius
                while (head < tail && colDeque[head] < start) head++
                dst[y * width + x] = tmp[colDeque[head] * width + x]
            }
        }

        return dst
    }

    fun minFilterU8(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius <= 0) return src
        val tmp = IntArray(src.size)
        val dst = IntArray(src.size)
        val rowDeque = IntArray(width)
        val colDeque = IntArray(height)

        for (y in 0 until height) {
            var head = 0
            var tail = 0
            val row = y * width
            val last = width - 1
            val prefill = minOf(radius, last)
            for (x in 0..prefill) {
                val v = src[row + x]
                while (tail > head && src[row + rowDeque[tail - 1]] >= v) tail--
                rowDeque[tail++] = x
            }
            for (x in 0 until width) {
                val end = x + radius
                if (end <= last && end > prefill) {
                    val v = src[row + end]
                    while (tail > head && src[row + rowDeque[tail - 1]] >= v) tail--
                    rowDeque[tail++] = end
                }
                val start = x - radius
                while (head < tail && rowDeque[head] < start) head++
                tmp[row + x] = src[row + rowDeque[head]]
            }
        }

        for (x in 0 until width) {
            var head = 0
            var tail = 0
            val last = height - 1
            val prefill = minOf(radius, last)
            for (y in 0..prefill) {
                val v = tmp[y * width + x]
                while (tail > head && tmp[colDeque[tail - 1] * width + x] >= v) tail--
                colDeque[tail++] = y
            }
            for (y in 0 until height) {
                val end = y + radius
                if (end <= last && end > prefill) {
                    val v = tmp[end * width + x]
                    while (tail > head && tmp[colDeque[tail - 1] * width + x] >= v) tail--
                    colDeque[tail++] = end
                }
                val start = y - radius
                while (head < tail && colDeque[head] < start) head++
                dst[y * width + x] = tmp[colDeque[head] * width + x]
            }
        }

        return dst
    }

    fun applyAiFeatherToDataUrl(dataUrl: String, feather: Float): String {
        val normalized = feather.coerceIn(-1f, 1f)
        if (normalized.absoluteValue < 0.001f) return dataUrl
        val key = "${dataUrl.length}-${dataUrl.hashCode()}-${(normalized * 1000f).roundToInt()}"
        synchronized(aiFeatherCache) {
            aiFeatherCache[key]?.let { return it }
        }
        val decoded = decodeDataUrlBitmap(dataUrl) ?: return dataUrl
        val width = decoded.width.coerceAtLeast(1)
        val height = decoded.height.coerceAtLeast(1)
        val pixels = IntArray(width * height)
        decoded.getPixels(pixels, 0, width, 0, 0, width, height)
        decoded.recycle()
        val maskU8 = IntArray(width * height) { i -> (pixels[i] shr 16) and 0xFF }
        val maxRadius = (minOf(width, height) * 0.04f).roundToInt().coerceIn(1, 60)
        val radius = (normalized.absoluteValue * maxRadius).roundToInt()
        val adjusted =
            if (radius >= 1) {
                if (normalized > 0f) maxFilterU8(maskU8, width, height, radius) else minFilterU8(maskU8, width, height, radius)
            } else {
                maskU8
            }
        val outPixels = IntArray(width * height) { idx ->
            val v = adjusted[idx].coerceIn(0, 255)
            (255 shl 24) or (v shl 16) or (v shl 8) or v
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, width, 0, 0, width, height)
        val encoded = encodeBitmapToPngDataUrl(out)
        out.recycle()
        if (encoded != null) {
            synchronized(aiFeatherCache) {
                aiFeatherCache[key] = encoded
                if (aiFeatherCache.size > 8) {
                    val iterator = aiFeatherCache.entries.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
            }
            return encoded
        }
        return dataUrl
    }

    fun rotateBitmapInPlaceSameSize(src: Bitmap, angleDegrees: Float): Bitmap {
        if (angleDegrees == 0f) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val w = src.width.coerceAtLeast(1)
        val h = src.height.coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        val matrix = Matrix().apply { postRotate(angleDegrees, w / 2f, h / 2f) }
        canvas.drawBitmap(src, matrix, paint)
        return out
    }

    fun rotateBitmapSteps(src: Bitmap, steps: Int): Bitmap {
        val s = ((steps % 4) + 4) % 4
        if (s == 0) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)

        val matrix = Matrix().apply { postRotate((s * 90).toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (out.config != Bitmap.Config.ARGB_8888) {
            val converted = out.copy(Bitmap.Config.ARGB_8888, true)
            out.recycle()
            return converted
        }
        return out
    }

    fun flipBitmap(src: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        if (!horizontal && !vertical) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val sx = if (horizontal) -1f else 1f
        val sy = if (vertical) -1f else 1f
        val matrix = Matrix().apply { postScale(sx, sy, src.width / 2f, src.height / 2f) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (out.config != Bitmap.Config.ARGB_8888) {
            val converted = out.copy(Bitmap.Config.ARGB_8888, true)
            out.recycle()
            return converted
        }
        return out
    }

    fun remapMaskDataUrlForTransform(
        dataUrl: String,
        baseWidthPx: Int,
        baseHeightPx: Int,
        oldAdjustments: AdjustmentState,
        newAdjustments: AdjustmentState
    ): String? {
        val decoded = decodeDataUrlBitmap(dataUrl) ?: return null
        val baseW = baseWidthPx.coerceAtLeast(1)
        val baseH = baseHeightPx.coerceAtLeast(1)

        val oldCrop = normalizedCropOrFull(oldAdjustments.crop)
        val newCrop = normalizedCropOrFull(newAdjustments.crop)

        val oldSteps = ((oldAdjustments.orientationSteps % 4) + 4) % 4
        val newSteps = ((newAdjustments.orientationSteps % 4) + 4) % 4
        val oldW = if (oldSteps % 2 == 1) baseH else baseW
        val oldH = if (oldSteps % 2 == 1) baseW else baseH
        val newW = if (newSteps % 2 == 1) baseH else baseW
        val newH = if (newSteps % 2 == 1) baseW else baseH

        val oldFull = Bitmap.createBitmap(oldW, oldH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(oldFull)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        val left = (oldCrop.x * oldW).coerceIn(0f, oldW.toFloat())
        val top = (oldCrop.y * oldH).coerceIn(0f, oldH.toFloat())
        val right = ((oldCrop.x + oldCrop.width) * oldW).coerceIn(0f, oldW.toFloat())
        val bottom = ((oldCrop.y + oldCrop.height) * oldH).coerceIn(0f, oldH.toFloat())
        if (right - left < 1f || bottom - top < 1f) {
            decoded.recycle()
            oldFull.recycle()
            return null
        }
        canvas.drawBitmap(decoded, null, android.graphics.RectF(left, top, right, bottom), paint)
        decoded.recycle()

        val unrotated =
            if (oldAdjustments.rotation == 0f) oldFull
            else rotateBitmapInPlaceSameSize(oldFull, -oldAdjustments.rotation).also { oldFull.recycle() }
        val unflipped =
            if (!oldAdjustments.flipHorizontal && !oldAdjustments.flipVertical) unrotated
            else flipBitmap(unrotated, oldAdjustments.flipHorizontal, oldAdjustments.flipVertical).also { unrotated.recycle() }
        val canonical =
            if (oldSteps == 0) unflipped
            else rotateBitmapSteps(unflipped, steps = -oldSteps).also { unflipped.recycle() }

        val oriented =
            if (newSteps == 0) canonical
            else rotateBitmapSteps(canonical, steps = newSteps).also { canonical.recycle() }
        val flipped =
            if (!newAdjustments.flipHorizontal && !newAdjustments.flipVertical) oriented
            else flipBitmap(oriented, newAdjustments.flipHorizontal, newAdjustments.flipVertical).also { oriented.recycle() }
        val rotated =
            if (newAdjustments.rotation == 0f) flipped
            else rotateBitmapInPlaceSameSize(flipped, newAdjustments.rotation).also { flipped.recycle() }

        val cropLeft = (newCrop.x * newW).roundToInt().coerceIn(0, newW - 1)
        val cropTop = (newCrop.y * newH).roundToInt().coerceIn(0, newH - 1)
        val cropW = (newCrop.width * newW).roundToInt().coerceAtLeast(1).coerceAtMost(newW - cropLeft)
        val cropH = (newCrop.height * newH).roundToInt().coerceAtLeast(1).coerceAtMost(newH - cropTop)
        val cropped = Bitmap.createBitmap(rotated, cropLeft, cropTop, cropW, cropH)
        if (rotated != cropped) rotated.recycle()

        val encoded = encodeBitmapToPngDataUrl(cropped)
        cropped.recycle()
        return encoded
    }

    fun estimateBaseDimsFromBitmap(bitmap: Bitmap, transform: MaskTransformState): Pair<Int, Int> {
        val crop = normalizedCropOrFull(transform.crop)
        val stepsMod = ((transform.orientationSteps % 4) + 4) % 4
        val preCropWAfterSteps =
            (bitmap.width.toFloat() / crop.width.coerceAtLeast(0.0001f)).coerceAtLeast(1f)
        val preCropHAfterSteps =
            (bitmap.height.toFloat() / crop.height.coerceAtLeast(0.0001f)).coerceAtLeast(1f)
        val preCropWAfterStepsPx = preCropWAfterSteps.roundToInt().coerceAtLeast(1)
        val preCropHAfterStepsPx = preCropHAfterSteps.roundToInt().coerceAtLeast(1)
        val baseW = if (stepsMod % 2 == 1) preCropHAfterStepsPx else preCropWAfterStepsPx
        val baseH = if (stepsMod % 2 == 1) preCropWAfterStepsPx else preCropHAfterStepsPx
        return baseW to baseH
    }

    fun resolveAiMaskBaseDims(
        dataUrl: String,
        baseTransform: MaskTransformState,
        baseWidthPx: Int?,
        baseHeightPx: Int?
    ): Pair<Int, Int>? {
        if (baseWidthPx != null && baseHeightPx != null) {
            return baseWidthPx.coerceAtLeast(1) to baseHeightPx.coerceAtLeast(1)
        }
        val decoded = decodeDataUrlBitmap(dataUrl) ?: return null
        val crop = normalizedCropOrFull(baseTransform.crop)
        val stepsMod = ((baseTransform.orientationSteps % 4) + 4) % 4
        val preCropWAfterSteps =
            (decoded.width.toFloat() / crop.width.coerceAtLeast(0.0001f)).coerceAtLeast(1f)
        val preCropHAfterSteps =
            (decoded.height.toFloat() / crop.height.coerceAtLeast(0.0001f)).coerceAtLeast(1f)
        decoded.recycle()
        val preCropWAfterStepsPx = preCropWAfterSteps.roundToInt().coerceAtLeast(1)
        val preCropHAfterStepsPx = preCropHAfterSteps.roundToInt().coerceAtLeast(1)
        val baseW = if (stepsMod % 2 == 1) preCropHAfterStepsPx else preCropWAfterStepsPx
        val baseH = if (stepsMod % 2 == 1) preCropWAfterStepsPx else preCropHAfterStepsPx
        return baseW to baseH
    }

    fun remapAiSubMaskForTransform(
        sub: SubMaskState,
        currentTransform: MaskTransformState,
        currentAdjustments: AdjustmentState
    ): SubMaskState {
        return when (sub.type) {
            SubMaskType.AiSubject.id -> {
                val dataUrl = sub.aiSubject.maskDataBase64 ?: return sub
                val baseTransform = sub.aiSubject.baseTransform ?: return sub
                if (baseTransform.matches(currentTransform)) return sub
                val baseDims =
                    resolveAiMaskBaseDims(
                        dataUrl = dataUrl,
                        baseTransform = baseTransform,
                        baseWidthPx = sub.aiSubject.baseWidthPx,
                        baseHeightPx = sub.aiSubject.baseHeightPx
                    ) ?: return sub
                val remapped =
                    remapMaskDataUrlForTransform(
                        dataUrl = dataUrl,
                        baseWidthPx = baseDims.first,
                        baseHeightPx = baseDims.second,
                        oldAdjustments = baseTransform.toAdjustmentState(),
                        newAdjustments = currentAdjustments
                    )
                if (remapped == null) sub else sub.copy(aiSubject = sub.aiSubject.copy(maskDataBase64 = remapped))
            }

            SubMaskType.AiEnvironment.id -> {
                val dataUrl = sub.aiEnvironment.maskDataBase64 ?: return sub
                val baseTransform = sub.aiEnvironment.baseTransform ?: return sub
                if (baseTransform.matches(currentTransform)) return sub
                val baseDims =
                    resolveAiMaskBaseDims(
                        dataUrl = dataUrl,
                        baseTransform = baseTransform,
                        baseWidthPx = sub.aiEnvironment.baseWidthPx,
                        baseHeightPx = sub.aiEnvironment.baseHeightPx
                    ) ?: return sub
                val remapped =
                    remapMaskDataUrlForTransform(
                        dataUrl = dataUrl,
                        baseWidthPx = baseDims.first,
                        baseHeightPx = baseDims.second,
                        oldAdjustments = baseTransform.toAdjustmentState(),
                        newAdjustments = currentAdjustments
                    )
                if (remapped == null) sub else sub.copy(aiEnvironment = sub.aiEnvironment.copy(maskDataBase64 = remapped))
            }

            else -> sub
        }
    }

    fun remapAiMasksForTransform(
        source: List<MaskState>,
        currentTransform: MaskTransformState,
        currentAdjustments: AdjustmentState
    ): List<MaskState> {
        return source.map { mask ->
            val updated =
                mask.subMasks.map { sub ->
                    remapAiSubMaskForTransform(sub, currentTransform, currentAdjustments)
                }
            if (updated == mask.subMasks) mask else mask.copy(subMasks = updated)
        }
    }

    fun masksForRender(source: List<MaskState>): List<MaskState> {
        if (environmentMaskingEnabled) return source
        return source.mapNotNull { mask ->
            val remaining = mask.subMasks.filterNot { it.type == SubMaskType.AiEnvironment.id }
            if (remaining.isEmpty()) null else mask.copy(subMasks = remaining)
        }
    }

    fun masksForRenderWithAiRemap(
        source: List<MaskState>,
        currentTransform: MaskTransformState,
        currentAdjustments: AdjustmentState
    ): List<MaskState> {
        val filtered = masksForRender(source)
        val remapped = remapAiMasksForTransform(filtered, currentTransform, currentAdjustments)
        return remapped.map { mask ->
            val updated =
                mask.subMasks.map { sub ->
                    when (sub.type) {
                        SubMaskType.AiSubject.id -> {
                            val dataUrl = sub.aiSubject.maskDataBase64 ?: return@map sub
                            val feathered = applyAiFeatherToDataUrl(dataUrl, sub.aiSubject.feather)
                            if (feathered == dataUrl) sub else sub.copy(aiSubject = sub.aiSubject.copy(maskDataBase64 = feathered))
                        }
                        SubMaskType.AiEnvironment.id -> {
                            val dataUrl = sub.aiEnvironment.maskDataBase64 ?: return@map sub
                            val feathered = applyAiFeatherToDataUrl(dataUrl, sub.aiEnvironment.feather)
                            if (feathered == dataUrl) sub else sub.copy(aiEnvironment = sub.aiEnvironment.copy(maskDataBase64 = feathered))
                        }
                        else -> sub
                    }
                }
            if (updated == mask.subMasks) mask else mask.copy(subMasks = updated)
        }
    }

    var exportMasks by remember { mutableStateOf<List<MaskState>>(emptyList()) }
    LaunchedEffect(masks, currentMaskTransform, environmentMaskingEnabled) {
        exportMasks =
            withContext(Dispatchers.Default) {
                masksForRenderWithAiRemap(masks, currentMaskTransform, adjustments)
            }
    }

	    fun remapMasksForTransform(old: AdjustmentState, next: AdjustmentState, currentMasks: List<MaskState>): List<MaskState> {
	        if (currentMasks.isEmpty()) return currentMasks
	        val oldCrop = normalizedCropOrFull(old.crop)
	        val newCrop = normalizedCropOrFull(next.crop)
	        val oldRotation = old.rotation
	        val newRotation = next.rotation
	        val oldSteps = old.orientationSteps
	        val newSteps = next.orientationSteps
	        val oldFlipH = old.flipHorizontal
	        val newFlipH = next.flipHorizontal
	        val oldFlipV = old.flipVertical
	        val newFlipV = next.flipVertical
	        if (oldCrop == newCrop && oldRotation == newRotation && oldSteps == newSteps && oldFlipH == newFlipH && oldFlipV == newFlipV) {
	            return currentMasks
	        }

	        val useUncroppedReference = isCropMode && uncroppedBitmap != null
	        val referenceBitmap = if (useUncroppedReference) uncroppedBitmap else editedBitmap
	        val ref = referenceBitmap ?: return currentMasks
	        val refW = ref.width.coerceAtLeast(1)
	        val refH = ref.height.coerceAtLeast(1)
	        val oldStepsMod = ((oldSteps % 4) + 4) % 4

	        val preCropWAfterSteps =
	            if (useUncroppedReference) refW.toFloat()
	            else (refW.toFloat() / oldCrop.width.coerceAtLeast(0.0001f)).coerceAtLeast(1f)
	        val preCropHAfterSteps =
	            if (useUncroppedReference) refH.toFloat()
	            else (refH.toFloat() / oldCrop.height.coerceAtLeast(0.0001f)).coerceAtLeast(1f)

	        val preCropWAfterStepsPx = preCropWAfterSteps.roundToInt().coerceAtLeast(1)
	        val preCropHAfterStepsPx = preCropHAfterSteps.roundToInt().coerceAtLeast(1)

	        val baseW = if (oldStepsMod % 2 == 1) preCropHAfterStepsPx else preCropWAfterStepsPx
	        val baseH = if (oldStepsMod % 2 == 1) preCropWAfterStepsPx else preCropHAfterStepsPx
	        val baseWf = baseW.toFloat()
	        val baseHf = baseH.toFloat()

	        val oldWf = if (oldStepsMod % 2 == 1) baseHf else baseWf
	        val oldHf = if (oldStepsMod % 2 == 1) baseWf else baseHf
	        val newStepsMod = ((newSteps % 4) + 4) % 4
	        val newWf = if (newStepsMod % 2 == 1) baseHf else baseWf
	        val newHf = if (newStepsMod % 2 == 1) baseWf else baseHf
	        val oldBaseDimPx = minOf(oldWf * oldCrop.width, oldHf * oldCrop.height).coerceAtLeast(1f)
	        val newBaseDimPx = minOf(newWf * newCrop.width, newHf * newCrop.height).coerceAtLeast(1f)
	        val lenScale = (oldBaseDimPx / newBaseDimPx).coerceAtLeast(0.0001f)
	        fun scaleLenIfNormalized(v: Float): Float {
	            if (v > 1.5f) return v
	            return (v * lenScale).coerceIn(0.0001f, 1.5f)
	        }

		        fun remapPoint(p: MaskPoint): MaskPoint {
		            val mapped =
		                remapPointForTransform(
		                    point = p,
	                    oldCrop = oldCrop,
	                    oldRotation = oldRotation,
	                    oldOrientationSteps = oldSteps,
	                    oldFlipH = oldFlipH,
	                    oldFlipV = oldFlipV,
	                    newCrop = newCrop,
	                    newRotation = newRotation,
	                    newOrientationSteps = newSteps,
	                    newFlipH = newFlipH,
	                    newFlipV = newFlipV,
	                    baseWidthPx = baseWf,
	                    baseHeightPx = baseHf
	                )
		            return MaskPoint(x = mapped.x.coerceIn(-1f, 1.5f), y = mapped.y.coerceIn(-1f, 1.5f), pressure = p.pressure)
		        }

	        return currentMasks.map { mask ->
	            mask.copy(
	                subMasks =
	                    mask.subMasks.map { sub ->
		                        when (sub.type) {
		                            SubMaskType.Brush.id ->
		                                sub.copy(
		                                    lines =
		                                        sub.lines.map { line ->
		                                            line.copy(
		                                                brushSize = scaleLenIfNormalized(line.brushSize),
		                                                points = line.points.map(::remapPoint)
		                                            )
		                                        }
		                                )

	                            SubMaskType.Linear.id -> {
	                                val start = remapPoint(MaskPoint(sub.linear.startX, sub.linear.startY))
	                                val end = remapPoint(MaskPoint(sub.linear.endX, sub.linear.endY))
	                                sub.copy(
	                                    linear =
	                                        sub.linear.copy(
	                                            startX = start.x,
	                                            startY = start.y,
	                                            endX = end.x,
	                                            endY = end.y,
	                                            range = scaleLenIfNormalized(sub.linear.range),
	                                        )
	                                )
	                            }

	                            SubMaskType.Radial.id -> {
	                                val center = remapPoint(MaskPoint(sub.radial.centerX, sub.radial.centerY))
	                                sub.copy(
	                                    radial =
	                                        sub.radial.copy(
	                                            centerX = center.x,
	                                            centerY = center.y,
	                                            radiusX = scaleLenIfNormalized(sub.radial.radiusX),
	                                            radiusY = scaleLenIfNormalized(sub.radial.radiusY),
	                                        )
	                                )
	                            }

                            SubMaskType.AiSubject.id -> {
                                val dataUrl = sub.aiSubject.maskDataBase64
                                if (dataUrl.isNullOrBlank() || sub.aiSubject.baseTransform != null) sub
                                else {
                                    val remapped =
                                        remapMaskDataUrlForTransform(
                                            dataUrl = dataUrl,
                                            baseWidthPx = baseW,
                                            baseHeightPx = baseH,
                                            oldAdjustments = old,
                                            newAdjustments = next
                                        )
                                    if (remapped == null) sub else sub.copy(aiSubject = sub.aiSubject.copy(maskDataBase64 = remapped))
                                }
                            }

                            SubMaskType.AiEnvironment.id -> {
                                val dataUrl = sub.aiEnvironment.maskDataBase64
                                if (dataUrl.isNullOrBlank() || sub.aiEnvironment.baseTransform != null) sub
                                else {
                                    val remapped =
                                        remapMaskDataUrlForTransform(
                                            dataUrl = dataUrl,
                                            baseWidthPx = baseW,
                                            baseHeightPx = baseH,
                                            oldAdjustments = old,
                                            newAdjustments = next
                                        )
                                    if (remapped == null) sub else sub.copy(aiEnvironment = sub.aiEnvironment.copy(maskDataBase64 = remapped))
                                }
                            }

                            else -> sub
                        }
                    }
            )
        }
    }

    fun applyAdjustmentsPreservingMasks(next: AdjustmentState) {
        val old = adjustments
        if (masks.isNotEmpty()) {
            masks = remapMasksForTransform(old = old, next = next, currentMasks = masks)
        }
        adjustments = next
    }

    LaunchedEffect(isCropMode) {
        if (!isCropMode) {
            cropDraft = null
            rotationDraft = null
            isStraightenActive = false
            isCropGestureActive = false
        } else {
            if (cropDraft == null && adjustments.crop == null) {
                cropDraft = CropState(0f, 0f, 1f, 1f)
            }
        }
    }

    LaunchedEffect(isCropMode, cropBaseWidthPx, cropBaseHeightPx, adjustments.rotation, adjustments.aspectRatio, adjustments.crop, isHistoryInteractionActive) {
        if (!isCropMode) return@LaunchedEffect
        if (isHistoryInteractionActive) return@LaunchedEffect

        val w = cropBaseWidthPx ?: return@LaunchedEffect
        val h = cropBaseHeightPx ?: return@LaunchedEffect
        val baseRatio = w.toFloat().coerceAtLeast(1f) / h.toFloat().coerceAtLeast(1f)

        if (adjustments.crop == null) {
            val auto =
                computeMaxCropNormalized(
                    AutoCropParams(
                        baseAspectRatio = baseRatio,
                        rotationDegrees = adjustments.rotation,
                        aspectRatio = adjustments.aspectRatio
                    )
                )
            cropDraft = auto
            if (adjustments.rotation != 0f || adjustments.aspectRatio != null) {
                applyAdjustmentsPreservingMasks(adjustments.copy(crop = auto))
            }
        } else if (cropDraft == null) {
            cropDraft = adjustments.crop
        }
    }

    LaunchedEffect(isCropMode, adjustments.crop, isCropGestureActive, isHistoryInteractionActive) {
        if (!isCropMode) return@LaunchedEffect
        if (isHistoryInteractionActive) return@LaunchedEffect
        if (isCropGestureActive) return@LaunchedEffect
        if (cropDraft != adjustments.crop) {
            cropDraft = adjustments.crop
        }
    }

    fun normalizeMaskSelectionForCurrentState() {
        val selectedMask = selectedMaskId?.takeIf { id -> masks.any { it.id == id } }
        val normalizedMaskId = selectedMask ?: masks.firstOrNull()?.id
        if (normalizedMaskId != selectedMaskId) {
            selectedMaskId = normalizedMaskId
        }
        val activeMask = masks.firstOrNull { it.id == normalizedMaskId }
        val selectedSub = selectedSubMaskId?.takeIf { id -> activeMask?.subMasks?.any { it.id == id } == true }
        val normalizedSubId = selectedSub ?: activeMask?.subMasks?.firstOrNull()?.id
        if (normalizedSubId != selectedSubMaskId) {
            selectedSubMaskId = normalizedSubId
        }
    }

    fun applyParsedEdits(parsed: ParsedEdits, resetHistory: Boolean) {
        isRestoringEditHistory = true
        adjustments = parsed.adjustments
        masks = parsed.masks
        detectedAiEnvironmentCategories = parsed.detectedAiEnvironmentCategories
        val maxOrder = parsed.masks.flatMap { it.subMasks }.flatMap { it.lines }.maxOfOrNull { it.order } ?: 0L
        strokeOrder.set(maxOf(strokeOrder.get(), maxOrder))
        parsed.masks.forEach { assignNumber(it.id) }
        if (parsed.masks.isNotEmpty() && selectedMaskId == null) {
            selectedMaskId = parsed.masks.first().id
            selectedSubMaskId = parsed.masks.first().subMasks.firstOrNull()?.id
        }
        normalizeMaskSelectionForCurrentState()
        if (resetHistory) {
            editHistory.clear()
            editHistory.add(EditorHistoryEntry(adjustments = adjustments, masks = masks))
            editHistoryIndex = 0
        }
        isRestoringEditHistory = false
    }

    fun openEditTimeline() {
        coroutineScope.launch {
            editTimelineEntries = withContext(Dispatchers.IO) { storage.loadEditHistory(galleryItem.projectId) }
            showEditTimelineDialog = true
        }
    }

    fun createManualVersion() {
        coroutineScope.launch {
            val json = buildCurrentEditsJson()
            val updatedAtMs = System.currentTimeMillis()
            val revisionId = UUID.randomUUID().toString()
            val parentRevisionId = currentRevisionId ?: baseRevisionId
            withContext(Dispatchers.IO) {
                storage.saveAdjustments(
                    projectId = galleryItem.projectId,
                    adjustmentsJson = json,
                    updatedAtMs = updatedAtMs
                )
                storage.appendEditHistory(
                    projectId = galleryItem.projectId,
                    entry = ProjectStorage.EditHistoryEntry(
                        id = revisionId,
                        source = ProjectStorage.EditHistorySource.Local,
                        updatedAtMs = updatedAtMs,
                        editsJson = json,
                        parentId = parentRevisionId
                    )
                )
            }
            lastSavedEditsJson = json
            currentRevisionId = revisionId
            sessionRevisionId = revisionId
            sessionParentRevisionId = parentRevisionId
            if (immichDescriptionSyncEnabled && !galleryItem.immichAssetId.isNullOrBlank()) {
                val req =
                    ImmichSidecarSyncRequest(
                        updatedAtMs = updatedAtMs,
                        editsJson = json,
                        parentRevisionId = parentRevisionId,
                        revisionId = revisionId
                    )
                lastImmichSidecarSyncRequest = req
                immichSidecarSyncRequests.trySend(req)
            }
            editTimelineEntries = withContext(Dispatchers.IO) { storage.loadEditHistory(galleryItem.projectId) }
        }
    }

    fun applyVersionEntry(entry: ProjectStorage.EditHistoryEntry) {
        coroutineScope.launch {
            val parsed = parseEditsJson(entry.editsJson)
            if (parsed == null) {
                Toast.makeText(context, "Could not load this edit.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (entry.source == ProjectStorage.EditHistorySource.Immich) {
                val backupJson = buildCurrentEditsJson()
                val backupAt = System.currentTimeMillis()
                val backupId = IridisSidecarDescription.buildRevisionId(backupAt, backupJson)
                withContext(Dispatchers.IO) {
                    storage.appendEditHistory(
                        projectId = galleryItem.projectId,
                        entry = ProjectStorage.EditHistoryEntry(
                            id = backupId,
                            source = ProjectStorage.EditHistorySource.Local,
                            updatedAtMs = backupAt,
                            editsJson = backupJson,
                            parentId = currentRevisionId
                        )
                    )
                }
            }
            lastSavedEditsJson = entry.editsJson
            currentRevisionId = entry.id
            sessionRevisionId = null
            sessionParentRevisionId = null
            baseRevisionId = null
            applyParsedEdits(parsed, resetHistory = true)
            withContext(Dispatchers.IO) {
                storage.saveAdjustments(
                    projectId = galleryItem.projectId,
                    adjustmentsJson = entry.editsJson,
                    updatedAtMs = entry.updatedAtMs
                )
                if (entry.source == ProjectStorage.EditHistorySource.Immich) {
                    val assetId = galleryItem.immichAssetId
                    if (!assetId.isNullOrBlank()) {
                        storage.setImmichSidecarInfo(
                            projectId = galleryItem.projectId,
                            assetId = assetId,
                            updatedAtMs = entry.updatedAtMs
                        )
                    }
                }
            }
            showEditTimelineDialog = false
        }
    }

    data class VersionTreeItem(
        val entry: ProjectStorage.EditHistoryEntry,
        val depth: Int,
        val guides: List<Boolean>,
        val isLast: Boolean,
        val hasChildren: Boolean
    )

    fun buildVersionTreeItems(entries: List<ProjectStorage.EditHistoryEntry>): List<VersionTreeItem> {
        if (entries.isEmpty()) return emptyList()
        val byId = entries.associateBy { it.id }
        val childrenByParent = mutableMapOf<String?, MutableList<ProjectStorage.EditHistoryEntry>>()
        entries.forEach { entry ->
            val parentId = entry.parentId?.takeIf { byId.containsKey(it) }
            childrenByParent.getOrPut(parentId) { mutableListOf() }.add(entry)
        }
        childrenByParent.values.forEach { list -> list.sortBy { it.updatedAtMs } }
        val roots = childrenByParent[null].orEmpty().sortedBy { it.updatedAtMs }
        val items = mutableListOf<VersionTreeItem>()

        fun walk(
            node: ProjectStorage.EditHistoryEntry,
            depth: Int,
            guides: List<Boolean>,
            isLast: Boolean
        ) {
            val children = childrenByParent[node.id].orEmpty()
            val hasChildren = children.isNotEmpty()
            items.add(VersionTreeItem(entry = node, depth = depth, guides = guides, isLast = isLast, hasChildren = hasChildren))
            if (children.isEmpty()) return
            val primaryChild = children.first()
            val branchChildren = if (children.size > 1) children.drop(1) else emptyList()
            val primaryIsLast = branchChildren.isEmpty()
            walk(primaryChild, depth, guides, primaryIsLast)
            branchChildren.forEachIndexed { index, child ->
                val branchIsLast = index == branchChildren.lastIndex
                val nextGuides = guides + !isLast
                walk(child, depth + 1, nextGuides, branchIsLast)
            }
        }

        roots.forEachIndexed { index, root ->
            walk(root, 0, emptyList(), index == roots.lastIndex)
        }
        return items
    }

    fun applyEditHistoryEntry(entry: EditorHistoryEntry) {
        isRestoringEditHistory = true
        adjustments = entry.adjustments
        masks = entry.masks
        normalizeMaskSelectionForCurrentState()
        isRestoringEditHistory = false
    }

    fun pushEditHistoryEntry(entry: EditorHistoryEntry) {
        if (isRestoringEditHistory) return
        if (editHistoryIndex < 0) return
        if (editHistory.getOrNull(editHistoryIndex) == entry) return

        while (editHistory.lastIndex > editHistoryIndex) {
            editHistory.removeAt(editHistory.lastIndex)
        }

        editHistory.add(entry)
        editHistoryIndex = editHistory.lastIndex

        val maxEntries = 250
        val overflow = editHistory.size - maxEntries
        if (overflow > 0) {
            repeat(overflow) { editHistory.removeAt(0) }
            editHistoryIndex = (editHistoryIndex - overflow).coerceAtLeast(0)
        }
    }

    fun beginEditInteraction() {
        if (!isHistoryInteractionActive) isHistoryInteractionActive = true
    }

    fun endEditInteraction() {
        if (!isHistoryInteractionActive) return
        isHistoryInteractionActive = false
        pushEditHistoryEntry(EditorHistoryEntry(adjustments = adjustments, masks = masks))
    }

    val renderVersion = remember { AtomicLong(0L) }
    val lastEditedPreviewStamp = remember { AtomicLong(-1L) }
    val lastEditedViewportStamp = remember { AtomicLong(-1L) }
    val lastOriginalPreviewStamp = remember { AtomicLong(-1L) }
    val lastUncroppedPreviewStamp = remember { AtomicLong(-1L) }
    val renderRequests = remember { Channel<RenderRequest>(capacity = Channel.CONFLATED) }
    val renderDispatcher = remember { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
    DisposableEffect(renderDispatcher) { onDispose { renderDispatcher.close() } }

    suspend fun requestIdentityPreview(): Bitmap? {
        val handle = sessionHandle
        if (handle == 0L) return null
        val json = withContext(Dispatchers.Default) {
            AdjustmentState(
                rotation = adjustments.rotation,
                flipHorizontal = adjustments.flipHorizontal,
                flipVertical = adjustments.flipVertical,
                orientationSteps = adjustments.orientationSteps,
                crop = adjustments.crop,
                toneMapper = adjustments.toneMapper
            ).toJson(emptyList())
        }
        val bmp = withContext(renderDispatcher) {
            runCatching { LibRawDecoder.lowdecodeFromSession(handle, json) }.getOrNull()
                ?.decodeToBitmap()
                ?: runCatching { LibRawDecoder.decodeFromSession(handle, json) }.getOrNull()?.decodeToBitmap()
        }
        if (bmp != null) return bmp

        val version = renderVersion.incrementAndGet()
        val stampBefore = lastOriginalPreviewStamp.get()
        renderRequests.trySend(
            RenderRequest(
                version = version,
                adjustmentsJson = json,
                target = RenderTarget.Original,
                rotationDegrees = adjustments.rotation
            )
        )

        var waited = 0
        while (waited < 2500) {
            if (lastOriginalPreviewStamp.get() > stampBefore) break
            delay(20)
            waited += 20
        }
        return originalBitmap
    }
    DisposableEffect(sessionHandle) {
        val handleToRelease = sessionHandle
        onDispose {
            if (handleToRelease != 0L) {
                LibRawDecoder.releaseSession(handleToRelease)
            }
        }
    }

    LaunchedEffect(galleryItem.projectId) {
        isRestoringEditHistory = true
        sessionRevisionId = null
        sessionParentRevisionId = null
        lastImmichSidecarSyncRequest = null
        baseRevisionId = null
        adjustments = AdjustmentState()
        masks = emptyList()
        selectedMaskId = null
        selectedSubMaskId = null
        maskOverlayBlinkSubMaskId = null
        maskOverlayBlinkKey = 0L
        editHistory.clear()
        editHistoryIndex = -1

        isComparingOriginal = false
        originalBitmap = null
        editedBitmap = null
        editedViewportBitmap = null
        editedViewportRoi = null
        currentViewportRoi.set(null)
        viewportScale = 1f
        viewportRoiForDebounce = null
        fullPreviewDirtyByViewport = false
        uncroppedBitmap = null
        editedPreviewRotationDegrees = 0f
        uncroppedPreviewRotationDegrees = 0f
        lastEditedPreviewStamp.set(-1L)
        lastEditedViewportStamp.set(-1L)
        lastOriginalPreviewStamp.set(-1L)
        lastUncroppedPreviewStamp.set(-1L)

        val raw = withContext(Dispatchers.IO) { storage.loadRawBytes(galleryItem.projectId) }
        if (raw == null) {
            sessionHandle = 0L
            errorMessage = "Failed to load RAW file."
            return@LaunchedEffect
        }
        val createResult = withContext(renderDispatcher) { runCatching { LibRawDecoder.createSession(raw) } }
        sessionHandle = createResult.getOrDefault(0L)
        if (sessionHandle == 0L) {
            val err = createResult.exceptionOrNull() ?: LibRawDecoder.loadErrorOrNull()
            errorMessage =
                when {
                    !LibRawDecoder.isAvailable() ->
                        "Native decoder failed to load (${err?.javaClass?.simpleName})."
                    err is UnsatisfiedLinkError ->
                        "Native decoder is out of date (JNI link error)."
                    else -> "Failed to initialize native decoder."
                }
            Log.e(logTag, "Failed to initialize native decoder", err)
            return@LaunchedEffect
        }

        val savedAdjustmentsJson = withContext(Dispatchers.IO) { storage.loadAdjustments(galleryItem.projectId) }
        lastSavedEditsJson = savedAdjustmentsJson
        val historyEntries = withContext(Dispatchers.IO) { storage.loadEditHistory(galleryItem.projectId) }
        var matchingHistoryEntry = historyEntries.lastOrNull { it.editsJson == savedAdjustmentsJson }
        val hadHistory = historyEntries.isNotEmpty()
        var createdBaseRevision = false
        if (!hadHistory && savedAdjustmentsJson.isNotBlank() && savedAdjustmentsJson != "{}") {
            val localUpdatedAtMs = withContext(Dispatchers.IO) { storage.getEditsUpdatedAtMs(galleryItem.projectId) }
            if (localUpdatedAtMs > 0L) {
                val baseId = IridisSidecarDescription.buildRevisionId(localUpdatedAtMs, savedAdjustmentsJson)
                val baseEntry =
                    ProjectStorage.EditHistoryEntry(
                        id = baseId,
                        source = ProjectStorage.EditHistorySource.Local,
                        updatedAtMs = localUpdatedAtMs,
                        editsJson = savedAdjustmentsJson,
                        parentId = null
                    )
                withContext(Dispatchers.IO) {
                    storage.appendEditHistory(
                        projectId = galleryItem.projectId,
                        entry = baseEntry
                    )
                }
                matchingHistoryEntry = baseEntry
                baseRevisionId = baseId
                createdBaseRevision = true
            }
        }
        var appliedFromRemote = false

        if (immichDescriptionSyncEnabled) {
            val immichConfig = resolveImmichConfigOrNull()
            val originImmichAssetId = galleryItem.immichAssetId?.trim().takeUnless { it.isNullOrBlank() }
            if (immichConfig != null && originImmichAssetId != null) {
                runCatching {
                    val remoteEntries = fetchRemoteIridisSidecarHistory(immichConfig, originImmichAssetId)
                    if (remoteEntries.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            storage.appendEditHistoryEntries(
                                projectId = galleryItem.projectId,
                                entries = remoteEntries.map {
                                    ProjectStorage.EditHistoryEntry(
                                        id = it.revisionId,
                                        source = ProjectStorage.EditHistorySource.Immich,
                                        updatedAtMs = it.updatedAtMs,
                                        editsJson = it.editsJson,
                                        parentId = it.parentRevisionId
                                    )
                                }
                            )
                        }
                    }

                    val latestRemote = remoteEntries.maxByOrNull { it.updatedAtMs }
                    val localUpdatedAtMs =
                        if (savedAdjustmentsJson.isBlank() || savedAdjustmentsJson == "{}") {
                            0L
                        } else {
                            withContext(Dispatchers.IO) { storage.getEditsUpdatedAtMs(galleryItem.projectId) }
                        }
                    val localRevisionId =
                        matchingHistoryEntry?.id
                            ?: if (localUpdatedAtMs > 0L && savedAdjustmentsJson.isNotBlank() && savedAdjustmentsJson != "{}") {
                                IridisSidecarDescription.buildRevisionId(localUpdatedAtMs, savedAdjustmentsJson)
                            } else {
                                null
                            }
                    if (latestRemote != null && latestRemote.updatedAtMs > localUpdatedAtMs && latestRemote.editsJson.isNotBlank()) {
                        if (savedAdjustmentsJson.isNotBlank() && savedAdjustmentsJson != "{}") {
                            if (matchingHistoryEntry == null && localRevisionId != null) {
                                withContext(Dispatchers.IO) {
                                    storage.appendEditHistory(
                                        projectId = galleryItem.projectId,
                                        entry = ProjectStorage.EditHistoryEntry(
                                            id = localRevisionId,
                                            source = ProjectStorage.EditHistorySource.Local,
                                            updatedAtMs = localUpdatedAtMs,
                                            editsJson = savedAdjustmentsJson,
                                            parentId = null
                                        )
                                    )
                                }
                            }
                        }

                        val parsedRemote = parseEditsJson(latestRemote.editsJson)
                        if (parsedRemote != null) {
                            applyParsedEdits(parsedRemote, resetHistory = true)
                            withContext(Dispatchers.IO) {
                                storage.saveAdjustments(
                                    projectId = galleryItem.projectId,
                                    adjustmentsJson = latestRemote.editsJson,
                                    updatedAtMs = latestRemote.updatedAtMs
                                )
                                storage.setImmichSidecarInfo(
                                    projectId = galleryItem.projectId,
                                    assetId = originImmichAssetId,
                                    updatedAtMs = latestRemote.updatedAtMs
                                )
                            }
                            lastSavedEditsJson = latestRemote.editsJson
                            appliedFromRemote = true
                            currentRevisionId = latestRemote.revisionId
                        }
                    }
                    if (!appliedFromRemote && localRevisionId != null && !createdBaseRevision) {
                        currentRevisionId = localRevisionId
                    }
                }.onFailure { error ->
                    Log.w(logTag, "Failed to ingest Immich edit history", error)
                }
            }
        }

        if (!appliedFromRemote) {
            val parsedEdits = parseEditsJson(savedAdjustmentsJson)
            if (parsedEdits != null) {
                applyParsedEdits(parsedEdits, resetHistory = true)
            } else {
                isRestoringEditHistory = true
                editHistory.clear()
                editHistory.add(EditorHistoryEntry(adjustments = adjustments, masks = masks))
                editHistoryIndex = 0
                isRestoringEditHistory = false
            }
            if (currentRevisionId == null && !createdBaseRevision && savedAdjustmentsJson.isNotBlank() && savedAdjustmentsJson != "{}") {
                currentRevisionId =
                    matchingHistoryEntry?.id
                        ?: run {
                            val localUpdatedAtMs = withContext(Dispatchers.IO) { storage.getEditsUpdatedAtMs(galleryItem.projectId) }
                            IridisSidecarDescription.buildRevisionId(localUpdatedAtMs, savedAdjustmentsJson)
                        }
            }
        }
        if (lastSavedEditsJson == "{}") {
            lastSavedEditsJson = buildCurrentEditsJson()
        }
    }

    LaunchedEffect(sessionHandle) { metadataJson = null }

    LaunchedEffect(displayBitmap) {
        val bmp = displayBitmap ?: run {
            histogramData = null
            return@LaunchedEffect
        }
        if (bmp.width < 512 && bmp.height < 512) return@LaunchedEffect
        delay(80)
        if (displayBitmap !== bmp) return@LaunchedEffect
        histogramData = withContext(Dispatchers.Default) { HistogramUtils.calculateHistogram(bmp) }
    }

    LaunchedEffect(adjustments, masks, isDraggingMaskHandle, isComparingOriginal) {
        if (isDraggingMaskHandle) return@LaunchedEffect
        if (isCropMode && !isComparingOriginal) return@LaunchedEffect
        val viewportRoi = currentViewportRoi.get()
        val useViewportRender = viewportRoi != null && !isComparingOriginal && !isCropMode
        val viewportMaxDim = if (useViewportRender) zoomMaxDimensionForScale(viewportScale) else 0
        val json =
            withContext(Dispatchers.Default) {
                if (useViewportRender) {
                    adjustments.toJsonObject(includeToneMapper = true).apply {
                        put("masks", JSONArray().apply { masks.forEach { put(it.toJsonObject()) } })
                        put(
                            "preview",
                            JSONObject().apply {
                                put("useZoom", true)
                                put("roi", viewportRoi?.toJsonObject() ?: JSONObject.NULL)
                                put("maxDimension", viewportMaxDim)
                            }
                        )
                    }.toString()
                } else if (isComparingOriginal) {
                    AdjustmentState(
                        rotation = adjustments.rotation,
                        flipHorizontal = adjustments.flipHorizontal,
                        flipVertical = adjustments.flipVertical,
                        orientationSteps = adjustments.orientationSteps,
                        crop = adjustments.crop,
                        toneMapper = adjustments.toneMapper
                    ).toJson(emptyList())
                } else {
                    val currentTransform = adjustments.toMaskTransformState()
                    adjustments.toJson(masksForRenderWithAiRemap(masks, currentTransform, adjustments))
                }
            }
        val version = renderVersion.incrementAndGet()
        renderRequests.trySend(
            RenderRequest(
                version = version,
                adjustmentsJson = json,
                target = if (isComparingOriginal) RenderTarget.Original else RenderTarget.Edited,
                rotationDegrees = adjustments.rotation,
                previewRoi = if (useViewportRender) viewportRoi else null
            )
        )
        if (useViewportRender) {
            fullPreviewDirtyByViewport = true
        } else if (!isComparingOriginal) {
            fullPreviewDirtyByViewport = false
        }
    }

    val uncroppedRenderKey = remember(adjustments) { adjustments.copy(crop = null, aspectRatio = null) }
    LaunchedEffect(isCropMode, isComparingOriginal, uncroppedRenderKey, isDraggingMaskHandle, sessionHandle) {
        if (!isCropMode) return@LaunchedEffect
        if (isComparingOriginal) return@LaunchedEffect
        if (sessionHandle == 0L) return@LaunchedEffect
        if (isDraggingMaskHandle) return@LaunchedEffect

        val json = withContext(Dispatchers.Default) { uncroppedRenderKey.toJson(emptyList()) }
        val version = renderVersion.incrementAndGet()
        renderRequests.trySend(
            RenderRequest(version = version, adjustmentsJson = json, target = RenderTarget.UncroppedEdited, rotationDegrees = uncroppedRenderKey.rotation)
        )
    }

    var wasCropMode by remember(galleryItem.projectId) { mutableStateOf(false) }
    LaunchedEffect(isCropMode, isComparingOriginal, sessionHandle) {
        if (sessionHandle == 0L) {
            wasCropMode = isCropMode
            return@LaunchedEffect
        }

        val leavingCropMode = wasCropMode && !isCropMode
        wasCropMode = isCropMode
        if (!leavingCropMode) return@LaunchedEffect
        if (isComparingOriginal) return@LaunchedEffect

        val json =
            withContext(Dispatchers.Default) {
                val currentTransform = adjustments.toMaskTransformState()
                adjustments.toJson(masksForRenderWithAiRemap(masks, currentTransform, adjustments))
            }
        val version = renderVersion.incrementAndGet()
        renderRequests.trySend(
            RenderRequest(version = version, adjustmentsJson = json, target = RenderTarget.Edited, rotationDegrees = adjustments.rotation)
        )
    }

    LaunchedEffect(adjustments, masks, isDraggingMaskHandle, detectedAiEnvironmentCategories) {
        if (isDraggingMaskHandle) return@LaunchedEffect
        if (isRestoringEditHistory || editHistoryIndex < 0) return@LaunchedEffect
        val json = buildCurrentEditsJson()
        if (json == lastSavedEditsJson) return@LaunchedEffect
        delay(350)
        if (json == lastSavedEditsJson) return@LaunchedEffect
        val updatedAtMs = System.currentTimeMillis()
        var revisionId = sessionRevisionId
        if (revisionId == null) {
            revisionId = UUID.randomUUID().toString()
            sessionRevisionId = revisionId
            sessionParentRevisionId = currentRevisionId ?: baseRevisionId
        }
        val parentRevisionId = sessionParentRevisionId
        withContext(Dispatchers.IO) {
            storage.saveAdjustments(galleryItem.projectId, json, updatedAtMs = updatedAtMs)
            storage.appendEditHistory(
                projectId = galleryItem.projectId,
                entry = ProjectStorage.EditHistoryEntry(
                    id = revisionId,
                    source = ProjectStorage.EditHistorySource.Local,
                    updatedAtMs = updatedAtMs,
                    editsJson = json,
                    parentId = parentRevisionId
                )
            )
        }
        lastSavedEditsJson = json
        currentRevisionId = revisionId
        if (!galleryItem.immichAssetId.isNullOrBlank()) {
            val req =
                ImmichSidecarSyncRequest(
                    updatedAtMs = updatedAtMs,
                    editsJson = json,
                    parentRevisionId = parentRevisionId,
                    revisionId = revisionId
                )
            lastImmichSidecarSyncRequest = req
            immichSidecarSyncRequests.trySend(req)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { EditorHistoryEntry(adjustments = adjustments, masks = masks) }
            .distinctUntilChanged()
            .collect { entry ->
                if (isRestoringEditHistory || isHistoryInteractionActive || isDraggingMaskHandle) return@collect
                pushEditHistoryEntry(entry)
            }
    }

    LaunchedEffect(isDraggingMaskHandle) { if (isDraggingMaskHandle) beginEditInteraction() else endEditInteraction() }

    LaunchedEffect(sessionHandle) {
        val handle = sessionHandle
        if (handle == 0L) return@LaunchedEffect

        val initialJson =
            withContext(Dispatchers.Default) {
                val currentTransform = adjustments.toMaskTransformState()
                adjustments.toJson(masksForRenderWithAiRemap(masks, currentTransform, adjustments))
            }
        renderRequests.trySend(
            RenderRequest(
                version = renderVersion.incrementAndGet(),
                adjustmentsJson = initialJson,
                target = RenderTarget.Edited,
                rotationDegrees = adjustments.rotation
            )
        )

        var currentRequest = renderRequests.receive()
        while (true) {
            while (true) {
                val next = renderRequests.tryReceive().getOrNull() ?: break
                currentRequest = next
            }

            val requestVersion = currentRequest.version
            val requestJson = currentRequest.adjustmentsJson
            val requestTarget = currentRequest.target
            val requestRotation = currentRequest.rotationDegrees
            val requestPreviewRoi = currentRequest.previewRoi
            val isViewportRequest = requestTarget == RenderTarget.Edited && requestPreviewRoi != null

            fun updateBitmapForRequest(version: Long, quality: Int, bitmap: Bitmap) {
                val stamp = version * 10L + quality.coerceIn(0, 9).toLong()
                when (requestTarget) {
                    RenderTarget.Original -> {
                        if (stamp > lastOriginalPreviewStamp.get()) {
                            originalBitmap = bitmap
                            lastOriginalPreviewStamp.set(stamp)
                        }
                    }

                    RenderTarget.UncroppedEdited -> {
                        if (stamp > lastUncroppedPreviewStamp.get()) {
                            uncroppedBitmap = bitmap
                            lastUncroppedPreviewStamp.set(stamp)
                            uncroppedPreviewRotationDegrees = requestRotation
                        }
                    }

                    RenderTarget.Edited -> {
                        if (requestPreviewRoi != null) {
                            if (stamp > lastEditedViewportStamp.get()) {
                                editedViewportBitmap = bitmap
                                editedViewportRoi = requestPreviewRoi
                                lastEditedViewportStamp.set(stamp)
                            }
                        } else if (stamp > lastEditedPreviewStamp.get()) {
                            editedBitmap = bitmap
                            lastEditedPreviewStamp.set(stamp)
                            editedPreviewRotationDegrees = requestRotation
                        }
                    }
                }
            }

            if (lowQualityPreviewEnabled) {
                if (!isViewportRequest) {
                    val superLowBitmap =
                        withContext(renderDispatcher) {
                            val bytes = runCatching { LibRawDecoder.lowlowdecodeFromSession(handle, requestJson) }.getOrNull()
                            bytes?.decodeToBitmap()
                        }
                    if (superLowBitmap != null) updateBitmapForRequest(version = requestVersion, quality = 0, bitmap = superLowBitmap)

                    val maybeUpdatedAfterSuperLow = withTimeoutOrNull(60) { renderRequests.receive() }
                    if (maybeUpdatedAfterSuperLow != null) {
                        currentRequest = maybeUpdatedAfterSuperLow
                        continue
                    }
                }

                val lowBitmap =
                    withContext(renderDispatcher) {
                        val bytes = runCatching { LibRawDecoder.lowdecodeFromSession(handle, requestJson) }.getOrNull()
                        bytes?.decodeToBitmap()
                    }
                if (lowBitmap != null) updateBitmapForRequest(version = requestVersion, quality = 1, bitmap = lowBitmap)

                val maybeUpdatedAfterLow = withTimeoutOrNull(180) { renderRequests.receive() }
                if (maybeUpdatedAfterLow != null) {
                    currentRequest = maybeUpdatedAfterLow
                    continue
                }
            }

            isLoading = true
            val fullBitmap =
                withContext(renderDispatcher) {
                    val bytes = runCatching { LibRawDecoder.decodeFromSession(handle, requestJson) }.getOrNull()
                    bytes?.decodeToBitmap()
                }
            isLoading = false

            val isLatest = requestVersion == renderVersion.get()
            if (requestTarget == RenderTarget.Edited && requestPreviewRoi == null && isLatest) {
                errorMessage = if (fullBitmap == null) "Failed to render preview." else null
            }
            if (fullBitmap != null) updateBitmapForRequest(version = requestVersion, quality = 2, bitmap = fullBitmap)

            if (requestTarget == RenderTarget.Edited && requestPreviewRoi == null && isLatest) fullBitmap?.let { bmp ->
                withContext(Dispatchers.IO) {
                    val maxSize = 1024
                    val scale = minOf(maxSize.toFloat() / bmp.width, maxSize.toFloat() / bmp.height)
                    val scaledWidth = (bmp.width * scale).toInt()
                    val scaledHeight = (bmp.height * scale).toInt()
                    val thumbnail = Bitmap.createScaledBitmap(bmp, scaledWidth, scaledHeight, true)

                    val outputStream = java.io.ByteArrayOutputStream()
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    val thumbnailBytes = outputStream.toByteArray()
                    storage.saveThumbnail(galleryItem.projectId, thumbnailBytes)
                    if (thumbnail != bmp) thumbnail.recycle()
                }
            }

            currentRequest = renderRequests.receive()
        }
    }

    val selectedMaskForEdit = masks.firstOrNull { it.id == selectedMaskId }
    val selectedSubMaskForEdit = selectedMaskForEdit?.subMasks?.firstOrNull { it.id == selectedSubMaskId }
    var selectedMaskForOverlay by remember { mutableStateOf<MaskState?>(null) }
    LaunchedEffect(selectedMaskId, masks, currentMaskTransform, environmentMaskingEnabled) {
        val baseMask =
            selectedMaskForEdit?.let { mask ->
                if (environmentMaskingEnabled) {
                    mask
                } else {
                    val remaining = mask.subMasks.filterNot { it.type == SubMaskType.AiEnvironment.id }
                    if (remaining.isEmpty()) null else mask.copy(subMasks = remaining)
                }
            }
        if (baseMask == null) {
            selectedMaskForOverlay = null
            return@LaunchedEffect
        }
        selectedMaskForOverlay =
            withContext(Dispatchers.Default) {
                remapAiMasksForTransform(listOf(baseMask), currentMaskTransform, adjustments).firstOrNull()
            }
    }
    val isMaskMode = panelTab == EditorPanelTab.Masks
    val isInteractiveMaskingEnabled =
        isMaskMode && isPaintingMask && selectedMaskId != null && selectedSubMaskId != null &&
                (selectedSubMaskForEdit?.type == SubMaskType.Brush.id || selectedSubMaskForEdit?.type == SubMaskType.AiSubject.id)

    val onMaskTap: ((MaskPoint) -> Unit)? =
        if (!isMaskMode || maskTapMode == MaskTapMode.None) null
        else tap@{ point ->
            val maskId = selectedMaskId
            val subId = selectedSubMaskId
            if (maskId == null || subId == null) {
                maskTapMode = MaskTapMode.None
                return@tap
            }
            masks =
                masks.map { mask ->
                    if (mask.id != maskId) return@map mask
                    mask.copy(
                        subMasks =
                            mask.subMasks.map { sub ->
                                if (sub.id != subId) return@map sub
                                when (maskTapMode) {
                                    MaskTapMode.SetRadialCenter ->
                                        if (sub.type != SubMaskType.Radial.id) sub else sub.copy(radial = sub.radial.copy(centerX = point.x, centerY = point.y))

                                    MaskTapMode.SetLinearStart ->
                                        if (sub.type != SubMaskType.Linear.id) sub else sub.copy(linear = sub.linear.copy(startX = point.x, startY = point.y))

                                    MaskTapMode.SetLinearEnd ->
                                        if (sub.type != SubMaskType.Linear.id) sub else sub.copy(linear = sub.linear.copy(endX = point.x, endY = point.y))

                                    MaskTapMode.None -> sub
                                }
                            }
                    )
                }
            maskTapMode = MaskTapMode.None
        }

    val onBrushStrokeFinished: (List<MaskPoint>, Float) -> Unit = onBrush@{ points, brushSizeNorm ->
        val maskId = selectedMaskId ?: return@onBrush
        val subId = selectedSubMaskId ?: return@onBrush
        if (points.isEmpty()) return@onBrush
        val newLine =
            BrushLineState(
                tool = if (brushTool == BrushTool.Eraser) "eraser" else "brush",
                brushSize = brushSizeNorm,
                feather = if (brushTool == BrushTool.Eraser) eraserSoftness else brushSoftness,
                order = strokeOrder.incrementAndGet(),
                points = points
            )
        masks =
            masks.map { mask ->
                if (mask.id != maskId) return@map mask
                mask.copy(subMasks = mask.subMasks.map { sub -> if (sub.id != subId) sub else sub.copy(lines = sub.lines + newLine) })
            }
        showMaskOverlay = true
    }

    val onSubMaskHandleDrag: (MaskHandle, MaskPoint) -> Unit = onDrag@{ handle, point ->
        val maskId = selectedMaskId ?: return@onDrag
        val subId = selectedSubMaskId ?: return@onDrag
        masks =
            masks.map { mask ->
                if (mask.id != maskId) return@map mask
                mask.copy(
                    subMasks =
                        mask.subMasks.map { sub ->
                            if (sub.id != subId) return@map sub
                            when (handle) {
                                MaskHandle.RadialCenter ->
                                    if (sub.type != SubMaskType.Radial.id) sub else sub.copy(radial = sub.radial.copy(centerX = point.x, centerY = point.y))
                                MaskHandle.LinearStart ->
                                    if (sub.type != SubMaskType.Linear.id) sub else sub.copy(linear = sub.linear.copy(startX = point.x, startY = point.y))
                                MaskHandle.LinearEnd ->
                                    if (sub.type != SubMaskType.Linear.id) sub else sub.copy(linear = sub.linear.copy(endX = point.x, endY = point.y))
                            }
                        }
                )
            }
        showMaskOverlay = true
    }

    val aiSubjectMaskGenerator = remember { AiSubjectMaskGenerator(context) }
    val aiEnvironmentMaskGenerator = remember(environmentMaskingEnabled) { if (environmentMaskingEnabled) AiEnvironmentMaskGenerator(context) else null }

    var aiEnvironmentSourceBitmap by remember(galleryItem.projectId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(sessionHandle) {
        aiEnvironmentSourceBitmap?.recycle()
        aiEnvironmentSourceBitmap = null
    }

    suspend fun getAiEnvironmentSourceBitmap(handle: Long): Bitmap? {
        aiEnvironmentSourceBitmap?.let { return it }
        val baseJson = AdjustmentState().toJson(emptyList())
        val bytes =
            withContext(renderDispatcher) {
                runCatching { LibRawDecoder.decodeFromSession(handle, baseJson) }.getOrNull()
            } ?: return null
        val decoded = bytes.decodeToBitmap() ?: return null
        aiEnvironmentSourceBitmap = decoded
        return decoded
    }

    fun missingSubjectModels(): List<ModelInfo> {
        return missingModels(
            context,
            listOf(ModelInfo("Subject AI model (U2Net)", U2NetOnnxSegmenter.MODEL_FILENAME))
        )
    }

    fun missingEnvironmentModels(): List<ModelInfo> {
        return missingModels(
            context,
            listOf(
                ModelInfo("Environment AI model (Cityscapes)", AiEnvironmentMaskGenerator.CITYSCAPES_MODEL_FILENAME),
                ModelInfo("Environment AI model (ADE20K)", AiEnvironmentMaskGenerator.ADE20K_MODEL_FILENAME)
            )
        )
    }

    fun requestModelDownloadConfirmation(
        missing: List<ModelInfo>,
        action: PendingModelDownloadAction,
        subjectPoints: List<MaskPoint> = emptyList()
    ): Boolean {
        if (missing.isEmpty()) return false
        pendingModelDownloadNames = missing.map { it.displayName }
        pendingModelDownloadAction = action
        if (subjectPoints.isNotEmpty()) pendingSubjectMaskPoints = subjectPoints
        showModelDownloadDialog = true
        return true
    }

    fun createMaskForType(type: SubMaskType) {
        val newMaskId = UUID.randomUUID().toString()
        val newSubId = UUID.randomUUID().toString()
        val subMask = newSubMaskState(newSubId, SubMaskMode.Additive, type)
        val maskName =
            when (type) {
                SubMaskType.AiEnvironment -> AiEnvironmentCategory.fromId(subMask.aiEnvironment.category).label
                SubMaskType.AiSubject -> "Subject"
                SubMaskType.Brush -> "Brush"
                SubMaskType.Linear -> "Gradient"
                SubMaskType.Radial -> "Radial"
            }
        val newMask =
            MaskState(
                id = newMaskId,
                name = maskName,
                subMasks = listOf(subMask)
            )
        assignNumber(newMaskId)
        masks = masks + newMask
        selectedMaskId = newMaskId
        selectedSubMaskId = newSubId
        isPaintingMask = type == SubMaskType.Brush || type == SubMaskType.AiSubject
        requestMaskOverlayBlink(null)
    }

    fun addSubMaskToMask(maskId: String, mode: SubMaskMode, type: SubMaskType) {
        if (masks.none { it.id == maskId }) return
        val newSubId = UUID.randomUUID().toString()
        masks =
            masks.map { m ->
                if (m.id != maskId) m
                else m.copy(subMasks = m.subMasks + newSubMaskState(newSubId, mode, type))
            }
        selectedMaskId = maskId
        selectedSubMaskId = newSubId
        isPaintingMask = type == SubMaskType.Brush || type == SubMaskType.AiSubject
        requestMaskOverlayBlink(newSubId)
    }

    fun startSubjectModelDownloadAndCreateMask(pending: PendingSubjectMaskCreation) {
        coroutineScope.launch {
            val ready = runCatching { aiSubjectMaskGenerator.ensureModelReady() }.isSuccess
            if (!ready) return@launch
            if (pending.createNewMask) {
                createMaskForType(pending.type)
            } else {
                val target = pending.targetMaskId ?: return@launch
                addSubMaskToMask(target, pending.mode, pending.type)
            }
        }
    }

    fun startSubjectMaskGeneration(points: List<MaskPoint>) {
        val maskId = selectedMaskId ?: return
        val subId = selectedSubMaskId ?: return
        val bmp = editedBitmap ?: return
        if (points.size < 3) return

        coroutineScope.launch {
            isGeneratingAiMask = true
            statusMessage = "Generating subject mask..."
            val dataUrl =
                runCatching {
                    aiSubjectMaskGenerator.generateSubjectMaskDataUrl(
                        previewBitmap = bmp,
                        lassoPoints = points.map { NormalizedPoint(it.x, it.y) }
                    )
                }.getOrNull()

            if (dataUrl == null) {
                statusMessage = "Failed to generate subject mask."
            } else {
                val baseTransform = adjustments.toMaskTransformState()
                val (baseW, baseH) = estimateBaseDimsFromBitmap(bmp, baseTransform)
                masks =
                    masks.map { mask ->
                        if (mask.id != maskId) return@map mask
                        mask.copy(
                            subMasks =
                                mask.subMasks.map { sub ->
                                    if (sub.id != subId) sub
                                    else
                                        sub.copy(
                                            aiSubject =
                                                sub.aiSubject.copy(
                                                    maskDataBase64 = dataUrl,
                                                    baseTransform = baseTransform,
                                                    baseWidthPx = baseW,
                                                    baseHeightPx = baseH
                                                )
                                        )
                                }
                        )
                    }
                showMaskOverlay = true
                statusMessage = "Subject mask added."
            }
            isGeneratingAiMask = false
            delay(1500)
            if (statusMessage == "Subject mask added.") statusMessage = null
        }
    }

    fun startGenerateAiEnvironmentMask() {
        if (!environmentMaskingEnabled) return
        val maskId = selectedMaskId ?: return
        val subId = selectedSubMaskId ?: return
        val handle = sessionHandle
        if (handle == 0L) return
        val sub =
            masks.firstOrNull { it.id == maskId }?.subMasks?.firstOrNull { it.id == subId }
                ?: return
        if (sub.type != SubMaskType.AiEnvironment.id) return

        val category = AiEnvironmentCategory.fromId(sub.aiEnvironment.category)
        coroutineScope.launch {
            isGeneratingAiMask = true
            statusMessage = "Generating ${category.label.lowercase()} mask..."
            val result =
                runCatching {
                    val generator = aiEnvironmentMaskGenerator ?: error("Environment masking disabled.")
                    val srcBmp = getAiEnvironmentSourceBitmap(handle) ?: error("Failed to render environment mask source.")
                    generator.generateEnvironmentMaskDataUrl(
                        previewBitmap = srcBmp,
                        category = category
                    )
                }

            val dataUrl = result.getOrNull()
            if (dataUrl == null) {
                val err = result.exceptionOrNull()
                val detail = err?.message?.takeIf { it.isNotBlank() } ?: err?.javaClass?.simpleName
                statusMessage = if (detail.isNullOrBlank()) "Failed to generate environment mask." else "Failed to generate environment mask: $detail"
            } else {
                val srcBmp = aiEnvironmentSourceBitmap
                val baseTransform = MaskTransformState()
                val baseDims = srcBmp?.let { estimateBaseDimsFromBitmap(it, baseTransform) }
                masks =
                    masks.map { mask ->
                        if (mask.id != maskId) return@map mask
                        mask.copy(
                            subMasks =
                                mask.subMasks.map { s ->
                                    if (s.id != subId) s
                                    else
                                        s.copy(
                                            aiEnvironment =
                                                s.aiEnvironment.copy(
                                                    maskDataBase64 = dataUrl,
                                                    baseTransform = baseTransform,
                                                    baseWidthPx = baseDims?.first,
                                                    baseHeightPx = baseDims?.second
                                                )
                                        )
                                }
                        )
                    }
                showMaskOverlay = true
                statusMessage = "${category.label} mask added."
            }
            isGeneratingAiMask = false
            delay(1500)
            if (statusMessage == "${category.label} mask added.") statusMessage = null
        }
    }

    fun startDetectAiEnvironmentCategories() {
        if (!environmentMaskingEnabled) return
        if (isDetectingAiEnvironmentCategories) return
        if (detectedAiEnvironmentCategories != null) return
        val handle = sessionHandle
        if (handle == 0L) return

        coroutineScope.launch {
            isDetectingAiEnvironmentCategories = true
            val detected =
                runCatching {
                    val generator = aiEnvironmentMaskGenerator ?: error("Environment masking disabled.")
                    val srcBmp = getAiEnvironmentSourceBitmap(handle) ?: error("Failed to render environment mask source.")
                    generator.detectAvailableCategories(srcBmp)
                }.getOrNull()
            if (detected != null) detectedAiEnvironmentCategories = detected
            isDetectingAiEnvironmentCategories = false
        }
    }

    val onCreateMask: (SubMaskType) -> Unit = onCreateMask@{ type ->
        if (type == SubMaskType.AiSubject) {
            val missing = missingSubjectModels()
            if (requestModelDownloadConfirmation(missing, PendingModelDownloadAction.SubjectMaskSelect)) {
                pendingSubjectMaskCreation =
                    PendingSubjectMaskCreation(
                        createNewMask = true,
                        targetMaskId = null,
                        mode = SubMaskMode.Additive,
                        type = type
                    )
                return@onCreateMask
            }
        }
        createMaskForType(type)
    }

    val onCreateSubMask: (SubMaskMode, SubMaskType) -> Unit = onCreateSubMask@{ mode, type ->
        val targetMaskId = selectedMaskId ?: return@onCreateSubMask
        if (type == SubMaskType.AiSubject) {
            val missing = missingSubjectModels()
            if (requestModelDownloadConfirmation(missing, PendingModelDownloadAction.SubjectMaskSelect)) {
                pendingSubjectMaskCreation =
                    PendingSubjectMaskCreation(
                        createNewMask = false,
                        targetMaskId = targetMaskId,
                        mode = mode,
                        type = type
                    )
                return@onCreateSubMask
            }
        }
        addSubMaskToMask(targetMaskId, mode, type)
    }
    val onLassoFinished: (List<MaskPoint>) -> Unit = onLasso@{ points ->
        if (selectedMaskId == null || selectedSubMaskId == null || editedBitmap == null) return@onLasso
        if (points.size < 3) return@onLasso
        val missing = missingSubjectModels()
        if (requestModelDownloadConfirmation(missing, PendingModelDownloadAction.SubjectMask, points)) return@onLasso
        startSubjectMaskGeneration(points)
    }

    val onGenerateAiEnvironmentMask: (() -> Unit)? = if (!environmentMaskingEnabled) null else fun() {
        val maskId = selectedMaskId ?: return
        val subId = selectedSubMaskId ?: return
        val sub =
            masks.firstOrNull { it.id == maskId }?.subMasks?.firstOrNull { it.id == subId }
                ?: return
        if (sub.type != SubMaskType.AiEnvironment.id) return
        val missing = missingEnvironmentModels()
        if (requestModelDownloadConfirmation(missing, PendingModelDownloadAction.EnvironmentMask)) return
        startGenerateAiEnvironmentMask()
    }

    val onDetectAiEnvironmentCategories: (() -> Unit)? = if (!environmentMaskingEnabled) null else fun() {
        if (isDetectingAiEnvironmentCategories) return
        if (detectedAiEnvironmentCategories != null) return
        val handle = sessionHandle
        if (handle == 0L) return
        val missing = missingEnvironmentModels()
        if (requestModelDownloadConfirmation(missing, PendingModelDownloadAction.EnvironmentDetect)) return
        startDetectAiEnvironmentCategories()
    }

    val canUndo = editHistoryIndex > 0
    val canRedo = editHistoryIndex in 0 until editHistory.lastIndex

    fun undo() {
        val nextIndex = editHistoryIndex - 1
        val entry = editHistory.getOrNull(nextIndex) ?: return
        editHistoryIndex = nextIndex
        applyEditHistoryEntry(entry)
    }

    fun redo() {
        val nextIndex = editHistoryIndex + 1
        val entry = editHistory.getOrNull(nextIndex) ?: return
        editHistoryIndex = nextIndex
        applyEditHistoryEntry(entry)
    }

    val onPreviewViewportRoiChange: (CropState?, Float) -> Unit = { roi, scale ->
        viewportScale = scale
        viewportRoiForDebounce = roi
        currentViewportRoi.set(roi)
        if (roi == null) {
            editedViewportBitmap = null
            editedViewportRoi = null
        }
    }

    LaunchedEffect(sessionHandle, isCropMode, isComparingOriginal) {
        val handle = sessionHandle
        if (handle == 0L) return@LaunchedEffect
        if (isCropMode) return@LaunchedEffect
        if (isComparingOriginal) return@LaunchedEffect

        snapshotFlow { viewportRoiForDebounce?.normalized() to viewportScale }
            .debounce(1000)
            .collect { (roi, scale) ->
                if (isDraggingMaskHandle) return@collect
                if (sessionHandle != handle) return@collect
                if (isCropMode) return@collect
                if (isComparingOriginal) return@collect

                if (roi != null) {
                    val maxDim = zoomMaxDimensionForScale(scale)
                    val json =
                        withContext(Dispatchers.Default) {
                            val currentTransform = adjustments.toMaskTransformState()
                            adjustments.toJsonObject(includeToneMapper = true).apply {
                                put(
                                    "masks",
                                    JSONArray().apply {
                                        masksForRenderWithAiRemap(masks, currentTransform, adjustments).forEach { put(it.toJsonObject()) }
                                    }
                                )
                                put(
                                    "preview",
                                    JSONObject().apply {
                                        put("useZoom", true)
                                        put("roi", roi.toJsonObject())
                                        put("maxDimension", maxDim)
                                    }
                                )
                            }.toString()
                        }
                    val version = renderVersion.incrementAndGet()
                    renderRequests.trySend(
                        RenderRequest(
                            version = version,
                            adjustmentsJson = json,
                            target = RenderTarget.Edited,
                            rotationDegrees = adjustments.rotation,
                            previewRoi = roi
                        )
                    )
                } else if (fullPreviewDirtyByViewport) {
                    fullPreviewDirtyByViewport = false
                    val json =
                        withContext(Dispatchers.Default) {
                            val currentTransform = adjustments.toMaskTransformState()
                            adjustments.toJson(masksForRenderWithAiRemap(masks, currentTransform, adjustments))
                        }
                    val version = renderVersion.incrementAndGet()
                    renderRequests.trySend(
                        RenderRequest(version = version, adjustmentsJson = json, target = RenderTarget.Edited, rotationDegrees = adjustments.rotation)
                    )
                }
            }
    }

    MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val onSelectPanelTab: (EditorPanelTab) -> Unit = { tab ->
                when (tab) {
                    EditorPanelTab.Masks -> {
                        maskTapMode = MaskTapMode.None
                    }
                    else -> {
                        isPaintingMask = false
                        maskTapMode = MaskTapMode.None
                    }
                }
                panelTab = tab
            }

            val cropAspectRatio = if (isCropMode) adjustments.aspectRatio else null
            val configuration = LocalConfiguration.current
            val isTabletLayout = configuration.screenWidthDp >= 900
            val tabletControlsWidth = if (configuration.screenWidthDp >= 1200) 320.dp else 280.dp

            @Composable
            fun PreviewPane(modifier: Modifier = Modifier) {
                Box(modifier = modifier) {
                    Box {
                        ImagePreview(
                            bitmap = if (isComparingOriginal) originalBitmap ?: displayBitmap else displayBitmap,
                            viewportBitmap = if (isComparingOriginal || isCropMode) null else editedViewportBitmap,
                            viewportRoi = if (isComparingOriginal || isCropMode) null else editedViewportRoi,
                            onViewportRoiChange = onPreviewViewportRoiChange,
                            maskOverlay = selectedMaskForOverlay,
                            activeSubMask = selectedSubMaskForEdit,
                            isMaskMode = isMaskMode && !isComparingOriginal,
                            showMaskOverlay = showMaskOverlay && !isComparingOriginal,
                            maskOverlayBlinkKey = maskOverlayBlinkKey,
                            maskOverlayBlinkSubMaskId = maskOverlayBlinkSubMaskId,
                            isPainting = isInteractiveMaskingEnabled && !isComparingOriginal,
                            brushSize = brushSize,
                            maskTapMode = if (isComparingOriginal) MaskTapMode.None else maskTapMode,
                            onMaskTap = if (isComparingOriginal) null else onMaskTap,
                            requestBeforePreview = { requestIdentityPreview() },
                            onBrushStrokeFinished = onBrushStrokeFinished,
                            onLassoFinished = onLassoFinished,
                            onSubMaskHandleDrag = onSubMaskHandleDrag,
                            onSubMaskHandleDragStateChange = { isDraggingMaskHandle = it },
                            onRequestAiSubjectOverride = {
                                val maskId = selectedMaskId
                                val subId = selectedSubMaskId
                                if (maskId != null && subId != null) {
                                    aiSubjectOverrideTarget = maskId to subId
                                    showAiSubjectOverrideDialog = true
                                }
                            },
                            isCropMode = isCropMode && !isComparingOriginal,
                            cropState = if (isCropMode && !isComparingOriginal) (cropDraft ?: adjustments.crop) else null,
                            cropAspectRatio = cropAspectRatio,
                            extraRotationDegrees = previewRotationDelta,
                            isStraightenActive = isCropMode && isStraightenActive && !isComparingOriginal,
                            onStraightenResult = { rotation ->
                                beginEditInteraction()
                                val baseRatio =
                                    (cropBaseWidthPx?.toFloat()?.coerceAtLeast(1f) ?: 1f) /
                                        (cropBaseHeightPx?.toFloat()?.coerceAtLeast(1f) ?: 1f)
                                val autoCrop =
                                    computeMaxCropNormalized(
                                        AutoCropParams(
                                            baseAspectRatio = baseRatio,
                                            rotationDegrees = rotation,
                                            aspectRatio = adjustments.aspectRatio
                                        )
                                    )
                                cropDraft = autoCrop
                                rotationDraft = null
                                isStraightenActive = false
                                applyAdjustmentsPreservingMasks(adjustments.copy(rotation = rotation, crop = autoCrop))
                                endEditInteraction()
                            },
                            onCropDraftChange = { cropDraft = it },
                            onCropInteractionStart = {
                                isCropGestureActive = true
                                beginEditInteraction()
                            },
                            onCropInteractionEnd = { crop ->
                                isCropGestureActive = false
                                cropDraft = crop
                                applyAdjustmentsPreservingMasks(adjustments.copy(crop = crop))
                                endEditInteraction()
                            }
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            contentColor = Color.White
                        ) {
                            Row(
                                modifier = Modifier.height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                IconButton(
                                    onClick = ::undo,
                                    enabled = canUndo,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    ),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                                        contentDescription = "Undo",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                VerticalDivider(
                                    thickness = 1.dp,
                                    color = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .fillMaxHeight()
                                )

                                IconButton(
                                    onClick = ::redo,
                                    enabled = canRedo,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    ),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Redo,
                                        contentDescription = "Redo",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            LaunchedEffect(isPressed) {
                                isComparingOriginal = isPressed
                                if (isPressed && originalBitmap == null) {
                                    originalBitmap = requestIdentityPreview()
                                }
                            }

                            Surface(
                                onClick = { },
                                interactionSource = interactionSource,
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CompareArrows,
                                        contentDescription = "Compare",
                                        modifier = Modifier
                                            .size(26.dp)
                                            .rotate(45f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            @Composable
            fun ControlsPane(modifier: Modifier = Modifier) {
                Surface(
                    modifier = modifier,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shape = if (isTabletLayout) RoundedCornerShape(topStart = 24.dp) else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    AnimatedContent(
                        targetState = panelTab,
                        transitionSpec = {
                            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                                initialOffsetX = { it * direction }
                            ) + fadeIn(animationSpec = tween(200))).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                                    targetOffsetX = { -it * direction }
                                ) + fadeOut(animationSpec = tween(200))
                            )
                        },
                        label = "TabAnimation"
                    ) { currentTab ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .background(color = MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            statusMessage?.let { Text(text = it, color = Color(0xFF1B5E20), style = MaterialTheme.typography.bodySmall) }

                            EditorControlsContent(
                                panelTab = currentTab,
                                adjustments = adjustments,
                                onAdjustmentsChange = { applyAdjustmentsPreservingMasks(it) },
                                onBeginEditInteraction = ::beginEditInteraction,
                                onEndEditInteraction = ::endEditInteraction,
                                histogramData = histogramData,
                                masks = masks,
                                onMasksChange = { updated ->
                                    if (panelTab == EditorPanelTab.Masks && showMaskOverlay) showMaskOverlay = false
                                    masks = updated
                                },
                                maskNumbers = maskNumbers,
                                selectedMaskId = selectedMaskId,
                                onSelectedMaskIdChange = { selectedMaskId = it },
                                selectedSubMaskId = selectedSubMaskId,
                                onSelectedSubMaskIdChange = { selectedSubMaskId = it },
                                isPaintingMask = isPaintingMask,
                                onPaintingMaskChange = { isPaintingMask = it },
                                showMaskOverlay = showMaskOverlay,
                                onShowMaskOverlayChange = { showMaskOverlay = it },
                                onRequestMaskOverlayBlink = ::requestMaskOverlayBlink,
                                onCreateMask = onCreateMask,
                                onCreateSubMask = onCreateSubMask,
                                brushSize = brushSize,
                                onBrushSizeChange = { brushSize = it },
                                brushTool = brushTool,
                                onBrushToolChange = { brushTool = it },
                                brushSoftness = brushSoftness,
                                onBrushSoftnessChange = { brushSoftness = it },
                                eraserSoftness = eraserSoftness,
                                onEraserSoftnessChange = { eraserSoftness = it },
                                maskTapMode = maskTapMode,
                                onMaskTapModeChange = { maskTapMode = it },
                                cropBaseWidthPx = cropBaseWidthPx,
                                cropBaseHeightPx = cropBaseHeightPx,
                                rotationDraft = rotationDraft,
                                onRotationDraftChange = { rotationDraft = it },
                                isStraightenActive = isStraightenActive,
                                onStraightenActiveChange = { isStraightenActive = it },
                                environmentMaskingEnabled = environmentMaskingEnabled,
                                isGeneratingAiMask = isGeneratingAiMask,
                                onGenerateAiEnvironmentMask = onGenerateAiEnvironmentMask,
                                detectedAiEnvironmentCategories = detectedAiEnvironmentCategories,
                                isDetectingAiEnvironmentCategories = isDetectingAiEnvironmentCategories,
                                onDetectAiEnvironmentCategories = onDetectAiEnvironmentCategories,
                                maskRenameTags = maskRenameTags
                            )
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                }
            }

            @Composable
            fun ExportOverlayButton(
                modifier: Modifier = Modifier,
                content: @Composable () -> Unit
            ) {
                Box(modifier = modifier) {
                    content()
                    ExportButton(
                        label = "",
                        sessionHandle = sessionHandle,
                        adjustments = adjustments,
                        masks = exportMasks,
                        originImmichAssetId = galleryItem.immichAssetId,
                        originImmichAlbumId = galleryItem.immichAlbumId,
                        sourceFileName = galleryItem.fileName,
                        isExporting = isExporting,
                        nativeDispatcher = renderDispatcher,
                        context = context,
                        onExportStart = { isExporting = true },
                        onExportComplete = { success, message ->
                            isExporting = false
                            if (success) {
                                if (message.startsWith("Saved to ")) {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    statusMessage = null
                                } else {
                                    statusMessage = message
                                }
                                errorMessage = null
                            } else {
                                errorMessage = message
                                statusMessage = null
                            }
                        },
                        modifier = Modifier.matchParentSize().alpha(0f)
                    )
                }
            }

            @Composable
            fun PanelToolbar() {
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                        toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        toolbarContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    floatingActionButton = {
                        ExportOverlayButton {
                            FloatingToolbarDefaults.StandardFloatingActionButton(
                                onClick = { },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(Icons.Filled.Download, "Export")
                            }
                        }
                    },
                    content = {
                        EditorPanelTab.entries.forEach { tab ->
                            val selected = panelTab == tab
                            IconButton(onClick = { onSelectPanelTab(tab) }) {
                                Icon(
                                    imageVector = if (selected) tab.iconSelected else tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }

            @Composable
            fun TabletBottomBar(modifier: Modifier = Modifier) {
                BottomAppBar(
                    modifier = modifier,
                    windowInsets = WindowInsets(0),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    actions = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            EditorPanelTab.entries.forEach { tab ->
                                val selected = panelTab == tab
                                IconButton(onClick = { onSelectPanelTab(tab) }) {
                                    Icon(
                                        imageVector = if (selected) tab.iconSelected else tab.icon,
                                        contentDescription = tab.label,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .windowInsetsPadding(WindowInsets.statusBars),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val req = lastImmichSidecarSyncRequest
                                        if (req != null) {
                                            uploadIridisSidecarToImmich(req, force = true, showToastOnFailure = true)
                                        }
                                        onBackClick()
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = CircleShape) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = galleryItem.fileName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (immichDescriptionSyncEnabled && isImmichSidecarSyncing) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            LoadingIndicator(
                                                modifier = Modifier.size(12.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isTabletLayout) {
                                    ExportOverlayButton(
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        IconButton(
                                            enabled = sessionHandle != 0L && !isExporting,
                                            onClick = { },
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                            )
                                        ) {
                                            Icon(
                                                Icons.Filled.Download,
                                                contentDescription = "Export",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { openEditTimeline() },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                    )
                                ) {
                                    Icon(Icons.Filled.History, contentDescription = "Edit timeline", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(
                                    enabled = sessionHandle != 0L,
                                    onClick = { showMetadataDialog = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)) {
                            if (isTabletLayout) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    PreviewPane(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                    Column(
                                        modifier = Modifier
                                            .width(tabletControlsWidth)
                                            .fillMaxHeight()
                                    ) {
                                        ControlsPane(
                                            Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                        )
                                        TabletBottomBar(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                        )
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    PreviewPane(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    )
                                    ControlsPane(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(360.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 16.dp)
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                        .zIndex(1f)
                                ) {
                                    PanelToolbar()
                                }
                            }
                        }
                    }


                Surface(
                    modifier = Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.surface
                ) {}
            }
        }
    }

    if (showEditTimelineDialog) {
        val formatter = remember { java.text.DateFormat.getDateTimeInstance() }
        val treeItems =
            remember(editTimelineEntries) {
                buildVersionTreeItems(editTimelineEntries)
            }
        val lineColor = MaterialTheme.colorScheme.outlineVariant
        AlertDialog(
            onDismissRequest = { showEditTimelineDialog = false },
            confirmButton = { TextButton(onClick = { showEditTimelineDialog = false }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { createManualVersion() }) { Text("New version") } },
            title = { Text("Version tree") },
            text = {
                if (treeItems.isEmpty()) {
                    Text("No edits yet.")
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Tap a node to apply it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        treeItems.forEach { node ->
                            val entry = node.entry
                            val isCurrent = entry.id == currentRevisionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .clickable(enabled = !isCurrent) { applyVersionEntry(entry) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                node.guides.forEach { hasLine ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(12.dp)
                                    ) {
                                        if (hasLine) {
                                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                                val centerX = size.width / 2f
                                                drawLine(
                                                    color = lineColor,
                                                    start = androidx.compose.ui.geometry.Offset(centerX, 0f),
                                                    end = androidx.compose.ui.geometry.Offset(centerX, size.height),
                                                    strokeWidth = 2f
                                                )
                                            }
                                        }
                                    }
                                }
                                val sourceColor =
                                    if (entry.source == ProjectStorage.EditHistorySource.Immich) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.tertiary
                                    }
                                val currentColor = MaterialTheme.colorScheme.primary
                                val currentInnerColor = MaterialTheme.colorScheme.onPrimary
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(18.dp)
                                ) {
                                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f
                                        val endY = if (node.isLast && !node.hasChildren) centerY else size.height
                                        drawLine(
                                            color = lineColor,
                                            start = androidx.compose.ui.geometry.Offset(centerX, 0f),
                                            end = androidx.compose.ui.geometry.Offset(centerX, endY),
                                            strokeWidth = 2f
                                        )
                                        drawLine(
                                            color = lineColor,
                                            start = androidx.compose.ui.geometry.Offset(centerX, centerY),
                                            end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                                            strokeWidth = 2f
                                        )
                                        val radius = size.minDimension * 0.22f
                                        drawCircle(
                                            color = if (isCurrent) currentColor else sourceColor,
                                            radius = radius,
                                            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                                        )
                                        if (isCurrent) {
                                            drawCircle(
                                                color = currentInnerColor,
                                                radius = radius * 0.5f,
                                                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val sourceLabel =
                                        if (entry.source == ProjectStorage.EditHistorySource.Immich) "Immich" else "Local"
                                    val title =
                                        if (isCurrent) "$sourceLabel edit (current)" else "$sourceLabel edit"
                                    Text(title, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        formatter.format(java.util.Date(entry.updatedAtMs)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        )
    }

    if (showMetadataDialog) {
        val handle = sessionHandle
        LaunchedEffect(handle) {
            if (handle != 0L && metadataJson == null) {
                metadataJson = withContext(renderDispatcher) { runCatching { LibRawDecoder.getMetadataJsonFromSession(handle) }.getOrNull() }
            }
        }

        val pairs =
            remember(metadataJson) {
                val json = metadataJson ?: return@remember emptyList()
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
                        "Date" to obj.optString("dateTimeOriginal")
                    ).filter { it.second.isNotBlank() && it.second != "null" }
                }.getOrDefault(emptyList())
            }

        AlertDialog(
            onDismissRequest = { showMetadataDialog = false },
            confirmButton = { TextButton(onClick = { showMetadataDialog = false }) { Text("Close") } },
            title = { Text("RAW Metadata") },
            text = {
                if (metadataJson == null) {
                    Text("Reading metadata…")
                } else if (pairs.isEmpty()) {
                    Text("No metadata available.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pairs.forEach { (k, v) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(v, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        )
    }

    if (showModelDownloadDialog) {
        val modelsText = pendingModelDownloadNames.joinToString(", ")
        val message =
            if (modelsText.isBlank()) {
                "This feature needs to download AI model files from the internet. Your photos stay on-device; only model files are downloaded."
            } else {
                "This feature needs to download: $modelsText. The models are downloaded from third-party servers. Your photos stay on-device; only model files are downloaded."
            }
        AlertDialog(
            onDismissRequest = { clearModelDownloadState() },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = pendingModelDownloadAction
                        val subjectPoints = pendingSubjectMaskPoints
                        val pendingCreation = pendingSubjectMaskCreation
                        clearModelDownloadState()
                        when (action) {
                            PendingModelDownloadAction.SubjectMask -> startSubjectMaskGeneration(subjectPoints)
                            PendingModelDownloadAction.EnvironmentMask -> startGenerateAiEnvironmentMask()
                            PendingModelDownloadAction.EnvironmentDetect -> startDetectAiEnvironmentCategories()
                            PendingModelDownloadAction.SubjectMaskSelect -> {
                                if (pendingCreation != null) startSubjectModelDownloadAndCreateMask(pendingCreation)
                            }
                            null -> Unit
                        }
                    }
                ) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { clearModelDownloadState() }) { Text("Cancel") }
            },
            title = { Text("Download AI model(s)?") },
            text = { Text(message) }
        )
    }

    if (showAiSubjectOverrideDialog) {
        AlertDialog(
            onDismissRequest = {
                showAiSubjectOverrideDialog = false
                aiSubjectOverrideTarget = null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = aiSubjectOverrideTarget
                        if (target != null) {
                            val (maskId, subId) = target
                            masks =
                                masks.map { mask ->
                                    if (mask.id != maskId) return@map mask
                                    mask.copy(
                                        subMasks =
                                            mask.subMasks.map { sub ->
                                                if (sub.id != subId) sub else sub.copy(aiSubject = sub.aiSubject.copy(maskDataBase64 = null))
                                            }
                                    )
                                }
                            showMaskOverlay = true
                            statusMessage = "Cleared subject mask. Draw a new one."
                        }
                        showAiSubjectOverrideDialog = false
                        aiSubjectOverrideTarget = null
                    }
                ) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { showAiSubjectOverrideDialog = false; aiSubjectOverrideTarget = null }) { Text("Cancel") } },
            title = { Text("Replace subject mask?") },
            text = { Text("This will delete the current subject mask so you can draw a new one.") }
        )
    }
}
