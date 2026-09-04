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
        assertEquals("piper-lessac-high", TtsModelPackageCatalog.directoryName(TtsModel.PIPER_LESSAC_HIGH))
        assertEquals("kitten-mini-en-v0_8", TtsModelPackageCatalog.directoryName(TtsModel.KITTEN_MINI))
        assertEquals("matcha-icefall-en_US-ljspeech", TtsModelPackageCatalog.directoryName(TtsModel.MATCHA_LJSPEECH))
        assertNull(TtsModelPackageCatalog.packageFor(TtsModel.ANDROID))
    }

    @Test
    fun `keeps package engine family data typed`() {
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.SUPERTONIC)?.engineFiles is SherpaModelFiles.Supertonic)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.KOKORO)?.engineFiles is SherpaModelFiles.Kokoro)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.GOSIA)?.engineFiles is SherpaModelFiles.Vits)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.KITTEN_MINI)?.engineFiles is SherpaModelFiles.Kitten)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.MATCHA_LJSPEECH)?.engineFiles is SherpaModelFiles.Matcha)
        assertTrue(TtsModelPackageCatalog.packageFor(TtsModel.MNN_0_6B_BASE_INT8)?.engineFiles is MnnModelFiles)
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
    fun `registers model assets and generated config for native runtimes`() {
        val mnn = checkNotNull(TtsModelPackageCatalog.packageFor(TtsModel.MNN_0_6B_BASE_INT8))
        assertEquals(listOf("config.json"), mnn.generatedFiles.map(GeneratedFile::name))
        assertEquals("config.json", (mnn.engineFiles as MnnModelFiles).config)
        assertTrue(mnn.generatedFiles.single().content.contains("\"backend_type\": \"cpu\""))
        assertTrue(mnn.generatedFiles.single().content.contains("\"mllm\""))
        assertTrue(mnn.requiredFiles.contains("qwen3_tts_ref.wav"))
    }

    @Test
    fun `preflight includes model bytes and staging headroom`() {
        val mnn = checkNotNull(TtsModelPackageCatalog.packageFor(TtsModel.MNN_0_6B_BASE_INT8))
        val downloadBytes = mnn.files.sumOf(RemoteFile::size)
        val requiredBytes = mnn.requiredStorageBytes()

        assertTrue(requiredBytes > downloadBytes)
        assertTrue(requiredBytes - downloadBytes >= 128L * 1024 * 1024)
        assertFalse(hasEnoughTtsModelStorage(downloadBytes, requiredBytes))
        assertTrue(hasEnoughTtsModelStorage(requiredBytes, requiredBytes))
    }

    @Test
    fun `pins int8 tokenizer to an existing modelscope revision`() {
        val tokenizer = checkNotNull(
            TtsModelPackageCatalog.packageFor(TtsModel.MNN_0_6B_BASE_INT8)
                ?.files
                ?.single { it.name == "tokenizer.txt" }
        )

        assertEquals(
            "https://www.modelscope.cn/models/huangzhengxiang/Qwen3-TTS-0.6B-Base-INT8-MNN/" +
                "resolve/cfc9c9ff6f976fbde0be10b97d3d927c2c4a6049/tokenizer.txt",
            tokenizer.url
        )
    }
}
