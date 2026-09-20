package com.hiosdra.hreader.presentation.article

import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.isRead

internal fun shouldAutomaticallyMarkRead(entry: Entry): Boolean =
    !entry.isRead && entry.feed.autoMarkRead
