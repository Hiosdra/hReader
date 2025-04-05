package com.hiosdra.hreader.ui.article

import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@Composable
fun ArticleScreen(navController: NavHostController, articleId: Long) {
    TextField("Article ID: $articleId", onValueChange = {})
}
