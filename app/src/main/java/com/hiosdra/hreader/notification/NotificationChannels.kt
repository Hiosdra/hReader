package com.hiosdra.hreader.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.hiosdra.hreader.R

object NotificationChannels {
    const val SYNC = "sync"
    const val TTS = "tts"
    const val MODEL_DOWNLOAD = "model_download"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    SYNC,
                    context.getString(R.string.notification_channel_sync),
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    TTS,
                    context.getString(R.string.notification_channel_tts),
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    MODEL_DOWNLOAD,
                    context.getString(R.string.notification_channel_model_download),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        )
    }
}
