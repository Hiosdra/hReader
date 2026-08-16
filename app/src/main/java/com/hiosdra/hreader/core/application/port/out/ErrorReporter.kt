package com.hiosdra.hreader.core.application.port.out

interface ErrorReporter {
    companion object {
        const val PRIVACY_POLICY_URL = "https://sentry.io/privacy/"
    }

    fun initialize()
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun captureException(throwable: Throwable, component: String)
    fun captureMessage(message: String, component: String)
}
