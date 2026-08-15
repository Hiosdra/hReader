package com.hiosdra.hreader.data.tts

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hiosdra.hreader.MainActivity
import com.hiosdra.hreader.R
import com.hiosdra.hreader.notification.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ArticleTtsPlaybackService : Service() {
    companion object {
        const val ACTION_START = "com.hiosdra.hreader.action.TTS_START"
        private const val ACTION_PAUSE = "com.hiosdra.hreader.action.TTS_PAUSE"
        private const val ACTION_RESUME = "com.hiosdra.hreader.action.TTS_RESUME"
        private const val ACTION_STOP = "com.hiosdra.hreader.action.TTS_STOP"
        private const val NOTIFICATION_ID = 4201
        private const val REQUEST_CONTENT = 4202
        private const val REQUEST_PAUSE = 4203
        private const val REQUEST_RESUME = 4204
        private const val REQUEST_STOP = 4205
    }

    private val controller: ArticleTtsController by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaSession
    private var stateJob: Job? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        mediaSession = MediaSession(this, getString(R.string.notification_tts_title)).apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = this@ArticleTtsPlaybackService.controller.resume()

                override fun onPause() = this@ArticleTtsPlaybackService.controller.pause()

                override fun onStop() = this@ArticleTtsPlaybackService.controller.stop()
            })
            setSessionActivity(contentIntent())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startPlayback()
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_STOP -> {
                controller.stop()
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
        mediaSession.isActive = false
        mediaSession.release()
        controller.stopFromService()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPlayback() {
        if (!foregroundStarted) {
            foregroundStarted = true
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(controller.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
            mediaSession.isActive = true
            observeState()
        }
        updateState(controller.state.value)
    }

    private fun observeState() {
        stateJob = serviceScope.launch {
            controller.state.collectLatest(::updateState)
        }
    }

    private fun updateState(state: ArticleTtsState) {
        if (!foregroundStarted) return
        if (state.articleId == null) {
            mediaSession.isActive = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, state.title)
                .build()
        )
        mediaSession.setPlaybackState(playbackState(state))
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification(state))
    }

    private fun playbackState(state: ArticleTtsState): PlaybackState {
        val playback = when {
            state.isPreparing -> PlaybackState.STATE_BUFFERING
            state.isPaused -> PlaybackState.STATE_PAUSED
            state.isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_NONE
        }
        val actions = PlaybackState.ACTION_STOP or if (state.isPaused) {
            PlaybackState.ACTION_PLAY
        } else {
            PlaybackState.ACTION_PAUSE
        }
        return PlaybackState.Builder()
            .setActions(actions)
            .setState(playback, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build()
    }

    private fun notification(state: ArticleTtsState): android.app.Notification {
        val text = when {
            state.error != null -> state.error
            state.isPreparing -> getString(
                R.string.tts_preparing_voice,
                state.model?.let { getString(it.displayNameRes) }
                    ?: getString(R.string.tts_voice_model_default)
            )
            state.isPaused -> getString(R.string.tts_paused)
            state.totalChunks > 0 -> getString(
                R.string.tts_chunk_progress,
                state.currentChunk + 1,
                state.totalChunks
            )
            else -> getString(R.string.notification_tts_title)
        }
        val builder = NotificationCompat.Builder(this, NotificationChannels.TTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(state.title.ifBlank { getString(R.string.notification_tts_title) })
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setDeleteIntent(actionIntent(ACTION_STOP, REQUEST_STOP))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (state.isPaused) {
            builder.addAction(
                R.drawable.baseline_details_24,
                getString(R.string.tts_resume),
                actionIntent(ACTION_RESUME, REQUEST_RESUME)
            )
        } else {
            builder.addAction(
                R.drawable.baseline_details_24,
                getString(R.string.tts_pause),
                actionIntent(ACTION_PAUSE, REQUEST_PAUSE)
            )
        }
        builder.addAction(
            R.drawable.baseline_details_24,
            getString(R.string.tts_stop),
            actionIntent(ACTION_STOP, REQUEST_STOP)
        )
        return builder.build()
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_CONTENT,
        Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, ArticleTtsPlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
