package com.dueckis.kawaiiraweditor.ios.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.ios.model.IosGalleryItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosGalleryScreen(
    items: List<IosGalleryItem>,
    onImportClick: () -> Unit,
    onDelete: (Set<String>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filtered = remember(items, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) items else items.filter { it.fileName.lowercase().contains(q) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kawaii RAW Gallery", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onImportClick) {
                Icon(Icons.Default.Add, contentDescription = "Import RAW")
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                label = { Text("Search") }
            )

            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No RAW files yet... tap + (^-^)")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered) { item ->
                        val isSelected = item.projectId in selectedIds
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = {
                                        selectedIds =
                                            if (selectedIds.isEmpty()) emptySet()
                                            else if (isSelected) selectedIds - item.projectId else selectedIds + item.projectId
                                    },
                                    onLongClick = {
                                        selectedIds =
                                            if (isSelected) selectedIds - item.projectId else selectedIds + item.projectId
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                // thumbnail not generated yet on iOS -> show RAW badge
                                Text(
                                    text = "RAW",
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.align(Alignment.Center)
                                )

                                Text(
                                    text = item.fileName,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalIconButton(onClick = { onDelete(selectedIds); selectedIds = emptySet() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}
