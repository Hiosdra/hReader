package com.hiosdra.hreader.core.application.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoordinatorTest {
    private val coordinator = SyncCoordinator()

    @Test
    fun `regular user sync only drains the normal content stages`() {
        val plan = coordinator.plan(SyncIntent.User())

        assertFalse(plan.forceFullSync)
        assertFalse(plan.expedited)
        assertFalse(plan.includeFullPages)
        assertFalse(plan.drainRemaining)
    }

    @Test
    fun `full offline intent includes every stage and drains the bounded queue`() {
        val plan = coordinator.plan(SyncIntent.PrepareFullOffline)

        assertTrue(plan.forceFullSync)
        assertTrue(plan.expedited)
        assertTrue(plan.ignoreQuietHours)
        assertTrue(plan.drainRemaining)
        assertTrue(plan.offlinePreparation)
        assertTrue(plan.fullOfflinePreparation)
        assertTrue(plan.includeFullPages)
    }

    @Test
    fun `periodic and background intents stay noninteractive`() {
        listOf(SyncIntent.Periodic, SyncIntent.Background).forEach { intent ->
            val plan = coordinator.plan(intent)

            assertFalse(plan.forceFullSync)
            assertFalse(plan.expedited)
            assertFalse(plan.ignoreQuietHours)
            assertFalse(plan.userVisible)
        }
    }

    @Test
    fun `resync intent is a visible full sync without offline preparation`() {
        val plan = coordinator.plan(SyncIntent.Resync)

        assertTrue(plan.forceFullSync)
        assertTrue(plan.expedited)
        assertTrue(plan.ignoreQuietHours)
        assertTrue(plan.userVisible)
        assertFalse(plan.offlinePreparation)
        assertFalse(plan.includeFullPages)
    }

    @Test
    fun `content-only offline intent drains without archiving original pages`() {
        val plan = coordinator.plan(SyncIntent.PrepareOffline)

        assertTrue(plan.forceFullSync)
        assertTrue(plan.drainRemaining)
        assertTrue(plan.offlinePreparation)
        assertFalse(plan.fullOfflinePreparation)
        assertFalse(plan.includeFullPages)
    }

    @Test
    fun `user intent carries its visibility and full-sync request`() {
        val plan = coordinator.plan(
            SyncIntent.User(
                forceFullSync = true,
                userVisible = true,
                operationTitle = "Manual refresh"
            )
        )

        assertTrue(plan.forceFullSync)
        assertTrue(plan.expedited)
        assertTrue(plan.ignoreQuietHours)
        assertTrue(plan.userVisible)
        assertFalse(plan.offlinePreparation)
    }
}
