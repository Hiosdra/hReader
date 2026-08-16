package com.hiosdra.hreader.entrypoint.tts

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlaybackServiceControl

class ArticleTtsPlaybackServiceLauncher(context: Context) : ArticleTtsPlaybackServiceControl {
    private val appContext = context.applicationContext

    override fun start(): Boolean = runCatching {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, ArticleTtsPlaybackService::class.java)
                .setAction(ArticleTtsPlaybackService.ACTION_START)
        )
    }.onFailure {
        Log.e(TAG, "Could not start TTS playback service", it)
    }.isSuccess

    override fun stop() {
        appContext.stopService(Intent(appContext, ArticleTtsPlaybackService::class.java))
    }

    private companion object {
        const val TAG = "ArticleTtsServiceLauncher"
    }
}
