package com.hiosdra.hreader.presentation.settings

import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.sync.SyncOperationError

internal val SyncOperationError.messageResId: Int
    get() = when (this) {
        SyncOperationError.CONFIGURE_SERVER -> R.string.settings_configure_server
        SyncOperationError.CACHE_UPDATE_FAILED -> R.string.settings_cache_update_failed
    }
