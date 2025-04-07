package com.hiosdra.hreader.ui.article

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.hiosdra.hreader.navigation.openChromeCustomTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(navController: NavHostController, articleId: Long) {
    val articleUrl = "https://example.com"
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Article")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        openChromeCustomTab(navController.context, articleUrl)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Open in Chrome")
                    }
                }
            )
        }
    ) { paddingValues ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    //settings.javaScriptEnabled = true
                }
            },
            update = { webView ->
                webView.loadUrl(articleUrl)
            },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Preview
@Composable
fun ArticleScreenPreview() {
    ArticleScreen(navController = rememberNavController(), articleId = 1)
}
