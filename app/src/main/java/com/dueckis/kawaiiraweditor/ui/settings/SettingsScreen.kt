@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dueckis.kawaiiraweditor.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dueckis.kawaiiraweditor.ui.dialogs.AboutDialog

@Composable
internal fun SettingsScreen(
    lowQualityPreviewEnabled: Boolean,
    automaticTaggingEnabled: Boolean,
    environmentMaskingEnabled: Boolean,
    openEditorOnImportEnabled: Boolean,
    maskRenameTags: List<String>,
    onLowQualityPreviewEnabledChange: (Boolean) -> Unit,
    onAutomaticTaggingEnabledChange: (Boolean) -> Unit,
    onEnvironmentMaskingEnabledChange: (Boolean) -> Unit,
    onOpenEditorOnImportEnabledChange: (Boolean) -> Unit,
    onMaskRenameTagsChange: (List<String>) -> Unit,
    onBackClick: () -> Unit
) {
    var showInfoDialog by remember { mutableStateOf(false) }

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

        }
    }

    if (showInfoDialog) {
        AboutDialog(onDismissRequest = { showInfoDialog = false })
    }
}
