package com.hiosdra.hreader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val EdgeWidth = 24.dp
private val PeekWidth = 96.dp
private val OpenThreshold = 56.dp
private const val FlingOpenVelocity = 600f

internal fun shouldOpenAfterSwipe(offsetPx: Float, velocityPxPerSecond: Float, thresholdPx: Float): Boolean {
    if (offsetPx <= 0f) return false
    return offsetPx >= thresholdPx || velocityPxPerSecond >= FlingOpenVelocity
}

@Composable
fun SwipeFromLeftEdge(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val peekPx = with(density) { PeekWidth.toPx() }
    val thresholdPx = with(density) { OpenThreshold.toPx() }
    val edgePx = with(density) { EdgeWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnOpen by rememberUpdatedState(onOpen)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x > edgePx) return@awaitEachGesture

                    val velocityTracker = VelocityTracker()
                    var dragged = 0f
                    val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        if (overSlop > 0f) {
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            dragged = overSlop.coerceAtMost(peekPx)
                            scope.launch { offset.snapTo(dragged) }
                        }
                    }
                    if (slopChange == null || dragged <= 0f) return@awaitEachGesture

                    horizontalDrag(slopChange.id) { change ->
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        dragged = (dragged + change.positionChange().x).coerceIn(0f, peekPx)
                        val target = dragged
                        scope.launch { offset.snapTo(target) }
                    }

                    val velocity = velocityTracker.calculateVelocity().x
                    val released = dragged
                    scope.launch {
                        if (shouldOpenAfterSwipe(released, velocity, thresholdPx)) {
                            offset.animateTo(peekPx, tween(durationMillis = 120))
                            currentOnOpen()
                        }
                        offset.animateTo(0f, tween(durationMillis = 240))
                    }
                }
            }
    ) {
        Icon(
            Icons.Filled.Menu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp)
                .size(28.dp)
                .graphicsLayer { alpha = (offset.value / peekPx).coerceIn(0f, 1f) }
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(EdgeWidth)
                .systemGestureExclusion()
        )
        Box(modifier = Modifier.graphicsLayer { translationX = offset.value }) {
            content()
        }
    }
}
