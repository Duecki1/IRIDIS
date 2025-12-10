package com.dueckis.kawaiiraweditor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush as UiBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.ui.theme.KawaiiRawEditorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private enum class EditTab(val label: String, val icon: ImageVector) {
    Crop("Crop", Icons.Rounded.Crop),
    Adjust("Adjust", Icons.Rounded.Tune),
    Mask("Masks", Icons.Rounded.Brush),
    Export("Export", Icons.Rounded.IosShare)
}

private data class GalleryItem(
    val id: String,
    val title: String,
    val category: String,
    val rawBytes: ByteArray? = null,
    val uri: Uri? = null,
    val preview: Bitmap? = null,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KawaiiRawEditorTheme {
                KawaiiRawApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KawaiiRawApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedItem by remember { mutableStateOf<GalleryItem?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val galleryItems = remember { mutableStateListOf<GalleryItem>() }

    fun upsertItem(item: GalleryItem) {
        val idx = galleryItems.indexOfFirst { it.id == item.id }
        if (idx >= 0) galleryItems[idx] = item else galleryItems.add(0, item)
        selectedItem = item
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                val preview = bytes?.let {
                    withContext(Dispatchers.Default) {
                        runCatching {
                            LibRawDecoder.decode(it, 1f, 1f, 0f, 0f)
                        }.getOrNull()
                    }
                }
                val importedItem = GalleryItem(
                    id = uri.toString(),
                    title = displayNameForUri(context, uri),
                    category = "Imported RAW",
                    rawBytes = bytes,
                    uri = uri,
                    preview = preview
                )
                upsertItem(importedItem)
            }
        }
    }

    val filteredItems =
        if (query.isBlank()) galleryItems
        else galleryItems.filter { item ->
            item.title.contains(query, ignoreCase = true) ||
                item.category.contains(query, ignoreCase = true)
        }

    if (selectedItem == null) {
        GalleryScreen(
            items = filteredItems,
            query = query,
            expanded = expanded,
            onQueryChange = { query = it },
            onExpandedChange = { expanded = it },
            onItemClick = { selectedItem = it },
            onAddClick = { pickerLauncher.launch(arrayOf("image/x-sony-arw", "image/*")) }
        )
    } else {
        EditScreen(
            item = selectedItem!!,
            onBack = { selectedItem = null },
            onPickAnother = { pickerLauncher.launch(arrayOf("image/x-sony-arw", "image/*")) },
            onItemUpdate = { upsertItem(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryScreen(
    items: List<GalleryItem>,
    query: String,
    expanded: Boolean,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onItemClick: (GalleryItem) -> Unit,
    onAddClick: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Gallery") },
                    navigationIcon = {
                        IconButton(onClick = { /* menu */ }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* account */ }) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = "Account")
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                SearchBarTop(
                    query = query,
                    expanded = expanded,
                    onQueryChange = onQueryChange,
                    onExpandedChange = onExpandedChange,
                    onSearch = { onExpandedChange(false) }
                )
            }
        },
        bottomBar = { GalleryBottomBar(onAdd = onAddClick) }
    ) { innerPadding ->
        GalleryGrid(
            items = items,
            onItemClick = onItemClick,
            onAddClick = onAddClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarTop(
    query: String,
    expanded: Boolean,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onSearch: (String) -> Unit
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 0.dp else 20.dp,
        label = "searchCorners"
    )

    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp, top = 0.dp),
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        active = expanded,
        onActiveChange = onExpandedChange,
        placeholder = { Text("Search") },
        leadingIcon = {
            if (expanded) {
                IconButton(onClick = { onExpandedChange(false) }) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            } else {
                Icon(Icons.Rounded.Search, contentDescription = null)
            }
        },
        trailingIcon = {
            RowTrailingIcons(
                showClear = expanded || query.isNotBlank(),
                onClear = {
                    if (query.isNotBlank()) onQueryChange("")
                    else onExpandedChange(false)
                }
            )
        },
        shape = RoundedCornerShape(cornerRadius),
        tonalElevation = 0.dp,
        windowInsets = SearchBarDefaults.windowInsets,
        colors = SearchBarDefaults.colors()
    ) {
        SearchSuggestions(
            onSuggestionClick = { suggestion ->
                onQueryChange(suggestion)
                onExpandedChange(false)
            }
        )
    }
}

@Composable
private fun RowTrailingIcons(
    showClear: Boolean,
    onClear: () -> Unit
) {
    AnimatedVisibility(
        visible = showClear,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Rounded.Close, contentDescription = "Clear")
        }
    }
    IconButton(onClick = { /* mic */ }) {
        Icon(Icons.Filled.Mic, contentDescription = "Mic")
    }
}

