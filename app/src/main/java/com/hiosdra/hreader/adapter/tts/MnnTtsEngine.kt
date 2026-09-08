package com.hiosdra.hreader.adapter.tts

import android.os.SystemClock
import android.util.Log
import com.hiosdra.hreader.core.application.port.out.TtsModelCachePreparer
import com.hiosdra.hreader.core.application.tts.MnnTtsBackend
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog

internal class MnnTtsEngine(
    private val modelManager: TtsModelManager
) : NeuralTtsEngine, TtsModelCachePreparer {
    private val runtime = MnnTtsNative()
    private var loadedConfiguration: LoadedConfiguration? = null
    private var cacheReadyCandidate = false

    override val supportedModels: Set<TtsModel> = TtsModelCatalog.models
        .filter {
            it.family == TtsModel.MNN_0_6B_BASE_INT8.family &&
                TtsModelPackageCatalog.packageFor(it)?.engineFiles is MnnModelFiles
        }
        .toSet()

    @Synchronized
    override fun prepare(model: TtsModel, settings: TtsAdvancedSettings) {
        ensureLoaded(model, settings)
    }

    @Synchronized
    override fun generate(
        model: TtsModel,
        text: String,
        speed: Float,
        language: String,
        settings: TtsAdvancedSettings
    ): TtsAudio {
        val files = modelFiles(model)
        val effectiveBackend = ensureLoaded(model, settings)
        val referenceAudio = modelManager.directory(model)
            .resolve(files.referenceAudio)
            .absolutePath
        val textCharacters = text.codePointCount(0, text.length)
        val maxFrames = mnnTtsMaxFrames(text)
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(
            TAG,
            "synthesis start model=${model.name} requestedBackend=${settings.mnnBackend.wireName} " +
                "backend=${effectiveBackend.wireName} " +
                "threads=${settings.numThreads} textChars=$textCharacters maxFrames=$maxFrames"
        )
        val samples = try {
            runtime.synthesize(
                text = text,
                language = QwenTtsLanguage.mnnName(language),
                referenceAudio = referenceAudio,
                maxFrames = maxFrames
            )
        } catch (error: Exception) {
            Log.e(
                TAG,
                "synthesis failed model=${model.name} backend=${effectiveBackend.wireName} " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                error
            )
            throw error
        }
        Log.i(
            TAG,
            "synthesis complete model=${model.name} backend=${effectiveBackend.wireName} " +
                "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} samples=${samples.size}"
        )
        cacheReadyCandidate = samples.isNotEmpty()
        return TtsAudio(
            samples = samples,
            sampleRate = SAMPLE_RATE,
            playbackSpeed = speed.coerceIn(0.7f, 1.4f)
        )
    }

    @Synchronized
    override fun prepareCache(
        model: TtsModel,
        settings: TtsAdvancedSettings,
        forceRefresh: Boolean
    ): MnnTtsBackend =
        try {
            if (forceRefresh) {
                releaseRuntime()
                mnnTtsBackendCandidates(settings.mnnBackend).forEach { backend ->
                    modelManager.invalidate(model, backend)
                }
            }
            val audio = generate(
                model = model,
                text = CACHE_WARMUP_TEXT,
                speed = 1f,
                language = "en",
                settings = settings
            )
            check(audio.samples.isNotEmpty()) { "MNN cache warm-up produced no audio" }
            checkNotNull(loadedConfiguration).effectiveBackend
        } finally {
            releaseRuntime()
        }

    @Synchronized
    override fun release() {
        releaseRuntime()
    }

    private fun ensureLoaded(model: TtsModel, settings: TtsAdvancedSettings): MnnTtsBackend {
        check(model in supportedModels) { "MNN does not support ${model.name}" }
        val request = LoadRequest(model, settings.numThreads, settings.mnnBackend)
        loadedConfiguration?.takeIf { it.request == request }?.let { return it.effectiveBackend }
        releaseRuntime()
        val files = modelFiles(model)
        var lastError: Exception? = null
        for (backend in mnnTtsBackendCandidates(settings.mnnBackend)) {
            try {
                val cacheDirectory = modelManager.runtimeCacheDirectory(model, backend)
                check(cacheDirectory.isDirectory || cacheDirectory.mkdirs()) {
                    "Could not create MNN runtime cache directory ${cacheDirectory.absolutePath}"
                }
                if (!modelManager.isReady(model, backend)) {
                    modelManager.invalidate(model, backend)
                }
                val cacheBytesBefore = cacheDirectoryBytes(cacheDirectory)
                val startedAt = SystemClock.elapsedRealtime()
                runtime.load(
                    modelDirectory = modelManager.directory(model).absolutePath,
                    configName = files.config,
                    numThreads = settings.numThreads,
                    backend = backend.wireName,
                    cacheDirectory = cacheDirectory.absolutePath
                )
                loadedConfiguration = LoadedConfiguration(request, backend)
                cacheReadyCandidate = false
                Log.i(
                    TAG,
                    "backend ready model=${model.name} backend=${backend.wireName} " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                        "cacheBytesBefore=$cacheBytesBefore"
                )
                if (backend != settings.mnnBackend) {
                    Log.w(
                        TAG,
                        "backend fallback model=${model.name} requested=${settings.mnnBackend.wireName} " +
                            "effective=${backend.wireName}"
                    )
                }
                return backend
            } catch (error: Exception) {
                lastError = error
                runtime.release()
                Log.w(
                    TAG,
                    "backend failed model=${model.name} backend=${backend.wireName} " +
                        "cacheBytesAfter=${cacheDirectoryBytes(modelManager.runtimeCacheDirectory(model, backend))}",
                    error
                )
            }
        }
        throw checkNotNull(lastError) { "MNN TTS backend initialization failed" }
    }

    private fun releaseRuntime() {
        val configuration = loadedConfiguration
        val markCacheReady = cacheReadyCandidate
        runtime.release()
        loadedConfiguration = null
        cacheReadyCandidate = false
        configuration?.let {
            if (markCacheReady) {
                runCatching { modelManager.markReady(it.request.model, it.effectiveBackend) }
                    .onFailure { error -> Log.w(TAG, "Could not mark MNN runtime cache ready", error) }
            }
            Log.i(
                TAG,
                "runtime released model=${it.request.model.name} backend=${it.effectiveBackend.wireName} " +
                    "cacheBytes=${cacheDirectoryBytes(modelManager.runtimeCacheDirectory(it.request.model, it.effectiveBackend))}"
            )
        }
    }

    private fun cacheDirectoryBytes(directory: java.io.File): Long =
        if (!directory.isDirectory) {
            0L
        } else {
            directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }

    private fun modelFiles(model: TtsModel): MnnModelFiles =
        checkNotNull(TtsModelPackageCatalog.packageFor(model)?.engineFiles as? MnnModelFiles) {
            "No MNN model package registered for ${model.name}"
        }

    private data class LoadedConfiguration(
        val request: LoadRequest,
        val effectiveBackend: MnnTtsBackend
    )

    private data class LoadRequest(
        val model: TtsModel,
        val numThreads: Int,
        val requestedBackend: MnnTtsBackend
    )

    private companion object {
        const val TAG = "MnnTtsEngine"
        const val CACHE_WARMUP_TEXT = "This is a short voice cache warm-up."
        const val SAMPLE_RATE = 24_000
    }
}

internal const val MNN_TTS_MAX_CHUNK_CHARACTERS = 120
internal const val MNN_TTS_MIN_FRAMES = 128
internal const val MNN_TTS_MAX_FRAMES = 384

internal fun mnnTtsBackendCandidates(requestedBackend: MnnTtsBackend): List<MnnTtsBackend> =
    when (requestedBackend) {
        MnnTtsBackend.CPU -> listOf(MnnTtsBackend.CPU)
        MnnTtsBackend.OPENCL -> listOf(MnnTtsBackend.OPENCL, MnnTtsBackend.CPU)
        MnnTtsBackend.VULKAN -> listOf(MnnTtsBackend.VULKAN, MnnTtsBackend.OPENCL, MnnTtsBackend.CPU)
    }

internal fun mnnTtsMaxFrames(text: String): Int =
    (text.codePointCount(0, text.length) * 2).coerceIn(MNN_TTS_MIN_FRAMES, MNN_TTS_MAX_FRAMES)
