package com.hiosdra.hreader

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hiosdra.hreader.navigation.AppNavigation
import com.hiosdra.hreader.ui.theme.HReaderTheme
import com.hiosdra.hreader.worker.ArticleContentSyncWorker
import com.hiosdra.hreader.worker.ContentSyncWorker

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val SYNC_WORK_NAME = "OnExitChainedSync"
        private const val MIN_INTERVAL_MS = 2 * 60 * 1000L
        private var lastSyncAt: Long = 0L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            HReaderTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        triggerContentSync()
    }

    private fun triggerContentSync() {
        val now = System.currentTimeMillis()
        if (now - lastSyncAt < MIN_INTERVAL_MS) {
            Log.i(TAG, "Skipping sync (throttled)")
            return
        }
        lastSyncAt = now
        Log.i(TAG, "Scheduling chained content sync")
        val wm = WorkManager.getInstance(applicationContext)
        val contentSync = OneTimeWorkRequestBuilder<ContentSyncWorker>().build()
        val articleContentSync = OneTimeWorkRequestBuilder<ArticleContentSyncWorker>().build()
        wm.beginUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, contentSync)
            .then(articleContentSync)
            .enqueue()
    }
}
