package com.hiosdra.hreader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.hiosdra.hreader.util.ImageLoader
import com.hiosdra.hreader.util.NetworkMonitor
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
    imageLoader: ImageLoader = koinInject(),
    networkMonitor: NetworkMonitor = koinInject()
) {
    val isOnline by networkMonitor.isOnline.collectAsState()
    var resolvedImageUrl by remember(entryId, imageUrl) { mutableStateOf<String?>(null) }

    LaunchedEffect(entryId, imageUrl, isOnline) {
        resolvedImageUrl = imageLoader.getImageModel(entryId, imageUrl, isOnline)
    }

    AsyncImage(
        model = resolvedImageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter
    )
}
