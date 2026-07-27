package com.hiosdra.hreader.data.remote.miniflux.dto

data class UpdateEntriesStatusRequest(
    val entry_ids: List<Long>,
    val status: String
)
