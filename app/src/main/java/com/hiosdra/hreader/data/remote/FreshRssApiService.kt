package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.remote.dto.QuickAddResponse
import com.hiosdra.hreader.data.remote.dto.StreamContentsResponse
import com.hiosdra.hreader.data.remote.dto.SubscriptionListResponse
import com.hiosdra.hreader.data.remote.dto.UnreadCountResponse
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

    @FormUrlEncoded
    @POST("reader/api/0/edit-tag")
    suspend fun editTag(
        @Field("i") itemIds: List<Long>,
        @Field("a") addTag: String?,
        @Field("r") removeTag: String?,
        @Field("T") writeToken: String
    ): ResponseBody
}
