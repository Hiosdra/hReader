package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleTtsModelSelectionTest {
    @Test
    fun `keeps an installed compatible neural model on arm64`() {
        assertEquals(
            TtsModel.KOKORO,
            resolveArticleTtsModel(
                modelOverride = TtsModel.KOKORO,
                settingsModel = TtsModel.SUPERTONIC,
                language = "en",
                statuses = mapOf(TtsModel.KOKORO to TtsModelStatus.Available),
                supportsArm64 = true
            )
        )
    }

    @Test
    fun `falls back to Android when the requested model is not installed`() {
        assertEquals(
            TtsModel.ANDROID,
            resolveArticleTtsModel(
                modelOverride = TtsModel.KOKORO,
                settingsModel = TtsModel.SUPERTONIC,
                language = "en",
                statuses = emptyMap(),
                supportsArm64 = true
            )
        )
    }

    @Test
    fun `falls back to Android when the requested model does not support the language`() {
        assertEquals(
            TtsModel.ANDROID,
            resolveArticleTtsModel(
                modelOverride = TtsModel.KOKORO,
                settingsModel = TtsModel.SUPERTONIC,
                language = "pl",
                statuses = mapOf(TtsModel.KOKORO to TtsModelStatus.Available),
                supportsArm64 = true
            )
        )
    }

    @Test
    fun `falls back to Android on devices without arm64`() {
        assertEquals(
            TtsModel.ANDROID,
            resolveArticleTtsModel(
                modelOverride = TtsModel.SUPERTONIC,
                settingsModel = TtsModel.SUPERTONIC,
                language = "pl",
                statuses = mapOf(TtsModel.SUPERTONIC to TtsModelStatus.Available),
                supportsArm64 = false
            )
        )
    }

    @Test
    fun `uses language setting when there is no temporary override`() {
        assertEquals(
            TtsModel.SUPERTONIC,
            resolveArticleTtsModel(
                modelOverride = null,
                settingsModel = TtsModel.SUPERTONIC,
                language = "pl",
                statuses = mapOf(TtsModel.SUPERTONIC to TtsModelStatus.Available),
                supportsArm64 = true
            )
        )
    }
}
