package com.dueckis.kawaiiraweditor.ui.editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.immich.ImmichAlbum
import com.dueckis.kawaiiraweditor.data.immich.ImmichConfig
import com.dueckis.kawaiiraweditor.data.immich.fetchImmichAlbums
import java.util.Locale

@Composable
internal fun ImmichAlbumPickerDialog(
    config: ImmichConfig,
    initialSelectionId: String?,
    onDismissRequest: () -> Unit,
    onAlbumSelected: (String?) -> Unit
) {
    var albums by remember { mutableStateOf<List<ImmichAlbum>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var selectedId by remember(initialSelectionId) { mutableStateOf(initialSelectionId.orEmpty()) }

    fun triggerReload() {
        reloadToken += 1
    }

    LaunchedEffect(config, reloadToken) {
        isLoading = true
        errorMessage = null
        val loaded = fetchImmichAlbums(config)
        if (loaded == null) {
            errorMessage = "Could not load Immich albums."
            albums = emptyList()
        } else {
            albums = loaded.sortedBy { it.name.lowercase(Locale.US) }
            if (selectedId.isNotBlank() && albums.none { it.id == selectedId }) {
                selectedId =
                    initialSelectionId?.takeIf { candidate -> albums.any { it.id == candidate } }
                        ?: ""
            }
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Choose Immich location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Select where Immich should store this export.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Loading albums...")
                        }
                    }
                    errorMessage != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(errorMessage ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = ::triggerReload) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> {
                        val rows = listOf<ImmichAlbum?>(null) + albums
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(rows, key = { it?.id ?: "__all__" }) { album ->
                                val rowId = album?.id.orEmpty()
                                val isSelected = selectedId == rowId
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedId = rowId }
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = isSelected, onClick = { selectedId = rowId })
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = album?.name ?: "All photos",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        val subtitle =
                                            album?.let { "${it.assetCount} items" }
                                                ?: "Upload without assigning an album"
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized = selectedId.takeIf { it.isNotBlank() }
                    onAlbumSelected(normalized)
                },
                enabled = !isLoading && errorMessage == null
            ) {
                Text("Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
