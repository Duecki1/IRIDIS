package com.dueckis.kawaiiraweditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.ui.theme.KawaiiRawEditorTheme
import com.homesoft.photo.libraw.LibRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.Field
import java.nio.Buffer
import java.nio.ByteBuffer

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

    // This buffer is allocated only once and then reused.
    var reusableBuffer by remember { mutableStateOf<ByteBuffer?>(null) }
    var displayedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var sliderPosition by remember { mutableStateOf(1.0f) }
    var debouncedExposure by remember { mutableStateOf(1.0f) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isLoading = true
                error = null
                reusableBuffer = null
                displayedBitmap = null
                try {
                    val rawBytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (rawBytes != null) {
                        // Allocate the expensive buffer only ONCE.
                        reusableBuffer = ByteBuffer.allocateDirect(rawBytes.size).apply {
                            put(rawBytes)
                            flip()
                        }
                        sliderPosition = 1.0f
                        debouncedExposure = 1.0f
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
        debouncedExposure = sliderPosition
    }

    LaunchedEffect(reusableBuffer, debouncedExposure) {
        val buffer = reusableBuffer ?: return@LaunchedEffect
        isLoading = true
        val newBitmap = withContext(Dispatchers.Default) {
            decodeFromBuffer(buffer, debouncedExposure)
        }
        if (newBitmap != null) {
            displayedBitmap = newBitmap
        } else {
            error = "Failed to decode image with new exposure."
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
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (displayedBitmap != null) {
                Image(
                    bitmap = displayedBitmap!!.asImageBitmap(),
                    contentDescription = "RAW Image",
                    modifier = Modifier.fillMaxSize()
                )
            } else error?.let { Text(text = it, color = androidx.compose.ui.graphics.Color.Red) }
            if (isLoading) {
                CircularProgressIndicator()
            }
        }

        if (reusableBuffer != null) {
            Spacer(Modifier.height(16.dp))
            Text("Exposure: ${"%.2f".format(debouncedExposure)}")
            Slider(
                value = sliderPosition,
                onValueChange = { newValue -> sliderPosition = newValue },
                valueRange = 0.25f..4.0f,
            )
        }
    }
}

fun getDirectBufferAddress(buffer: ByteBuffer): Long {
    val addressField: Field = Buffer::class.java.getDeclaredField("address")
    addressField.isAccessible = true
    return addressField.getLong(buffer)
}

fun decodeFromBuffer(buffer: ByteBuffer, exposure: Float): Bitmap? {
    return try {
        // CRITICAL: Rewind the buffer before each use to reset its position to 0.
        buffer.rewind()
        val address = getDirectBufferAddress(buffer)

        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        try {
            val rawOptions = IntArray(5)
            rawOptions[0] = 1
            rawOptions[1] = 0
            rawOptions[2] = 3
            rawOptions[3] = 0
            rawOptions[4] = (exposure * 1000).toInt()
            val field = options.javaClass.getField("inRawOptions")
            field.set(options, rawOptions)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        LibRaw.decodeBitmap(address, buffer.capacity(), options)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewRawImagePicker() {
    KawaiiRawEditorTheme {
        RawImagePicker()
    }
}