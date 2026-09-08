package com.hiosdra.hreader.core.application.tts

sealed interface TtsModelCacheStatus {
    data object NotPrepared : TtsModelCacheStatus
    data class Preparing(val progress: Float) : TtsModelCacheStatus
    data object Ready : TtsModelCacheStatus
    data class Failed(val message: String) : TtsModelCacheStatus
}
