package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.core.application.exception.StaleSyncSessionException
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.PendingChangeStore
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import kotlinx.coroutines.CancellationException

private const val STATUS_UPDATE_CHUNK = 200

internal class PendingArticleStatusUploader(
    private val api: FeedBackend,
    private val backendIdentity: BackendIdentity,
    private val pendingChanges: PendingChangeStore
) {
    suspend fun push(ownerKey: String) {
        val pending = pendingChanges.getPendingStatuses()
        if (pending.isEmpty()) return
        Log.i(TAG, "Pushing ${pending.size} queued status changes")
        pending.groupBy { it.status ?: ArticleStatus.UNREAD }.forEach { (status, queued) ->
            checkSession(ownerKey)
            try {
                queued.map { it.id }.chunked(STATUS_UPDATE_CHUNK).forEach { chunk ->
                    api.updateEntriesStatus(chunk.map { it.toLong() }, status)
                    checkSession(ownerKey)
                    pendingChanges.clearPendingStatuses(chunk, status)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Status push failed; queued for the next sync: ${error.message}")
            }
        }
    }

    private fun checkSession(ownerKey: String) {
        if (ownerKey.isNotBlank() && backendIdentity.cacheOwnerKey() != ownerKey) {
            throw StaleSyncSessionException()
        }
    }

    private companion object {
        const val TAG = "PendingStatusUploader"
    }
}
