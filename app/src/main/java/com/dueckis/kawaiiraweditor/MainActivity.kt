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
import com.dueckis.kawaiiraweditor.data.immich.IMMICH_OAUTH_APP_REDIRECT_URI
import com.dueckis.kawaiiraweditor.domain.update.StartupUpdateInfo
import com.dueckis.kawaiiraweditor.domain.update.fetchStartupUpdateInfo
import com.dueckis.kawaiiraweditor.domain.update.getInstalledVersionName
import com.dueckis.kawaiiraweditor.ui.startup.StartupSplash
import com.dueckis.kawaiiraweditor.ui.theme.KawaiiRawEditorTheme

class MainActivity : ComponentActivity() {

    private val pendingProjectToOpenState = mutableStateOf<String?>(null)
    private val pendingImmichOAuthRedirectState = mutableStateOf<String?>(null)

    /**
     * Inspects the intent for Project IDs or OAuth redirects.
     * IMPORTANT: If an OAuth redirect is found, it extracts the URL and then CLEARS
     * the intent.data to prevent the UI from trying to open it as a file (which causes a crash).
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Handle Project ID (e.g. from Widget)
        intent.getStringExtra("project_id")?.let {
            pendingProjectToOpenState.value = it
            intent.removeExtra("project_id")
        }

        val data = intent.data ?: return
        val scheme = data.scheme?.lowercase() ?: return

        // 2. Ignore standard file/web schemes so the Gallery can handle imports normally
        if (scheme == "http" || scheme == "https" || scheme == "content" || scheme == "file") {
            return
        }

        // 3. Detect OAuth Redirects
        var isOAuth = false

        // Check A: Explicit standard Immich scheme
        if (scheme == "app.immich") {
            isOAuth = true
        }
        // Check B: Configured custom scheme
        else {
            val expectedScheme = Uri.parse(IMMICH_OAUTH_APP_REDIRECT_URI).scheme?.lowercase()
            if (expectedScheme != null && scheme == expectedScheme) {
                isOAuth = true
            }
            // Check C: Heuristics for other callback formats (codes, states)
            else {
                val hasState = !data.getQueryParameter("state").isNullOrBlank()
                val hasCode = !data.getQueryParameter("code").isNullOrBlank()
                val hasError = !data.getQueryParameter("error").isNullOrBlank() ||
                        !data.getQueryParameter("error_description").isNullOrBlank()

                // Fragment check (some providers put params in fragment)
                val fragment = data.fragment.orEmpty()
                val fragmentParams = if (fragment.isNotBlank()) {
                    runCatching { Uri.parse("dummy://oauth/?$fragment") }.getOrNull()
                } else null

                val hasFragmentState = !fragmentParams?.getQueryParameter("state").isNullOrBlank()
                val hasFragmentCode = !fragmentParams?.getQueryParameter("code").isNullOrBlank()
                val hasFragmentError = !fragmentParams?.getQueryParameter("error").isNullOrBlank() ||
                        !fragmentParams?.getQueryParameter("error_description").isNullOrBlank()

                if (hasState || hasCode || hasError || hasFragmentState || hasFragmentCode || hasFragmentError) {
                    isOAuth = true
                }
            }
        }

        // 4. If identified as OAuth, extract URL and consume the data to prevent crash
        if (isOAuth) {
            pendingImmichOAuthRedirectState.value = data.toString()
            intent.data = null // <--- CRITICAL FIX: Stops GalleryScreen from reading this as a file
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle intent immediately before UI setup
        handleIntent(intent)

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
                val pendingProjectToOpen by pendingProjectToOpenState
                val pendingImmichOAuthRedirect by pendingImmichOAuthRedirectState

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
                        KawaiiApp(
                            pendingProjectToOpen = pendingProjectToOpen,
                            pendingImmichOAuthRedirect = pendingImmichOAuthRedirect,
                            onProjectOpened = { pendingProjectToOpenState.value = null },
                            onImmichOAuthHandled = { pendingImmichOAuthRedirectState.value = null }
                        )
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
        handleIntent(intent) // Process new intent (OAuth callback or Widget click)
        super.onNewIntent(intent)
        intent?.let { setIntent(it) }
    }
}