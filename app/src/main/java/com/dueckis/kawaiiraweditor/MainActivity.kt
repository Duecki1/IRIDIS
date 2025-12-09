package com.dueckis.kawaiiraweditor

import android.graphics.Bitmap
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.ui.theme.KawaiiRawEditorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainTab { CUT_ROTATE, ADJUSTMENTS, MASKS, EXPORT }
enum class AdjustSubTab { LIGHT, COLOR, EFFECTS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KawaiiRawEditorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RawImagePicker(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun RawImagePicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var originalRawBytes by remember { mutableStateOf<ByteArray?>(null) }
    var displayedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    // Slider is in EV stops (Lightroom-like). 0 = neutral, range -20..+20 for headroom.
    var sliderPosition by remember { mutableStateOf(0.0f) }
    var debouncedExposureMultiplier by remember { mutableStateOf(1.0f) }
    var rotateDegrees by remember { mutableStateOf(0f) }

    // Placeholder states for upcoming adjustments (not yet applied)
    var contrast by remember { mutableStateOf(0f) }
    var whites by remember { mutableStateOf(0f) }
    var blacks by remember { mutableStateOf(0f) }
    var highlights by remember { mutableStateOf(0f) }
    var shadows by remember { mutableStateOf(0f) }
    var temperature by remember { mutableStateOf(0f) }
    var tint by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(0f) }
    var vibrance by remember { mutableStateOf(0f) }

    var mainTab by remember { mutableStateOf(MainTab.ADJUSTMENTS) }
    var adjustSubTab by remember { mutableStateOf(AdjustSubTab.LIGHT) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isLoading = true
                error = null
                originalRawBytes = null
                displayedBitmap = null
                try {
                    val rawBytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (rawBytes != null) {
                        originalRawBytes = rawBytes
                        sliderPosition = 0.0f
                        debouncedExposureMultiplier = 1.0f
                    } else {
                        error = "Could not read file."
                        isLoading = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    error = "Exception: ${e.localizedMessage}"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(sliderPosition) {
        delay(200)
        // Map EV stops to a multiplier: exposureMultiplier = 2^EV.
        debouncedExposureMultiplier = 2.0f.pow(sliderPosition)
    }

    LaunchedEffect(originalRawBytes, debouncedExposureMultiplier) {
        val rawBytes = originalRawBytes ?: return@LaunchedEffect

        isLoading = true
        // Call our new JNI-powered decoder
        val newBitmap = withContext(Dispatchers.Default) {
            LibRawDecoder.decode(rawBytes, debouncedExposureMultiplier)
        }
        if (newBitmap != null) {
            displayedBitmap = newBitmap
        } else {
            error = "Failed to decode image with new exposure. Check Logcat for JNI errors."
        }
        isLoading = false
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { launcher.launch(arrayOf("image/x-sony-arw", "image/*")) }) {
            Text("Select ARW Image")
        }
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val currentBitmap = displayedBitmap
            val currentError = error

            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "RAW Image",
                    modifier = Modifier.fillMaxSize()
                )
            } else if (currentError != null) {
                Text(text = currentError, color = androidx.compose.ui.graphics.Color.Red)
            }
            if (isLoading) {
                CircularProgressIndicator()
            }
        }

        Spacer(Modifier.height(12.dp))

