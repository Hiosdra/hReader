package com.hiosdra.hreader.presentation.article

import com.hiosdra.hreader.core.application.content.hasReadableArticleText

internal enum class ArticleTtsContentState {
    LOADING,
    UNAVAILABLE,
    AVAILABLE
}

internal fun articleTtsContentState(
    content: String?,
    contentLoadFinished: Boolean
): ArticleTtsContentState = when {
    hasReadableArticleText(content) -> ArticleTtsContentState.AVAILABLE
    contentLoadFinished -> ArticleTtsContentState.UNAVAILABLE
    else -> ArticleTtsContentState.LOADING
}
