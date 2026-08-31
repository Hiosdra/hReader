package com.hiosdra.hreader.adapter.tts

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.content.hasReadableArticleText
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlayer
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlaybackServiceControl
import com.hiosdra.hreader.core.application.port.out.ArticleTtsState
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.core.application.port.out.TtsPreferences
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.tts.TtsModel
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

class ArticleTtsController internal constructor(
    context: Context,
    private val preferences: TtsPreferences,
    private val modelManager: TtsModelGateway,
    private val neuralTts: NeuralTtsEngine,
    private val playbackService: ArticleTtsPlaybackServiceControl
) : ArticleTtsPlayer {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val languageDetector = TtsLanguageDetector(appContext)
    private val _state = MutableStateFlow(ArticleTtsState())
    override val state: StateFlow<ArticleTtsState> = _state.asStateFlow()
    private var playbackJob: Job? = null
    private var playbackVersion = 0
    private var warmReleaseJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var androidTts: TextToSpeech? = null
    private var resumeSignal = CompletableDeferred(Unit)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val speechAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS -> stop()
        }
    }
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun play(
        articleId: Long,
        title: String,
        html: String,
        modelOverride: TtsModel?
    ) {
        stopPlayback()
        if (!hasReadableArticleText(html)) {
            _state.value = ArticleTtsState(error = appContext.getString(R.string.tts_no_article_text))
            scheduleWarmRelease()
            return
        }
        val chunks = TtsTextProcessor.fromHtml(title, html)
        if (chunks.isEmpty()) {
            _state.value = ArticleTtsState(error = appContext.getString(R.string.tts_no_article_text))
            scheduleWarmRelease()
            return
        }
        if (!requestAudioFocus()) {
            _state.value = ArticleTtsState(error = appContext.getString(R.string.tts_audio_focus_unavailable))
            scheduleWarmRelease()
            return
        }
        warmReleaseJob?.cancel()
        warmReleaseJob = null
        val version = playbackVersion
        _state.value = ArticleTtsState(
            articleId = articleId,
            title = title,
            model = modelOverride ?: preferences.getTtsModel(),
            isPreparing = true,
            totalChunks = chunks.size
        )
        if (!playbackService.start()) {
            abandonAudioFocus()
            _state.value = ArticleTtsState(
                error = appContext.getString(R.string.tts_background_playback_failed)
            )
            scheduleWarmRelease()
            return
        }
        playbackJob = scope.launch {
            try {
                val language = withContext(Dispatchers.Default) {
                    languageDetector.detect(chunks.take(2).joinToString(" "))
                }
                val model = resolveArticleTtsModel(
                    modelOverride = modelOverride,
                    settingsModel = preferences.getTtsModelForLanguage(language),
                    language = language,
                    statuses = modelManager.statuses.value,
                    supportsArm64 = Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")
                )
                _state.value = _state.value.copy(
                    model = model,
                    isPreparing = true,
                    error = null
                )
                runCatchingCancellable {
                    if (model == TtsModel.ANDROID) {
                        speakWithAndroid(chunks, language)
                    } else {
                        speakWithNeuralTts(model, chunks, language)
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
                        runCatchingCancellable { speakWithAndroid(chunks, language) }
                            .onFailure { androidFailure ->
                                if (version == playbackVersion && androidFailure !is CancellationException) {
                                    finishPlaybackWithError(
                                        version,
                                        appContext.getString(R.string.tts_system_voice_failed)
                                    )
                                }
                            }
                    } else if (it !is CancellationException) {
                        finishPlaybackWithError(
                            version,
                            appContext.getString(R.string.tts_speech_playback_failed)
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finishPlaybackWithError(
                    version,
                    appContext.getString(R.string.tts_speech_playback_failed)
                )
            }
            if (version == playbackVersion) scheduleWarmRelease()
        }
    }

    override fun stop() {
        stopPlayback()
        scheduleWarmRelease()
        playbackService.stop()
    }

    override fun pause() {
        if (_state.value.articleId == null || _state.value.isPaused) return
        resumeSignal = CompletableDeferred()
        audioTrack?.runCatching { pause() }
        androidTts?.runCatching { stop() }
        abandonAudioFocus()
        _state.value = _state.value.copy(isPaused = true, isPlaying = false)
    }

    override fun resume() {
        if (!_state.value.isPaused) return
        if (!requestAudioFocus()) {
            _state.value = _state.value.copy(error = appContext.getString(R.string.tts_audio_focus_unavailable))
            return
        }
        resumeSignal.complete(Unit)
        audioTrack?.runCatching { play() }
        _state.value = _state.value.copy(isPaused = false, isPlaying = true)
    }

    private fun stopPlayback() {
        playbackVersion++
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.runCatching { stop() }
        androidTts?.runCatching { stop() }
        androidTts?.runCatching { shutdown() }
        androidTts = null
        abandonAudioFocus()
        resumeSignal.complete(Unit)
        _state.value = ArticleTtsState()
    }

    override fun stopFromService() {
        stopPlayback()
        scheduleWarmRelease()
    }

    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAudioAttributes)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
        audioFocusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        audioFocusRequest = null
    }

    private fun finishPlaybackWithError(version: Int, message: String) {
        if (version != playbackVersion) return
        abandonAudioFocus()
        _state.value = _state.value.copy(
            isPreparing = false,
            isPlaying = false,
            isPaused = false,
            error = message
        )
        playbackService.stop()
    }

    private fun scheduleWarmRelease() {
        warmReleaseJob?.cancel()
        warmReleaseJob = scope.launch {
            delay(MODEL_WARM_TIMEOUT_MS)
            withContext(Dispatchers.Default) { neuralTts.release() }
            warmReleaseJob = null
        }
    }

    private suspend fun speakWithNeuralTts(model: TtsModel, chunks: List<String>, language: String) {
        coroutineScope {
            val settings = preferences.getTtsAdvancedSettings()
            val modelPreparation = async(Dispatchers.Default) {
                neuralTts.prepare(model, settings)
            }
            modelPreparation.await()
            val speed = preferences.getTtsSpeed()
            var audio = withContext(Dispatchers.Default) {
                neuralTts.generate(model, chunks.first(), speed, language, settings)
            }
            chunks.forEachIndexed { index, _ ->
                val nextAudio = chunks.getOrNull(index + 1)?.let { nextChunk ->
                    async(Dispatchers.Default) {
                        neuralTts.generate(model, nextChunk, speed, language, settings)
                    }
                }
                resumeSignal.await()
                _state.value = _state.value.copy(
                    isPreparing = false,
                    isPlaying = !_state.value.isPaused,
                    currentChunk = index
                )
                playSamples(audio.samples, audio.sampleRate, audio.playbackSpeed)
                audio = nextAudio?.await() ?: return@coroutineScope
            }
        }
        abandonAudioFocus()
        _state.value = ArticleTtsState()
    }

    private suspend fun playSamples(
        samples: FloatArray,
        sampleRate: Int,
        playbackSpeed: Float
    ) = withContext(Dispatchers.IO) {
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
                speechAudioAttributes
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
            if (playbackSpeed != 1f) {
                track.playbackParams = PlaybackParams()
                    .setSpeed(playbackSpeed)
                    .setPitch(1f)
            }
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
        try {
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
                ) { appContext.getString(R.string.tts_system_voice_language_missing) }
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
            abandonAudioFocus()
            _state.value = ArticleTtsState()
        } finally {
            if (androidTts === tts) androidTts = null
            tts.runCatching { shutdown() }
        }
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
                    continuation.resumeWithException(
                        IllegalStateException(appContext.getString(R.string.tts_system_voice_failed))
                    )
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException(appContext.getString(R.string.tts_system_voice_failed))
                    )
                }
            }
        })
        continuation.invokeOnCancellation { tts.stop() }
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS && continuation.isActive) {
            continuation.resumeWithException(
                IllegalStateException(appContext.getString(R.string.tts_system_voice_start_failed))
            )
        }
        }

    private suspend fun initializeAndroidTts(): TextToSpeech = withContext(Dispatchers.Main) {
        androidTts?.let { return@withContext it }
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            lateinit var instance: TextToSpeech
            instance = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS && continuation.isActive) {
                    androidTts = instance
                    continuation.resume(instance) { _, _, _ ->
                        if (androidTts === instance) androidTts = null
                        instance.shutdown()
                    }
                } else {
                    instance.shutdown()
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException(
                                appContext.getString(R.string.tts_system_voice_unavailable)
                            )
                        )
                    }
                }
            }
            continuation.invokeOnCancellation {
                if (androidTts === instance) androidTts = null
                instance.shutdown()
            }
        }
    }

    private fun neuralFallbackMessage(model: TtsModel, error: Throwable): String {
        val reason = error.message?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
        val modelName = appContext.getString(model.displayNameRes)
        return if (reason == null) {
            appContext.getString(R.string.tts_neural_voice_fallback, modelName)
        } else {
            appContext.getString(R.string.tts_neural_voice_fallback_reason, modelName, reason)
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
