package com.dueckis.kawaiiraweditor

import android.graphics.Bitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var isLoading by remember { mutableStateOf(false) }

    var sliderPosition by remember { mutableStateOf(1.0f) }
    var debouncedExposure by remember { mutableStateOf(1.0f) }

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

    LaunchedEffect(originalRawBytes, debouncedExposure) {
        val rawBytes = originalRawBytes ?: return@LaunchedEffect

        isLoading = true
        // Call our new JNI-powered decoder
        val newBitmap = withContext(Dispatchers.Default) {
            LibRawDecoder.decode(rawBytes, debouncedExposure)
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
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Read the state variable into a local immutable variable
            val currentBitmap = displayedBitmap
            val currentError = error

            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "RAW Image",
                    modifier = Modifier.fillMaxSize()
                )
            } else if (currentError != null) {
                // Use the local variable here
                Text(text = currentError, color = androidx.compose.ui.graphics.Color.Red)
            }
            if (isLoading) {
                CircularProgressIndicator()
            }
        }

        if (originalRawBytes != null) {
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

@Preview(showBackground = true)
@Composable
fun PreviewRawImagePicker() {
    KawaiiRawEditorTheme {
        RawImagePicker()
    }
}