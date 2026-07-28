package com.hiosdra.hreader.ui.article

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.model.CredibilityConfidence
import com.hiosdra.hreader.data.model.CredibilityLevel
import com.hiosdra.hreader.data.model.CredibilityReport
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.isRead
import com.hiosdra.hreader.data.paywall.PaywallBypassService
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.navigation.openChromeCustomTab
import com.hiosdra.hreader.ui.components.OfflineAwareImage
import com.hiosdra.hreader.ui.theme.LocalExtendedColors
import com.hiosdra.hreader.util.cleanUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArticleScreen(
    navController: NavHostController,
    articleIds: List<Long>,
    initialIndex: Int = 0,
    viewModel: ArticleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = 0) { uiState.entries.size }
    var isWebViewMode by remember { mutableStateOf(false) }

    val preferencesManager: PreferencesManager = koinInject()
    val paywallBypassService: PaywallBypassService = koinInject()

    LaunchedEffect(articleIds) {
        viewModel.loadArticlesByIds(articleIds)
    }

    LaunchedEffect(initialIndex, uiState.entries.size) {
        if (uiState.entries.isNotEmpty() && initialIndex in uiState.entries.indices) {
            pagerState.scrollToPage(initialIndex)
        }
    }

    // Sync pager with state
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != uiState.currentIndex && pagerState.currentPage in uiState.entries.indices) {
            viewModel.setCurrentIndex(pagerState.currentPage)
        }
    }
    LaunchedEffect(uiState.currentIndex) {
        if (uiState.currentIndex in uiState.entries.indices && pagerState.currentPage != uiState.currentIndex) {
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
                Text(text = uiState.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            uiState.entries.isNotEmpty() -> {
                ArticlePager(
                    entries = uiState.entries,
                    pagerState = pagerState,
                    isWebViewMode = isWebViewMode,
                    paddingValues = paddingValues,
                    onReadStatusChange = { index, status -> viewModel.updateReadStatus(index, status) },
                    getContentForEntry = { entryId -> viewModel.getContentForEntry(entryId) },
                    aiOverviews = uiState.aiOverviews,
                    generatingOverviewIds = uiState.generatingOverviewIds,
                    onAiOverview = { entryId -> viewModel.generateAiOverview(entryId) },
                    credibilityEnabled = uiState.credibilityEnabled,
                    credibilityReports = uiState.credibilityReports,
                    analyzingCredibilityIds = uiState.analyzingCredibilityIds,
                    onAnalyzeCredibility = { entryId, force -> viewModel.analyzeCredibility(entryId, force) }
                )
            }
        }

        // AI Overview Error Dialog
        uiState.overviewError?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.clearOverviewError() },
                title = { Text("AI Overview Error") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearOverviewError() }) {
                        Text("OK")
                    }
                }
            )
        }

        // Credibility Score Error Dialog
        uiState.scoreError?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.clearScoreError() },
                title = { Text("Credibility Analysis Error") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearScoreError() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ArticlePager(
    entries: List<Entry>,
    pagerState: PagerState,
    isWebViewMode: Boolean,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    onReadStatusChange: ((Int, Boolean) -> Unit)? = null,
    getContentForEntry: (Long) -> String?,
    aiOverviews: Map<Long, String> = emptyMap(),
    generatingOverviewIds: Set<Long> = emptySet(),
    onAiOverview: ((Long) -> Unit)? = null,
    credibilityEnabled: Boolean = false,
    credibilityReports: Map<Long, CredibilityReport> = emptyMap(),
    analyzingCredibilityIds: Set<Long> = emptySet(),
    onAnalyzeCredibility: ((Long, Boolean) -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val entry = entries[page]
            val mainImageUrl = entry.enclosures.firstOrNull { it.isImage }?.url
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
                    articleContent = getContentForEntry(entry.id) ?: "No content available",
                    aiOverview = aiOverviews[entry.id],
                    isGeneratingOverview = generatingOverviewIds.contains(entry.id),
                    onAiOverview = onAiOverview,
                    credibilityEnabled = credibilityEnabled,
                    credibilityReport = credibilityReports[entry.id],
                    isAnalyzingCredibility = analyzingCredibilityIds.contains(entry.id),
                    onAnalyzeCredibility = onAnalyzeCredibility
                )
                LaunchedEffect(entry.id) {
                    if (!entry.isRead) {
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
    articleContent: String,
    aiOverview: String? = null,
    isGeneratingOverview: Boolean = false,
    onAiOverview: ((Long) -> Unit)? = null,
    credibilityEnabled: Boolean = false,
    credibilityReport: CredibilityReport? = null,
    isAnalyzingCredibility: Boolean = false,
    onAnalyzeCredibility: ((Long, Boolean) -> Unit)? = null
) {
    val dateText = remember(entry.publishedAt) { formatTimestamp(entry.publishedAt) }
    val progressState = remember { mutableFloatStateOf(0f) }
    var zoomImageUrl by remember { mutableStateOf<String?>(null) }
    var imageActionsUrl by remember { mutableStateOf<String?>(null) }
    var imageShareUrl by remember { mutableStateOf<String?>(null) }
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
                            checked = entry.isRead,
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
                        readingTimeMinutes = entry.readingTime,
                        entryId = entry.id,
                        aiOverview = aiOverview,
                        isGeneratingOverview = isGeneratingOverview,
                        onAiOverviewClick = if (onAiOverview != null) { { onAiOverview(entry.id) } } else null,
                        credibilityEnabled = credibilityEnabled,
                        credibilityReport = credibilityReport,
                        isAnalyzingCredibility = isAnalyzingCredibility,
                        onAnalyzeCredibility = if (onAnalyzeCredibility != null) {
                            { force -> onAnalyzeCredibility(entry.id, force) }
                        } else null
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()

                    if (mainImageUrl != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        OfflineAwareImage(
                            entryId = entry.id,
                            imageUrl = mainImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { zoomImageUrl = mainImageUrl },
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    ArticleWebView(
                        articleContent = articleContent,
                        baseUrl = entry.url,
                        modifier = Modifier.fillMaxWidth(),
                        onLinkClick = { url -> openChromeCustomTab(context, url) },
                        onScrollProgress = { p -> progressState.floatValue = p },
                        onImageLongClick = { url -> imageActionsUrl = url }
                    )
                }
            }
        }
    }
    val actionsUrl = imageActionsUrl
    if (actionsUrl != null) {
        ImageActionsDialog(
            imageUrl = actionsUrl,
            onDismiss = { imageActionsUrl = null },
            onView = {
                zoomImageUrl = actionsUrl
                imageActionsUrl = null
            },
            onCopy = {
                copyTextToClipboard(context, "Image URL", actionsUrl)
                imageActionsUrl = null
            },
            onDownload = {
                enqueueImageDownload(context, actionsUrl)
                imageActionsUrl = null
            },
            onShare = {
                imageShareUrl = actionsUrl
                imageActionsUrl = null
            }
        )
    }
    val shareTarget = imageShareUrl
    if (shareTarget != null) {
        LaunchedEffect(shareTarget) {
            Toast.makeText(context, "Preparing image...", Toast.LENGTH_SHORT).show()
            val shared = shareImageFile(context, entry.title, shareTarget)
            if (!shared) Toast.makeText(context, "Image share failed", Toast.LENGTH_SHORT).show()
            imageShareUrl = null
        }
    }
    val zoomUrl = zoomImageUrl
    if (zoomUrl != null) {
        Dialog(onDismissRequest = { zoomImageUrl = null }) {
            ZoomableImage(url = zoomUrl) { zoomImageUrl = null }
        }
    }
}

@Composable
private fun MetaChips(
    author: String?,
    dateText: String,
    readingTimeMinutes: Int?,
    entryId: Long? = null,
    aiOverview: String? = null,
    isGeneratingOverview: Boolean = false,
    onAiOverviewClick: (() -> Unit)? = null,
    credibilityEnabled: Boolean = false,
    credibilityReport: CredibilityReport? = null,
    isAnalyzingCredibility: Boolean = false,
    onAnalyzeCredibility: ((Boolean) -> Unit)? = null
) {
    val isAiExpanded = rememberSaveable { mutableStateOf(false) }
    val isCredibilityExpanded = rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val items = buildList {
                if (!author.isNullOrBlank()) add(author)
                add(dateText)
                if (readingTimeMinutes != null && readingTimeMinutes > 0) add("${readingTimeMinutes} min read")
            }
            items.forEach { text ->
                androidx.compose.material3.AssistChip(
                    onClick = {},
                    label = { Text(text) }
                )
            }
            if (entryId != null && onAiOverviewClick != null) {
                androidx.compose.material3.AssistChip(
                    onClick = {
                        if (aiOverview == null) {
                            isAiExpanded.value = true
                            onAiOverviewClick()
                        } else {
                            isAiExpanded.value = !isAiExpanded.value
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "AI Overview",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val chipText = when {
                                aiOverview == null && isGeneratingOverview -> "Generating..."
                                aiOverview == null -> "AI Overview"
                                isAiExpanded.value -> "Hide Overview"
                                else -> "Show Overview"
                            }
                            Text(chipText)
                        }
                    },
                    enabled = !(aiOverview == null && isGeneratingOverview),
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = if (aiOverview != null || isGeneratingOverview)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            if (entryId != null && credibilityEnabled && onAnalyzeCredibility != null) {
                CredibilityChip(
                    report = credibilityReport,
                    isAnalyzing = isAnalyzingCredibility,
                    onClick = {
                        if (credibilityReport == null) {
                            isCredibilityExpanded.value = true
                            onAnalyzeCredibility(false)
                        } else {
                            isCredibilityExpanded.value = !isCredibilityExpanded.value
                        }
                    }
                )
            }
        }

        // AI Overview Content with Animation
        if (entryId != null && (aiOverview != null || isGeneratingOverview) && isAiExpanded.value) {
            AnimatedVisibility(
                visible = isAiExpanded.value,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "AI Overview",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Overview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isGeneratingOverview) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Generating overview...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = aiOverview ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (entryId != null && credibilityEnabled && isCredibilityExpanded.value &&
            (credibilityReport != null || isAnalyzingCredibility)
        ) {
            AnimatedVisibility(
                visible = isCredibilityExpanded.value,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                CredibilityCard(
                    report = credibilityReport,
                    isAnalyzing = isAnalyzingCredibility,
                    onReanalyze = { onAnalyzeCredibility?.invoke(true) }
                )
            }
        }
    }
}

@Composable
private fun CredibilityChip(
    report: CredibilityReport?,
    isAnalyzing: Boolean,
    onClick: () -> Unit
) {
    val accent = credibilityAccent(report)
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accent ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                val chipText = when {
                    isAnalyzing -> "Analyzing..."
                    report == null -> "Check credibility"
                    else -> credibilityLevelLabel(report.level)
                }
                Text(chipText)
            }
        },
        enabled = !isAnalyzing,
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = when {
                accent != null -> accent.copy(alpha = 0.18f)
                isAnalyzing -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    )
}

