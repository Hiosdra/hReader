package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleTtsModelSelectionTest {
    @Test
    fun `keeps an installed compatible neural model on arm64`() {
        assertEquals(
            TtsModel.KITTEN_MINI,
            resolveArticleTtsModel(
                modelOverride = TtsModel.KITTEN_MINI,
                settingsModel = TtsModel.SUPERTONIC,
                language = "en",
                statuses = mapOf(TtsModel.KITTEN_MINI to TtsModelStatus.Available),
                supportsArm64 = true
            )
        )
    }

    @Test
    fun `falls back to Android when the requested model is not installed`() {
        assertEquals(
            TtsModel.ANDROID,
            resolveArticleTtsModel(
                modelOverride = TtsModel.KITTEN_MINI,
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
                modelOverride = TtsModel.KITTEN_MINI,
                settingsModel = TtsModel.SUPERTONIC,
                language = "pl",
                statuses = mapOf(TtsModel.KITTEN_MINI to TtsModelStatus.Available),
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
                settingsModel = TtsModel.GOSIA,
                language = "pl",
                statuses = mapOf(TtsModel.SUPERTONIC to TtsModelStatus.Available),
                supportsArm64 = false
            )
        )
    }

    @Test
    fun `uses language setting when there is no temporary override`() {
        assertEquals(
            TtsModel.GOSIA,
            resolveArticleTtsModel(
                modelOverride = null,
                settingsModel = TtsModel.GOSIA,
                language = "pl",
                statuses = mapOf(TtsModel.GOSIA to TtsModelStatus.Available),
                supportsArm64 = true
            )
        )
    }
}
