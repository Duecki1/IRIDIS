package com.dueckis.kawaiiraweditor.ui.editor.masking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.SubMaskMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskState
import com.dueckis.kawaiiraweditor.data.model.SubMaskType

@Composable
internal fun SubMaskItemChip(
    subMask: SubMaskState,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary) else null,
        modifier = modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = if (subMask.mode == SubMaskMode.Additive) "+" else "-",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(6.dp))
            MaskIcon(subMask.type, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text =
                    when (subMask.type) {
                        SubMaskType.AiSubject.id -> "Subject"
                        SubMaskType.AiEnvironment.id -> "Environment"
                        SubMaskType.Brush.id -> "Brush"
                        SubMaskType.Linear.id -> "Linear"
                        SubMaskType.Radial.id -> "Radial"
                        else -> "Edit"
                    },
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Menu",
                modifier =
                    Modifier
                        .size(16.dp)
                        .clickable { onMenuClick() }
            )
        }
    }
}
