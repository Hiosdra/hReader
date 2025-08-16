package com.hiosdra.hreader

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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
import org.koin.androidx.compose.KoinAndroidContext

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoinAndroidContext {
                HReaderTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        AppNavigation()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "App stopping, triggering content sync")
        triggerContentSync()
    }

    private fun triggerContentSync() {
        val contentSyncRequest = OneTimeWorkRequestBuilder<ContentSyncWorker>().build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "OnExitContentSync",
            ExistingWorkPolicy.REPLACE,
            contentSyncRequest
        )

        val articleContentSyncRequest = OneTimeWorkRequestBuilder<ArticleContentSyncWorker>().build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "OnExitArticleContentSync",
            ExistingWorkPolicy.REPLACE,
            articleContentSyncRequest
        )

        Log.i(TAG, "Content sync tasks scheduled")
    }
}
