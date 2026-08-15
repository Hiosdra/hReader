package com.hiosdra.hreader.util

enum class SyncPerformanceOperation(val key: String) {
    ARTICLE_PAGES("article_pages"),
    OFFLINE_BACKLOG_TOP_UP("offline_backlog_top_up"),
    FULL_PAGE_PREFETCH("full_page_prefetch"),
    ARTICLE_REFRESH("article_refresh"),
    ORPHANED_CONTENT_CLEANUP("orphaned_content_cleanup"),
    ARTICLE_CONTENT_PREFETCH("article_content_prefetch"),
    ENCLOSURE_IMAGES_DOWNLOAD("enclosure_images_download"),
    BATCH_PROCESSING("batch_processing"),
    INCREMENTAL_SYNC("incremental_sync"),
    FULL_SYNC("full_sync")
}
