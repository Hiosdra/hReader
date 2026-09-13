package com.hiosdra.hreader.core.application.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageModelsTest {
    @Test
    fun `removable bytes and largest category exclude protected data`() {
        val snapshot = StorageSnapshot(
            availableDeviceBytes = LOW_STORAGE_THRESHOLD_BYTES,
            categories = listOf(
                StorageCategoryUsage(StorageCategory.ARTICLE_DATA, 900L, 3),
                StorageCategoryUsage(StorageCategory.DOWNLOADED_IMAGES, 1_200L, 4),
                StorageCategoryUsage(StorageCategory.OFFLINE_PAGES, 800L, 2),
                StorageCategoryUsage(StorageCategory.OTHER, 500L, 0)
            )
        )

        assertEquals(2_000L, snapshot.removableBytes)
        assertEquals(StorageCategory.DOWNLOADED_IMAGES, snapshot.largestRemovableCategory?.category)
        assertFalse(snapshot.isLowStorage)
    }

    @Test
    fun `low storage is reported below the threshold`() {
        val snapshot = StorageSnapshot(availableDeviceBytes = LOW_STORAGE_THRESHOLD_BYTES - 1L)

        assertTrue(snapshot.isLowStorage)
    }

    @Test
    fun `cleanup progress is bounded and indeterminate when there are no items`() {
        val indeterminate = StorageCleanupProgress(StorageCleanupAction.DOWNLOADED_IMAGES, 0, 0)
        val complete = StorageCleanupProgress(StorageCleanupAction.DOWNLOADED_IMAGES, 8, 3)

        assertEquals(null, indeterminate.fraction)
        assertEquals(1f, complete.fraction)
    }
}
