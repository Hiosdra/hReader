package com.hiosdra.hreader.core.domain.model

data class Enclosure(
    val url: String,
    val mimeType: String?
) {
    val isImage: Boolean get() = mimeType?.startsWith("image/") == true
}
