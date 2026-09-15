package com.hiosdra.hreader.adapter.preferences

import android.app.Application
import com.hiosdra.hreader.core.application.sync.SyncCheckpoint
import com.hiosdra.hreader.core.application.sync.SyncCheckpointMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = PreferencesManagerPersistenceTestApplication::class, sdk = [35])
class PreferencesManagerPersistenceTest {

    @Test
    fun `sync recovery state survives a new preferences manager instance`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val checkpoint = SyncCheckpoint(
            ownerKey = "miniflux|https://reader.example",
            mode = SyncCheckpointMode.INCREMENTAL,
            startedAt = 1_000L,
            changedAfter = 900L,
            cursor = "cursor.with.separators",
            fullSyncRunId = "full-run"
        )

        val firstManager = PreferencesManager(context)
        try {
            firstManager.awaitReady()
            firstManager.setCacheOwnerKey(checkpoint.ownerKey)
            firstManager.setCacheCleanupPending(true)
            firstManager.setLastSyncTimestamp(2_000L)
            firstManager.setLastFullSyncTimestamp(1_500L)
            firstManager.setSyncCheckpoint(checkpoint)
            firstManager.recordSyncStarted(1_000L, "run-1")
            firstManager.awaitWrites()
        } finally {
            close(firstManager)
        }

        val restartedManager = PreferencesManager(context)
        try {
            restartedManager.awaitReady()

            assertEquals(checkpoint.ownerKey, restartedManager.getCacheOwnerKey())
            assertTrue(restartedManager.isCacheCleanupPending())
            assertEquals(2_000L, restartedManager.getLastSyncTimestamp())
            assertEquals(1_500L, restartedManager.getLastFullSyncTimestamp())
            assertEquals(checkpoint, restartedManager.getSyncCheckpoint())
            assertEquals("run-1", restartedManager.getSnapshot().lastRun?.runId)
        } finally {
            close(restartedManager)
        }
    }

    private suspend fun close(manager: PreferencesManager) {
        val scopeField = PreferencesManager::class.java.getDeclaredField("scope")
        scopeField.isAccessible = true
        val scope = scopeField.get(manager) as CoroutineScope
        val job = scope.coroutineContext[Job]
        scope.cancel()
        job?.join()
    }
}

private class PreferencesManagerPersistenceTestApplication : Application()
