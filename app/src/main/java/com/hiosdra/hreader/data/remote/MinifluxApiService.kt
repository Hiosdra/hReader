package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.model.CreateFeedRequest
import com.hiosdra.hreader.data.model.CreateFeedResponse
import com.hiosdra.hreader.data.model.DiscoverRequest
import com.hiosdra.hreader.data.model.DiscoverResponse
import com.hiosdra.hreader.data.model.EntriesResponse
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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
    ): List<Feed>

    @POST("v1/feeds")
    suspend fun createFeed(
        @Body request: CreateFeedRequest
    ): CreateFeedResponse

    @POST("v1/discover")
    suspend fun discoverFeeds(
        @Body request: DiscoverRequest
    ): List<DiscoverResponse>

    @PUT("v1/entries")
    suspend fun updateEntriesStatus(
        @Body request: UpdateEntriesStatusRequest
    )
}
