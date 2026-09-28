package com.hiosdra.hreader.core.application.usecase.settings

import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import kotlinx.coroutines.flow.StateFlow

class NetworkStatusUseCase(networkStatus: NetworkStatus) {
    val isOnline: StateFlow<Boolean> = networkStatus.isOnline
}
