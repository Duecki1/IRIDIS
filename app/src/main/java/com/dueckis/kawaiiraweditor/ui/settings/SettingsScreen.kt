@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dueckis.kawaiiraweditor.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.immich.ImmichAuthMode
import com.dueckis.kawaiiraweditor.data.immich.ImmichLoginResult
import com.dueckis.kawaiiraweditor.data.immich.ImmichOAuthStartResult
import com.dueckis.kawaiiraweditor.ui.dialogs.AboutDialog
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreen(
    lowQualityPreviewEnabled: Boolean,
    automaticTaggingEnabled: Boolean,
    environmentMaskingEnabled: Boolean,
    openEditorOnImportEnabled: Boolean,
    maskRenameTags: List<String>,
    immichServerUrl: String,
    immichAuthMode: ImmichAuthMode,
    immichLoginEmail: String,
    immichAccessToken: String,
    immichApiKey: String,
    onLowQualityPreviewEnabledChange: (Boolean) -> Unit,
    onAutomaticTaggingEnabledChange: (Boolean) -> Unit,
    onEnvironmentMaskingEnabledChange: (Boolean) -> Unit,
    onOpenEditorOnImportEnabledChange: (Boolean) -> Unit,
    onMaskRenameTagsChange: (List<String>) -> Unit,
    onImmichServerUrlChange: (String) -> Unit,
    onImmichAuthModeChange: (ImmichAuthMode) -> Unit,
    onImmichLoginEmailChange: (String) -> Unit,
    onImmichAccessTokenChange: (String) -> Unit,
    onImmichLogin: suspend (String, String, String) -> ImmichLoginResult?,
    onImmichOAuthStart: suspend (String) -> ImmichOAuthStartResult?,
    onImmichApiKeyChange: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showInfoDialog by remember { mutableStateOf(false) }
    var showImmichServerDialog by remember { mutableStateOf(false) }
    var showImmichApiKeyDialog by remember { mutableStateOf(false) }
    var showImmichLoginDialog by remember { mutableStateOf(false) }
    var immichServerDraft by remember { mutableStateOf("") }
    var immichApiKeyDraft by remember { mutableStateOf("") }
    var immichEmailDraft by remember { mutableStateOf("") }
    var immichPasswordDraft by remember { mutableStateOf("") }
    var immichLoginInFlight by remember { mutableStateOf(false) }
    var immichOauthInFlight by remember { mutableStateOf(false) }
    var immichLoginError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "App Info")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text("Low quality preview") },
                supportingContent = {
                    Text(
                        "Show fast, lower-detail previews while editing. Disabling renders previews at full quality only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(
                        checked = lowQualityPreviewEnabled,
                        onCheckedChange = onLowQualityPreviewEnabledChange
                    )
                },
                modifier = Modifier.clickable { onLowQualityPreviewEnabledChange(!lowQualityPreviewEnabled) }
            )
            ListItem(
                headlineContent = { Text("Enable automatic tagging") },
                supportingContent = {
                    Text(
                        "This will download an AI Model and automatically tag every raw file imported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(
                        checked = automaticTaggingEnabled,
                        onCheckedChange = onAutomaticTaggingEnabledChange
                    )
                },
                modifier = Modifier.clickable { onAutomaticTaggingEnabledChange(!automaticTaggingEnabled) }
            )
            ListItem(
                headlineContent = { Text("Enable environment AI masks") },
                supportingContent = {
                    Text(
                        "Adds an AI-driven environment mask tool that detects scene elements and generates masks. This is disabled by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(
                        checked = environmentMaskingEnabled,
                        onCheckedChange = onEnvironmentMaskingEnabledChange
                    )
                },
                modifier = Modifier.clickable { onEnvironmentMaskingEnabledChange(!environmentMaskingEnabled) }
            )
            ListItem(
                headlineContent = { Text("Open editor after import") },
                supportingContent = {
                    Text(
                        "Automatically open the imported RAW in the editor right away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ) },
                trailingContent = {
                    Switch(
                        checked = openEditorOnImportEnabled,
                        onCheckedChange = onOpenEditorOnImportEnabledChange
                    )
                },
                modifier = Modifier.clickable { onOpenEditorOnImportEnabledChange(!openEditorOnImportEnabled) }
            )

            ListItem(
                headlineContent = { Text("Immich server") },
                supportingContent = {
                    Text(
                        text = if (immichServerUrl.isNotBlank()) immichServerUrl else "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.clickable {
                    immichServerDraft = immichServerUrl
                    showImmichServerDialog = true
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InputChip(
                    selected = immichAuthMode == ImmichAuthMode.Login,
                    onClick = { onImmichAuthModeChange(ImmichAuthMode.Login) },
                    label = { Text("Login") },
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
                InputChip(
                    selected = immichAuthMode == ImmichAuthMode.ApiKey,
                    onClick = { onImmichAuthModeChange(ImmichAuthMode.ApiKey) },
                    label = { Text("API key") },
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }

            if (immichAuthMode == ImmichAuthMode.Login) {
                val loginSupporting = when {
                    immichServerUrl.isBlank() -> "Set the server URL first."
                    immichAccessToken.isNotBlank() -> {
                        if (immichLoginEmail.isNotBlank()) "Signed in as $immichLoginEmail" else "Signed in"
                    }
                    else -> "Tap to log in."
                }
                ListItem(
                    headlineContent = { Text("Immich account") },
                    supportingContent = {
                        Text(
                            text = loginSupporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        if (immichAccessToken.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    onImmichAccessTokenChange("")
                                    Toast.makeText(context, "Logged out.", Toast.LENGTH_SHORT).show()
                                }
                            ) { Text("Log out") }
                        }
                    },
                    modifier = Modifier.clickable {
                        immichEmailDraft = immichLoginEmail
                        immichPasswordDraft = ""
                        immichLoginError = null
                        showImmichLoginDialog = true
                    }
                )
            } else {
                ListItem(
                    headlineContent = { Text("Immich API key") },
                    supportingContent = {
                        Text(
                            text = if (immichApiKey.isNotBlank()) "Configured" else "Not set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable {
                        immichApiKeyDraft = immichApiKey
                        showImmichApiKeyDialog = true
                    }
                )
            }

            var showMaskTagsDialog by remember { mutableStateOf(false) }
            var maskTagsDraft by remember { mutableStateOf("") }

            ListItem(
                headlineContent = { Text("Mask rename tags") },
                supportingContent = {
                    Text(
                        "Used as quick-pick suggestions when renaming masks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = { Text("${maskRenameTags.size}") },
                modifier = Modifier.clickable {
                    maskTagsDraft = maskRenameTags.joinToString("\n")
                    showMaskTagsDialog = true
                }
            )

            if (showMaskTagsDialog) {
                AlertDialog(
                    onDismissRequest = { showMaskTagsDialog = false },
                    title = { Text("Mask rename tags") },
                    text = {
                        OutlinedTextField(
                            value = maskTagsDraft,
                            onValueChange = { maskTagsDraft = it },
                            label = { Text("One tag per line") },
                            minLines = 6
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val tags =
                                maskTagsDraft
                                    .lines()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .distinct()
                            onMaskRenameTagsChange(tags)
                            showMaskTagsDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMaskTagsDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (showImmichServerDialog) {
                AlertDialog(
                    onDismissRequest = { showImmichServerDialog = false },
                    title = { Text("Immich server") },
                    text = {
                        OutlinedTextField(
                            value = immichServerDraft,
                            onValueChange = { immichServerDraft = it },
                            label = { Text("Server URL") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onImmichServerUrlChange(immichServerDraft)
                            showImmichServerDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImmichServerDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (showImmichApiKeyDialog) {
                AlertDialog(
                    onDismissRequest = { showImmichApiKeyDialog = false },
                    title = { Text("Immich API key") },
                    text = {
                        OutlinedTextField(
                            value = immichApiKeyDraft,
                            onValueChange = { immichApiKeyDraft = it },
                            label = { Text("API key") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onImmichApiKeyChange(immichApiKeyDraft)
                            showImmichApiKeyDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImmichApiKeyDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (showImmichLoginDialog) {
                AlertDialog(
                    onDismissRequest = { showImmichLoginDialog = false },
                    title = { Text("Immich login") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = immichEmailDraft,
                                onValueChange = { immichEmailDraft = it },
                                label = { Text("Email") },
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = immichPasswordDraft,
                                onValueChange = { immichPasswordDraft = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                enabled = !immichOauthInFlight,
                                onClick = {
                                    val server = immichServerUrl.trim()
                                    if (server.isBlank()) {
                                        Toast.makeText(context, "Set the server URL first.", Toast.LENGTH_SHORT).show()
                                        return@TextButton
                                    }
                                    immichOauthInFlight = true
                                    immichLoginError = null
                                    coroutineScope.launch {
                                        val result = onImmichOAuthStart(server)
                                        immichOauthInFlight = false
                                        val authUrl = result?.authorizationUrl
                                        if (authUrl.isNullOrBlank()) {
                                            immichLoginError = result?.errorMessage ?: "OAuth login failed."
                                            return@launch
                                        }
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                        val launched = runCatching { context.startActivity(intent) }.isSuccess
                                        if (launched) {
                                            showImmichLoginDialog = false
                                            Toast.makeText(context, "Finish logging in with Immich.", Toast.LENGTH_SHORT)
                                                .show()
                                        } else {
                                            immichLoginError = "Could not open the browser."
                                        }
                                    }
                                }
                            ) { Text(if (immichOauthInFlight) "Opening browser..." else "Log in with browser") }
                            if (immichLoginError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = immichLoginError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !immichLoginInFlight,
                            onClick = {
                                val server = immichServerUrl.trim()
                                if (server.isBlank()) {
                                    Toast.makeText(context, "Set the server URL first.", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                if (immichEmailDraft.isBlank() || immichPasswordDraft.isBlank()) {
                                    Toast.makeText(context, "Enter email and password.", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                immichLoginInFlight = true
                                immichLoginError = null
                                coroutineScope.launch {
                                    val result = onImmichLogin(server, immichEmailDraft, immichPasswordDraft)
                                    immichLoginInFlight = false
                                    val token = result?.accessToken
                                    if (token.isNullOrBlank()) {
                                        immichLoginError = result?.errorMessage ?: "Login failed."
                                    } else {
                                        onImmichLoginEmailChange(immichEmailDraft)
                                        onImmichAccessTokenChange(token)
                                        immichPasswordDraft = ""
                                        showImmichLoginDialog = false
                                        Toast.makeText(context, "Logged in.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) { Text(if (immichLoginInFlight) "Logging in..." else "Log in") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImmichLoginDialog = false }) { Text("Cancel") }
                    }
                )
            }

        }
    }

    if (showInfoDialog) {
        AboutDialog(onDismissRequest = { showInfoDialog = false })
    }
}
