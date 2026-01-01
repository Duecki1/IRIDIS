package com.dueckis.kawaiiraweditor.ios

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.window.ComposeUIViewController
import com.dueckis.kawaiiraweditor.ios.model.IosGalleryItem
import com.dueckis.kawaiiraweditor.ios.picker.IosRawPicker
import com.dueckis.kawaiiraweditor.ios.storage.IosProjectStorage
import com.dueckis.kawaiiraweditor.ios.ui.IosGalleryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val storage = remember { IosProjectStorage() }
    var items by remember { mutableStateOf<List<IosGalleryItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            val metas = withContext(Dispatchers.Default) { storage.getAllProjects() }
            items = metas.map { meta ->
                IosGalleryItem(
                    projectId = meta.id,
                    fileName = meta.fileName,
                    rating = meta.rating,
                    tags = meta.tags,
                    rawMetadata = meta.rawMetadata
                )
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Presenter is the ComposeUIViewController itself (this works fine for modal pickers)
    val presenter = remember { this@ComposeUIViewController }
    val picker = remember(presenter) {
        IosRawPicker(presenter = presenter, onPicked = { files ->
            if (files.isEmpty()) return@IosRawPicker
            scope.launch {
                withContext(Dispatchers.Default) {
                    files.forEach { f -> storage.importRawFile(f.name, f.bytes) }
                }
                refresh()
            }
        })
    }

    MaterialTheme {
        IosGalleryScreen(
            items = items,
            onImportClick = { picker.present() },
            onDelete = { ids ->
                scope.launch {
                    withContext(Dispatchers.Default) { ids.forEach { storage.deleteProject(it) } }
                    refresh()
                }
            }
        )
    }
}
