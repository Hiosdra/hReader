package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.OfflineReadiness
import kotlinx.coroutines.flow.Flow

interface OfflineReadinessStore {
    fun observe(): Flow<OfflineReadiness>
}
