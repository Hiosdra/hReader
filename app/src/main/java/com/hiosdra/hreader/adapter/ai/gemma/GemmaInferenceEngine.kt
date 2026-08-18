package com.hiosdra.hreader.adapter.ai.gemma

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.hiosdra.hreader.core.application.ai.GemmaBackend
import com.hiosdra.hreader.core.application.ai.GemmaModelNotInstalledException
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.GemmaModelLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "GemmaInference"

internal fun backendAttempts(preference: GemmaBackend): List<GemmaBackend> = when (preference) {
    GemmaBackend.AUTO -> listOf(GemmaBackend.GPU, GemmaBackend.CPU)
    GemmaBackend.CPU -> listOf(GemmaBackend.CPU)
    GemmaBackend.GPU -> listOf(GemmaBackend.GPU, GemmaBackend.CPU)
    GemmaBackend.NPU -> listOf(GemmaBackend.NPU, GemmaBackend.GPU, GemmaBackend.CPU)
}

class GemmaInferenceEngine(
    context: Context,
    private val modelManager: GemmaModelManager,
    private val preferences: AiPreferences
) : GemmaModelLifecycle {
    private val appContext = context.applicationContext
    private val lock = Mutex()
    private var engine: Engine? = null
    private var enginePath: String? = null
    private var engineBackend: GemmaBackend? = null

    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
        temperature: Double
    ): Result<String> = withContext(Dispatchers.Default) {
        lock.withLock {
            if (!modelManager.isInstalled()) {
                return@withLock Result.failure(GemmaModelNotInstalledException())
            }
            val path = modelManager.modelPath()
            var lastFailure: Throwable? = null
            for (backend in backendAttempts(preferences.getGemmaBackend())) {
                try {
                    val activeEngine = getEngine(path, backend)
                    val response = activeEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(systemPrompt),
                            samplerConfig = SamplerConfig(
                                topK = 40,
                                topP = 0.95,
                                temperature = temperature,
                                seed = 0
                            ),
                            maxOutputToken = maxOutputTokens
                        )
                    ).use { conversation ->
                        buildString {
                            conversation.sendMessageAsync(Contents.of(userPrompt)).collect { message ->
                                message.contents.contents
                                    .filterIsInstance<Content.Text>()
                                    .forEach { append(it.text) }
                            }
                        }.trim()
                    }
                    if (response.isBlank()) error("Gemma returned an empty response")
                    Log.d(TAG, "Gemma inference completed with backend $backend")
                    return@withLock Result.success(response)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LinkageError) {
                    lastFailure = e
                    closeEngine()
                    Log.w(TAG, "Gemma backend $backend is not available", e)
                } catch (e: Exception) {
                    lastFailure = e
                    closeEngine()
                    Log.w(TAG, "Gemma backend $backend failed", e)
                }
            }
            Result.failure(lastFailure ?: IllegalStateException("No Gemma backend available"))
        }
    }

    override suspend fun close() = lock.withLock { closeEngine() }

    private fun getEngine(path: String, backend: GemmaBackend): Engine {
        if (engine != null && enginePath == path && engineBackend == backend) return engine!!
        closeEngine()
        val created = Engine(
            EngineConfig(
                modelPath = path,
                backend = backend.toLiteRtBackend()
            )
        )
        return try {
            created.initialize()
            engine = created
            enginePath = path
            engineBackend = backend
            created
        } catch (error: Throwable) {
            runCatching { created.close() }
            throw error
        }
    }

    private fun GemmaBackend.toLiteRtBackend(): Backend = when (this) {
        GemmaBackend.AUTO -> error("AUTO must be resolved before engine creation")
        GemmaBackend.CPU -> Backend.CPU()
        GemmaBackend.GPU -> Backend.GPU()
        GemmaBackend.NPU -> Backend.NPU(appContext.applicationInfo.nativeLibraryDir)
    }

    private fun closeEngine() {
        val active = engine
        engine = null
        enginePath = null
        engineBackend = null
        runCatching { active?.close() }
            .onFailure { Log.w(TAG, "Could not close Gemma engine", it) }
    }
}
