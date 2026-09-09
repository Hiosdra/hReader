package com.hiosdra.hreader.presentation.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.sync.SyncFailureReason
import com.hiosdra.hreader.core.application.sync.SyncFailureStage
import com.hiosdra.hreader.core.application.sync.SyncFreshnessState
import com.hiosdra.hreader.core.application.sync.SyncRunState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHealthScreen(
    navController: NavController,
    viewModel: SyncHealthViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.sync_health_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SyncHealthSummary(
                    state = state,
                    onRetry = viewModel::retry
                )
            }
            item {
                SyncHealthTimes(state)
            }
            item {
                SyncHealthLatestRun(state)
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sync_health_feeds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(18.dp)
                                .height(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            if (state.feeds.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sync_health_no_feeds),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.feeds, key = { it.feed.id }) { feed ->
                    SyncHealthFeedCard(
                        state = feed,
                        now = state.now,
                        isOnline = state.isOnline,
                        isSyncing = state.isSyncing,
                        onRetry = { viewModel.retry(feed.feed.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncHealthSummary(
    state: SyncHealthUiState,
    onRetry: () -> Unit
) {
    val statusColor = if (state.freshness == SyncFreshnessState.FAILED) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (state.freshness == SyncFreshnessState.FAILED) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = freshnessLabel(state.freshness),
                style = MaterialTheme.typography.headlineSmall,
                color = statusColor,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = freshnessExplanation(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.isOnline) {
                Text(
                    text = stringResource(R.string.sync_health_waiting_for_connection),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else if (state.isSyncing) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(stringResource(R.string.sync_health_sync_now))
                }
            }
        }
    }
}

@Composable
private fun SyncHealthTimes(state: SyncHealthUiState) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            SyncHealthTimestampRow(
                label = stringResource(R.string.sync_health_last_successful),
                timestamp = state.snapshot.lastSuccessfulSyncAt,
                now = state.now
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            SyncHealthTimestampRow(
                label = stringResource(R.string.sync_health_last_attempted),
                timestamp = state.snapshot.lastAttemptedSyncAt,
                now = state.now
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            SyncHealthTimestampRow(
                label = stringResource(R.string.sync_health_next_scheduled),
                timestamp = state.nextScheduledSyncAt ?: 0L,
                now = state.now
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(
                text = stringResource(
                    R.string.sync_health_interval,
                    formatInterval(state.intervalMinutes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SyncHealthLatestRun(state: SyncHealthUiState) {
    val run = state.snapshot.lastRun ?: return
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sync_health_latest_run),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.sync_health_run_status,
                    runStateLabel(effectiveRunState(state, run.state))
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            SyncHealthTimestampRow(
                label = stringResource(R.string.sync_health_run_started),
                timestamp = run.startedAt,
                now = state.now
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.sync_health_feeds_updated, run.updatedFeedIds.size))
            Text(stringResource(R.string.sync_health_feeds_failed, run.failedFeedIds.size))
            Text(stringResource(R.string.sync_health_feeds_skipped, run.skippedFeedIds.size))
            Text(stringResource(R.string.sync_health_new_articles, run.newArticles))
            run.failureStage?.let { stage ->
                Text(
                    stringResource(R.string.sync_health_failure_stage, failureStageLabel(stage)),
                    color = MaterialTheme.colorScheme.error
                )
            }
            run.failureReason?.let { reason ->
                Text(
                    stringResource(R.string.sync_health_failure_reason, failureReasonLabel(reason)),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SyncHealthFeedCard(
    state: SyncHealthFeedUiState,
    now: Long,
    isOnline: Boolean,
    isSyncing: Boolean,
    onRetry: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.feed.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = feedFreshnessLabel(state.freshness),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.freshness == SyncFreshnessState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SyncHealthTimestampRow(
                label = stringResource(R.string.sync_health_last_checked),
                timestamp = state.lastAttemptedSyncAt,
                now = now
            )
            SyncHealthTimestampRow(
                label = stringResource(R.string.sync_health_cached_at),
                timestamp = state.lastSuccessfulSyncAt,
                now = now
            )
            SyncHealthTimestampRow(
                label = stringResource(R.string.sync_health_latest_published),
                timestamp = state.latestArticlePublishedAt,
                now = now
            )
            if (state.latestErrorReason != null) {
                Text(
                    text = stringResource(
                        R.string.sync_health_feed_error,
                        failureReasonLabel(state.latestErrorReason),
                        state.latestErrorStage?.let { failureStageLabel(it) }
                            ?: stringResource(R.string.sync_health_stage_article)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (state.freshness == SyncFreshnessState.FAILED) {
                TextButton(
                    onClick = onRetry,
                    enabled = isOnline && !isSyncing,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.sync_health_retry_sync))
                }
            }
        }
    }
}

@Composable
private fun SyncHealthTimestampRow(
    label: String,
    timestamp: Long,
    now: Long
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (timestamp > 0L) {
            Text(
                text = relativeTimestamp(timestamp, now),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = exactTimestamp(timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(R.string.sync_health_not_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun freshnessLabel(state: SyncFreshnessState): String = when (state) {
    SyncFreshnessState.NEVER_SYNCED -> stringResource(R.string.sync_health_status_never)
    SyncFreshnessState.SYNCING -> stringResource(R.string.sync_health_status_syncing)
    SyncFreshnessState.UP_TO_DATE -> stringResource(R.string.sync_health_status_up_to_date)
    SyncFreshnessState.STALE -> stringResource(R.string.sync_health_status_stale)
    SyncFreshnessState.FAILED -> stringResource(R.string.sync_health_status_failed)
    SyncFreshnessState.PARTIALLY_SUCCESSFUL -> stringResource(R.string.sync_health_status_partial)
    SyncFreshnessState.OFFLINE -> stringResource(R.string.sync_health_status_offline)
}

@Composable
private fun feedFreshnessLabel(state: SyncFreshnessState): String = when (state) {
    SyncFreshnessState.NEVER_SYNCED -> stringResource(R.string.sync_health_feed_never)
    SyncFreshnessState.SYNCING -> stringResource(R.string.sync_health_feed_syncing)
    SyncFreshnessState.UP_TO_DATE -> stringResource(R.string.sync_health_feed_up_to_date)
    SyncFreshnessState.STALE -> stringResource(R.string.sync_health_feed_stale)
    SyncFreshnessState.FAILED -> stringResource(R.string.sync_health_feed_failed)
    SyncFreshnessState.PARTIALLY_SUCCESSFUL -> stringResource(R.string.sync_health_feed_failed)
    SyncFreshnessState.OFFLINE -> stringResource(R.string.sync_health_status_offline)
}

@Composable
private fun freshnessExplanation(state: SyncHealthUiState): String = when (state.freshness) {
    SyncFreshnessState.NEVER_SYNCED -> stringResource(R.string.sync_health_explanation_never)
    SyncFreshnessState.SYNCING -> stringResource(R.string.sync_health_explanation_syncing)
    SyncFreshnessState.UP_TO_DATE -> stringResource(R.string.sync_health_explanation_up_to_date)
    SyncFreshnessState.STALE -> stringResource(R.string.sync_health_explanation_stale)
    SyncFreshnessState.FAILED -> stringResource(R.string.sync_health_explanation_failed)
    SyncFreshnessState.PARTIALLY_SUCCESSFUL ->
        stringResource(R.string.sync_health_explanation_partial)
    SyncFreshnessState.OFFLINE -> stringResource(R.string.sync_health_explanation_offline)
}

@Composable
private fun runStateLabel(state: SyncRunState): String = when (state) {
    SyncRunState.RUNNING ->
        stringResource(R.string.sync_health_status_syncing)
    SyncRunState.SUCCEEDED ->
        stringResource(R.string.sync_health_status_up_to_date)
    SyncRunState.FAILED ->
        stringResource(R.string.sync_health_status_failed)
    SyncRunState.PARTIALLY_SUCCESSFUL ->
        stringResource(R.string.sync_health_status_partial)
    SyncRunState.CANCELLED ->
        stringResource(R.string.sync_health_status_cancelled)
}

private fun effectiveRunState(state: SyncHealthUiState, runState: SyncRunState): SyncRunState =
    if (runState != SyncRunState.RUNNING || !state.isOnline || state.isSyncing) {
        runState
    } else if (state.snapshot.lastSuccessfulSyncAt > 0L) {
        SyncRunState.PARTIALLY_SUCCESSFUL
    } else {
        SyncRunState.FAILED
    }

@Composable
private fun failureStageLabel(stage: SyncFailureStage): String = when (stage) {
    SyncFailureStage.ARTICLE_SYNC -> stringResource(R.string.sync_health_stage_article)
    SyncFailureStage.FEED_SYNC -> stringResource(R.string.sync_health_stage_feed)
    SyncFailureStage.ARTICLE_CONTENT -> stringResource(R.string.sync_health_stage_content)
    SyncFailureStage.FULL_PAGE -> stringResource(R.string.sync_health_stage_full_page)
}

@Composable
private fun failureReasonLabel(reason: SyncFailureReason): String = when (reason) {
    SyncFailureReason.NETWORK -> stringResource(R.string.sync_health_error_network)
    SyncFailureReason.SERVER -> stringResource(R.string.sync_health_error_server)
    SyncFailureReason.TIMEOUT -> stringResource(R.string.sync_health_error_timeout)
    SyncFailureReason.CONFIGURATION -> stringResource(R.string.sync_health_error_configuration)
    SyncFailureReason.UNKNOWN -> stringResource(R.string.sync_health_error_unknown)
}

@Composable
private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> pluralStringResource(R.plurals.sync_minutes, minutes, minutes)
    minutes < 1440 && minutes % 60 == 0 -> {
        val hours = minutes / 60
        pluralStringResource(R.plurals.sync_hours, hours, hours)
    }
    minutes < 1440 -> pluralStringResource(R.plurals.sync_minutes, minutes, minutes)
    else -> stringResource(R.string.sync_one_day)
}

@Composable
private fun relativeTimestamp(timestamp: Long, now: Long): String {
    val isFuture = timestamp > now
    val elapsed = Duration.ofMillis(
        if (isFuture) timestamp - now else (now - timestamp).coerceAtLeast(0L)
    )
    if (isFuture) {
        return when {
            elapsed.toMinutes() < 1 -> stringResource(R.string.sync_health_in_less_than_minute)
            elapsed.toHours() < 1 -> pluralStringResource(
                R.plurals.sync_health_in_minutes,
                elapsed.toMinutes().toInt(),
                elapsed.toMinutes().toInt()
            )
            elapsed.toDays() < 1 -> pluralStringResource(
                R.plurals.sync_health_in_hours,
                elapsed.toHours().toInt(),
                elapsed.toHours().toInt()
            )
            else -> pluralStringResource(
                R.plurals.sync_health_in_days,
                elapsed.toDays().toInt(),
                elapsed.toDays().toInt()
            )
        }
    }
    return when {
        elapsed.toMinutes() < 1 -> stringResource(R.string.offline_just_now)
        elapsed.toHours() < 1 -> stringResource(
            R.string.offline_minutes_ago,
            elapsed.toMinutes().toInt()
        )
        elapsed.toDays() < 1 -> stringResource(
            R.string.offline_hours_ago,
            elapsed.toHours().toInt()
        )
        else -> pluralStringResource(
            R.plurals.offline_days_ago,
            elapsed.toDays().toInt(),
            elapsed.toDays().toInt()
        )
    }
}

@Composable
private fun exactTimestamp(timestamp: Long): String {
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
    }
    return formatter.format(Instant.ofEpochMilli(timestamp))
}
