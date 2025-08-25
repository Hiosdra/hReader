package com.hiosdra.hreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.util.SyncPerformanceLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException

class ContentSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {
    private val repository: ArticleRepository by inject()
    private val syncPerformanceLogger: SyncPerformanceLogger by inject()

    companion object {
        private const val TAG = "ContentSyncWorker"
    }

    override suspend fun doWork(): Result = try {
        Log.i(TAG, "Starting ContentSyncWorker")
        
        syncPerformanceLogger.measureSyncTime("Article refresh") {
            repository.refreshArticles()
        }
        
        Log.i(TAG, "ContentSyncWorker completed successfully")
        Result.success()
    } catch (e: Exception) {
        Log.e(TAG, "ContentSyncWorker failed: ${e.message}", e)
        if (e is IOException) Result.retry() else Result.failure()
    }
}