@Composable
private fun GalleryBottomBar(
    onAdd: () -> Unit
) {
    BottomAppBar(
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = "Add image")
            }
        },
        actions = {}
    )
}

@Composable
private fun GalleryGrid(
    items: List<GalleryItem>,
    onItemClick: (GalleryItem) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyGalleryState(
            onAddClick = onAddClick,
            modifier = modifier
        )
        return
    }
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        modifier = modifier,
        state = gridState,
        columns = GridCells.Adaptive(minSize = 152.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { item ->
            GalleryCard(
                item = item,
                onClick = { onItemClick(item) }
            )
        }
    }
}

@Composable
private fun EmptyGalleryState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Keine Bilder vorhanden",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Füge RAWs aus deinen Dateien hinzu. Alle bearbeiteten Bilder erscheinen hier.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onAddClick) {
            Text("RAW importieren")
        }
    }
}

@Composable
private fun GalleryCard(
    item: GalleryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = CardDefaults.shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(CardDefaults.shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            item.preview?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(
                        UiBrush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                        ),
                        shape = RectangleShape
                    )
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.preview == null) {
                Text(
                    text = "Kein Vorschaubild",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchSuggestions(
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = listOf("Portrait", "Street", "Textures", "Minimal", "Architecture", "Nature")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Quick filters",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                AssistChip(
                    onClick = { onSuggestionClick(suggestion) },
                    label = { Text(suggestion) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditScreen(
    item: GalleryItem,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
    onItemUpdate: (GalleryItem) -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(EditTab.Adjust) }
    var exposure by remember(item.id) { mutableStateOf(item.exposure) }
    var contrast by remember(item.id) { mutableStateOf(item.contrast) }
    var shadows by remember(item.id) { mutableStateOf(item.shadows) }
    var highlights by remember(item.id) { mutableStateOf(item.highlights) }
    var whites by remember(item.id) { mutableStateOf(item.whites) }
    var blacks by remember(item.id) { mutableStateOf(item.blacks) }
    var displayedBitmap by remember(item.id) { mutableStateOf<Bitmap?>(item.preview) }
    var error by remember { mutableStateOf<String?>(null) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    var debouncedExposureMultiplier by remember { mutableStateOf(1.0f) }
    var debouncedContrast by remember { mutableStateOf(1.0f) }
    var debouncedWhites by remember { mutableStateOf(0f) }
    var debouncedBlacks by remember { mutableStateOf(0f) }

    fun persistCurrent(preview: Bitmap? = displayedBitmap) {
        onItemUpdate(
            item.copy(
                preview = preview ?: item.preview,
                exposure = exposure,
                contrast = contrast,
                shadows = shadows,
                highlights = highlights,
                whites = whites,
                blacks = blacks
            )
        )
    }

    LaunchedEffect(exposure) {
        delay(200)
        debouncedExposureMultiplier = 2.0f.pow(exposure)
    }
    LaunchedEffect(contrast) {
        delay(200)
        debouncedContrast = 1.0f + contrast / 100.0f
    }
    LaunchedEffect(whites) {
        delay(200)
        debouncedWhites = whites / 100.0f
    }
    LaunchedEffect(blacks) {
        delay(200)
        debouncedBlacks = blacks / 100.0f
    }

    LaunchedEffect(item.rawBytes, debouncedExposureMultiplier, debouncedContrast, debouncedWhites, debouncedBlacks) {
        val rawBytes = item.rawBytes ?: return@LaunchedEffect
        isLoading = true
        error = null
        val newBitmap = withContext(Dispatchers.Default) {
            runCatching {
                LibRawDecoder.decode(
                    rawBytes,
                    debouncedExposureMultiplier,
                    debouncedContrast,
                    debouncedWhites,
                    debouncedBlacks
                )
            }.getOrNull()
        }
        if (newBitmap != null) {
            displayedBitmap = newBitmap
            persistCurrent(newBitmap)
        } else {
            error = "Failed to decode image. Try another file."
        }
        isLoading = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            item.title.ifBlank { "Image.dng" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (item.category.isNotBlank()) item.category else "f2.8  •  ISO 100  •  28mm",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to gallery")
                    }
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { /* undo stack: go back */ }) {
                            Icon(Icons.Rounded.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = onPickAnother) {
                            Icon(Icons.Rounded.Add, contentDescription = "Replace image")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            EditBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            ) {
                ImagePreview(
                    bitmap = displayedBitmap,
                    isLoading = isLoading,
                    stateKey = item.id,
                    modifier = Modifier
                        .fillMaxSize()
                )
                VerticalToolBar(
                    onSelectCrop = { selectedTab = EditTab.Crop },
                    onSelectAdjust = { selectedTab = EditTab.Adjust },
                    onSelectMask = { selectedTab = EditTab.Mask },
                    onOpenSettings = onPickAnother,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp)
                )
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            exportStatus?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            EditTabCard(
                selectedTab = selectedTab,
                exposure = exposure,
                contrast = contrast,
                shadows = shadows,
                highlights = highlights,
                whites = whites,
                blacks = blacks,
                onExposureChange = {
                    exposure = it
                    persistCurrent()
                },
                onContrastChange = {
                    contrast = it
                    persistCurrent()
                },
                onShadowsChange = {
                    shadows = it
                    persistCurrent()
                },
                onHighlightsChange = {
                    highlights = it
                    persistCurrent()
                },
                onWhitesChange = {
                    whites = it
                    persistCurrent()
                },
                onBlacksChange = {
                    blacks = it
                    persistCurrent()
                },
                onExport = {
                    val rawBytes = item.rawBytes ?: return@EditTabCard
                    if (isExporting) return@EditTabCard
                    exportStatus = null
                    error = null
                    isExporting = true
                    scope.launch {
                        val fullBitmap = withContext(Dispatchers.Default) {
                            LibRawDecoder.decodeFullRes(
                                rawBytes,
                                debouncedExposureMultiplier,
                                debouncedContrast,
                                debouncedWhites,
                                debouncedBlacks
                            )
                        }
                        if (fullBitmap == null) {
                            error = "Export failed: could not decode image"
                        } else {
                            val uri = withContext(Dispatchers.IO) {
                                saveJpegToPictures(context, fullBitmap)
                            }
                            if (uri != null) {
                                exportStatus = "Exported to $uri"
                            } else {
                                error = "Export failed: could not save JPEG"
                            }
                        }
                        isExporting = false
                    }
                },
                isExporting = isExporting,
                hasRaw = item.rawBytes != null
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImagePreview(
    bitmap: Bitmap?,
    isLoading: Boolean,
    stateKey: String,
    modifier: Modifier = Modifier
) {
    var scale by rememberSaveable(stateKey) { mutableStateOf(1f) }
    var offsetX by rememberSaveable(stateKey) { mutableStateOf(0f) }
    var offsetY by rememberSaveable(stateKey) { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 8f)
        offsetX += panChange.x
        offsetY += panChange.y
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Image preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        )
                    }
                    .transformable(transformState)
            )
        } else {
            Text(
                text = "Kein Bild geladen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator()
            }
        }
    }
}

@Composable
private fun VerticalToolBar(
    onSelectCrop: () -> Unit,
    onSelectAdjust: () -> Unit,
    onSelectMask: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onSelectCrop) {
                Icon(Icons.Rounded.Crop, contentDescription = "Crop")
            }
            IconButton(onClick = onSelectAdjust) {
                Icon(Icons.Rounded.Tune, contentDescription = "Adjust")
            }
            IconButton(onClick = onSelectMask) {
                Icon(Icons.Rounded.Brush, contentDescription = "Mask")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Import/Settings")
            }
        }
    }
}

