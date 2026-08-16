package com.hiosdra.hreader.core.application.port.out

interface ArticleImageSharer {
    suspend fun share(title: String?, url: String): Boolean
}
