package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.model.EntriesResponse
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.dto.CreateFeedRequest
import com.hiosdra.hreader.data.remote.dto.CreateFeedResponse
import com.hiosdra.hreader.data.remote.dto.DiscoverRequest
import com.hiosdra.hreader.data.remote.dto.DiscoverResponse
import com.hiosdra.hreader.data.remote.dto.FeedCountersResponse
import com.hiosdra.hreader.data.remote.dto.OriginalContentResponse
import com.hiosdra.hreader.data.remote.dto.UpdateEntriesStatusRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MinifluxApiService {
    @GET("v1/entries")
    suspend fun getEntries(
        @Query("status") status: String,
        @Query("order") order: String,
        @Query("direction") direction: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): EntriesResponse

    @GET("v1/feeds")
    suspend fun getFeeds(
    ): List<Feed>

    @GET("v1/feeds/counters")
    suspend fun getFeedCounters(): FeedCountersResponse

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

    @GET("v1/entries/{entryId}/fetch-content")
    suspend fun fetchOriginalContent(
        @Path("entryId") entryId: Long
    ): OriginalContentResponse
}
