package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.PreferenceWriteBarrier
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheOwnershipCoordinatorTest {
    private val cleaner = mockk<CacheDataCleaner>(relaxed = true)
    private val preferences = mockk<SyncPreferences>(relaxed = true)
    private val identity = mockk<BackendIdentity>(relaxed = true)
    private val writes = mockk<PreferenceWriteBarrier>(relaxed = true)
    private val coordinator = CacheOwnershipCoordinator(cleaner, preferences, identity, writes)

    @Test
    fun `same owner repairs without clearing the cache`() = runBlocking {
        every { preferences.isCacheCleanupPending() } returns false
        every { preferences.getCacheOwnerKey() } returns "owner"
        every { identity.cacheOwnerKey() } returns "owner"

        val changed = coordinator.ensureCacheOwner()

        assertFalse(changed)
        coVerify { cleaner.repair() }
        coVerify(exactly = 0) { cleaner.clearAll() }
    }

    @Test
    fun `owner mismatch clears before assigning the new owner`() = runBlocking {
        every { preferences.isCacheCleanupPending() } returns false
        every { preferences.getCacheOwnerKey() } returns "old-owner"
        every { identity.cacheOwnerKey() } returns "new-owner"

        val changed = coordinator.ensureCacheOwner()

        assertTrue(changed)
        coVerify { cleaner.clearAll() }
        verify { preferences.setCacheOwnerKey("new-owner") }
        coVerify { writes.awaitWrites() }
    }

    @Test
    fun `a failed cleanup keeps the recovery marker set`() = runBlocking {
        every { preferences.isCacheCleanupPending() } returns false
        every { preferences.getCacheOwnerKey() } returns "old-owner"
        every { identity.cacheOwnerKey() } returns "new-owner"
        coEvery { cleaner.clearAll() } throws IllegalStateException("disk failure")

        runCatching { coordinator.ensureCacheOwner() }

        verify { preferences.setCacheCleanupPending(true) }
        verify(exactly = 0) { preferences.setCacheCleanupPending(false) }
    }
}
