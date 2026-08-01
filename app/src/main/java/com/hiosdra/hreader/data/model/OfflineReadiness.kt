package com.hiosdra.hreader.data.model

import java.time.Instant

/**
 * What a reader about to lose connectivity needs to know: how much of the cache is actually
 * readable offline, not how many articles the list happens to show.
 */
data class OfflineReadiness(
    val articleCount: Int = 0,
    val unreadCount: Int = 0,
    val backlogCount: Int = 0,
    val storedContentCount: Int = 0,
    val storedImageCount: Int = 0,
    val storedImageBytes: Long = 0,
    val lastSyncAt: Instant? = null,
    val offlineTargetCount: Int = articleCount,
    val storedFullContentCount: Int = storedContentCount,
    val expectedImageCount: Int = 0,
    val storedExpectedImageCount: Int = 0
) {
    val missingContentCount: Int
        get() = (offlineTargetCount - storedContentCount).coerceAtLeast(0)

    val missingFullContentCount: Int
        get() = (offlineTargetCount - storedFullContentCount).coerceAtLeast(0)

    val missingImageCount: Int
        get() = (expectedImageCount - storedExpectedImageCount).coerceAtLeast(0)

    val isComplete: Boolean
        get() = offlineTargetCount > 0 && missingContentCount == 0
}
