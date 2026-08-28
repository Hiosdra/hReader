package com.hiosdra.hreader.presentation.article

import androidx.compose.runtime.Immutable
import coil3.ImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy

@Immutable
internal data class ArticleRowModel(
    val id: Long,
    val title: String,
    val preview: String?,
    val feedTitle: String,
    val publishedTime: String,
    val imageUrl: String?,
    val isRead: Boolean
)

@Immutable
internal data class ArticleImageDependencies(
    val articleImageLoader: ArticleImageLoader,
    val coilImageLoader: ImageLoader,
    val remoteResourcePolicy: RemoteResourcePolicy
)
