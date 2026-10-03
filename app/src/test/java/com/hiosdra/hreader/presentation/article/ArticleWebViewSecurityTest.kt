package com.hiosdra.hreader.presentation.article

import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleWebViewSecurityTest {
    @Test
    fun `allows only web links`() {
        assertTrue(isAllowedArticleLink("https://example.com/article"))
        assertTrue(isAllowedArticleLink("http://example.com/article"))
        assertFalse(isAllowedArticleLink("https://user:password@example.com/article"))
        assertFalse(isAllowedArticleLink("mailto:user@example.com"))
        assertFalse(isAllowedArticleLink("javascript:alert(1)"))
    }

    @Test
    fun `accepts only files below image directory`() {
        val root = Files.createTempDirectory("hreader-images").toFile()
        try {
            val images = File(root, "article_images").apply { mkdirs() }
            val image = File(images, "image.jpg").apply { writeText("image") }
            val outside = File(root, "outside.jpg").apply { writeText("outside") }
            val sibling = File(root, "article_images_backup").apply { mkdirs() }
            val siblingImage = File(sibling, "image.jpg").apply { writeText("sibling") }

            assertTrue(isFileWithinDirectory(image.path, images))
            assertFalse(isFileWithinDirectory(outside.path, images))
            assertFalse(isFileWithinDirectory(siblingImage.path, images))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `requires the same web origin`() {
        val baseUrl = "https://offline.hreader/article/42/"

        assertTrue(isSameWebOrigin("https://offline.hreader/article/42/assets/image.jpg", baseUrl))
        assertTrue(isSameWebOrigin("https://offline.hreader:443/article/42/assets/image.jpg", baseUrl))
        assertFalse(isSameWebOrigin("https://offline.hreader:8443/article/42/assets/image.jpg", baseUrl))
        assertFalse(isSameWebOrigin("http://offline.hreader/article/42/assets/image.jpg", baseUrl))
        assertFalse(isSameWebOrigin("https://user:password@offline.hreader/article/42/assets/image.jpg", baseUrl))
    }

    @Test
    fun `allows a cross-origin resource when the remote policy allows it`() {
        val imageUrl = "https://static.simonwillison.net/image.webp"
        val policy = TestRemoteResourcePolicy(setOf(imageUrl))

        assertFalse(
            isRemoteResourceBlocked(
                imageUrl,
                allowNetworkLoads = true,
                remoteResourcePolicy = policy
            )
        )
    }

    @Test
    fun `blocks web resources while offline`() {
        val imageUrl = "https://static.simonwillison.net/image.webp"
        val policy = TestRemoteResourcePolicy(setOf(imageUrl))

        assertTrue(
            isRemoteResourceBlocked(
                imageUrl,
                allowNetworkLoads = false,
                remoteResourcePolicy = policy
            )
        )
    }

    @Test
    fun `blocks web resources rejected by the remote policy`() {
        val imageUrl = "https://private.example/image.webp"

        assertTrue(
            isRemoteResourceBlocked(
                imageUrl,
                allowNetworkLoads = true,
                remoteResourcePolicy = TestRemoteResourcePolicy(emptySet())
            )
        )
    }

    @Test
    fun `does not block embedded non-web resources`() {
        assertFalse(
            isRemoteResourceBlocked(
                "data:image/png;base64,AAAA",
                allowNetworkLoads = false,
                remoteResourcePolicy = TestRemoteResourcePolicy(emptySet())
            )
        )
    }
}

private class TestRemoteResourcePolicy(
    private val allowedUrls: Set<String>
) : RemoteResourcePolicy {
    override fun allows(url: String): Boolean = url in allowedUrls
}
