package com.hiosdra.hreader.adapter.tts

import androidx.annotation.Keep
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import java.io.File
import kotlinx.coroutines.CancellationException

internal class VoxCpmTtsEngine(
    private val modelManager: TtsModelManager
) : NeuralTtsEngine {
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
        check(modelPackage.isComplete(directory)) {
            "VoxCPM2 model files are not installed"
        }

        val configuration = LoadedConfiguration(settings.numThreads)
        if (loadedConfiguration == configuration) return
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
        val samples = VoxCpmNative.generate(
            text = text,
            cfgValue = settings.voxCpmCfg,
            inferenceTimesteps = settings.voxCpmTimesteps
        ) ?: if (VoxCpmNative.wasCancelled()) {
            throw CancellationException()
        } else {
            error(nativeError())
        }
        check(samples.isNotEmpty()) { "VoxCPM2 returned an empty waveform" }
        return TtsAudio(
            samples = samples,
            sampleRate = SAMPLE_RATE,
            playbackSpeed = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        )
    }

    override fun cancel() {
        if (nativeLibraryLoaded) VoxCpmNative.cancel()
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

    private data class LoadedConfiguration(
        val numThreads: Int
    )

    private companion object {
        const val MAX_PLAYBACK_SPEED = 1.4f
        const val MIN_PLAYBACK_SPEED = 0.7f
        const val NATIVE_LIBRARY = "hreader_voxcpm"
        const val SAMPLE_RATE = 48_000
    }
}

@Keep
internal object VoxCpmNative {
    @JvmStatic
    external fun init(baseLmPath: String, acousticPath: String, numThreads: Int): Boolean

    @JvmStatic
    external fun generate(text: String, cfgValue: Float, inferenceTimesteps: Int): FloatArray?

    @JvmStatic
    external fun cancel()

    @JvmStatic
    external fun wasCancelled(): Boolean

    @JvmStatic
    external fun lastError(): String

    @JvmStatic
    external fun release()
}
