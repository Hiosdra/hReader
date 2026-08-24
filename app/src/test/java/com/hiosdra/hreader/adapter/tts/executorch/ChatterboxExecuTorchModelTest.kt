package com.hiosdra.hreader.adapter.tts.executorch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatterboxExecuTorchModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `describes the complete exported pipeline`() {
        assertEquals(24_000, ChatterboxExecuTorchModel.sampleRate)
        assertEquals(
            listOf(
                "voice_encoder.pte",
                "xvector_encoder.pte",
                "t3_cond_speech_emb.pte",
                "t3_cond_enc.pte",
                "t3_prefill.pte",
                "t3_decode.pte",
                "s3gen_encoder.pte",
                "cfm_step.pte",
                "hifigan.pte"
            ),
            ChatterboxExecuTorchModel.requiredModules.map(ChatterboxExecuTorchModule::fileName)
        )
    }

    @Test
    fun `requires every exported module file`() {
        val root = temporaryFolder.newFolder("chatterbox")

        assertFalse(ChatterboxExecuTorchModel.isComplete(root))
        ChatterboxExecuTorchModel.requiredModules.forEach { module ->
            temporaryFolder.newFile("chatterbox/${module.fileName}")
        }

        assertTrue(ChatterboxExecuTorchModel.isComplete(root))
        assertTrue(ChatterboxExecuTorchModel.missingFiles(root).isEmpty())
    }

    @Test
    fun `runtime validates package before loading native modules`() {
        val runtime = ChatterboxExecuTorchRuntime()

        assertThrows(IllegalStateException::class.java) {
            runtime.load(temporaryFolder.newFolder("incomplete"))
        }
        assertFalse(runtime.isLoaded())

        runtime.close()
    }
}
