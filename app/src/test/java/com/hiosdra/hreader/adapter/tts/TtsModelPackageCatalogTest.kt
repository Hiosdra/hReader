package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog
import com.hiosdra.hreader.core.application.tts.TtsEngineFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsModelPackageCatalogTest {
    @Test
    fun `registers a package for every non bundled catalog model`() {
        TtsModelCatalog.models
            .filterNot(TtsModel::bundled)
            .forEach { model -> assertNotNull(model.name, TtsModelPackageCatalog.packageFor(model)) }
    }

    @Test
    fun `registers one package for every neural model`() {
        assertEquals("supertonic", TtsModelPackageCatalog.directoryName(TtsModel.SUPERTONIC))
        assertEquals("kokoro", TtsModelPackageCatalog.directoryName(TtsModel.KOKORO))
        assertEquals("gosia", TtsModelPackageCatalog.directoryName(TtsModel.GOSIA))
        assertEquals("piper-bass-high", TtsModelPackageCatalog.directoryName(TtsModel.PIPER_BASS_HIGH))
        assertEquals("piper-darkman-medium", TtsModelPackageCatalog.directoryName(TtsModel.PIPER_DARKMAN_MEDIUM))
        assertEquals("piper-jarvis-medium", TtsModelPackageCatalog.directoryName(TtsModel.PIPER_JARVIS_MEDIUM))
        assertEquals("piper-justyna-medium", TtsModelPackageCatalog.directoryName(TtsModel.PIPER_JUSTYNA_MEDIUM))
        assertEquals("piper-mc-speech-medium", TtsModelPackageCatalog.directoryName(TtsModel.PIPER_MC_SPEECH_MEDIUM))
        assertEquals("piper-meski-medium", TtsModelPackageCatalog.directoryName(TtsModel.PIPER_MESKI_MEDIUM))
        assertEquals("piper-zenski-medium", TtsModelPackageCatalog.directoryName(TtsModel.PIPER_ZENSKI_MEDIUM))
        assertNull(TtsModelPackageCatalog.packageFor(TtsModel.ANDROID))
    }

    @Test
    fun `keeps package engine family data typed`() {
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.SUPERTONIC)?.engineFiles is SherpaModelFiles.Supertonic)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.KOKORO)?.engineFiles is SherpaModelFiles.Kokoro)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.GOSIA)?.engineFiles is SherpaModelFiles.Vits)
        TtsModelCatalog.models
            .filter { it.family == TtsEngineFamily.VITS }
            .forEach { model ->
                assertTrue(TtsModelPackageCatalog.packageFor(model)?.engineFiles is SherpaModelFiles.Vits)
            }
    }
}
