package com.hiosdra.hreader.adapter.image

import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageLoaderTest {
    private val mockRepo = mockk<ArticleImageStore>()

    @Test
    fun returnsLocalFilePath_whenLocalImageExists() = runBlocking {
        val entryId = 1L
        val imageUrl = "https://example.com/image.jpg"
        val localPath = "/data/data/com.hiosdra.hreader/files/article_images/abc123.jpg"
        coEvery { mockRepo.getLocalImagePath(entryId, imageUrl) } returns localPath
        val loader = ImageLoader(mockRepo) { it == localPath }
        val result = loader.getImagePath(entryId, imageUrl)
        assertEquals("file://$localPath", result)
    }

    @Test
    fun returnsOriginalUrl_whenNoLocalImage() = runBlocking {
        val entryId = 2L
        val imageUrl = "https://example.com/image2.jpg"
        coEvery { mockRepo.getLocalImagePath(entryId, imageUrl) } returns null
        val loader = ImageLoader(mockRepo) { false }
        val result = loader.getImagePath(entryId, imageUrl)
        assertEquals(imageUrl, result)
    }

    @Test
    fun returnsNoModelOffline_whenNoLocalImage() = runBlocking {
        val entryId = 3L
        val imageUrl = "https://example.com/image3.jpg"
        coEvery { mockRepo.getLocalImagePath(entryId, imageUrl) } returns null
        val loader = ImageLoader(mockRepo) { false }

        assertNull(loader.getImageModel(entryId, imageUrl, allowNetwork = false))
    }
}
