package com.hiosdra.hreader.data.model

enum class BackendType(val requiresUsername: Boolean) {
    FRESHRSS(requiresUsername = true),
    MINIFLUX(requiresUsername = false);

    companion object {
        fun fromName(name: String?): BackendType = entries.find { it.name == name } ?: FRESHRSS
    }
}
