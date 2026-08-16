package com.hiosdra.hreader.adapter.observability

import android.content.Context
import android.util.Log
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid

class ErrorReportingManager(
    private val context: Context,
    private val preferencesManager: AppPreferences
) : ErrorReporter {
    private val lock = Any()
    private var initialized = false

    override fun initialize() = synchronized(lock) {
        if (preferencesManager.getSentryReportingEnabled()) {
            initializeLocked()
        } else {
            closeLocked()
        }
    }

    override fun isEnabled(): Boolean = preferencesManager.getSentryReportingEnabled()

    override fun setEnabled(enabled: Boolean) {
        preferencesManager.setSentryReportingEnabled(enabled)
        synchronized(lock) {
            if (enabled) initializeLocked() else closeLocked()
        }
    }

    override fun captureException(throwable: Throwable, component: String) {
        capture(component) { Sentry.captureException(throwable) }
    }

    override fun captureMessage(message: String, component: String) {
        if (message.isNotBlank()) {
            capture(component) { Sentry.captureMessage(message, SentryLevel.ERROR) }
        }
    }

    private fun capture(component: String, capture: () -> Unit) {
        if (!isEnabled()) return
        synchronized(lock) {
            if (!initialized || !Sentry.isEnabled()) return
            runCatching {
                Sentry.withScope { scope ->
                    scope.setTag("component", component)
                    capture()
                }
            }.onFailure { failure ->
                Log.w(TAG, "Could not report an application error", failure)
            }
        }
    }

    private fun initializeLocked() {
        if (initialized) return

        val dsn = context.getString(R.string.sentry_dsn).trim()
        if (dsn.isEmpty()) return

        runCatching {
            SentryAndroid.init(context) { options ->
                options.setDsn(dsn)
                options.setSendDefaultPii(false)
                options.setAttachScreenshot(false)
                options.setAttachViewHierarchy(false)
                options.setEnableAutoSessionTracking(false)
                options.setEnableNdk(false)
                options.setMaxBreadcrumbs(0)
                options.enableAllAutoBreadcrumbs(false)
            }
            initialized = Sentry.isEnabled()
        }.onFailure { failure ->
            initialized = false
            Log.w(TAG, "Could not initialize error reporting", failure)
        }
    }

    private fun closeLocked() {
        if (initialized || Sentry.isEnabled()) {
            runCatching { Sentry.close() }
                .onFailure { failure ->
                    Log.w(TAG, "Could not disable error reporting", failure)
                }
        }
        initialized = false
    }

    companion object {
        const val SENTRY_PRIVACY_POLICY_URL = "https://sentry.io/privacy/"
        private const val TAG = "ErrorReportingManager"
    }
}
