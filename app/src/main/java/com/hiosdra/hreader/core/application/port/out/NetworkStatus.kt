package com.hiosdra.hreader.core.application.port.out

import kotlinx.coroutines.flow.StateFlow

interface NetworkStatus {
    val isOnline: StateFlow<Boolean>
}
