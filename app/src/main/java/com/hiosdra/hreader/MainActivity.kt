package com.hiosdra.hreader

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import com.hiosdra.hreader.presentation.navigation.AppNavigation
import com.hiosdra.hreader.presentation.navigation.EntryPoint
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val syncScheduler: SyncRequester by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryPoint = intent?.entryPoint() ?: EntryPoint.ArticleList
        setContent {
            HReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.safeDrawingPadding()) {
                        AppNavigation(entryPoint = entryPoint)
                    }
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

/**
 * Where the app was asked to open: the article list, or the subscribe screen with the address
 * another app handed over.
 */
private fun Intent.entryPoint(): EntryPoint {
    if (action == Intent.ACTION_VIEW && data?.scheme == SHORTCUT_SCHEME) {
        return EntryPoint.AddFeed(url = null)
    }
    val shared = sharedFeedUrl() ?: return EntryPoint.ArticleList
    return EntryPoint.AddFeed(url = shared)
}

/**
 * Shared text is rarely only a URL — most apps put the page title in front of it — so the first
 * http address in it wins rather than the whole string.
 */
private fun Intent.sharedFeedUrl(): String? {
    val raw = when (action) {
        Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_VIEW -> dataString
        else -> null
    } ?: return null

    return raw.split(Regex("\\s+"))
        .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        ?.takeIf { runCatching { it.toUri().host }.getOrNull() != null }
}

private const val SHORTCUT_SCHEME = "hreader"
