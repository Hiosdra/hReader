package com.hiosdra.hreader.ui.article

import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.paywall.PaywallBypassService
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.navigation.openChromeCustomTab
import com.hiosdra.hreader.util.cleanUrl
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPagerApi::class)
@Composable
fun ArticleScreen(
    navController: NavHostController,
    articleIds: List<Long>,
    initialIndex: Int = 0,
    viewModel: ArticleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = initialIndex)
    var isWebViewMode by remember { mutableStateOf(false) }

    val preferencesManager: PreferencesManager = koinInject()
    val paywallBypassService: PaywallBypassService = koinInject()

    LaunchedEffect(articleIds) {
        viewModel.loadArticlesByIds(articleIds)
    }

    LaunchedEffect(initialIndex) {
        pagerState.scrollToPage(initialIndex)
    }

    // Sync pager with state
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != uiState.currentIndex) {
            viewModel.setCurrentIndex(pagerState.currentPage)
        }
    }
    LaunchedEffect(uiState.currentIndex) {
        if (pagerState.currentPage != uiState.currentIndex) {
            pagerState.scrollToPage(uiState.currentIndex)
        }
    }

    Scaffold(
        topBar = {
            val entry = uiState.entries.getOrNull(uiState.currentIndex)
            ArticleTopBar(
                entryUrl = entry?.url,
                feedTitle = entry?.feed?.title,
                isWebViewMode = isWebViewMode,
                onBack = { navController.popBackStack() },
                onOpenInChrome = { if (entry != null) openChromeCustomTab(navController.context, cleanUrl(entry.url)) },
                onToggleWebView = { isWebViewMode = !isWebViewMode },
                onBypassPaywall = {
                    if (entry != null && entry.url.isNotBlank()) {
                        val bypassMethod = preferencesManager.getPaywallBypassMethod()
                        val bypassUrl = paywallBypassService.getBypassUrl(entry.url, bypassMethod)
                        openChromeCustomTab(navController.context, bypassUrl)
                    }
                },
                onShare = {
                    if (entry != null) {
                        val ctx = navController.context
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, entry.title)
                            putExtra(Intent.EXTRA_TEXT, "${entry.title}\n${cleanUrl(entry.url)}")
                        }
                        ctx.startActivity(Intent.createChooser(sendIntent, null))
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
            uiState.entries.isNotEmpty() -> {
                ArticlePager(
                    entries = uiState.entries,
                    pagerState = pagerState,
                    isWebViewMode = isWebViewMode,
                    paddingValues = paddingValues,
                    onReadStatusChange = { index, status -> viewModel.updateReadStatus(index, status) },
                    getContentForEntry = { entryId -> viewModel.getContentForEntry(entryId) }
                )
            }
        }
    }
}

@Composable
private fun ArticlePager(
    entries: List<Entry>,
    pagerState: com.google.accompanist.pager.PagerState,
    isWebViewMode: Boolean,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    onReadStatusChange: ((Int, Boolean) -> Unit)? = null,
    getContentForEntry: (Long) -> String?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            count = entries.size,
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val entry = entries[page]
            val mainImageUrl = entry.enclosures?.firstOrNull { it.mimeType?.startsWith("image/") == true }?.url
                ?: Regex("<img[^>]+src=\"([^\"]+)\"").find(entry.content ?: "")?.groupValues?.getOrNull(1)
            if (isWebViewMode) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                        }
                    },
                    update = { webView ->
                        webView.loadUrl(entry.url)
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                ArticleContent(
                    entry = entry,
                    mainImageUrl = mainImageUrl,
                    modifier = Modifier.padding(paddingValues),
                    onReadStatusChange = { status -> onReadStatusChange?.invoke(page, status) },
                    articleContent = getContentForEntry(entry.id) ?: "No content available"
                )
                LaunchedEffect(entry.id) {
                    if (entry.status != "read") {
                        onReadStatusChange?.invoke(page, true)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleTopBar(
    entryUrl: String?,
    feedTitle: String?,
    isWebViewMode: Boolean,
    onBack: () -> Unit,
    onOpenInChrome: () -> Unit,
    onToggleWebView: () -> Unit,
    onBypassPaywall: () -> Unit,
    onShare: () -> Unit
) {
    val paywallBypassService: PaywallBypassService = koinInject()

    TopAppBar(
        title = { Text(feedTitle ?: "hReader", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            if (entryUrl != null) {
                if (!paywallBypassService.isPaywallBypassUrl(entryUrl)) {
                    IconButton(onClick = onBypassPaywall) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Bypass Paywall",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(onClick = onOpenInChrome) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chrome_logo),
                        contentDescription = "Open in Chrome",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onToggleWebView) {
                    Icon(
                        painter = painterResource(id = if (isWebViewMode) R.drawable.baseline_web_asset_off_24 else R.drawable.baseline_web_asset_24),
                        contentDescription = if (isWebViewMode) "Show Content" else "Show WebView",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun ArticleContent(
    entry: Entry,
    mainImageUrl: String?,
    modifier: Modifier = Modifier,
    onReadStatusChange: ((Boolean) -> Unit)? = null,
    articleContent: String
) {
    val dateText = remember(entry.publishedAt) { formatPublishedDate(entry.publishedAt) }
    val progressState = remember { mutableFloatStateOf(0f) }
    var showImage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxSize(),
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 900.dp)
                        .padding(top = 12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = entry.status == "read",
                            onCheckedChange = { checked -> onReadStatusChange?.invoke(checked) }
                        )
                    }
                    if (progressState.floatValue in 0.02f..0.98f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progressState.floatValue },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    MetaChips(
                        author = entry.author,
                        dateText = dateText,
                        readingTimeMinutes = entry.readingTime
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Divider()
                    if (mainImageUrl != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Image(
                            painter = rememberAsyncImagePainter(mainImageUrl),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showImage = true },
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    ArticleWebView(
                        articleContent = articleContent,
                        baseUrl = entry.url,
                        modifier = Modifier.fillMaxWidth(),
                        onLinkClick = { url -> openChromeCustomTab(context, url) },
                        onScrollProgress = { p -> progressState.floatValue = p }
                    )
                }
            }
        }
    }
    if (showImage && mainImageUrl != null) {
        Dialog(onDismissRequest = { showImage = false }) {
            ZoomableImage(url = mainImageUrl) { showImage = false }
        }
    }
}

@Composable
private fun MetaChips(author: String?, dateText: String, readingTimeMinutes: Int?) {
    Row(modifier = Modifier.fillMaxWidth()) {
        val items = buildList {
            if (!author.isNullOrBlank()) add(author)
            add(dateText)
            if (readingTimeMinutes != null && readingTimeMinutes > 0) add("${readingTimeMinutes} min read")
        }
        items.forEachIndexed { index, text ->
            androidx.compose.material3.AssistChip(
                onClick = {},
                label = { Text(text) }
            )
            if (index != items.lastIndex) Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

private fun formatPublishedDate(raw: String): String {
    return runCatching {
        val instant = Instant.parse(raw)
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(instant)
    }.getOrElse { raw }
}