@Composable
private fun CredibilityCard(
    report: CredibilityReport?,
    isAnalyzing: Boolean,
    onReanalyze: () -> Unit
) {
    val accent = credibilityAccent(report) ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (report == null) "Credibility" else credibilityLevelLabel(report.level),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isAnalyzing || report == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Analyzing the article...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            if (report.summary.isNotBlank()) {
                Text(
                    text = report.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (report.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                CredibilityBulletList(
                    title = "What the model saw",
                    items = report.reasons,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (report.redFlags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                CredibilityBulletList(
                    title = "Red flags",
                    items = report.redFlags,
                    color = LocalExtendedColors.current.credibilityLow
                )
            }

            if (report.factors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                report.factors.forEach { factor ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = factor.name.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.4f)
                        )
                        LinearProgressIndicator(
                            progress = { factor.score },
                            color = credibilityColor(CredibilityLevel.fromScore(factor.score)),
                            modifier = Modifier
                                .weight(0.6f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = credibilityDisclaimer(report),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(
                onClick = onReanalyze,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Re-analyze")
            }
        }
    }
}

@Composable
private fun CredibilityBulletList(title: String, items: List<String>, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    items.forEach { item ->
        Row(modifier = Modifier.padding(vertical = 1.dp)) {
            Text(text = "• ", style = MaterialTheme.typography.bodySmall, color = color)
            Text(text = item, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

private fun credibilityLevelLabel(level: CredibilityLevel): String = when (level) {
    CredibilityLevel.HIGH -> "Credibility: strong signals"
    CredibilityLevel.MIXED -> "Credibility: mixed signals"
    CredibilityLevel.LOW -> "Credibility: weak signals"
}

@Composable
private fun credibilityColor(level: CredibilityLevel): Color {
    val extended = LocalExtendedColors.current
    return when (level) {
        CredibilityLevel.HIGH -> extended.credibilityHigh
        CredibilityLevel.MIXED -> extended.credibilityMixed
        CredibilityLevel.LOW -> extended.credibilityLow
    }
}

@Composable
private fun credibilityAccent(report: CredibilityReport?): Color? =
    report?.let { credibilityColor(it.level) }

private fun credibilityDisclaimer(report: CredibilityReport): String = buildString {
    append("AI estimate from the article text only — not a fact check. ")
    append(
        when (report.confidence) {
            CredibilityConfidence.HIGH -> "Model confidence: high. "
            CredibilityConfidence.MEDIUM -> "Model confidence: medium. "
            CredibilityConfidence.LOW -> "Model confidence: low. "
        }
    )
    if (report.contentTruncated) append("Long article, only the first part was analyzed. ")
    append("Analyzed ${formatTimestamp(report.analyzedAt)} with ${report.modelId}.")
}

private fun formatTimestamp(instant: Instant): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(instant)

@Composable
private fun ImageActionsDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(imageUrl, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        confirmButton = {
            Column {
                Button(onClick = onView, modifier = Modifier.fillMaxWidth()) { Text("View") }
                Spacer(Modifier.height(4.dp))
                Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text("Copy URL") }
                Spacer(Modifier.height(4.dp))
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Download") }
                Spacer(Modifier.height(4.dp))
                Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Share image") }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        },
        dismissButton = {}
    )
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun enqueueImageDownload(context: Context, url: String) {
    runCatching {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(url.toUri())
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, url.toUri().lastPathSegment ?: "image")
        dm.enqueue(request)
        Toast.makeText(context, "Downloading", Toast.LENGTH_SHORT).show()
    }
}

private val imageShareClient = OkHttpClient()

// Replace old shareImageUrl with new implementation
private suspend fun shareImageFile(context: Context, title: String?, url: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder().url(url).build()
        val resp = imageShareClient.newCall(request).execute()
        try {
            if (!resp.isSuccessful) return@withContext false
            val body = resp.body
            val bytes = body.bytes()
            val contentType = body.contentType()?.toString() ?: "image/jpeg"
            var ext = when {
                contentType.contains("png") -> ".png"
                contentType.contains("webp") -> ".webp"
                contentType.contains("gif") -> ".gif"
                contentType.contains("svg") -> ".svg"
                contentType.contains("jpeg") || contentType.contains("jpg") -> ".jpg"
                else -> ".img"
            }
            val lastSeg = url.toUri().lastPathSegment
            if (lastSeg != null && lastSeg.contains('.')) ext = "" // already has extension
            val safeNameBase = (lastSeg ?: "image_${System.currentTimeMillis()}").take(80)
            val fileName = if (ext.isEmpty()) safeNameBase else safeNameBase + ext
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { it.write(bytes) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", outFile)
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = contentType
                    putExtra(Intent.EXTRA_SUBJECT, title ?: "Image")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, null))
            }
            true
        } finally {
            resp.close()
        }
    } catch (_: Exception) {
        false
    }
}
