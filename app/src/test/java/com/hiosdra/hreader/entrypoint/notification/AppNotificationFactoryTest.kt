package com.hiosdra.hreader.entrypoint.notification

import android.app.Application
import android.app.Notification
import android.content.pm.ServiceInfo
import androidx.work.testing.WorkManagerTestInitHelper
import com.hiosdra.hreader.R
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = AppNotificationFactoryTestApplication::class, sdk = [35])
class AppNotificationFactoryTest {

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `sync foreground info contains cancellable progress notification`() {
        val context = RuntimeEnvironment.getApplication()
        val workerId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        val foregroundInfo = AppNotificationFactory.syncForegroundInfo(
            context = context,
            workerId = workerId,
            title = "Sync now",
            text = "Downloading",
            done = 3,
            total = 10
        )
        val notification = foregroundInfo.notification

        assertEquals(NotificationChannels.SYNC, notification.channelId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, foregroundInfo.foregroundServiceType)
        assertEquals("Sync now", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("Downloading", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(1, notification.actions.size)
        assertEquals(
            context.getString(R.string.notification_cancel),
            notification.actions.single().title
        )
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.actions.single().actionIntent != null)
    }

    @Test
    fun `model download notifications use their dedicated channels`() {
        val context = RuntimeEnvironment.getApplication()
        val workerId = UUID.fromString("00000000-0000-0000-0000-000000000002")

        val voiceInfo = AppNotificationFactory.modelDownloadForegroundInfo(
            context = context,
            workerId = workerId,
            modelName = "Supertonic",
            progress = 1.5f
        )
        val aiInfo = AppNotificationFactory.aiModelDownloadForegroundInfo(
            context = context,
            workerId = workerId,
            modelName = "Gemma",
            progress = -0.5f
        )

        assertEquals(NotificationChannels.MODEL_DOWNLOAD, voiceInfo.notification.channelId)
        assertEquals("Supertonic", voiceInfo.notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(
            context.getString(R.string.notification_model_download_title),
            voiceInfo.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
        )
        assertEquals(NotificationChannels.AI_MODEL_DOWNLOAD, aiInfo.notification.channelId)
        assertEquals("Gemma", aiInfo.notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(
            context.getString(R.string.notification_ai_model_download_title),
            aiInfo.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
        )
    }
}

private class AppNotificationFactoryTestApplication : Application()
