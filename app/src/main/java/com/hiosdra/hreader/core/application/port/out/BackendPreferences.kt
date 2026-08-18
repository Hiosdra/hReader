package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.BackendType

interface BackendPreferences {
    fun getBackendType(): BackendType
    fun setBackendType(backendType: BackendType)
    fun getServerUrl(backendType: BackendType): String
    fun setServerUrl(backendType: BackendType, url: String)
    fun getBackendSecret(backendType: BackendType): String
    fun setBackendSecret(backendType: BackendType, secret: String)
    fun getFreshRssUsername(): String
    fun setFreshRssUsername(username: String)
    fun hasBackendCredentials(): Boolean
}
