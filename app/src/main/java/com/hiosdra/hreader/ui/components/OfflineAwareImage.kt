package com.hiosdra.hreader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import com.hiosdra.hreader.util.ImageLoader
import org.koin.compose.koinInject

@Composable
fun OfflineAwareImage(
    entryId: Long,
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    imageLoader: ImageLoader = koinInject()
) {
    var resolvedImageUrl by remember(entryId, imageUrl) { mutableStateOf(imageUrl) }

    LaunchedEffect(entryId, imageUrl) {
        resolvedImageUrl = imageLoader.getImagePath(entryId, imageUrl)
    }

    Image(
        painter = rememberAsyncImagePainter(resolvedImageUrl),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter
    )
}

@Composable
fun rememberOfflineAwareImagePainter(
    entryId: Long,
    imageUrl: String,
    imageLoader: ImageLoader = koinInject()
): Painter {
    var resolvedImageUrl by remember(entryId, imageUrl) { mutableStateOf(imageUrl) }

    LaunchedEffect(entryId, imageUrl) {
        resolvedImageUrl = imageLoader.getImagePath(entryId, imageUrl)
    }

    return rememberAsyncImagePainter(resolvedImageUrl)
}