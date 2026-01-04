package com.dueckis.kawaiiraweditor.ui.editor.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.util.lerp
import com.dueckis.kawaiiraweditor.data.model.ColorGradingState
import com.dueckis.kawaiiraweditor.data.model.HueSatLumState
import com.dueckis.kawaiiraweditor.ui.components.AdjustmentSlider
import com.dueckis.kawaiiraweditor.ui.components.ColorWheelControl
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColorGradingEditor(
    colorGrading: ColorGradingState,
    onColorGradingChange: (ColorGradingState) -> Unit,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit,
    showMidtones: Boolean = true
) {
    val formatterInt: (Float) -> String = { it.roundToInt().toString() }

    // Order: Shadows -> Midtones -> Highlights
    val wheelEntries = buildList {
        add(
            Triple("Shadows", colorGrading.shadows) { value: HueSatLumState ->
                onColorGradingChange(colorGrading.copy(shadows = value))
            }
        )
        if (showMidtones) {
            add(
                Triple("Midtones", colorGrading.midtones) { value: HueSatLumState ->
                    onColorGradingChange(colorGrading.copy(midtones = value))
                }
            )
        }
        add(
            Triple("Highlights", colorGrading.highlights) { value: HueSatLumState ->
                onColorGradingChange(colorGrading.copy(highlights = value))
            }
        )
    }

    val pagerState = rememberPagerState(pageCount = { wheelEntries.size })
    val scope = rememberCoroutineScope()

    // Requester to snap the view into position on interaction
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // Wrapper to trigger scroll-to-view when user starts interacting
    val internalOnBeginEditInteraction = {
        scope.launch {
            bringIntoViewRequester.bringIntoView()
        }
        onBeginEditInteraction()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {

        // 1. Page Indicator (Dots)
        PageIndicator(
            pageCount = wheelEntries.size,
            currentPage = pagerState.currentPage,
            onDotClicked = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. The Carousel
        ColorGradingWheelCarousel(
            pagerState = pagerState,
            entries = wheelEntries,
            onBeginEditInteraction = internalOnBeginEditInteraction, // Pass wrapped callback
            onEndEditInteraction = onEndEditInteraction
        )

        // --- Visual Divider Section ---
        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(12.dp))
        // ------------------------------

        // 3. Bottom Global Sliders
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AdjustmentSlider(
                label = "Blending",
                value = colorGrading.blending,
                range = 0f..100f,
                step = 1f,
                defaultValue = 50f,
                formatter = formatterInt,
                onValueChange = { onColorGradingChange(colorGrading.copy(blending = it)) },
                onInteractionStart = internalOnBeginEditInteraction, // Pass wrapped callback
                onInteractionEnd = onEndEditInteraction
            )
            AdjustmentSlider(
                label = "Balance",
                value = colorGrading.balance,
                range = -100f..100f,
                step = 1f,
                defaultValue = 0f,
                formatter = formatterInt,
                onValueChange = { onColorGradingChange(colorGrading.copy(balance = it)) },
                onInteractionStart = internalOnBeginEditInteraction, // Pass wrapped callback
                onInteractionEnd = onEndEditInteraction
            )
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
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isSelected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    .clickable { onDotClicked(index) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorGradingWheelCarousel(
    pagerState: PagerState,
    entries: List<Triple<String, HueSatLumState, (HueSatLumState) -> Unit>>,
    onBeginEditInteraction: () -> Unit,
    onEndEditInteraction: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Size: 85% of screen width to act as a landscape card
    val cardWidth = (screenWidth * 0.85f).coerceAtMost(380.dp)
    val padding = (screenWidth - cardWidth) / 2

    val wheelDiameter = 120.dp

    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = padding),
        pageSpacing = 12.dp,
        verticalAlignment = Alignment.Top
    ) { page ->
        val (title, value, updater) = entries[page]
        val isEnabled = pagerState.currentPage == page

        val pageOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                ).absoluteValue

        Box(
            modifier = Modifier
                .graphicsLayer {
                    val scale = lerp(1f, 0.95f, pageOffset.coerceIn(0f, 1f))
                    scaleX = scale
                    scaleY = scale
                    alpha = lerp(1f, 0.3f, pageOffset.coerceIn(0f, 1f))
                }
                .fillMaxWidth()
        ) {
            ColorWheelControl(
                title = title,
                wheelSize = wheelDiameter,
                value = value,
                defaultValue = HueSatLumState(),
                onValueChange = updater,
                onBeginEditInteraction = onBeginEditInteraction,
                onEndEditInteraction = onEndEditInteraction,
                enabled = isEnabled
            )
        }
    }
}