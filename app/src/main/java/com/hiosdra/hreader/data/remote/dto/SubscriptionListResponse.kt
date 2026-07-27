package com.hiosdra.hreader.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubscriptionListResponse(
    val subscriptions: List<Subscription> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Subscription(
    val id: String,
    val title: String? = null,
    val url: String? = null,
    val htmlUrl: String? = null,
    val iconUrl: String? = null
)
