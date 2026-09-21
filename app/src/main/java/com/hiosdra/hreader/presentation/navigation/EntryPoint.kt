package com.hiosdra.hreader.presentation.navigation

sealed interface EntryPoint {
    data object ArticleList : EntryPoint

    data class AddFeed(val url: String?) : EntryPoint
}
