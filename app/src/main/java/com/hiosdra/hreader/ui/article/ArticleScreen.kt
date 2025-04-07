package com.hiosdra.hreader.ui.article

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.navigation.NavHostController
import com.hiosdra.hreader.navigation.openChromeCustomTab
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    navController: NavHostController,
    articleId: Long,
    viewModel: ArticleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isWebViewMode = remember { mutableStateOf(false) }

    LaunchedEffect(articleId) {
        viewModel.loadArticle(articleId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    uiState.entry?.let { entry ->
                        IconButton(onClick = {
                            openChromeCustomTab(navController.context, entry.url)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, "Open in Chrome")
                        }
                        IconButton(onClick = {
                            isWebViewMode.value = !isWebViewMode.value
                        }) {
                            Icon(
                                Icons.Filled.Warning,
                                if (isWebViewMode.value) "Show Content" else "Show WebView"
                            )
                        }
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

            uiState.entry != null -> {
                if (isWebViewMode.value) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = WebViewClient()
                            }
                        },
                        update = { webView ->
                            webView.loadUrl(uiState.entry!!.url)
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                } else {
                    Text(
                        text = AnnotatedString(
                            text = HtmlCompat.fromHtml(
                                uiState.entry!!.content ?: "No content available",
                                HtmlCompat.FROM_HTML_MODE_LEGACY
                            ).toString()
                        ),
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }
}
