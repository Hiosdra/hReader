package com.hiosdra.hreader.presentation.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.presentation.feedback.FeedbackKind
import com.hiosdra.hreader.presentation.feedback.FeedbackSnackbarVisuals
import com.hiosdra.hreader.presentation.theme.Green80
import java.text.NumberFormat

@Composable
internal fun MainActionDock(
    unreadCount: Int,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onMarkRead: () -> Unit,
    modifier: Modifier = Modifier,
    onHeightChanged: (Int) -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
            .onSizeChanged { onHeightChanged(it.height) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        SnackbarHost(snackbarHostState, Modifier.weight(1f)) { data ->
            MainFeedbackCard(data)
        }
        Box(Modifier.width(112.dp)) {
            if (unreadCount > 0 || busy) {
                val label = pluralStringResource(
                    R.plurals.main_mark_articles_read, unreadCount, unreadCount
                )
                Button(
                    onClick = onMarkRead,
                    enabled = !busy && unreadCount > 0,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .width(112.dp)
                        .height(64.dp)
                        .semantics { contentDescription = label }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clearAndSetSemantics {}
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Done, contentDescription = null)
                        }
                        Text(
                            text = if (unreadCount > 999) "999+" else NumberFormat.getIntegerInstance().format(unreadCount),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainFeedbackCard(data: SnackbarData) {
    val request = (data.visuals as? FeedbackSnackbarVisuals)?.request
    val kind = request?.kind
    val isError = kind == FeedbackKind.RECOVERABLE_ERROR
    val isSuccess = kind == FeedbackKind.UNDO
    val tint = when {
        isError -> MaterialTheme.colorScheme.error
        isSuccess -> Green80
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val typeLabel = stringResource(
        when {
            isError -> R.string.main_feedback_error
            isSuccess -> R.string.main_feedback_success
            else -> R.string.main_feedback_info
        }
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (isError) tint.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    when {
                        isError -> Icons.Default.Warning
                        isSuccess -> Icons.Default.CheckCircle
                        else -> Icons.Default.Info
                    },
                    contentDescription = typeLabel,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        data.visuals.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) tint else MaterialTheme.colorScheme.onSurface
                    )
                    request?.supportingText?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                data.visuals.actionLabel?.let {
                    TextButton(onClick = data::performAction, modifier = Modifier.weight(1f)) {
                        Text(it)
                    }
                }
                if (data.visuals.withDismissAction) {
                    IconButton(onClick = data::dismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.action_dismiss))
                    }
                }
            }
        }
    }
}
