package com.hiosdra.hreader.core.application.port.out

interface ArticleImageDownloader {
    suspend fun download(url: String): Boolean
}
