package com.hiosdra.hreader.core.domain.model

import java.time.Instant

data class ArticleStatusUpdate(
    val changedCount: Int,
    val readAt: Instant?
)
