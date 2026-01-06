package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.dueckis.kawaiiraweditor.data.model.ColorGradingState
import com.dueckis.kawaiiraweditor.data.model.HueSatLumState
import com.dueckis.kawaiiraweditor.ui.components.CompactAdjustmentSlider
import com.dueckis.kawaiiraweditor.ui.components.ColorWheelControl
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class GradingType(val label: String) {
    Shadows("Shadows"),
    Midtones("Midtones"),
    Highlights("Highlights")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColorGradingEditor(
    colorGrading: ColorGradingState,
    onColorGradingChange: (ColorGradingState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit,
    showMidtones: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val wheelEntries = remember(colorGrading, showMidtones) {
        buildList {
            add(Triple(GradingType.Shadows, colorGrading.shadows) { v: HueSatLumState ->
                onColorGradingChange(colorGrading.copy(shadows = v))
            })
            if (showMidtones) {
                add(Triple(GradingType.Midtones, colorGrading.midtones) { v: HueSatLumState ->
                    onColorGradingChange(colorGrading.copy(midtones = v))
                })
            }
            add(Triple(GradingType.Highlights, colorGrading.highlights) { v: HueSatLumState ->
                onColorGradingChange(colorGrading.copy(highlights = v))
            })
        }
    }

    val pagerState = rememberPagerState(pageCount = { wheelEntries.size })

    Column(modifier = Modifier.fillMaxWidth()) {

        // 1. CAROUSEL
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                val (type, value, updater) = wheelEntries[page]
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

                Box(
                    modifier = Modifier.graphicsLayer {
                        val scale = lerp(1f, 0.94f, pageOffset.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(1f, 0.6f, pageOffset.coerceIn(0f, 1f))
                    }
                ) {
                    GradingWheelCard(
                        type = type,
                        value = value,
                        onValueChange = updater,
                        onBeginEditInteraction = onBeginEditInteraction,
                        onEndEditInteraction = onEndEditInteraction
                    )
                }
            }
        }

        // 2. INDICATOR
        Spacer(modifier = Modifier.height(12.dp))
        PageIndicator(
            pageCount = wheelEntries.size,
            currentPage = pagerState.currentPage,
            onDotClicked = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
        )

        // 3. GLOBAL SLIDERS (Using Compact Version)
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactAdjustmentSlider(
                label = "Blending",
                value = colorGrading.blending,
                range = 0f..100f,
                step = 1f,
                defaultValue = 50f,
                onValueChange = { onColorGradingChange(colorGrading.copy(blending = it)) },
                onInteractionStart = onBeginEditInteraction,
                onInteractionEnd = onEndEditInteraction
            )
            CompactAdjustmentSlider(
                label = "Balance",
                value = colorGrading.balance,
                range = -100f..100f,
                step = 1f,
                defaultValue = 0f,
                onValueChange = { onColorGradingChange(colorGrading.copy(balance = it)) },
                onInteractionStart = onBeginEditInteraction,
                onInteractionEnd = onEndEditInteraction
            )
        }
    }
}

@Composable
private fun GradingWheelCard(
    type: GradingType,
    value: HueSatLumState,
    onValueChange: (HueSatLumState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = type.label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // SEPARATOR
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // WHEEL CONTAINER
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp), // Tighter padding to allow bigger wheel
                contentAlignment = Alignment.Center
            ) {
                ColorWheelControl(
                    title = "", // No title inside the wheel control area
                    wheelSize = 200.dp, // Placeholder size, layout uses weight now
                    value = value,
                    defaultValue = HueSatLumState(),
                    onValueChange = onValueChange,
                    onBeginEditInteraction = onBeginEditInteraction,
                    onEndEditInteraction = onEndEditInteraction
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    onDotClicked: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index

            // Fix: Use onSurface with alpha for inactive dots to ensure visibility
            val color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isSelected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onDotClicked(index) }
            )
        }
    }
}