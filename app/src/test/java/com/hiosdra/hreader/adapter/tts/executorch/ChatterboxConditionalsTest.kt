package com.hiosdra.hreader.adapter.tts.executorch

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatterboxConditionalsTest {
    @Test
    fun `loads tensor storages from PyTorch conditioning archive`() {
        val archive = createArchive()

        val conditionals = ChatterboxConditionals.load(archive)

        assertEquals(256, conditionals.speakerEmbedding.size)
        assertEquals(150, conditionals.conditioningSpeechTokens.size)
        assertEquals(75, conditionals.promptTokens.size)
        assertEquals(1f, conditionals.speakerEmbedding.first())
        assertEquals(3L, conditionals.conditioningSpeechTokens.first())
        assertEquals(6L, conditionals.promptTokens.first())
        assertEquals(157L, conditionals.promptTokenLength)
        assertEquals(314 * 80, conditionals.promptFeatures.size)
        assertEquals(9f, conditionals.promptFeatures.first())
        assertEquals(192, conditionals.xVector.size)
        assertEquals(13f, conditionals.xVector.first())
    }

    private fun createArchive(): File {
        val file = File.createTempFile("chatterbox-conds", ".pt")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { output ->
            write(output, "conds/byteorder", "little".toByteArray())
            write(output, "conds/data/0", floatsArray(FloatArray(256) { it + 1f }))
            write(output, "conds/data/1", longsArray(LongArray(150) { it + 3L }))
            write(output, "conds/data/2", floats(0.5f))
            write(output, "conds/data/3", longsArray(LongArray(158) { it + 6L }))
            write(output, "conds/data/4", longs(157L))
            write(output, "conds/data/5", floatsArray(FloatArray(314 * 80) { it + 9f }))
            write(output, "conds/data/6", floatsArray(FloatArray(192) { it + 13f }))
        }
        return file
    }

    private fun write(output: ZipOutputStream, name: String, bytes: ByteArray) {
        output.putNextEntry(ZipEntry(name))
        output.write(bytes)
        output.closeEntry()
    }

    private fun floats(vararg values: Float): ByteArray = floatsArray(values)

    private fun floatsArray(values: FloatArray): ByteArray =
        ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach(::putFloat)
        }.array()

    private fun longs(vararg values: Long): ByteArray = longsArray(values)

    private fun longsArray(values: LongArray): ByteArray =
        ByteBuffer.allocate(values.size * Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach(::putLong)
        }.array()
}
