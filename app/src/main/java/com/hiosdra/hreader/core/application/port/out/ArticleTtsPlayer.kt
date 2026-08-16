package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.tts.TtsModel
import kotlinx.coroutines.flow.StateFlow

data class ArticleTtsState(
    val articleId: Long? = null,
    val title: String = "",
    val model: TtsModel? = null,
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val error: String? = null
) {
    val progress: Float
        get() = if (totalChunks == 0) 0f else currentChunk.toFloat() / totalChunks
}

interface ArticleTtsPlayer {
    val state: StateFlow<ArticleTtsState>
    fun play(articleId: Long, title: String, html: String)
    fun stop()
    fun stopFromService()
    fun pause()
    fun resume()
}
