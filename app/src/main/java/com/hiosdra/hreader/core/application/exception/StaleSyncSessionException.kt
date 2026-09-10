package com.hiosdra.hreader.core.application.exception

import kotlinx.coroutines.CancellationException

class StaleSyncSessionException : CancellationException(
    "The sync session changed while the operation was in flight"
)
