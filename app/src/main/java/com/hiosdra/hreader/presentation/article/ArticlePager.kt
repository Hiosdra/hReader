package com.hiosdra.hreader.presentation.article

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
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
                    if (!isOnline && offlinePage != null) {
                        OfflinePageWebView(
                            page = offlinePage,
                            onLinkClick = { url ->
                                copyTextToClipboard(context, articleLinkLabel, url)
                                Toast.makeText(context, offlineLinkCopiedMessage, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(bottom = bottomContentPadding)
                        )
                    } else {
                        val loadedUrl = remember { mutableStateOf<String?>(null) }
                        val loadedWebView = remember { mutableStateOf<ReaderWebView?>(null) }
                        var savedScrollY by rememberSaveable(entry.id) { mutableIntStateOf(0) }
                        var renderProcessError by remember(entry.id, entry.url) { mutableStateOf(false) }
                        var renderAttempt by remember(entry.id, entry.url) { mutableIntStateOf(0) }
                        val webViewModifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(bottom = bottomContentPadding)
                        if (renderProcessError) {
                            ReaderWebViewError(
                                modifier = webViewModifier,
                                onRetry = {
                                    renderProcessError = false
                                    renderAttempt += 1
                                }
                            )
                        } else {
                            key(renderAttempt) {
                                AndroidView(
                                    factory = { context ->
                                        ReaderWebView(context).apply {
                                            protectVerticalScrollFromPager = true
                                            settings.hardenArticleContent()
                                            webViewClient = object : WebViewClient() {
                                                override fun onRenderProcessGone(
                                                    view: WebView,
                                                    detail: RenderProcessGoneDetail
                                                ): Boolean {
                                                    loadedWebView.value = null
                                                    (view as? ReaderWebView)?.destroyAfterRenderProcessGone()
                                                    renderProcessError = true
                                                    return true
                                                }

                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    super.onPageFinished(view, url)
                                                    val readerView = view as? ReaderWebView ?: return
                                                    readerView.postIfActive {
                                                        readerView.scrollTo(0, savedScrollY)
                                                    }
                                                }
                                            }
                                            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                                                savedScrollY = scrollY
                                            }
                                        }
                                    },
                                    update = { webView ->
                                        webView.settings.blockNetworkLoads = !isOnline
                                        if (loadedWebView.value !== webView || loadedUrl.value != entry.url) {
                                            loadedWebView.value = webView
                                            loadedUrl.value = entry.url
                                            webView.loadUrl(entry.url)
                                            webView.postIfActive { webView.scrollTo(0, savedScrollY) }
                                        }
                                    },
                                    onRelease = { webView -> webView.releaseResources() },
                                    modifier = webViewModifier
                                )
                            }
                        }
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
