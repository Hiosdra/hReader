package com.hiosdra.hreader.data.local

import androidx.room.TypeConverter
import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.Enclosure
import com.hiosdra.hreader.data.model.Feed
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.time.Instant

class Converters {
    private val moshi = Moshi.Builder().build()

    @TypeConverter
    fun feedToJson(feed: Feed?): String? = feed?.let {
        moshi.adapter(Feed::class.java).toJson(it)
    }

    @TypeConverter
    fun jsonToFeed(json: String?): Feed? = json?.let {
        moshi.adapter(Feed::class.java).fromJson(it)
    }

    @TypeConverter
    fun enclosuresToJson(enclosures: List<Enclosure>?): String? = enclosures?.let {
        val type = Types.newParameterizedType(List::class.java, Enclosure::class.java)
        moshi.adapter<List<Enclosure>>(type).toJson(it)
    }

    @TypeConverter
    fun jsonToEnclosures(json: String?): List<Enclosure>? = json?.let {
        val type = Types.newParameterizedType(List::class.java, Enclosure::class.java)
        moshi.adapter<List<Enclosure>>(type).fromJson(it)
    }

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? =
        instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(epochMillis: Long?): Instant? =
        epochMillis?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun articleStatusToString(status: ArticleStatus?): String? = status?.wire

    @TypeConverter
    fun stringToArticleStatus(value: String?): ArticleStatus? = when (value) {
        ArticleStatus.READ.wire -> ArticleStatus.READ
        ArticleStatus.UNREAD.wire -> ArticleStatus.UNREAD
        null -> null
        else -> ArticleStatus.UNREAD
    }
}
