package com.hiosdra.hreader.adapter.backend.freshrss

import com.hiosdra.hreader.adapter.backend.freshrss.dto.QuickAddResponse
import com.hiosdra.hreader.adapter.backend.freshrss.dto.StreamContentsResponse
import com.hiosdra.hreader.adapter.backend.freshrss.dto.SubscriptionListResponse
import com.hiosdra.hreader.adapter.backend.freshrss.dto.UnreadCountResponse
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface FreshRssApiService {
    @GET("reader/api/0/stream/contents/user/-/state/com.google/reading-list")
    suspend fun getStreamContents(
        @Query("output") output: String,
        @Query("n") count: Int,
        @Query("r") order: String,
        @Query("xt") excludeTarget: String?,
        @Query("ot") startTimeSeconds: Long?,
        @Query("c") continuation: String?
    ): StreamContentsResponse

    @GET("reader/api/0/subscription/list")
    suspend fun getSubscriptions(
        @Query("output") output: String
    ): SubscriptionListResponse

    @GET("reader/api/0/unread-count")
    suspend fun getUnreadCounts(
        @Query("output") output: String
    ): UnreadCountResponse

    @GET("reader/api/0/token")
    suspend fun getWriteToken(): ResponseBody

    @FormUrlEncoded
    @POST("reader/api/0/subscription/quickadd")
    suspend fun quickAddSubscription(
        @Field("quickadd") feedUrl: String,
        @Field("T") writeToken: String
    ): QuickAddResponse

    /**
     * The subscription editor: [action] is `edit` to rename and `unsubscribe` to drop a feed,
     * [streamId] is the `feed/<id>` form the stream list uses.
     */
    @FormUrlEncoded
    @POST("reader/api/0/subscription/edit")
    suspend fun editSubscription(
        @Field("ac") action: String,
        @Field("s") streamId: String,
        @Field("t") title: String?,
        @Field("T") writeToken: String
    ): ResponseBody

    @FormUrlEncoded
    @POST("reader/api/0/edit-tag")
    suspend fun editTag(
        @Field("i") itemIds: List<Long>,
        @Field("a") addTag: String?,
        @Field("r") removeTag: String?,
        @Field("T") writeToken: String
    ): ResponseBody
}
