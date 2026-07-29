package com.hiosdra.hreader.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.hiosdra.hreader.MainActivity

/**
 * What the widget draws, written by [UnreadWidgetUpdater].
 *
 * A Glance composition runs in the launcher's process and cannot open the database or wait on it,
 * so everything it needs is stored ahead of time.
 */
internal object WidgetState {
    val UNREAD_COUNT = intPreferencesKey("unread_count")
    val HEADLINES = stringPreferencesKey("headlines")

    /** Separates the stored headlines. The same record separator the Room converters use. */
    const val HEADLINE_SEPARATOR = ""

    fun encode(headlines: List<String>): String = headlines.joinToString(HEADLINE_SEPARATOR)

    fun decode(stored: String?): List<String> =
        stored?.split(HEADLINE_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
}

/** Unread count and the next few headlines, on the home screen. */
class UnreadWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val preferences = currentState<Preferences>()
                WidgetBody(
                    unread = preferences[WidgetState.UNREAD_COUNT] ?: 0,
                    headlines = WidgetState.decode(preferences[WidgetState.HEADLINES])
                )
            }
        }
    }
}

@Composable
private fun WidgetBody(unread: Int, headlines: List<String>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = if (unread == 0) "All caught up" else "$unread unread",
            style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (headlines.isEmpty()) {
            Text(
                text = "Nothing waiting to be read",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
        }
        headlines.forEach { headline ->
            Text(
                text = headline,
                maxLines = 2,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
        }
    }
}

class UnreadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UnreadWidget()
}
