package com.hiosdra.hreader.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.hiosdra.hreader.R

private const val PLACEHOLDER_ROWS = 6

/**
 * The shape the list is about to take, in place of a spinner in the middle of an empty screen.
 * It also keeps the layout from jumping when the real rows arrive.
 */
@Composable
fun ArticleListSkeleton(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.loading_articles)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = loadingDescription }
    ) {
        repeat(PLACEHOLDER_ROWS) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clearAndSetSemantics { },
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBar(width = 120.dp, height = 12.dp, alpha = alpha)
                        Spacer(modifier = Modifier.height(10.dp))
                        ShimmerBar(width = 240.dp, height = 16.dp, alpha = alpha)
                        Spacer(modifier = Modifier.height(10.dp))
                        ShimmerBar(width = 200.dp, height = 12.dp, alpha = alpha)
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBar(width = 160.dp, height = 12.dp, alpha = alpha)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(MaterialTheme.shapes.small)
                            .alpha(alpha)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerBar(width: Dp, height: Dp, alpha: Float) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(MaterialTheme.shapes.extraSmall)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.onSurfaceVariant)
    )
}