@Composable
private fun EditBottomBar(
    selectedTab: EditTab,
    onTabSelected: (EditTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        EditTab.values().forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

@Composable
private fun EditTabCard(
    selectedTab: EditTab,
    exposure: Float,
    contrast: Float,
    shadows: Float,
    highlights: Float,
    whites: Float,
    blacks: Float,
    onExposureChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onShadowsChange: (Float) -> Unit,
    onHighlightsChange: (Float) -> Unit,
    onWhitesChange: (Float) -> Unit,
    onBlacksChange: (Float) -> Unit,
    onExport: () -> Unit,
    isExporting: Boolean,
    hasRaw: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 240.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (selectedTab) {
                EditTab.Adjust -> {
                    Text(
                        text = "Adjustments",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdjustmentSlider("Exposure", exposure, -5f..5f, onExposureChange)
                        AdjustmentSlider("Contrast", contrast, -100f..100f, onContrastChange)
                        AdjustmentSlider("Shadows", shadows, -100f..100f, onShadowsChange, enabled = false)
                        AdjustmentSlider("Highlights", highlights, -100f..100f, onHighlightsChange, enabled = false)
                        AdjustmentSlider("Whites", whites, -100f..100f, onWhitesChange)
                        AdjustmentSlider("Blacks", blacks, -100f..100f, onBlacksChange)
                    }
                }
                EditTab.Export -> {
                    Text(
                        text = "Export",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (hasRaw) "Save your edit as a JPEG to Pictures" else "Pick a RAW file in the gallery to export.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.Button(
                        enabled = hasRaw && !isExporting,
                        onClick = onExport
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(18.dp)
                                    .width(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Export JPEG")
                    }
                }
                else -> {
                    Text(
                        text = selectedTab.label,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "This is where ${selectedTab.label.lowercase()} tools will appear.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    val sliderColors = SliderDefaults.colors(
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        thumbColor = MaterialTheme.colorScheme.primary,
        disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = String.format("%.1f", value),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            colors = sliderColors,
            modifier = Modifier.height(32.dp)
        )
    }
}

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalMaterial3ExpressiveApi

data class RoundedPolygon(val sides: Int, val cornerRadiusFraction: Float = 0.15f)

object LoadingIndicatorDefaults {
    val indicatorColor: Color
        @Composable get() = MaterialTheme.colorScheme.primary
    val IndeterminateIndicatorPolygons: List<RoundedPolygon> = listOf(
        RoundedPolygon(6, 0.18f),
        RoundedPolygon(5, 0.18f),
        RoundedPolygon(4, 0.18f)
    )
}

@ExperimentalMaterial3ExpressiveApi
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LoadingIndicatorDefaults.indicatorColor,
    polygons: List<RoundedPolygon> = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
) {
    val transition = rememberInfiniteTransition(label = "loadingIndicator")
    val scales = polygons.mapIndexed { index, _ ->
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    0.4f at 0
                    1f at 400
                    0.4f at 1200
                },
                initialStartOffset = StartOffset(
                    offsetMillis = 120 * index,
                    offsetType = StartOffsetType.Delay
                )
            ),
            label = "scale-$index"
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        polygons.forEachIndexed { index, polygon ->
            Canvas(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        alpha = 0.6f + 0.4f * scales[index].value
                    }
            ) {
                drawPolygonShape(
                    polygon = polygon,
                    color = color,
                    scale = scales[index].value
                )
            }
        }
    }
}

private fun DrawScope.drawPolygonShape(
    polygon: RoundedPolygon,
    color: Color,
    scale: Float
) {
    val radius = size.minDimension / 2f * scale
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val path = Path()
    val sides = polygon.sides.coerceAtLeast(3)
    for (i in 0 until sides) {
        val angle = 2 * PI * i / sides - PI / 2
        val x = centerX + radius * cos(angle).toFloat()
        val y = centerY + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = radius * 0.12f)
    )
}

private fun displayNameForUri(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "Imported RAW"
}

private fun saveJpegToPictures(context: Context, bitmap: Bitmap): Uri? {
    val filename = "KawaiiRaw_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/KawaiiRawEditor")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    throw IllegalStateException("Bitmap.compress returned false")
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }
    return uri
}
