package com.hiosdra.hreader.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
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
    imageLoader: ArticleImageLoader = koinInject(),
    networkMonitor: NetworkStatus = koinInject()
) {
    val resolvedImageUrl = rememberOfflineImageModel(entryId, imageUrl, imageLoader, networkMonitor)

    AsyncImage(
        model = resolvedImageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
fun OfflineAwareZoomableImage(
    entryId: Long,
    imageUrl: String,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    imageLoader: ArticleImageLoader = koinInject(),
    networkMonitor: NetworkStatus = koinInject()
) {
    val resolvedImageUrl = rememberOfflineImageModel(entryId, imageUrl, imageLoader, networkMonitor)

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        ZoomableAsyncImage(
            model = resolvedImageUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onClick = { _ -> onClick() }
        )
    }
}

@Composable
private fun rememberOfflineImageModel(
    entryId: Long,
    imageUrl: String,
    imageLoader: ArticleImageLoader,
    networkMonitor: NetworkStatus
): String? {
    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle()
    var resolvedImageUrl by remember(entryId, imageUrl) { mutableStateOf<String?>(null) }

    LaunchedEffect(entryId, imageUrl, isOnline) {
        resolvedImageUrl = imageLoader.getImageModel(entryId, imageUrl, isOnline)
    }

    return resolvedImageUrl
}
