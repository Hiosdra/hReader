package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog
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
        assertNull(TtsModelPackageCatalog.packageFor(TtsModel.ANDROID))
    }

    @Test
    fun `keeps package engine family data typed`() {
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.SUPERTONIC)?.engineFiles is SherpaModelFiles.Supertonic)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.KOKORO)?.engineFiles is SherpaModelFiles.Kokoro)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.GOSIA)?.engineFiles is SherpaModelFiles.Vits)
    }
}
