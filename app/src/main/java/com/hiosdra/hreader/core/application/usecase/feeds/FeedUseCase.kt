package com.hiosdra.hreader.core.application.usecase.feeds

import com.hiosdra.hreader.core.application.port.out.FeedStore
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import kotlinx.coroutines.flow.StateFlow

class FeedUseCase(
    feeds: FeedStore,
    network: NetworkStatus
) : FeedStore by feeds {
    val isOnline: StateFlow<Boolean> = network.isOnline
}
