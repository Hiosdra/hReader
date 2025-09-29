package com.hiosdra.hreader.data.model

import com.squareup.moshi.Json

enum class ArticleStatus(val wire: String) {
    @Json(name = "read") READ("read"),
    @Json(name = "unread") UNREAD("unread");
}

val Entry.isRead: Boolean get() = status == ArticleStatus.READ
