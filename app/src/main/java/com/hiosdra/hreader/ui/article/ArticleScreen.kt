package com.hiosdra.hreader.ui.article

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.navigation.openChromeCustomTab
import org.koin.androidx.compose.koinViewModel

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
    val isWebViewMode = remember { mutableStateOf(false) }

    LaunchedEffect(articleIds, initialIndex) {
        viewModel.loadArticles(articleIds, initialIndex)
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
                isWebViewMode = isWebViewMode.value,
                onBack = { navController.popBackStack() },
                onOpenInChrome = { if (entry != null) openChromeCustomTab(navController.context, entry.url) },
                onToggleWebView = { isWebViewMode.value = !isWebViewMode.value }
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
                    isWebViewMode = isWebViewMode.value,
                    paddingValues = paddingValues
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
    paddingValues: androidx.compose.foundation.layout.PaddingValues
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
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleTopBar(
    entryUrl: String?,
    isWebViewMode: Boolean,
    onBack: () -> Unit,
    onOpenInChrome: () -> Unit,
    onToggleWebView: () -> Unit
) {
    TopAppBar(
        title = { Text("hReader", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            if (entryUrl != null) {
                IconButton(onClick = onOpenInChrome) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, "Open in Chrome")
                }
                IconButton(onClick = onToggleWebView) {
                    Icon(
                        Icons.Filled.Warning,
                        if (isWebViewMode) "Show Content" else "Show WebView"
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = entry.title ?: "No title",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = listOfNotNull(entry.author, entry.publishedAt).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (mainImageUrl != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Image(
                    painter = rememberAsyncImagePainter(mainImageUrl),
                    contentDescription = "Main article image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = AnnotatedString(
                    text = HtmlCompat.fromHtml(
                        entry.content ?: "No content available",
                        HtmlCompat.FROM_HTML_MODE_LEGACY
                    ).toString()
                ),
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = TextUnit.Unspecified
            )
        }
    }
}
