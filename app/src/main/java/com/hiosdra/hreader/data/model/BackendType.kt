package com.hiosdra.hreader.data.model

enum class BackendType(
    val displayName: String,
    val secretLabel: String,
    val secretHint: String,
    val requiresUsername: Boolean
) {
    FRESHRSS(
        displayName = "FreshRSS",
        secretLabel = "API password",
        secretHint = "Profile → API management in FreshRSS",
        requiresUsername = true
    ),
    MINIFLUX(
        displayName = "Miniflux",
        secretLabel = "API token",
        secretHint = "Settings → API keys in Miniflux",
        requiresUsername = false
    );

    companion object {
        fun fromName(name: String?): BackendType = entries.find { it.name == name } ?: FRESHRSS
    }
}
