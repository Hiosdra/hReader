package com.hiosdra.hreader.presentation.article

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.OfflinePage
import com.hiosdra.hreader.R
import coil3.ImageLoader as CoilImageLoader

internal data class ArticlePagerBindings(
    val state: ArticleUiState,
    val readerPreferences: ReaderPreferences,
    val articleImageLoader: ArticleImageLoader,
    val coilImageLoader: CoilImageLoader,
    val remoteResourcePolicy: RemoteResourcePolicy,
    val onReadingProgressChanged: (Long, Float) -> Unit,
    val onReadingCompleted: (Long) -> Unit,
    val onRetryContent: (Long) -> Unit,
    val onEffect: (ArticleRouteEffect) -> Unit = {},
    val onAiOverview: ((Long) -> Unit)? = null,
    val onAnalyzeCredibility: ((Long, Boolean) -> Unit)? = null,
    val defaultPaywallBypassMethod: PaywallBypassMethod = PaywallBypassMethod.SMRY_AI,
    val canUsePaywallBypass: (String) -> Boolean = { false },
    val onOpenInChrome: (String) -> Unit = {},
    val onBypassPaywall: (String, PaywallBypassMethod) -> Unit = { _, _ -> }
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ArticlePager(
    entries: List<Entry>,
    pagerState: PagerState,
    isWebViewMode: Boolean,
    textScale: Float,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    bottomContentPadding: Dp = 0.dp,
    bindings: ArticlePagerBindings
) {
    val state = bindings.state
    val articleLinkLabel = stringResource(R.string.article_link)
    val offlineLinkCopiedMessage = stringResource(R.string.article_offline_link_copied)
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
                snapPositionalThreshold = if (isWebViewMode) {
                    WEB_PAGER_SNAP_POSITIONAL_THRESHOLD
                } else {
                    FEED_PAGER_SNAP_POSITIONAL_THRESHOLD
                }
            )
        ) { page ->
            val entry = entries.getOrNull(page) ?: return@HorizontalPager
            key(entry.id) {
                val offlinePage = state.content.offlinePages[entry.id]
                val contentState = state.content.contentLoadState(entry.id)
                val contentProvenance = state.displayedProvenance(entry, isWebViewMode)
                if (isWebViewMode && (state.content.isOnline || offlinePage != null)) {
                    val webViewModifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(bottom = bottomContentPadding)
                    if (!state.content.isOnline && offlinePage != null) {
                        OfflinePageWebView(
                            page = offlinePage,
                            onLinkClick = { url ->
                                bindings.onEffect(
                                    ArticleRouteEffect.CopyText(
                                        label = articleLinkLabel,
                                        text = url,
                                        followUpMessage = offlineLinkCopiedMessage
                                    )
                                )
                            },
                            readingPositionLoaded = entry.id in state.content.readingProgress.loadedIds,
                            savedReadingProgress = state.content.readingProgress.positions[entry.id],
                            onReadingProgressChanged = bindings.onReadingProgressChanged,
                            onReadingCompleted = bindings.onReadingCompleted,
                            modifier = webViewModifier
                        )
                    } else {
                        RemoteArticleWebView(
                            entryId = entry.id,
                            url = entry.url,
                            isOnline = state.content.isOnline,
                            remoteResourcePolicy = bindings.remoteResourcePolicy,
                            readingPositionLoaded = entry.id in state.content.readingProgress.loadedIds,
                            savedReadingProgress = state.content.readingProgress.positions[entry.id],
                            onReadingProgressChanged = bindings.onReadingProgressChanged,
                            onReadingCompleted = bindings.onReadingCompleted,
                            modifier = webViewModifier
                        )
                    }
                } else {
                    ArticleContent(
                        entry = entry,
                        textScale = textScale,
                        modifier = Modifier
                            .padding(paddingValues)
                            .padding(bottom = bottomContentPadding),
                        bindings = bindings
                    )
                }
            }
        }
    }
}
