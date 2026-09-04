package com.hiosdra.hreader.adapter.tts

import android.os.SystemClock
import android.util.Log
import com.hiosdra.hreader.core.application.tts.MnnTtsBackend
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog

internal class MnnTtsEngine(
    private val modelManager: TtsModelManager
) : NeuralTtsEngine {
    private val runtime = MnnTtsNative()
    private var loadedConfiguration: LoadedConfiguration? = null

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
        ensureLoaded(model, settings)
        val referenceAudio = modelManager.directory(model)
            .resolve(files.referenceAudio)
            .absolutePath
        val textCharacters = text.codePointCount(0, text.length)
        val maxFrames = mnnTtsMaxFrames(text)
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(
            TAG,
            "synthesis start model=${model.name} backend=${settings.mnnBackend.wireName} " +
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
                "synthesis failed model=${model.name} backend=${settings.mnnBackend.wireName} " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                error
            )
            throw error
        }
        Log.i(
            TAG,
            "synthesis complete model=${model.name} backend=${settings.mnnBackend.wireName} " +
                "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} samples=${samples.size}"
        )
        return TtsAudio(
            samples = samples,
            sampleRate = SAMPLE_RATE,
            playbackSpeed = speed.coerceIn(0.7f, 1.4f)
        )
    }

    @Synchronized
    override fun release() {
        runtime.release()
        loadedConfiguration = null
    }

    private fun ensureLoaded(model: TtsModel, settings: TtsAdvancedSettings) {
        check(model in supportedModels) { "MNN does not support ${model.name}" }
        val configuration = LoadedConfiguration(model, settings.numThreads, settings.mnnBackend)
        if (configuration == loadedConfiguration) return
        runtime.release()
        loadedConfiguration = null
        val files = modelFiles(model)
        val cacheDirectory = modelManager.runtimeCacheDirectory(model, settings.mnnBackend)
        cacheDirectory.mkdirs()
        runtime.load(
            modelDirectory = modelManager.directory(model).absolutePath,
            configName = files.config,
            numThreads = settings.numThreads,
            backend = settings.mnnBackend.wireName,
            cacheDirectory = cacheDirectory.absolutePath
        )
        loadedConfiguration = configuration
    }

    private fun modelFiles(model: TtsModel): MnnModelFiles =
        checkNotNull(TtsModelPackageCatalog.packageFor(model)?.engineFiles as? MnnModelFiles) {
            "No MNN model package registered for ${model.name}"
        }

    private data class LoadedConfiguration(
        val model: TtsModel,
        val numThreads: Int,
        val backend: MnnTtsBackend
    )

    private companion object {
        const val TAG = "MnnTtsEngine"
        const val SAMPLE_RATE = 24_000
    }
}

internal const val MNN_TTS_MAX_CHUNK_CHARACTERS = 120
internal const val MNN_TTS_MIN_FRAMES = 128
internal const val MNN_TTS_MAX_FRAMES = 384

internal fun mnnTtsMaxFrames(text: String): Int =
    (text.codePointCount(0, text.length) * 2).coerceIn(MNN_TTS_MIN_FRAMES, MNN_TTS_MAX_FRAMES)