        // Main content area driven by selected tab
        when (mainTab) {
            MainTab.CUT_ROTATE -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Text("Rotate")
                    Slider(
                        value = rotateDegrees,
                        onValueChange = { rotateDegrees = it },
                        valueRange = -180f..180f
                    )
                    Text("${rotateDegrees.toInt()} deg (coming soon)")
                }
            }
            MainTab.ADJUSTMENTS -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    val tabs = listOf(
                        AdjustSubTab.LIGHT to "Light",
                        AdjustSubTab.COLOR to "Color",
                        AdjustSubTab.EFFECTS to "Effects"
                    )
                    TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == adjustSubTab }) {
                        tabs.forEachIndexed { index, pair ->
                            Tab(
                                selected = adjustSubTab == pair.first,
                                onClick = { adjustSubTab = pair.first },
                                text = { Text(pair.second) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (adjustSubTab) {
                            AdjustSubTab.LIGHT -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val ev = sliderPosition
                                    Text("Brightness: ${if (ev >= 0) "+" else ""}${"%.1f".format(ev)} EV")
                                    Slider(
                                        value = sliderPosition,
                                        onValueChange = { newValue -> sliderPosition = newValue },
                                        valueRange = -20f..20f,
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    Text("Contrast (coming soon)")
                                    Slider(
                                        value = contrast,
                                        onValueChange = { contrast = it },
                                        valueRange = -100f..100f,
                                        enabled = false
                                    )

                                    Text("Whites (coming soon)")
                                    Slider(
                                        value = whites,
                                        onValueChange = { whites = it },
                                        valueRange = -100f..100f,
                                        enabled = false
                                    )

                                    Text("Blacks (coming soon)")
                                    Slider(
                                        value = blacks,
                                        onValueChange = { blacks = it },
                                        valueRange = -100f..100f,
                                        enabled = false
                                    )

                                    Text("Highlights (coming soon)")
                                    Slider(
                                        value = highlights,
                                        onValueChange = { highlights = it },
                                        valueRange = -100f..100f,
                                        enabled = false
                                    )

                                    Text("Shadows (coming soon)")
                                    Slider(
                                        value = shadows,
                                        onValueChange = { shadows = it },
                                        valueRange = -100f..100f,
                                        enabled = false
                                    )
                                }
                            }
                            AdjustSubTab.COLOR -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("Temperature (coming soon)")
                                    Slider(
                                        value = temperature,
                                        onValueChange = { temperature = it },
                                        valueRange = -100f..100f,
                                        enabled = false
                                    )
                                    Text("Tint (coming soon)")
                                    Slider(
                                        value = tint,
                                        onValueChange = { tint = it },
                                        valueRange = -100f..100f,
                                        enabled = false
                                    )
                                    Text("Saturation (coming soon)")
                                    Slider(
                                        value = saturation,
                                        onValueChange = { saturation = it },
                                        valueRange = -100f..100f,
                                        enabled = false
                                    )
                                    Text("Vibrance (coming soon)")
                                    Slider(
                                        value = vibrance,
                                        onValueChange = { vibrance = it },
                                        valueRange = -100f..100f,
                                        enabled = false
                                    )
                                }
                            }
                            AdjustSubTab.EFFECTS -> {
                                Text("Effects coming soon")
                            }
                        }
                    }
                }
            }
            MainTab.MASKS -> {
                Text("Masks coming soon")
            }
            MainTab.EXPORT -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (displayedBitmap == null) {
                        Text("Load an image first")
                    }
                    Button(
                        enabled = !isLoading && !isExporting && displayedBitmap != null && originalRawBytes != null,
                        onClick = {
                            val rawBytes = originalRawBytes ?: return@Button
                            coroutineScope.launch {
                                isExporting = true
                                error = null
                                exportStatus = null
                                try {
                                    val fullBitmap = withContext(Dispatchers.Default) {
                                        LibRawDecoder.decodeFullRes(rawBytes, debouncedExposureMultiplier)
                                    }
                                    if (fullBitmap == null) {
                                        error = "Export failed: native decode returned null"
                                    } else {
                                        val uri = withContext(Dispatchers.IO) {
                                            saveJpegToPictures(context, fullBitmap)
                                        }
                                        if (uri != null) {
                                            exportStatus = "Exported to $uri"
                                        } else {
                                            error = "Export failed: could not save JPEG"
                                        }
                                    }
                                } catch (e: Exception) {
                                    error = "Export exception: ${e.localizedMessage}"
                                } finally {
                                    isExporting = false
                                }
                            }
                        }
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Export JPEG (Full Res)")
                    }
                    exportStatus?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        NavigationBar {
            NavigationBarItem(
                selected = mainTab == MainTab.CUT_ROTATE,
                onClick = { mainTab = MainTab.CUT_ROTATE },
                icon = { Text("Cut") },
                label = { Text("Cut/Rotate") }
            )
            NavigationBarItem(
                selected = mainTab == MainTab.ADJUSTMENTS,
                onClick = { mainTab = MainTab.ADJUSTMENTS },
                icon = { Text("Adj") },
                label = { Text("Adjust") }
            )
            NavigationBarItem(
                selected = mainTab == MainTab.MASKS,
                onClick = { mainTab = MainTab.MASKS },
                icon = { Text("Mask") },
                label = { Text("Masks") }
            )
            NavigationBarItem(
                selected = mainTab == MainTab.EXPORT,
                onClick = { mainTab = MainTab.EXPORT },
                icon = { Text("Exp") },
                label = { Text("Export") }
            )
        }
    }
}

private fun saveJpegToPictures(context: android.content.Context, bitmap: Bitmap): Uri? {
    val filename = "KawaiiRaw_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/KawaiiRawEditor")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    throw IllegalStateException("Bitmap.compress returned false")
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }
    return uri
}

@Preview(showBackground = true)
@Composable
fun PreviewRawImagePicker() {
    KawaiiRawEditorTheme {
        RawImagePicker()
    }
}
