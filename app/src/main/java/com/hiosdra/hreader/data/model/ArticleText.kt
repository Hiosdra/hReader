package com.hiosdra.hreader.data.model

/** An article body as it is read: ready to render, with the picture that leads it worked out. */
data class ArticleText(
    val html: String,
    /** Null when the body carries the picture itself, and nothing belongs above the text. */
    val leadImageUrl: String?
)
