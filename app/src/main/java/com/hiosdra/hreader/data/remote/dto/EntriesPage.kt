package com.hiosdra.hreader.data.remote.dto

import com.hiosdra.hreader.data.model.Entry

data class EntriesPage(
    val entries: List<Entry>,
    val continuation: String?
)
