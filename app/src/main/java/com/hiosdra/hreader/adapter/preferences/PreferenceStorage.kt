package com.hiosdra.hreader.adapter.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.hiosdra.hreader.core.application.port.out.PreferenceWriteBarrier
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class PreferenceStorage(
    context: Context,
    private val migration: PreferenceMigration = PreferenceMigration(context)
) : PreferenceWriteBarrier {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferencesDataStore = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { applicationContext.preferencesDataStoreFile(PREFERENCES_FILE) }
    )
    private val secretDataStore = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { applicationContext.preferencesDataStoreFile(SECRETS_FILE) }
    )

    private val preferenceState = AtomicReference<Preferences>(emptyPreferences())
    private val secretState = AtomicReference<Preferences>(emptyPreferences())
    private val preferenceStateLock = Any()
    private val secretStateLock = Any()
    private val pendingPreferenceTransforms = mutableListOf<MutablePreferences.() -> Unit>()
    private val pendingSecretTransforms = mutableListOf<MutablePreferences.() -> Unit>()
    private val preferenceReady = CompletableDeferred<Unit>()
    private val secretReady = CompletableDeferred<Unit>()
    private val migrationReady = CompletableDeferred<Unit>()
    private val preferenceWrites = Channel<WriteRequest>(Channel.UNLIMITED)
    private val secretWrites = Channel<WriteRequest>(Channel.UNLIMITED)
    private val preferenceWriteFailure = AtomicReference<Throwable?>(null)
    private val secretWriteFailure = AtomicReference<Throwable?>(null)

    init {
        scope.launch {
            try {
                migration.migrate(preferencesDataStore, secretDataStore)
                migrationReady.complete(Unit)
            } catch (error: CancellationException) {
                preferenceReady.completeExceptionally(error)
                secretReady.completeExceptionally(error)
                migrationReady.completeExceptionally(error)
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Preference migration failed; retaining stored values", error)
                migrationReady.complete(Unit)
            }
        }
        scope.launch {
            migrationReady.await()
            hydratePreferences()
        }
        scope.launch {
            migrationReady.await()
            hydrateSecrets()
        }
        scope.launch {
            processWrites(
                dataStore = preferencesDataStore,
                writes = preferenceWrites,
                failure = preferenceWriteFailure,
                ready = preferenceReady,
                storageName = "regular"
            )
        }
        scope.launch {
            processWrites(
                dataStore = secretDataStore,
                writes = secretWrites,
                failure = secretWriteFailure,
                ready = secretReady,
                storageName = "secret"
            )
        }
    }

    override suspend fun awaitReady() {
        preferenceReady.await()
        secretReady.await()
    }

    override suspend fun awaitWrites() {
        awaitReady()
        val preferenceCompletion = CompletableDeferred<Unit>()
        val secretCompletion = CompletableDeferred<Unit>()
        preferenceWrites.send(WriteRequest({}, preferenceCompletion))
        secretWrites.send(WriteRequest({}, secretCompletion))
        val preferenceFailure = awaitCompletion(preferenceCompletion)
        val secretFailure = awaitCompletion(secretCompletion)
        preferenceWriteFailure.getAndSet(null)?.let { preferenceFailure ?: throw it }
        secretWriteFailure.getAndSet(null)?.let { secretFailure ?: throw it }
        preferenceFailure?.let { throw it }
        secretFailure?.let { throw it }
    }

    internal fun <T> get(key: Preferences.Key<T>): T? = preferenceState.get()[key]

    internal fun <T> getSecret(key: Preferences.Key<T>): T? = secretState.get()[key]

    internal fun currentPreferences(): Preferences = preferenceState.get()

    internal fun <T> observe(key: Preferences.Key<T>): Flow<T?> = preferencesDataStore.data
        .map { it[key] }
        .distinctUntilChanged()

    internal fun observePreferences(): Flow<Preferences> = preferencesDataStore.data

    internal fun update(transform: MutablePreferences.() -> Unit) {
        synchronized(preferenceStateLock) {
            if (!preferenceReady.isCompleted) pendingPreferenceTransforms += transform
            val updated = preferenceState.get().toMutablePreferences().apply(transform).toPreferences()
            preferenceState.set(updated)
            preferenceWrites.trySend(WriteRequest(transform))
        }
    }

    internal fun updateSecrets(transform: MutablePreferences.() -> Unit) {
        synchronized(secretStateLock) {
            if (!secretReady.isCompleted) pendingSecretTransforms += transform
            val updated = secretState.get().toMutablePreferences().apply(transform).toPreferences()
            secretState.set(updated)
            secretWrites.trySend(WriteRequest(transform))
        }
    }

    internal suspend fun close() {
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }

    private suspend fun hydratePreferences() {
        try {
            val loaded = preferencesDataStore.data.first()
            synchronized(preferenceStateLock) {
                preferenceState.set(loaded.withPending(pendingPreferenceTransforms))
                pendingPreferenceTransforms.clear()
                preferenceReady.complete(Unit)
            }
        } catch (error: CancellationException) {
            preferenceReady.completeExceptionally(error)
            throw error
        } catch (error: Throwable) {
            preferenceReady.completeExceptionally(error)
            throw error
        } finally {
            if (!preferenceReady.isCompleted) preferenceReady.complete(Unit)
        }
    }

    private suspend fun hydrateSecrets() {
        try {
            val loaded = try {
                secretDataStore.data.first()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Secret DataStore read failed; retaining in-memory defaults", error)
                emptyPreferences()
            }
            synchronized(secretStateLock) {
                secretState.set(loaded.withPending(pendingSecretTransforms))
                pendingSecretTransforms.clear()
                secretReady.complete(Unit)
            }
        } catch (error: CancellationException) {
            secretReady.completeExceptionally(error)
            throw error
        } finally {
            if (!secretReady.isCompleted) secretReady.complete(Unit)
        }
    }

    private suspend fun processWrites(
        dataStore: DataStore<Preferences>,
        writes: Channel<WriteRequest>,
        failure: AtomicReference<Throwable?>,
        ready: CompletableDeferred<Unit>,
        storageName: String
    ) {
        ready.await()
        for (request in writes) {
            try {
                dataStore.edit(request.transform)
                request.completion?.complete(Unit)
            } catch (error: CancellationException) {
                request.completion?.cancel(error)
                throw error
            } catch (error: Throwable) {
                failure.set(error)
                Log.e(TAG, "Could not persist $storageName preferences", error)
                request.completion?.completeExceptionally(error)
            }
        }
    }

    private suspend fun awaitCompletion(completion: CompletableDeferred<Unit>): Throwable? =
        try {
            completion.await()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error
        }

    private fun Preferences.withPending(
        transforms: List<MutablePreferences.() -> Unit>
    ): Preferences = toMutablePreferences().apply {
        transforms.forEach { transform -> transform() }
    }.toPreferences()

    private data class WriteRequest(
        val transform: MutablePreferences.() -> Unit,
        val completion: CompletableDeferred<Unit>? = null
    )

    private companion object {
        const val PREFERENCES_FILE = "hreader_prefs"
        const val SECRETS_FILE = "hreader_secrets"
        const val TAG = "PreferenceStorage"
    }
}
