package com.hiosdra.hreader.core.application.feeds

data class OpmlImportResult(
    val added: Int,
    val skipped: Int,
    val failed: List<String>
)
