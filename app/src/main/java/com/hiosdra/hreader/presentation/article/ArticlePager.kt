package com.hiosdra.hreader.presentation.article

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.OfflinePage
import com.hiosdra.hreader.R

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ArticlePager(
    entries: List<Entry>,
    pagerState: PagerState,
    isWebViewMode: Boolean,
    textScale: Float,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    bottomContentPadding: Dp = 0.dp,
    getContentForEntry: (Long) -> String?,
    getLeadImageForEntry: (Long) -> String?,
    getOfflinePageForEntry: (Long) -> OfflinePage?,
    loadedContentIds: Set<Long>,
    loadedReadingPositionIds: Set<Long>,
    readingProgressForEntry: (Long) -> Float?,
    onReadingProgressChanged: (Long, Float) -> Unit,
    onReadingCompleted: (Long) -> Unit,
    localImagePaths: Map<Long, Map<String, String>> = emptyMap(),
    isOnline: Boolean = true,
    aiOverviews: Map<Long, String> = emptyMap(),
    generatingOverviewIds: Set<Long> = emptySet(),
    onAiOverview: ((Long) -> Unit)? = null,
    credibilityEnabled: Boolean = false,
    credibilityReports: Map<Long, CredibilityReport> = emptyMap(),
    analyzingCredibilityIds: Set<Long> = emptySet(),
    onAnalyzeCredibility: ((Long, Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
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
                val offlinePage = getOfflinePageForEntry(entry.id)
                if (isWebViewMode && (isOnline || offlinePage != null)) {
                    val webViewModifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(bottom = bottomContentPadding)
                    if (!isOnline && offlinePage != null) {
                        OfflinePageWebView(
                            page = offlinePage,
                            onLinkClick = { url ->
                                copyTextToClipboard(context, articleLinkLabel, url)
                                Toast.makeText(context, offlineLinkCopiedMessage, Toast.LENGTH_SHORT).show()
                            },
                            modifier = webViewModifier
                        )
                    } else {
                        RemoteArticleWebView(
                            entryId = entry.id,
                            url = entry.url,
                            isOnline = isOnline,
                            modifier = webViewModifier
                        )
                    }
                } else {
                    ArticleContent(
                        entry = entry,
                        mainImageUrl = getLeadImageForEntry(entry.id),
                        textScale = textScale,
                        modifier = Modifier
                            .padding(paddingValues)
                            .padding(bottom = bottomContentPadding),
                        articleContent = getContentForEntry(entry.id) ?: stringResource(R.string.article_no_content),
                        contentLoaded = entry.id in loadedContentIds,
                        readingPositionLoaded = entry.id in loadedReadingPositionIds,
                        savedReadingProgress = readingProgressForEntry(entry.id),
                        onReadingProgressChanged = onReadingProgressChanged,
                        onReadingCompleted = onReadingCompleted,
                        localImagePaths = localImagePaths[entry.id].orEmpty(),
                        isOnline = isOnline,
                        aiOverview = aiOverviews[entry.id],
                        isGeneratingOverview = generatingOverviewIds.contains(entry.id),
                        onAiOverview = onAiOverview,
                        credibilityEnabled = credibilityEnabled,
                        credibilityReport = credibilityReports[entry.id],
                        isAnalyzingCredibility = analyzingCredibilityIds.contains(entry.id),
                        onAnalyzeCredibility = onAnalyzeCredibility
                    )
                }
            }
        }
    }
}
