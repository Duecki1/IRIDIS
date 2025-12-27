package com.dueckis.kawaiiraweditor.ui.editor.masking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dueckis.kawaiiraweditor.data.model.MaskState
import com.dueckis.kawaiiraweditor.data.model.SubMaskMode
import com.dueckis.kawaiiraweditor.data.model.SubMaskState
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

@Composable
internal fun MaskItemCard(
    mask: MaskState,
    maskIndex: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        border = border,
        modifier =
            Modifier
                .width(110.dp)
                .height(72.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                val mainType = mask.subMasks.firstOrNull()?.type.orEmpty()
                MaskIcon(mainType, modifier = Modifier.size(18.dp))

                if (mask.invert) {
                    Icon(
                        imageVector = Icons.Filled.CompareArrows,
                        contentDescription = "Inverted",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = mask.name.ifBlank { "Mask $maskIndex" },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clickable { onMenuClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Menu",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SubMaskItemChip(
    subMask: SubMaskState,
    isSelected: Boolean,
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
        modifier = Modifier.height(32.dp)
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
