package com.hiosdra.hreader.adapter.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceMigrationTest {
    @Test
    fun `legacy preference migration is idempotent and preserves a current value`() {
        val preferences = mutablePreferencesOf(
            TtsPreferenceKeys.legacyTtsVitsNoiseScale to 0.5f,
            TtsPreferenceKeys.legacyTtsVitsDurationNoiseScale to 0.6f
        )

        migrateLegacyPreferenceKeys(preferences)
        migrateLegacyPreferenceKeys(preferences)

        assertEquals(0.5f, preferences[TtsPreferenceKeys.ttsVitsNoiseScale])
        assertEquals(0.6f, preferences[TtsPreferenceKeys.ttsVitsDurationNoiseScale])
        assertFalse(preferences.contains(TtsPreferenceKeys.legacyTtsVitsNoiseScale))
        assertFalse(preferences.contains(TtsPreferenceKeys.legacyTtsVitsDurationNoiseScale))
    }

    @Test
    fun `legacy preference migration does not overwrite a renamed value`() {
        val preferences = mutablePreferencesOf(
            TtsPreferenceKeys.ttsVitsNoiseScale to 0.8f,
            TtsPreferenceKeys.legacyTtsVitsNoiseScale to 0.5f
        )

        migrateLegacyPreferenceKeys(preferences)

        assertEquals(0.8f, preferences[TtsPreferenceKeys.ttsVitsNoiseScale])
        assertTrue(!preferences.contains(TtsPreferenceKeys.legacyTtsVitsNoiseScale))
    }

    @Test
    fun `legacy preference migration drops retired voice settings and keeps the Kokoro voice`() {
        val preferences = mutablePreferencesOf(
            TtsPreferenceKeys.legacyTtsKokoroSpeaker to 40,
            TtsPreferenceKeys.legacyTtsKittenSpeaker to 5,
            TtsPreferenceKeys.ttsKokoroSpeaker to 16
        )

        migrateLegacyPreferenceKeys(preferences)

        assertFalse(preferences.contains(TtsPreferenceKeys.legacyTtsKokoroSpeaker))
        assertFalse(preferences.contains(TtsPreferenceKeys.legacyTtsKittenSpeaker))
        assertEquals(16, preferences[TtsPreferenceKeys.ttsKokoroSpeaker])
    }
}
