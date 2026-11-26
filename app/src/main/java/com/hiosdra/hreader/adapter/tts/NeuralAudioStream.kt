package com.hiosdra.hreader.adapter.tts

import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class NeuralAudioStream(
    val track: AudioTrack,
    val sampleRate: Int,
    private val awaitResumed: suspend () -> Unit,
    private val onChunkStarted: (Int) -> Unit
) {
    private val markers = ArrayDeque<ChunkMarker>()
    private val sliceFrames = maxOf(1, sampleRate / SLICES_PER_SECOND)
    private var writtenFrames = 0L

    fun markChunkStart(chunkIndex: Int) {
        markers.addLast(ChunkMarker(writtenFrames, chunkIndex))
    }

    suspend fun write(samples: FloatArray) {
        var offset = 0
        while (offset < samples.size) {
            awaitResumed()
            currentCoroutineContext().ensureActive()
            val length = minOf(sliceFrames, samples.size - offset)
            val written = withContext(Dispatchers.IO) {
                track.write(samples, offset, length, AudioTrack.WRITE_BLOCKING)
            }
            check(written >= 0) { "Audio playback failed ($written)" }
            if (written == 0) delay(RETRY_DELAY_MS)
            offset += written
            writtenFrames += written
            dispatchMarkers()
        }
        dispatchMarkers()
    }

    suspend fun drain() {
        val audibleFrames = writtenFrames
        write(FloatArray(track.bufferSizeInFrames))
        while (playedFrames() < audibleFrames) {
            awaitResumed()
            dispatchMarkers()
            delay(POLL_DELAY_MS)
        }
        dispatchMarkers()
    }

    fun close() {
        track.runCatching { stop() }
        track.release()
    }

    private fun playedFrames(): Long = track.playbackHeadPosition.toLong() and UINT_MASK

    private fun dispatchMarkers() {
        val played = playedFrames()
        while (markers.isNotEmpty() && markers.first().frame <= played) {
            onChunkStarted(markers.removeFirst().chunkIndex)
        }
    }

    private data class ChunkMarker(val frame: Long, val chunkIndex: Int)

    private companion object {
        const val SLICES_PER_SECOND = 20
        const val RETRY_DELAY_MS = 10L
        const val POLL_DELAY_MS = 20L
        const val UINT_MASK = 0xFFFFFFFFL
    }
}
