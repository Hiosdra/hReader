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
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.OfflinePage
import com.hiosdra.hreader.core.application.ai.AiProvider
import com.hiosdra.hreader.core.application.port.out.ArticleImageDownloader
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticleImageSharer
import com.hiosdra.hreader.core.application.port.out.ArticleTtsState
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import coil3.ImageLoader as CoilImageLoader

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ArticlePager(
    entries: List<Entry>,
    pagerState: PagerState,
    isWebViewMode: Boolean,
    textScale: Float,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    getContentForEntry: (Long) -> String?,
    getLeadImageForEntry: (Long) -> String?,
    getOfflinePageForEntry: (Long) -> OfflinePage?,
    loadedContentIds: Set<Long>,
    loadedReadingPositionIds: Set<Long>,
    readingProgressForEntry: (Long) -> Float?,
    onReadingProgressChanged: (Long, Float) -> Unit,
    onReadingCompleted: (Long) -> Unit,
    readerPreferences: ReaderPreferences,
    articleImageLoader: ArticleImageLoader,
    coilImageLoader: CoilImageLoader,
    remoteResourcePolicy: RemoteResourcePolicy,
    articleImageSharer: ArticleImageSharer,
    articleImageDownloader: ArticleImageDownloader,
    localImagePaths: Map<Long, Map<String, String>> = emptyMap(),
    isOnline: Boolean = true,
    aiOverviews: Map<Long, String> = emptyMap(),
    aiProvider: AiProvider = AiProvider.OPENROUTER,
    generatingOverviewIds: Set<Long> = emptySet(),
    aiOverviewProgress: Map<Long, ArticleAiProgress> = emptyMap(),
    onAiOverview: ((Long) -> Unit)? = null,
    credibilityEnabled: Boolean = false,
    credibilityReports: Map<Long, CredibilityReport> = emptyMap(),
    analyzingCredibilityIds: Set<Long> = emptySet(),
    onAnalyzeCredibility: ((Long, Boolean) -> Unit)? = null,
    ttsState: ArticleTtsState = ArticleTtsState(),
    onSpeechPosition: ((Long, Int) -> Unit)? = null,
    onReadFromSelection: ((Long, Int) -> Unit)? = null
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
                            remoteResourcePolicy = remoteResourcePolicy,
                            modifier = webViewModifier
                        )
                    }
                } else {
                    val activeTtsState = ttsState.takeIf { it.articleId == entry.id }
                    ArticleContent(
                        entry = entry,
                        mainImageUrl = getLeadImageForEntry(entry.id),
                        textScale = textScale,
                        modifier = Modifier
                            .padding(paddingValues),
                        articleContent = getContentForEntry(entry.id) ?: stringResource(R.string.article_no_content),
                        contentLoaded = entry.id in loadedContentIds,
                        readingPositionLoaded = entry.id in loadedReadingPositionIds,
                        savedReadingProgress = readingProgressForEntry(entry.id),
                        onReadingProgressChanged = onReadingProgressChanged,
                        onReadingCompleted = onReadingCompleted,
                        readerPreferences = readerPreferences,
                        articleImageLoader = articleImageLoader,
                        coilImageLoader = coilImageLoader,
                        remoteResourcePolicy = remoteResourcePolicy,
                        imageSharer = articleImageSharer,
                        imageDownloader = articleImageDownloader,
                        localImagePaths = localImagePaths[entry.id].orEmpty(),
                        isOnline = isOnline,
                        aiOverview = aiOverviews[entry.id],
                        aiProvider = aiProvider,
                        isGeneratingOverview = generatingOverviewIds.contains(entry.id),
                        aiOverviewProgress = aiOverviewProgress[entry.id],
                        onAiOverview = onAiOverview,
                        credibilityEnabled = credibilityEnabled,
                        credibilityReport = credibilityReports[entry.id],
                        isAnalyzingCredibility = analyzingCredibilityIds.contains(entry.id),
                        onAnalyzeCredibility = onAnalyzeCredibility,
                        speechInteractionEnabled = entry.id in loadedContentIds,
                        speechRange = activeTtsState?.currentRange,
                        onSpeechPosition = onSpeechPosition?.let { handler ->
                            { offset -> handler(entry.id, offset) }
                        },
                        onReadFromSelection = onReadFromSelection?.let { handler ->
                            { offset -> handler(entry.id, offset) }
                        }
                    )
                }
            }
        }
    }
}
