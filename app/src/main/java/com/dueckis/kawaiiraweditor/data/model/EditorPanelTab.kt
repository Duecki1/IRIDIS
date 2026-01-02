package com.dueckis.kawaiiraweditor.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class EditorPanelTab(
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector
) {
    CropTransform("Crop", Icons.Outlined.Crop, Icons.Filled.Crop),
    Adjustments("Adjust", Icons.Outlined.Tune, Icons.Filled.Tune),
    Color("Color", Icons.Outlined.Palette, Icons.Filled.Palette),
    Effects("Effects", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    Masks("Masking", Icons.Outlined.Layers, Icons.Filled.Layers),
}
