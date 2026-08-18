package com.hiosdra.hreader.presentation.article

import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.net.URLConnection

internal fun serveLocalArticleImage(localPath: String, filesDir: File?): WebResourceResponse? {
    filesDir ?: return null
    val imageDirectory = File(filesDir, "article_images")
    if (!isFileWithinDirectory(localPath, imageDirectory)) return null
    val file = runCatching { File(localPath).canonicalFile }.getOrNull() ?: return null
    return runCatching {
        WebResourceResponse(
            URLConnection.guessContentTypeFromName(file.name) ?: "image/*",
            null,
            FileInputStream(file)
        )
    }.getOrNull()
}

internal fun isAllowedArticleLink(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme?.lowercase() in setOf("http", "https") &&
        uri.userInfo == null &&
        !uri.host.isNullOrBlank()
}.getOrDefault(false)

@Suppress("DEPRECATION")
internal fun WebSettings.hardenArticleContent() {
    javaScriptEnabled = false
    allowFileAccess = false
    allowContentAccess = false
    allowUniversalAccessFromFileURLs = false
    allowFileAccessFromFileURLs = false
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
}

internal fun isFileWithinDirectory(path: String, directory: File): Boolean {
    val root = runCatching { directory.canonicalFile }.getOrNull() ?: return false
    val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
    return file.isFile && file.path.startsWith(root.path + File.separator)
}
