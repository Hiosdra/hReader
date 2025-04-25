package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.model.EntriesResponse
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.FeedsResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface MinifluxApiService {
    @GET("v1/entries")
    suspend fun getEntries(
        @Query("status") status: String = "unread",
        @Query("order") order: String = "published_at",
        @Query("direction") direction: String = "asc",
        @Query("limit") limit: Int = 100
    ): EntriesResponse

    @GET("v1/entries")
    suspend fun getEntriesByIds(
        @Query("ids") ids: String
    ): EntriesResponse

    @GET("v1/entries/{entryId}")
    suspend fun getEntryById(
        @Path("entryId") entryId: Long
    ): Entry

    @GET("v1/feeds")
    suspend fun getFeeds(
        @Header("X-Auth-Token") apiKey: String
    ): FeedsResponse
}
