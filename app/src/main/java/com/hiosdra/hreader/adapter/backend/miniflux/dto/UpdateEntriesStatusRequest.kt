package com.hiosdra.hreader.adapter.backend.miniflux.dto

data class UpdateEntriesStatusRequest(
    val entry_ids: List<Long>,
    val status: String
)
