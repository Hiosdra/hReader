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

    private val preferenceStore = PreferenceStoreState()
    private val secretStore = PreferenceStoreState()
    private val migrationReady = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                migration.migrate(preferencesDataStore, secretDataStore)
                migrationReady.complete(Unit)
            } catch (error: CancellationException) {
                preferenceStore.ready.completeExceptionally(error)
                secretStore.ready.completeExceptionally(error)
                migrationReady.completeExceptionally(error)
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Preference migration failed; retaining stored values", error)
                migrationReady.complete(Unit)
            }
        }
        scope.launch {
            migrationReady.await()
            hydrate(preferencesDataStore, preferenceStore)
        }
        scope.launch {
            migrationReady.await()
            hydrate(secretDataStore, secretStore, recoverReadFailure = true)
        }
        scope.launch {
            processWrites(
                dataStore = preferencesDataStore,
                state = preferenceStore,
                storageName = "regular"
            )
        }
        scope.launch {
            processWrites(
                dataStore = secretDataStore,
                state = secretStore,
                storageName = "secret"
            )
        }
    }

    override suspend fun awaitReady() {
        preferenceStore.ready.await()
        secretStore.ready.await()
    }

    override suspend fun awaitWrites() {
        awaitReady()
        flush(preferenceStore)
        flush(secretStore)
    }

    internal fun <T> get(key: Preferences.Key<T>): T? = preferenceStore.values.get()[key]

    internal fun <T> getSecret(key: Preferences.Key<T>): T? = secretStore.values.get()[key]

    internal fun currentPreferences(): Preferences = preferenceStore.values.get()

    internal fun <T> observe(key: Preferences.Key<T>): Flow<T?> = preferencesDataStore.data
        .map { it[key] }
        .distinctUntilChanged()

    internal fun observePreferences(): Flow<Preferences> = preferencesDataStore.data

    internal fun update(transform: MutablePreferences.() -> Unit) {
        update(preferenceStore, transform)
    }

    internal fun updateSecrets(transform: MutablePreferences.() -> Unit) {
        update(secretStore, transform)
    }

    internal suspend fun close() {
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }

    private suspend fun hydrate(
        dataStore: DataStore<Preferences>,
        state: PreferenceStoreState,
        recoverReadFailure: Boolean = false
    ) {
        try {
            val loaded = try {
                dataStore.data.first()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!recoverReadFailure) throw error
                Log.e(TAG, "Secret DataStore read failed; retaining in-memory defaults", error)
                emptyPreferences()
            }
            synchronized(state.lock) {
                state.values.set(loaded.withPending(state.pendingTransforms))
                state.pendingTransforms.clear()
                state.ready.complete(Unit)
            }
        } catch (error: CancellationException) {
            state.ready.completeExceptionally(error)
            throw error
        } catch (error: Throwable) {
            state.ready.completeExceptionally(error)
            throw error
        } finally {
            if (!state.ready.isCompleted) state.ready.complete(Unit)
        }
    }

    private suspend fun processWrites(
        dataStore: DataStore<Preferences>,
        state: PreferenceStoreState,
        storageName: String
    ) {
        state.ready.await()
        for (request in state.writes) {
            try {
                dataStore.edit(request.transform)
                request.completion?.complete(Unit)
            } catch (error: CancellationException) {
                request.completion?.cancel(error)
                throw error
            } catch (error: Throwable) {
                state.writeFailure.set(error)
                Log.e(TAG, "Could not persist $storageName preferences", error)
                request.completion?.completeExceptionally(error)
            }
        }
    }

    private suspend fun flush(state: PreferenceStoreState) {
        val completion = CompletableDeferred<Unit>()
        state.writes.send(WriteRequest({}, completion))
        val failure = awaitCompletion(completion)
        state.writeFailure.getAndSet(null)?.let { failure ?: throw it }
        failure?.let { throw it }
    }

    private fun update(state: PreferenceStoreState, transform: MutablePreferences.() -> Unit) {
        synchronized(state.lock) {
            if (!state.ready.isCompleted) state.pendingTransforms += transform
            state.values.set(state.values.get().toMutablePreferences().apply(transform).toPreferences())
            state.writes.trySend(WriteRequest(transform))
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

    private class PreferenceStoreState {
        val values = AtomicReference<Preferences>(emptyPreferences())
        val lock = Any()
        val pendingTransforms = mutableListOf<MutablePreferences.() -> Unit>()
        val ready = CompletableDeferred<Unit>()
        val writes = Channel<WriteRequest>(Channel.UNLIMITED)
        val writeFailure = AtomicReference<Throwable?>(null)
    }

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

internal fun <T> PreferenceStorage.value(key: Preferences.Key<T>, default: T): T =
    get(key) ?: default

internal fun <T> PreferenceStorage.set(key: Preferences.Key<T>, value: T) {
    update { this[key] = value }
}

internal fun <T> PreferenceStorage.observeValue(key: Preferences.Key<T>, default: T): Flow<T> =
    observe(key).map { it ?: default }
