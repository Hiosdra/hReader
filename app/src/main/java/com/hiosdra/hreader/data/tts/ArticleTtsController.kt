package com.hiosdra.hreader.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ArticleTtsState(
    val articleId: Long? = null,
    val title: String = "",
    val model: TtsModel? = null,
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val error: String? = null
) {
    val progress: Float
        get() = if (totalChunks == 0) 0f else currentChunk.toFloat() / totalChunks
}

class ArticleTtsController(
    context: Context,
    private val preferences: PreferencesManager,
    private val modelManager: TtsModelManager
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sherpa = SherpaTtsEngine(modelManager)
    private val languageDetector = TtsLanguageDetector(appContext)
    private val _state = MutableStateFlow(ArticleTtsState())
    val state: StateFlow<ArticleTtsState> = _state.asStateFlow()
    private var playbackJob: Job? = null
    private var playbackVersion = 0
    private var warmReleaseJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var androidTts: TextToSpeech? = null
    private var resumeSignal = CompletableDeferred(Unit)

    fun play(articleId: Long, title: String, html: String) {
        stopPlayback()
        val chunks = TtsTextProcessor.fromHtml(title, html)
        if (chunks.isEmpty()) {
            _state.value = ArticleTtsState(error = "There is no article text to read.")
            scheduleWarmRelease()
            return
        }
        warmReleaseJob?.cancel()
        warmReleaseJob = null
        val version = playbackVersion
        _state.value = ArticleTtsState(
            articleId = articleId,
            title = title,
            model = preferences.getTtsModel(),
            isPreparing = true,
            totalChunks = chunks.size
        )
        playbackJob = scope.launch {
            val language = withContext(Dispatchers.Default) {
                languageDetector.detect(chunks.take(2).joinToString(" "))
            }
            val requestedModel = preferences.getTtsModelForLanguage(language)
            val available = modelManager.statuses.value[requestedModel] == TtsModelStatus.Available
            val compatible = TtsLanguages.isCompatible(requestedModel, language)
            val model = if (
                available &&
                compatible &&
                Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")
            ) {
                requestedModel
            } else {
                TtsModel.ANDROID
            }
            _state.value = _state.value.copy(
                model = model,
                isPreparing = true,
                error = null
            )
            runCatching {
                if (model == TtsModel.ANDROID) {
                    speakWithAndroid(chunks, language)
                } else {
                    speakWithSherpa(model, chunks, language)
                }
            }.onFailure failure@{
                if (version != playbackVersion) return@failure
                if (it !is CancellationException && model != TtsModel.ANDROID) {
                    Log.e(TAG, "Neural TTS failed for ${model.name}", it)
                    _state.value = _state.value.copy(
                        model = TtsModel.ANDROID,
                        isPreparing = true,
                        error = neuralFallbackMessage(model, it)
                    )
                    runCatching { speakWithAndroid(chunks, language) }
                        .onFailure { androidFailure ->
                            if (version == playbackVersion && androidFailure !is CancellationException) {
                                _state.value = _state.value.copy(
                                    isPreparing = false,
                                    isPlaying = false,
                                    error = androidFailure.message
                                        ?: "Android TTS playback failed."
                                )
                            }
                        }
                } else if (it !is CancellationException) {
                    _state.value = _state.value.copy(
                        isPreparing = false,
                        isPlaying = false,
                        error = it.message ?: "Speech playback failed."
                    )
                }
            }
            if (version == playbackVersion) scheduleWarmRelease()
        }
    }

    fun stop() {
        stopPlayback()
        scheduleWarmRelease()
    }

    fun pause() {
        if (_state.value.articleId == null || _state.value.isPaused) return
        resumeSignal = CompletableDeferred()
        audioTrack?.runCatching { pause() }
        androidTts?.runCatching { stop() }
        _state.value = _state.value.copy(isPaused = true, isPlaying = false)
    }

    fun resume() {
        if (!_state.value.isPaused) return
        resumeSignal.complete(Unit)
        audioTrack?.runCatching { play() }
        _state.value = _state.value.copy(isPaused = false, isPlaying = true)
    }

    private fun stopPlayback() {
        playbackVersion++
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.runCatching { stop() }
        androidTts?.stop()
        resumeSignal.complete(Unit)
        _state.value = ArticleTtsState()
    }

    private fun scheduleWarmRelease() {
        warmReleaseJob?.cancel()
        warmReleaseJob = scope.launch {
            delay(MODEL_WARM_TIMEOUT_MS)
            withContext(Dispatchers.Default) { sherpa.release() }
            warmReleaseJob = null
        }
    }

    private suspend fun speakWithSherpa(model: TtsModel, chunks: List<String>, language: String) {
        coroutineScope {
            val settings = preferences.getTtsAdvancedSettings()
            val modelPreparation = async(Dispatchers.Default) {
                sherpa.prepare(model, settings)
            }
            modelPreparation.await()
            val speed = preferences.getTtsSpeed()
            var audio = withContext(Dispatchers.Default) {
                sherpa.generate(model, chunks.first(), speed, language, settings)
            }
            chunks.forEachIndexed { index, _ ->
                val nextAudio = chunks.getOrNull(index + 1)?.let { nextChunk ->
                    async(Dispatchers.Default) {
                        sherpa.generate(model, nextChunk, speed, language, settings)
                    }
                }
                resumeSignal.await()
                _state.value = _state.value.copy(
                    isPreparing = false,
                    isPlaying = !_state.value.isPaused,
                    currentChunk = index
                )
                playSamples(audio.samples, audio.sampleRate)
                audio = nextAudio?.await() ?: return@coroutineScope
            }
        }
        _state.value = ArticleTtsState()
    }

    private suspend fun playSamples(samples: FloatArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferSize = maxOf(
            samples.size * Float.SIZE_BYTES,
            pcmFloatMonoBufferSize(sampleRate, minBuffer)
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        try {
            audioTrack = track
            val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            check(written == samples.size) { "Audio playback failed ($written/${samples.size})" }
            track.play()
            while (track.playbackHeadPosition < samples.size) {
                resumeSignal.await()
                delay(20)
            }
        } finally {
            track.runCatching { stop() }
            track.release()
            if (audioTrack === track) audioTrack = null
        }
    }

    private suspend fun speakWithAndroid(chunks: List<String>, language: String) {
        val tts = initializeAndroidTts()
        tts.setSpeechRate(preferences.getTtsSpeed())
        val languageResult = tts.setLanguage(Locale.forLanguageTag(language))
        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            val fallbackResult = tts.setLanguage(Locale.getDefault())
            check(
                fallbackResult != TextToSpeech.LANG_MISSING_DATA &&
                    fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED
            ) { "Android TTS does not support this language." }
        }
        chunks.forEachIndexed { index, chunk ->
            resumeSignal.await()
            _state.value = _state.value.copy(
                isPreparing = false,
                isPlaying = !_state.value.isPaused,
                currentChunk = index
            )
            while (!speakChunk(tts, chunk)) resumeSignal.await()
        }
        _state.value = ArticleTtsState()
    }

    private suspend fun speakChunk(tts: TextToSpeech, text: String): Boolean =
        suspendCancellableCoroutine { continuation ->
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit

            override fun onDone(id: String?) {
                if (id == utteranceId && continuation.isActive) continuation.resume(true)
            }

            override fun onStop(id: String?, interrupted: Boolean) {
                if (id == utteranceId && continuation.isActive) continuation.resume(false)
            }

            @Deprecated("Deprecated by Android")
            override fun onError(id: String?) {
                if (id == utteranceId && continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("Android TTS playback failed."))
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException("Android TTS playback failed ($errorCode).")
                    )
                }
            }
        })
        continuation.invokeOnCancellation { tts.stop() }
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS && continuation.isActive) {
            continuation.resumeWithException(IllegalStateException("Android TTS could not start."))
        }
        }

    private suspend fun initializeAndroidTts(): TextToSpeech = withContext(Dispatchers.Main) {
        androidTts?.let { return@withContext it }
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            lateinit var instance: TextToSpeech
            instance = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    androidTts = instance
                    continuation.resume(instance) { _, _, _ -> instance.shutdown() }
                } else {
                    instance.shutdown()
                    continuation.cancel(IllegalStateException("Android TTS is unavailable."))
                }
            }
        }
    }

    private fun neuralFallbackMessage(model: TtsModel, error: Throwable): String {
        val reason = error.message?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
        return if (reason == null) {
            "${model.displayName} failed; using Android TTS."
        } else {
            "${model.displayName} failed: $reason. Using Android TTS."
        }
    }

    private companion object {
        const val TAG = "ArticleTtsController"
        const val MODEL_WARM_TIMEOUT_MS = 5 * 60 * 1_000L
    }
}

internal fun pcmFloatMonoBufferSize(sampleRate: Int, minimumSize: Int): Int {
    require(sampleRate > 0)
    val frameSize = Float.SIZE_BYTES
    val fallbackSize = sampleRate * frameSize / 10
    val requestedSize = maxOf(minimumSize, fallbackSize)
    return ((requestedSize + frameSize - 1) / frameSize) * frameSize
}
