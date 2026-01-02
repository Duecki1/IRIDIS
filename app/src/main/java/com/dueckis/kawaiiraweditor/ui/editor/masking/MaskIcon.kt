package com.dueckis.kawaiiraweditor.ui.editor.masking

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dueckis.kawaiiraweditor.data.model.SubMaskType

@Composable
internal fun MaskIcon(type: String, modifier: Modifier = Modifier) {
    val icon =
        when (type) {
            SubMaskType.AiSubject.id -> Icons.Outlined.AutoAwesome
            SubMaskType.AiEnvironment.id -> Icons.Outlined.AutoAwesome
            SubMaskType.Brush.id -> Icons.Filled.Edit
            SubMaskType.Linear.id -> Icons.Outlined.Tune
            SubMaskType.Radial.id -> Icons.Filled.RadioButtonChecked
            else -> Icons.Outlined.Layers
        }
    Icon(imageVector = icon, contentDescription = null, modifier = modifier)
}
