package com.hiosdra.hreader.core.application.storage

enum class StorageCategory(
    val isRemovable: Boolean,
    val isProtected: Boolean
) {
    ARTICLE_DATA(isRemovable = false, isProtected = true),
    OFFLINE_PAGES(isRemovable = true, isProtected = false),
    DOWNLOADED_IMAGES(isRemovable = true, isProtected = false),
    TTS_MODELS(isRemovable = true, isProtected = false),
    ON_DEVICE_AI_MODEL(isRemovable = true, isProtected = false),
    TEMPORARY_CACHE(isRemovable = true, isProtected = false),
    OTHER(isRemovable = false, isProtected = true)
}

enum class StorageCleanupAction(val category: StorageCategory) {
    OFFLINE_PAGES(StorageCategory.OFFLINE_PAGES),
    DOWNLOADED_IMAGES(StorageCategory.DOWNLOADED_IMAGES),
    TTS_MODELS(StorageCategory.TTS_MODELS),
    ON_DEVICE_AI_MODEL(StorageCategory.ON_DEVICE_AI_MODEL),
    TEMPORARY_CACHE(StorageCategory.TEMPORARY_CACHE)
}

data class StorageCategoryUsage(
    val category: StorageCategory,
    val bytes: Long,
    val itemCount: Int
)

data class StorageSnapshot(
    val appBytes: Long = 0L,
    val totalDeviceBytes: Long = 0L,
    val availableDeviceBytes: Long = 0L,
    val categories: List<StorageCategoryUsage> = emptyList(),
    val articleCount: Int = 0,
    val feedCount: Int = 0,
    val storedContentCount: Int = 0,
    val readingPositionCount: Int = 0
) {
    val isLowStorage: Boolean
        get() = availableDeviceBytes < LOW_STORAGE_THRESHOLD_BYTES

    val removableBytes: Long
        get() = categories
            .filter { it.category.isRemovable }
            .sumOf(StorageCategoryUsage::bytes)

    val largestRemovableCategory: StorageCategoryUsage?
        get() = categories
            .filter { it.category.isRemovable && it.bytes > 0L }
            .maxByOrNull(StorageCategoryUsage::bytes)
}

data class StorageCleanupProgress(
    val action: StorageCleanupAction,
    val completedItems: Int,
    val totalItems: Int
) {
    val fraction: Float?
        get() = totalItems.takeIf { it > 0 }
            ?.let { completedItems.toFloat() / it }
            ?.coerceIn(0f, 1f)
}

data class StorageCleanupResult(
    val action: StorageCleanupAction,
    val reclaimedBytes: Long,
    val totalItems: Int,
    val failedItems: Int
) {
    val isPartial: Boolean
        get() = failedItems > 0
}

const val LOW_STORAGE_THRESHOLD_BYTES = 512L * 1024L * 1024L
