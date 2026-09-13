package com.hiosdra.hreader.presentation.article

internal fun shouldAnimateArticleReadState(
    requestedReadState: Boolean?,
    currentReadState: Boolean
): Boolean = requestedReadState == currentReadState
