package com.hiosdra.hreader.core.application.usecase.settings

import com.hiosdra.hreader.core.application.port.out.StorageStore
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.application.storage.StorageCleanupAction
import com.hiosdra.hreader.core.application.storage.StorageCleanupResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StorageUseCaseTest {
    private val storage = mockk<StorageStore>()
    private val sync = mockk<SyncRequester>(relaxed = true)
    private val useCase = StorageUseCase(storage, sync)

    @Test
    fun `cleanup suspends sync and restores its schedule`() = runBlocking {
        val calls = mutableListOf<String>()
        val action = StorageCleanupAction.DOWNLOADED_IMAGES
        val expected = StorageCleanupResult(action, reclaimedBytes = 100L, totalItems = 1, failedItems = 0)
        coEvery { sync.cancelAllSync() } coAnswers { calls += "cancel" }
        coEvery { storage.cleanup(action, any()) } coAnswers {
            calls += "cleanup"
            expected
        }

        val actual = useCase.cleanup(action)

        assertEquals(expected, actual)
        assertEquals(listOf("cancel", "cleanup"), calls)
        verify { sync.schedulePeriodicSync() }
    }

    @Test
    fun `cleanup restores its schedule when sync cancellation fails`() {
        coEvery { sync.cancelAllSync() } throws IllegalStateException("sync failure")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { useCase.cleanup(StorageCleanupAction.TEMPORARY_CACHE) }
        }

        coVerify(exactly = 0) { storage.cleanup(any(), any()) }
        verify { sync.schedulePeriodicSync() }
    }
}
