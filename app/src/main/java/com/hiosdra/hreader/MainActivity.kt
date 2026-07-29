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
import com.hiosdra.hreader.navigation.AppNavigation
import com.hiosdra.hreader.ui.theme.HReaderTheme
import com.hiosdra.hreader.worker.SyncScheduler
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val syncScheduler: SyncScheduler by inject()

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
        Log.i(TAG, "Scheduling chained content sync")
        syncScheduler.enqueueBackgroundSyncChain()
    }
}
