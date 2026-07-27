package com.hiosdra.hreader.data.model

enum class ArticleStatus {
    READ,
    UNREAD
}

val Entry.isRead: Boolean get() = status == ArticleStatus.READ
