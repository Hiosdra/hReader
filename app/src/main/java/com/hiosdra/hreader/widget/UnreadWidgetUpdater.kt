package com.hiosdra.hreader.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "UnreadWidgetUpdater"

/** How many headlines fit a widget without turning it into a second article list. */
private const val HEADLINE_LIMIT = 4

/**
 * How long requests are collected before one refresh runs. Paging through articles marks each of
 * them read in turn, and a widget rewrite per swipe is a binder round trip the reader pays for.
 */
private const val COALESCE_WINDOW_MILLIS = 1_000L

/**
 * Fills the widget with what it shows. The Glance composition runs in the launcher's process and
 * cannot open the database, so everything it needs is written here — after a sync, and after the
 * read state changes in the app.
 */
class UnreadWidgetUpdater(
    private val context: Context,
    private val articleRepository: ArticleRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    /**
     * Conflated: while a refresh is pending, further requests collapse into it. What matters is
     * that a refresh happens after the last change, not that one happens per change.
     */
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (request in requests) {
                delay(COALESCE_WINDOW_MILLIS)
                refresh()
            }
        }
    }

    /** Asks for a refresh soon. Returns immediately; use [refresh] when it has to have happened. */
    fun requestRefresh() {
        requests.trySend(Unit)
    }

    /**
     * Does nothing when no widget has been placed — reading the article list to write it nowhere is
     * work every sync would otherwise repeat.
     */
    suspend fun refresh() {
        val ids = runCatching { GlanceAppWidgetManager(context).getGlanceIds(UnreadWidget::class.java) }
            .getOrElse {
                Log.w(TAG, "Could not enumerate widgets: ${it.message}")
                return
            }
        if (ids.isEmpty()) return

        val summary = runCatching { articleRepository.unreadSummary(HEADLINE_LIMIT) }
            .getOrElse {
                Log.w(TAG, "Could not read the unread summary: ${it.message}")
                return
            }

        ids.forEach { id ->
            updateAppWidgetState(context, id) { preferences: MutablePreferences ->
                preferences[WidgetState.UNREAD_COUNT] = summary.unreadCount
                preferences[WidgetState.HEADLINES] = WidgetState.encode(summary.headlines)
            }
        }
        UnreadWidget().updateAll(context)
    }
}
