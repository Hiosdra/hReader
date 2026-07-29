package com.hiosdra.hreader.data.remote.miniflux

import com.hiosdra.hreader.data.remote.miniflux.dto.CreateFeedRequest
import com.hiosdra.hreader.data.remote.miniflux.dto.CreateFeedResponse
import com.hiosdra.hreader.data.remote.miniflux.dto.DiscoverRequest
import com.hiosdra.hreader.data.remote.miniflux.dto.DiscoverResponse
import com.hiosdra.hreader.data.remote.miniflux.dto.FeedCountersResponse
import com.hiosdra.hreader.data.remote.miniflux.dto.MinifluxEntriesResponse
import com.hiosdra.hreader.data.remote.miniflux.dto.MinifluxFeed
import com.hiosdra.hreader.data.remote.miniflux.dto.OriginalContentResponse
import com.hiosdra.hreader.data.remote.miniflux.dto.UpdateEntriesStatusRequest
import com.hiosdra.hreader.data.remote.miniflux.dto.UpdateFeedRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MinifluxApiService {
    /**
     * [afterEntryId] drives keyset pagination: offsets shift underneath a sync whenever an entry
     * changes status mid-run, which silently skips entries. [changedAfter] is a unix timestamp —
     * Miniflux parses these filters as int64 and quietly ignores anything else.
     */
    @GET("v1/entries")
    suspend fun getEntries(
        @Query("status") statuses: List<String>,
        @Query("order") order: String,
        @Query("direction") direction: String,
        @Query("limit") limit: Int,
        @Query("after_entry_id") afterEntryId: Long?,
        @Query("changed_after") changedAfter: Long?,
        @Query("before_entry_id") beforeEntryId: Long? = null
    ): MinifluxEntriesResponse

    @GET("v1/feeds")
    suspend fun getFeeds(
    ): List<MinifluxFeed>

    @GET("v1/feeds/counters")
    suspend fun getFeedCounters(): FeedCountersResponse

    @POST("v1/feeds")
    suspend fun createFeed(
        @Body request: CreateFeedRequest
    ): CreateFeedResponse

    @DELETE("v1/feeds/{feedId}")
    suspend fun deleteFeed(
        @Path("feedId") feedId: Long
    )

    @PUT("v1/feeds/{feedId}")
    suspend fun updateFeed(
        @Path("feedId") feedId: Long,
        @Body request: UpdateFeedRequest
    ): MinifluxFeed

    /** Miniflux has no "set" for bookmarks, only a per-entry flip of whatever is stored. */
    @PUT("v1/entries/{entryId}/bookmark")
    suspend fun toggleBookmark(
        @Path("entryId") entryId: Long
    )

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
