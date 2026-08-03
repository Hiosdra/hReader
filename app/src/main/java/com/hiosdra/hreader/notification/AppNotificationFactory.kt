package com.hiosdra.hreader.notification

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.hiosdra.hreader.R
import java.util.UUID

object AppNotificationFactory {
    private const val SYNC_NOTIFICATION_BASE = 0x10000000
    private const val MODEL_DOWNLOAD_NOTIFICATION_BASE = 0x20000000

    fun syncForegroundInfo(
        context: Context,
        workerId: UUID,
        title: String,
        text: String,
        done: Int = 0,
        total: Int = 0
    ): ForegroundInfo {
        NotificationChannels.ensure(context)
        val notification = NotificationCompat.Builder(context, NotificationChannels.SYNC)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(
                total,
                done.coerceIn(0, total.coerceAtLeast(0)),
                total <= 0
            )
            .addAction(
                R.drawable.baseline_details_24,
                context.getString(R.string.notification_cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(workerId)
            )
            .build()
        return ForegroundInfo(
            notificationId(workerId, SYNC_NOTIFICATION_BASE),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    fun modelDownloadForegroundInfo(
        context: Context,
        workerId: UUID,
        modelName: String,
        progress: Float
    ): ForegroundInfo {
        NotificationChannels.ensure(context)
        val percentage = (progress.coerceIn(0f, 1f) * 100).toInt()
        val notification = NotificationCompat.Builder(context, NotificationChannels.MODEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.notification_model_download_title))
            .setContentText(modelName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percentage, false)
            .addAction(
                R.drawable.baseline_details_24,
                context.getString(R.string.notification_cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(workerId)
            )
            .build()
        return ForegroundInfo(
            notificationId(workerId, MODEL_DOWNLOAD_NOTIFICATION_BASE),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun notificationId(workerId: UUID, base: Int): Int =
        base + (workerId.hashCode() and 0x0fffffff)
}
