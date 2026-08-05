package com.hiosdra.hreader.ui.article

internal fun readStatusActionLabel(isRead: Boolean): String =
    if (isRead) "Mark as unread" else "Mark as read"
