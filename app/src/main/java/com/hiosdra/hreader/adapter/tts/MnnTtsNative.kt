package com.hiosdra.hreader.adapter.tts

internal class MnnTtsNative {
    private var handle = 0L

    fun load(
        modelDirectory: String,
        configName: String,
        numThreads: Int,
        backend: String,
        cacheDirectory: String
    ) {
        ensureHandle()
        check(nativeLoad(handle, modelDirectory, configName, numThreads, backend, cacheDirectory)) { lastError() }
    }

    fun synthesize(
        text: String,
        language: String,
        referenceAudio: String,
        maxFrames: Int
    ): FloatArray {
        ensureHandle()
        return nativeSynthesize(
            handle = handle,
            text = text,
            language = language,
            referenceAudio = referenceAudio,
            maxFrames = maxFrames
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
            check(handle != 0L) { "Could not create MNN TTS context" }
        }
    }

    private fun ensureLibraryLoaded() {
        Companion.ensureLibraryLoaded()
    }

    private fun lastError(): String =
        nativeLastError(handle)?.takeIf(String::isNotBlank) ?: "MNN TTS synthesis failed"

    private external fun nativeCreate(): Long

    private external fun nativeFree(handle: Long)

    private external fun nativeLoad(
        handle: Long,
        modelDirectory: String,
        configName: String,
        numThreads: Int,
        backend: String,
        cacheDirectory: String
    ): Boolean

    private external fun nativeSynthesize(
        handle: Long,
        text: String,
        language: String,
        referenceAudio: String,
        maxFrames: Int
    ): FloatArray?

    private external fun nativeLastError(handle: Long): String?

    private companion object {
        @Volatile
        private var libraryLoaded = false

        @Synchronized
        fun ensureLibraryLoaded() {
            if (!libraryLoaded) {
                System.loadLibrary("hreader_mnn_tts")
                libraryLoaded = true
            }
        }
    }
}
