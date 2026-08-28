package com.hiosdra.hreader.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader as CoilImageLoader
import coil3.compose.AsyncImage
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OfflineAwareImage(
    entryId: Long,
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    isOnline: Boolean = true,
    localImagePath: String? = null,
    lookupLocalPath: Boolean = true,
    checkRemotePolicy: Boolean = true,
    articleImageLoader: ArticleImageLoader,
    coilImageLoader: CoilImageLoader,
    remoteResourcePolicy: RemoteResourcePolicy,
) {
    var localImageUrl by remember(entryId, imageUrl, localImagePath) {
        mutableStateOf(localImagePath?.let { "file://$it" })
    }
    var remoteImageAllowed by remember(entryId, imageUrl) { mutableStateOf(false) }

    LaunchedEffect(entryId, imageUrl, localImagePath, lookupLocalPath, checkRemotePolicy, isOnline) {
        localImageUrl = when {
            localImagePath != null -> "file://$localImagePath"
            lookupLocalPath -> articleImageLoader.getImageModel(entryId, imageUrl, allowNetwork = false)
            else -> null
        }
        remoteImageAllowed = localImagePath != null ||
            (isOnline && (!checkRemotePolicy || withContext(Dispatchers.IO) {
                remoteResourcePolicy.allows(imageUrl)
            }))
    }

    AsyncImage(
        model = localImageUrl ?: imageUrl.takeIf { isOnline && remoteImageAllowed },
        imageLoader = coilImageLoader,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    )
}
