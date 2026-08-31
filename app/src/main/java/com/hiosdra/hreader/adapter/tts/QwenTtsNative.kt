package com.hiosdra.hreader.adapter.tts

internal class QwenTtsNative {
    private var handle = 0L

    fun load(modelDirectory: String, modelName: String, numThreads: Int) {
        ensureHandle()
        check(nativeLoad(handle, modelDirectory, modelName, numThreads)) { lastError() }
    }

    fun synthesize(
        text: String,
        languageId: Int,
        maxAudioTokens: Int,
        numThreads: Int,
        speaker: String?,
        instruction: String?
    ): FloatArray {
        ensureHandle()
        return nativeSynthesize(
            handle = handle,
            text = text,
            languageId = languageId,
            maxAudioTokens = maxAudioTokens,
            numThreads = numThreads,
            speaker = speaker,
            instruction = instruction
        ) ?: error(lastError())
    }

    fun release() {
        if (handle == 0L) return
        nativeFree(handle)
        handle = 0L
    }

    private fun ensureHandle() {
        ensureLibraryLoaded()
        if (handle == 0L) {
            handle = nativeCreate()
            check(handle != 0L) { "Could not create Qwen3-TTS context" }
        }
    }

    private fun ensureLibraryLoaded() {
        Companion.ensureLibraryLoaded()
    }

    private fun lastError(): String =
        nativeLastError(handle)?.takeIf(String::isNotBlank) ?: "Qwen3-TTS synthesis failed"

    private external fun nativeCreate(): Long

    private external fun nativeFree(handle: Long)

    private external fun nativeLoad(
        handle: Long,
        modelDirectory: String,
        modelName: String,
        numThreads: Int
    ): Boolean

    private external fun nativeSynthesize(
        handle: Long,
        text: String,
        languageId: Int,
        maxAudioTokens: Int,
        numThreads: Int,
        speaker: String?,
        instruction: String?
    ): FloatArray?

    private external fun nativeLastError(handle: Long): String?

    private companion object {
        @Volatile
        private var libraryLoaded = false

        @Synchronized
        fun ensureLibraryLoaded() {
            if (!libraryLoaded) {
                System.loadLibrary("hreader_qwen3_tts")
                libraryLoaded = true
            }
        }
    }
}
