@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.dueckis.kawaiiraweditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dueckis.kawaiiraweditor.domain.update.StartupUpdateInfo
import com.dueckis.kawaiiraweditor.domain.update.fetchStartupUpdateInfo
import com.dueckis.kawaiiraweditor.domain.update.getInstalledVersionName
import com.dueckis.kawaiiraweditor.ui.startup.StartupSplash
import com.dueckis.kawaiiraweditor.ui.theme.KawaiiRawEditorTheme

class MainActivity : ComponentActivity() {
    // Simple bridge to pass an incoming project id to the composable app
    class IntentLaunchBridge(var pendingProjectToOpen: String? = null)

    private val launchBridge = IntentLaunchBridge()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Extract project_id from launch intent if present
        intent?.getStringExtra("project_id")?.let { launchBridge.pendingProjectToOpen = it }

        setTheme(R.style.Theme_KawaiiRawEditor)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KawaiiRawEditorTheme {
                val context = LocalContext.current
                var showStartupSplash by remember { mutableStateOf(true) }
                var updateInfo by remember { mutableStateOf<StartupUpdateInfo?>(null) }
                var didCheckForUpdates by remember { mutableStateOf(false) }
                val currentVersionName = remember(context) { getInstalledVersionName(context) }

                LaunchedEffect(showStartupSplash) {
                    if (showStartupSplash) return@LaunchedEffect
                    if (didCheckForUpdates) return@LaunchedEffect
                    didCheckForUpdates = true
                    updateInfo = fetchStartupUpdateInfo(
                        currentVersionName = currentVersionName,
                        githubOwner = BuildConfig.GITHUB_OWNER,
                        githubRepo = BuildConfig.GITHUB_REPO
                    )
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        KawaiiApp(launchBridge = launchBridge)
                        if (showStartupSplash) {
                            StartupSplash(onFinished = { showStartupSplash = false })
                        }
                    }
                }

                updateInfo?.let { info ->
                    AlertDialog(
                        onDismissRequest = { updateInfo = null },
                        title = { Text("Update available") },
                        text = {
                            Text("Version ${info.latestVersionName} is available (you have ${info.currentVersionName}).")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    updateInfo = null
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl))
                                    runCatching { context.startActivity(intent) }
                                }
                            ) {
                                Text("Update")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateInfo = null }) { Text("Later") }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // If the activity is already running and receives a widget click, pass the project id into the bridge
        intent?.getStringExtra("project_id")?.let { launchBridge.pendingProjectToOpen = it }
    }
}
