package com.hiosdra.hreader.adapter.tts.executorch

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

internal data class ChatterboxConditionals(
    val speakerEmbedding: FloatArray,
    val conditioningSpeechTokens: LongArray,
    val promptTokens: LongArray,
    val promptTokenLength: Long,
    val promptFeatures: FloatArray,
    val promptFeatureFrames: Int,
    val xVector: FloatArray
) {
    companion object {
        private const val SPEAKER_EMBEDDING_SIZE = 256
        private const val CONDITIONING_SPEECH_TOKEN_SIZE = 150
        private const val PROMPT_TOKEN_SIZE = 75
        private const val PROMPT_FEATURE_FRAMES = 314
        private const val PROMPT_FEATURE_CHANNELS = 80
        private const val XVECTOR_SIZE = 192

        fun load(file: File): ChatterboxConditionals = ZipFile(file).use { archive ->
            val byteOrder = archive.readEntry("conds/byteorder").decodeToString()
            check(byteOrder == "little") { "Unsupported Chatterbox conditioning byte order" }
            val speakerEmbedding = archive.readFloats("conds/data/0", SPEAKER_EMBEDDING_SIZE)
            val conditioningSpeechTokens = archive.readLongs(
                "conds/data/1",
                CONDITIONING_SPEECH_TOKEN_SIZE
            )
            archive.readFloats("conds/data/2", 1)
            val promptTokens = archive.readLongs("conds/data/3", PROMPT_TOKEN_SIZE)
            val promptTokenLength = archive.readLongs("conds/data/4", 1).single()
            val promptFeatures = archive.readFloats(
                "conds/data/5",
                PROMPT_FEATURE_FRAMES * PROMPT_FEATURE_CHANNELS
            )
            val xVector = archive.readFloats("conds/data/6", XVECTOR_SIZE)
            ChatterboxConditionals(
                speakerEmbedding = speakerEmbedding,
                conditioningSpeechTokens = conditioningSpeechTokens,
                promptTokens = promptTokens,
                promptTokenLength = promptTokenLength,
                promptFeatures = promptFeatures,
                promptFeatureFrames = PROMPT_FEATURE_FRAMES,
                xVector = xVector
            )
        }

        private fun ZipFile.readFloats(name: String, count: Int): FloatArray =
            readEntry(name).toFloatArray(count)

        private fun ZipFile.readLongs(name: String, count: Int): LongArray =
            readEntry(name).toLongArray(count)

        private fun ZipFile.readEntry(name: String): ByteArray =
            getInputStream(checkNotNull(getEntry(name)) { "Missing Chatterbox conditioning entry: $name" })
                .use { it.readBytes() }

        private fun ByteArray.toFloatArray(count: Int): FloatArray {
            check(size >= count * Float.SIZE_BYTES) { "Invalid Chatterbox float tensor size" }
            return FloatArray(count).also { result ->
                asByteBuffer().asFloatBuffer().get(result)
            }
        }

        private fun ByteArray.toLongArray(count: Int): LongArray {
            check(size >= count * Long.SIZE_BYTES) { "Invalid Chatterbox long tensor size" }
            return LongArray(count).also { result ->
                asByteBuffer().asLongBuffer().get(result)
            }
        }

        private fun ByteArray.asByteBuffer(): ByteBuffer =
            ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    }
}
