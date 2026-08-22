package com.hiosdra.hreader.core.application.ai

import com.hiosdra.hreader.adapter.ai.gemma.Gemma4E2bModel
import org.junit.Assert.assertEquals
import org.junit.Test

class GemmaModelStorageTest {
    @Test
    fun initialDownloadNeedsTheModelAndSafetyMargin() {
        assertEquals(
            Gemma4E2bModel.MODEL_SIZE_BYTES + GEMMA_DOWNLOAD_SAFETY_MARGIN_BYTES,
            requiredGemmaDownloadBytes(Gemma4E2bModel.MODEL_SIZE_BYTES, 0L)
        )
    }

    @Test
    fun resumedDownloadNeedsOnlyTheRemainingBytesAndSafetyMargin() {
        assertEquals(
            Gemma4E2bModel.MODEL_SIZE_BYTES - 1_000_000_000L +
                GEMMA_DOWNLOAD_SAFETY_MARGIN_BYTES,
            requiredGemmaDownloadBytes(Gemma4E2bModel.MODEL_SIZE_BYTES, 1_000_000_000L)
        )
    }

    @Test
    fun invalidPartialFileNeedsAFullDownload() {
        assertEquals(
            Gemma4E2bModel.MODEL_SIZE_BYTES + GEMMA_DOWNLOAD_SAFETY_MARGIN_BYTES,
            requiredGemmaDownloadBytes(
                Gemma4E2bModel.MODEL_SIZE_BYTES,
                Gemma4E2bModel.MODEL_SIZE_BYTES + 1L
            )
        )
    }
}
