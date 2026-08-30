package com.hiosdra.hreader.entrypoint.notification

import android.app.Application
import android.app.NotificationManager
import com.hiosdra.hreader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = NotificationChannelsTestApplication::class, sdk = [35])
class NotificationChannelsTest {

    @Test
    fun `ensure creates all low importance application channels`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)

        NotificationChannels.ensure(context)
        NotificationChannels.ensure(context)

        val channels = manager.notificationChannels.associateBy { it.id }
        assertEquals(
            setOf(
                NotificationChannels.SYNC,
                NotificationChannels.TTS,
                NotificationChannels.MODEL_DOWNLOAD,
                NotificationChannels.AI_MODEL_DOWNLOAD
            ),
            channels.keys
        )
        assertTrue(channels.values.all { it.importance == NotificationManager.IMPORTANCE_LOW })
        assertEquals(
            context.getString(R.string.notification_channel_sync),
            channels.getValue(NotificationChannels.SYNC).name
        )
        assertEquals(
            context.getString(R.string.notification_channel_ai_model_download),
            channels.getValue(NotificationChannels.AI_MODEL_DOWNLOAD).name
        )
    }
}

private class NotificationChannelsTestApplication : Application()
