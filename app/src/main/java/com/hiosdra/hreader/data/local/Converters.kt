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
    fun enclosuresToStorage(enclosures: List<Enclosure>): String =
        enclosures.joinToString(RECORD_SEPARATOR) { "${'$'}{it.url}${'$'}FIELD_SEPARATOR${'$'}{it.mimeType.orEmpty()}" }

    @TypeConverter
    fun storageToEnclosures(stored: String?): List<Enclosure> {
        if (stored.isNullOrEmpty()) return emptyList()
        return stored.split(RECORD_SEPARATOR).mapNotNull { record ->
            val url = record.substringBefore(FIELD_SEPARATOR).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Enclosure(url = url, mimeType = record.substringAfter(FIELD_SEPARATOR, "").takeIf { it.isNotBlank() })
        }
    }

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? =
        instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(epochMillis: Long?): Instant? =
        epochMillis?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun articleStatusToString(status: ArticleStatus?): String? = status?.name

    @TypeConverter
    fun stringToArticleStatus(value: String?): ArticleStatus? =
        value?.let { name -> ArticleStatus.entries.find { it.name == name } ?: ArticleStatus.UNREAD }
}

private const val RECORD_SEPARATOR = "\u001e"
private const val FIELD_SEPARATOR = "\u001f"
