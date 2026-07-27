package com.hiosdra.hreader.data.model

data class Enclosure(
    val url: String,
    val mimeType: String?
) {
    val isImage: Boolean get() = mimeType?.startsWith("image/") == true
}
