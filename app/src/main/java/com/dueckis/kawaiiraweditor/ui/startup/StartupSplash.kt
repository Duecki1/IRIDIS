package com.dueckis.kawaiiraweditor.ui.startup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@Composable
internal fun StartupSplash(
    onFinished: () -> Unit
) {
    val bladePath = remember {
        Path().apply {
            moveTo(-10f, -150f)
            lineTo(80f, -150f)
            lineTo(140f, -40f)
            lineTo(0f, -40f)
            close()
        }
    }
    val centerHexPath = remember {
        Path().apply {
            moveTo(0f, -40f)
            lineTo(35f, -20f)
            lineTo(35f, 20f)
            lineTo(0f, 40f)
            lineTo(-35f, 20f)
            lineTo(-35f, -20f)
            close()
        }
    }

    val blades = remember {
        listOf(
            0f to Color(0xFFFF5252),
            60f to Color(0xFFFFD740),
            120f to Color(0xFF69F0AE),
            180f to Color(0xFF40C4FF),
            240f to Color(0xFF536DFE),
            300f to Color(0xFFE040FB)
        )
    }

    val bladeTravel = remember { blades.map { Animatable(1f) } }
    val bladeRotations = remember { blades.map { Animatable(0f) } }
    val centerScale = remember { Animatable(0f) }
    val centerRotation = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    val overshootEasing = remember { CubicBezierEasing(0.3f, 0.0f, 0.0f, 1.3f) }
    val fastOutSlowIn = remember { CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f) }

    LaunchedEffect(Unit) {
        val bladeStagger = 35L
        val bladeDur = 500
        val centerDur = 450
        val holdTime = 250L
        val fadeDur = 250

        coroutineScope {
            val jobs = bladeTravel.mapIndexed { idx, anim ->
                launch {
                    val startDelay = idx * bladeStagger
                    delay(startDelay)

                    launch {
                        anim.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = bladeDur, easing = overshootEasing)
                        )
                    }

                    launch {
                        bladeRotations[idx].animateTo(
                            targetValue = -15f,
                            animationSpec = tween(durationMillis = bladeDur / 2, easing = fastOutSlowIn)
                        )
                        bladeRotations[idx].animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = bladeDur / 2, easing = fastOutSlowIn)
                        )
                    }
                }
            }
            jobs.joinAll()
        }

        coroutineScope {
            launch {
                centerScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = (centerDur * 0.7).toInt(), easing = overshootEasing)
                )
            }
            launch {
                centerRotation.animateTo(
                    targetValue = 360f,
                    animationSpec = tween(durationMillis = centerDur, easing = fastOutSlowIn)
                )
            }
        }

        delay(holdTime)

        overlayAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = fadeDur, easing = fastOutSlowIn)
        )
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .graphicsLayer(alpha = overlayAlpha.value.coerceIn(0f, 1f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .zIndex(100f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val canvasScale = size.minDimension / 420f
            withTransform({
                scale(scaleX = canvasScale, scaleY = canvasScale)
                translate(left = size.width / 2f, top = size.height / 2f)
            }) {
                blades.forEachIndexed { idx, (initialRotationDeg, bladeColor) ->
                    val dist = bladeTravel[idx].value.coerceIn(0f, 1f) * 200f
                    val currentBladeRotation = bladeRotations[idx].value
                    val bladePivot = Offset(0f, -40f)

                    rotate(degrees = initialRotationDeg, pivot = Offset.Zero) {
                        translate(top = -dist) {
                            rotate(degrees = currentBladeRotation, pivot = bladePivot) {
                                drawPath(
                                    path = bladePath,
                                    color = bladeColor.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                val cScale = centerScale.value.coerceIn(0f, 1f)
                if (cScale > 0.001f) {
                    rotate(degrees = centerRotation.value, pivot = Offset.Zero) {
                        scale(scale = cScale, pivot = Offset.Zero) {
                            drawPath(path = centerHexPath, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
