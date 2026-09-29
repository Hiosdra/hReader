package com.hiosdra.hreader.core.application.usecase.settings

import com.hiosdra.hreader.core.application.ai.GemmaBackend
import com.hiosdra.hreader.core.application.observability.SyncPerformanceRecord
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.PerformancePreferences
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import com.hiosdra.hreader.core.application.port.out.TtsPreferences
import com.hiosdra.hreader.core.application.tts.TtsModel

data class SettingsPreferenceSnapshot(
    val paywallBypassMethod: PaywallBypassMethod,
    val bionicReadingEnabled: Boolean,
    val credibilityScoreEnabled: Boolean,
    val errorReportingEnabled: Boolean,
    val gemmaBackend: GemmaBackend,
    val gemmaDownloadOnUnmeteredOnly: Boolean,
    val ttsModel: TtsModel
)

class SettingsPreferenceUseCase(
    private val reader: ReaderPreferences,
    private val tts: TtsPreferences,
    private val ai: AiPreferences,
    private val errors: ErrorReporter,
    private val performance: PerformancePreferences
) {
    fun current(): SettingsPreferenceSnapshot = SettingsPreferenceSnapshot(
        paywallBypassMethod = reader.getPaywallBypassMethod(),
        bionicReadingEnabled = reader.getBionicReadingEnabled(),
        credibilityScoreEnabled = reader.getCredibilityScoreEnabled(),
        errorReportingEnabled = errors.isEnabled(),
        gemmaBackend = ai.getGemmaBackend(),
        gemmaDownloadOnUnmeteredOnly = ai.getGemmaDownloadOnUnmeteredOnly(),
        ttsModel = tts.getTtsModel()
    )

    fun setPaywallBypassMethod(method: PaywallBypassMethod) = reader.setPaywallBypassMethod(method)
    fun setBionicReadingEnabled(enabled: Boolean) = reader.setBionicReadingEnabled(enabled)
    fun setCredibilityScoreEnabled(enabled: Boolean) = reader.setCredibilityScoreEnabled(enabled)
    fun setErrorReportingEnabled(enabled: Boolean) = errors.setEnabled(enabled)
    fun setGemmaBackend(backend: GemmaBackend) = ai.setGemmaBackend(backend)
    fun setGemmaDownloadOnUnmeteredOnly(enabled: Boolean) =
        ai.setGemmaDownloadOnUnmeteredOnly(enabled)

    fun performanceRecords(): List<SyncPerformanceRecord> = performance.getSyncPerformanceRecords()

    fun clearPerformanceRecords() = performance.clearSyncPerformanceRecords()
}
