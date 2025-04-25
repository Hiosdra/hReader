package com.hiosdra.hreader.data.remote

data class UpdateEntriesStatusRequest(
    val entry_ids: List<Long>,
    val status: String
)
