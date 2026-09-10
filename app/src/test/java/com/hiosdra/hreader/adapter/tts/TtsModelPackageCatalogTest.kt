package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog
import com.hiosdra.hreader.core.application.tts.TtsEngineFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

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
        assertEquals(
            "vits-coqui-pl-mai_female",
            TtsModelPackageCatalog.directoryName(TtsModel.COQUI_PL_MAI_FEMALE)
        )
        assertEquals("piper-lessac-high", TtsModelPackageCatalog.directoryName(TtsModel.PIPER_LESSAC_HIGH))
        assertEquals("kitten-mini-en-v0_8", TtsModelPackageCatalog.directoryName(TtsModel.KITTEN_MINI))
        assertEquals("matcha-icefall-en_US-ljspeech", TtsModelPackageCatalog.directoryName(TtsModel.MATCHA_LJSPEECH))
        assertNull(TtsModelPackageCatalog.packageFor(TtsModel.ANDROID))
    }

    @Test
    fun `keeps package engine family data typed`() {
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.SUPERTONIC)?.engineFiles is SherpaModelFiles.Supertonic)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.KOKORO)?.engineFiles is SherpaModelFiles.Kokoro)
        val coqui = TtsModelPackageCatalog.packageFor(TtsModel.COQUI_PL_MAI_FEMALE)
        assertTrue(coqui?.engineFiles is SherpaModelFiles.Vits)
        assertEquals(listOf("model.onnx", "tokens.txt"), coqui?.files?.map(RemoteFile::name))
        assertEquals("", (coqui?.engineFiles as SherpaModelFiles.Vits).dataDir)
        assertNull(coqui.archive)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.KITTEN_MINI)?.engineFiles is SherpaModelFiles.Kitten)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.MATCHA_LJSPEECH)?.engineFiles is SherpaModelFiles.Matcha)
        assertEquals(
            listOf("vocos-22khz-univ.onnx"),
            TtsModelPackageCatalog.packageFor(TtsModel.MATCHA_LJSPEECH)
                ?.supplementalFiles
                ?.map(RemoteFile::name)
        )
        TtsModelCatalog.models
            .filter { it.family == TtsEngineFamily.VITS }
            .forEach { model ->
                assertTrue(TtsModelPackageCatalog.packageFor(model)?.engineFiles is SherpaModelFiles.Vits)
            }
    }

    @Test
    fun `preflight includes model bytes and staging headroom`() {
        val packageDefinition =
            checkNotNull(TtsModelPackageCatalog.packageFor(TtsModel.SUPERTONIC))
        val downloadBytes =
            (packageDefinition.archive?.size ?: 0L) +
                packageDefinition.files.sumOf(RemoteFile::size) +
                packageDefinition.supplementalFiles.sumOf(RemoteFile::size)
        val requiredBytes = packageDefinition.requiredStorageBytes()

        assertTrue(requiredBytes > downloadBytes)
        assertTrue(requiredBytes - downloadBytes >= 128L * 1024 * 1024)
        assertFalse(hasEnoughTtsModelStorage(downloadBytes, requiredBytes))
        assertTrue(hasEnoughTtsModelStorage(requiredBytes, requiredBytes))
    }

    @Test
    fun `installed package requires expected downloaded file sizes`() {
        val root = Files.createTempDirectory("hreader-tts-package").toFile()
        try {
            val packageDefinition = TtsModelPackage(
                directoryName = "test",
                engineFiles = SherpaModelFiles.Vits("model.onnx", "tokens.txt", "data"),
                requiredFiles = listOf("model.onnx"),
                files = listOf(RemoteFile("model.onnx", "https://example.invalid/model", "hash", 3))
            )
            val model = root.resolve("model.onnx")

            model.writeText("12")
            assertFalse(packageDefinition.isComplete(root))

            model.writeText("123")
            assertTrue(packageDefinition.isComplete(root))
        } finally {
            root.deleteRecursively()
        }
    }
}
