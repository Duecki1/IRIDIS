@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dueckis.kawaiiraweditor.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.BuildConfig

@Composable
internal fun AboutDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val versionName = try {
        BuildConfig.VERSION_NAME
    } catch (_: Exception) {
        "?"
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("About IRIDIS") },
        text = {
            Column {
                Text(
                    "Android app by Duecki1 using image processing from RapidRaw by CyberTimon.\n\nIRIDIS is an open-source RAW photo editor for Android, leveraging the RapidRaw engine for fast and high-quality image processing.\n\nImmich integration supported."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Version: $versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("OK") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Duecki1/IRIDIS"))
                    context.startActivity(intent)
                }) {
                    Text("IRIDIS GitHub")
                }
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CyberTimon/RapidRaw"))
                    context.startActivity(intent)
                }) {
                    Text("RapidRaw GitHub")
                }
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/immich-app/immich"))
                    context.startActivity(intent)
                }) {
                    Text("Immich GitHub")
                }
            }
        }
    )
}
