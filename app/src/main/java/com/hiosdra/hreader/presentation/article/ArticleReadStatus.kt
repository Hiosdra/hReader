package com.hiosdra.hreader.presentation.article

import androidx.annotation.StringRes
import com.hiosdra.hreader.R

@StringRes
internal fun readStatusActionLabel(isRead: Boolean): Int =
    if (isRead) R.string.article_mark_as_unread else R.string.article_mark_as_read
