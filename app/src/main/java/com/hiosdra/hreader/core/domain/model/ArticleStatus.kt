package com.hiosdra.hreader.core.domain.model

enum class ArticleStatus {
    READ,
    UNREAD
}

val Entry.isRead: Boolean get() = status == ArticleStatus.READ

val ArticleListEntry.isRead: Boolean get() = status == ArticleStatus.READ
