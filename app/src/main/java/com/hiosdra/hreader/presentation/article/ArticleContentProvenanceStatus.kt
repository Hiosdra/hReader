package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.domain.model.ArticleContentDelivery
import com.hiosdra.hreader.core.domain.model.ArticleContentKind
import com.hiosdra.hreader.core.domain.model.ArticleContentProvenance
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ArticleContentProvenanceStatus(
    provenance: ArticleContentProvenance,
    contentState: ArticleContentLoadState = ArticleContentLoadState.FULL,
    onRetry: (() -> Unit)? = null,
    onOpenOriginal: (() -> Unit)? = null,
    onSwitchToFeed: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var detailsVisible by rememberSaveable(
        provenance.kind,
        provenance.sourceUrl,
        provenance.fetchedAt,
        provenance.delivery,
        provenance.isComplete,
        contentState
    ) { mutableStateOf(false) }
    val isLoading = contentState == ArticleContentLoadState.LOADING
    val kind = when {
        isLoading -> null
        contentState == ArticleContentLoadState.FALLBACK -> ArticleContentKind.FEED_FALLBACK
        contentState == ArticleContentLoadState.UNAVAILABLE -> ArticleContentKind.UNAVAILABLE
        else -> provenance.kind
    }
    val label = stringResource(
        if (isLoading) {
            R.string.article_content_source_loading
        } else {
            contentSourceLabel(kind ?: ArticleContentKind.UNKNOWN)
        }
    )
    val detailsDescription = stringResource(R.string.article_content_show_details)

    AssistChip(
        onClick = { detailsVisible = true },
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null
            )
        },
        modifier = modifier.semantics {
            contentDescription = detailsDescription
            stateDescription = label
        }
    )

    if (detailsVisible) {
        ArticleContentDetailsDialog(
            label = label,
            kind = kind ?: ArticleContentKind.UNKNOWN,
            provenance = provenance,
            isLoading = isLoading,
            onRetry = onRetry,
            onOpenOriginal = onOpenOriginal,
            onSwitchToFeed = onSwitchToFeed,
            onDismiss = { detailsVisible = false }
        )
    }
}

@Composable
private fun ArticleContentDetailsDialog(
    label: String,
    kind: ArticleContentKind,
    provenance: ArticleContentProvenance,
    isLoading: Boolean,
    onRetry: (() -> Unit)?,
    onOpenOriginal: (() -> Unit)?,
    onSwitchToFeed: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val locale = LocalLocale.current.platformLocale
    val description = stringResource(
        if (isLoading) {
            R.string.article_content_source_loading_description
        } else {
            contentSourceDescription(kind)
        }
    )
    val canRetry = !isLoading &&
        kind in setOf(ArticleContentKind.FEED_FALLBACK, ArticleContentKind.UNAVAILABLE) &&
        onRetry != null
    val canOpenOriginal = !isLoading &&
        kind in setOf(ArticleContentKind.FEED_FALLBACK, ArticleContentKind.UNAVAILABLE) &&
        onOpenOriginal != null
    val canSwitchToFeed = !isLoading &&
        kind in setOf(ArticleContentKind.SAVED_WEB_PAGE, ArticleContentKind.EXTERNAL_WEB_PAGE) &&
        onSwitchToFeed != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.article_content_details)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.article_content_currently_showing, label),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(description)
                Spacer(modifier = Modifier.height(12.dp))
                provenance.sourceUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    sourceHost(url)?.let { host ->
                        Text(stringResource(R.string.article_content_detail_host, host))
                    }
                    Text(
                        text = stringResource(R.string.article_content_detail_url, url),
                        style = MaterialTheme.typography.bodySmall
                    )
                } ?: Text(
                    stringResource(R.string.article_content_detail_source_unavailable),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.article_content_detail_delivery,
                        deliveryLabel(provenance.delivery)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                provenance.fetchedAt?.let { fetchedAt ->
                    Text(
                        text = stringResource(
                            R.string.article_content_detail_fetched_at,
                            formatContentTimestamp(fetchedAt, locale)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                } ?: Text(
                    stringResource(R.string.article_content_detail_time_unavailable),
                    style = MaterialTheme.typography.bodySmall
                )
                if (kind == ArticleContentKind.SAVED_WEB_PAGE && provenance.isComplete == false) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.article_content_saved_page_incomplete),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (canRetry) {
                    val retryAction = requireNotNull(onRetry)
                    TextButton(
                        onClick = {
                            onDismiss()
                            retryAction()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.article_content_retry_full))
                    }
                }
                if (canOpenOriginal) {
                    val openOriginalAction = requireNotNull(onOpenOriginal)
                    TextButton(
                        onClick = {
                            onDismiss()
                            openOriginalAction()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.article_content_open_original))
                    }
                }
                if (canSwitchToFeed) {
                    val switchToFeedAction = requireNotNull(onSwitchToFeed)
                    TextButton(
                        onClick = {
                            onDismiss()
                            switchToFeedAction()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.article_show_feed_content))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    )
}

private fun contentSourceLabel(kind: ArticleContentKind): Int = when (kind) {
    ArticleContentKind.FULL_ARTICLE -> R.string.article_content_source_full
    ArticleContentKind.FEED_FALLBACK -> R.string.article_content_source_feed_fallback
    ArticleContentKind.SAVED_WEB_PAGE -> R.string.article_content_source_saved_page
    ArticleContentKind.EXTERNAL_WEB_PAGE -> R.string.article_content_source_external_page
    ArticleContentKind.UNAVAILABLE -> R.string.article_content_source_unavailable
    ArticleContentKind.UNKNOWN -> R.string.article_content_source_unknown
}

private fun contentSourceDescription(kind: ArticleContentKind): Int = when (kind) {
    ArticleContentKind.FULL_ARTICLE -> R.string.article_content_source_full_description
    ArticleContentKind.FEED_FALLBACK -> R.string.article_content_source_feed_fallback_description
    ArticleContentKind.SAVED_WEB_PAGE -> R.string.article_content_source_saved_page_description
    ArticleContentKind.EXTERNAL_WEB_PAGE -> R.string.article_content_source_external_page_description
    ArticleContentKind.UNAVAILABLE -> R.string.article_content_source_unavailable_description
    ArticleContentKind.UNKNOWN -> R.string.article_content_source_unknown_description
}

@Composable
private fun deliveryLabel(delivery: ArticleContentDelivery): String = stringResource(
    when (delivery) {
        ArticleContentDelivery.NETWORK -> R.string.article_content_delivery_network
        ArticleContentDelivery.LOCAL_STORAGE -> R.string.article_content_delivery_local
        ArticleContentDelivery.UNKNOWN -> R.string.article_content_delivery_unknown
    }
)

private fun sourceHost(url: String): String? = runCatching { URI(url).host }
    .getOrNull()
    ?.takeIf { it.isNotBlank() }

private fun formatContentTimestamp(timestamp: Instant, locale: Locale): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(locale)
    .withZone(ZoneId.systemDefault())
    .format(timestamp)
