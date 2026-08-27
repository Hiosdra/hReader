package com.hiosdra.hreader.core.application.sync

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
}
