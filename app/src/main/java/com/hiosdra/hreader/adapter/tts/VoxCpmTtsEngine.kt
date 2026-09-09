package com.hiosdra.hreader.adapter.tts

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.Keep
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import java.io.File
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong

internal class VoxCpmTtsEngine(
    context: Context,
    private val modelManager: TtsModelManager
) : NeuralTtsEngine {
    private val activityManager = checkNotNull(context.getSystemService(ActivityManager::class.java))
    private val generationIds = AtomicLong()
    override val supportedModels: Set<TtsModel> = setOf(TtsModel.VOXCPM2)
    private var loadedConfiguration: LoadedConfiguration? = null
    @Volatile
    private var nativeLibraryLoaded = false

    @Synchronized
    override fun prepare(model: TtsModel, settings: TtsAdvancedSettings) {
        check(model == TtsModel.VOXCPM2) { "VoxCPM2 engine does not support ${model.name}" }
        val modelPackage = checkNotNull(TtsModelPackageCatalog.packageFor(model)) {
            "No model package registered for ${model.name}"
        }
        val files = modelPackage.engineFiles as? VoxCpm2ModelFiles
            ?: error("VoxCPM2 model package has an incompatible engine configuration")
        val directory = modelManager.directory(model)
        val configuration = LoadedConfiguration(settings.numThreads)
        if (loadedConfiguration == configuration) return
        check(modelManager.hasValidIntegrity(model)) {
            "VoxCPM2 model files are missing or corrupt"
        }
        ensureMemoryAvailable()
        release()
        ensureNativeLibrary()
        val initialized = VoxCpmNative.init(
            baseLmPath = File(directory, files.baseLm).absolutePath,
            acousticPath = File(directory, files.acoustic).absolutePath,
            numThreads = settings.numThreads
        )
        check(initialized) {
            "VoxCPM2 runtime initialization failed: ${nativeError()}"
        }
        loadedConfiguration = configuration
    }

    @Synchronized
    override fun generate(
        model: TtsModel,
        text: String,
        speed: Float,
        language: String,
        settings: TtsAdvancedSettings
    ): TtsAudio {
        prepare(model, settings)
        val generation = generationIds.incrementAndGet()
        activeGeneration = generation
        val samples = try {
            VoxCpmNative.generate(
                text = text,
                cfgValue = settings.voxCpmCfg,
                inferenceTimesteps = settings.voxCpmTimesteps,
                generation = generation
            ) ?: if (VoxCpmNative.wasCancelled()) {
                throw CancellationException()
            } else {
                error(nativeError())
            }
        } finally {
            if (activeGeneration == generation) activeGeneration = 0L
        }
        check(samples.isNotEmpty()) { "VoxCPM2 returned an empty waveform" }
        return TtsAudio(
            samples = samples,
            sampleRate = SAMPLE_RATE,
            playbackSpeed = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        )
    }

    override fun cancel() {
        val generation = activeGeneration
        if (nativeLibraryLoaded && generation != 0L) VoxCpmNative.cancel(generation)
    }

    @Synchronized
    override fun release() {
        if (nativeLibraryLoaded) VoxCpmNative.release()
        loadedConfiguration = null
    }

    private fun ensureNativeLibrary() {
        if (nativeLibraryLoaded) return
        try {
            System.loadLibrary(NATIVE_LIBRARY)
            nativeLibraryLoaded = true
        } catch (error: UnsatisfiedLinkError) {
            throw IllegalStateException("VoxCPM2 native runtime is unavailable", error)
        }
    }

    private fun nativeError(): String = VoxCpmNative.lastError().ifBlank {
        "VoxCPM2 native operation failed"
    }

    private fun ensureMemoryAvailable() {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        check(
            !memoryInfo.lowMemory &&
                memoryInfo.totalMem >= MIN_TOTAL_MEMORY_BYTES &&
                memoryInfo.availMem >= MIN_AVAILABLE_MEMORY_BYTES
        ) {
            "VoxCPM2 requires at least 6 GB RAM and 1 GB currently available"
        }
    }

    private data class LoadedConfiguration(
        val numThreads: Int
    )

    @Volatile
    private var activeGeneration = 0L

    private companion object {
        const val MAX_PLAYBACK_SPEED = 1.4f
        const val MIN_PLAYBACK_SPEED = 0.7f
        const val NATIVE_LIBRARY = "hreader_voxcpm"
        const val SAMPLE_RATE = 48_000
        const val MIN_TOTAL_MEMORY_BYTES = 6L * 1024 * 1024 * 1024
        const val MIN_AVAILABLE_MEMORY_BYTES = 1L * 1024 * 1024 * 1024
    }
}

@Keep
internal object VoxCpmNative {
    @JvmStatic
    external fun init(baseLmPath: String, acousticPath: String, numThreads: Int): Boolean

    @JvmStatic
    external fun generate(
        text: String,
        cfgValue: Float,
        inferenceTimesteps: Int,
        generation: Long
    ): FloatArray?

    @JvmStatic
    external fun cancel(generation: Long)

    @JvmStatic
    external fun wasCancelled(): Boolean

    @JvmStatic
    external fun lastError(): String

    @JvmStatic
    external fun release()
}
