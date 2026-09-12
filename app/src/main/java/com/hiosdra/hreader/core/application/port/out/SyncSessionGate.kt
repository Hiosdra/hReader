package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.exception.StaleSyncSessionException

data class SyncSession(
    val ownerKey: String,
    val generation: Long
)

interface SyncSessionGate {
    fun currentSession(): SyncSession

    fun isCurrent(session: SyncSession): Boolean

    suspend fun <T> withSession(session: SyncSession, block: suspend () -> T): T

    suspend fun <T> withSessionChange(block: suspend () -> T): T
}

object NoopSyncSessionGate : SyncSessionGate {
    override fun currentSession(): SyncSession = SyncSession(ownerKey = "", generation = 0L)

    override fun isCurrent(session: SyncSession): Boolean = true

    override suspend fun <T> withSession(session: SyncSession, block: suspend () -> T): T = block()

    override suspend fun <T> withSessionChange(block: suspend () -> T): T = block()
}

suspend fun <T> SyncSessionGate.withCheckedSession(
    session: SyncSession,
    block: suspend () -> T
): T = withSession(session) {
    if (!isCurrent(session)) throw StaleSyncSessionException()
    block()
}
