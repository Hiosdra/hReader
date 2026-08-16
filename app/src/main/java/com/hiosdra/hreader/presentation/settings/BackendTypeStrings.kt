package com.hiosdra.hreader.presentation.settings

import androidx.annotation.StringRes
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.domain.model.BackendType

@get:StringRes
internal val BackendType.displayNameRes: Int
    get() = when (this) {
        BackendType.FRESHRSS -> R.string.backend_freshrss
        BackendType.MINIFLUX -> R.string.backend_miniflux
    }

@get:StringRes
internal val BackendType.secretLabelRes: Int
    get() = when (this) {
        BackendType.FRESHRSS -> R.string.backend_freshrss_secret_label
        BackendType.MINIFLUX -> R.string.backend_miniflux_secret_label
    }

@get:StringRes
internal val BackendType.secretHintRes: Int
    get() = when (this) {
        BackendType.FRESHRSS -> R.string.backend_freshrss_secret_hint
        BackendType.MINIFLUX -> R.string.backend_miniflux_secret_hint
    }
