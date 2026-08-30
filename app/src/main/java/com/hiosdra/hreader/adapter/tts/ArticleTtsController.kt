package com.hiosdra.hreader.adapter.tts

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.content.hasReadableArticleText
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlayer
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlaybackServiceControl
import com.hiosdra.hreader.core.application.port.out.ArticleTtsState
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.port.out.TtsPreferences
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsTextDocumentFactory
import com.hiosdra.hreader.core.application.tts.TtsTextRange
import com.hiosdra.hreader.core.application.tts.TtsTextSegment
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
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
    private var currentRequest: PlaybackRequest? = null
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
        modelOverride: TtsModel?,
        startOffset: Int
    ) {
        stopPlayback()
        startPlayback(
            request = PlaybackRequest(articleId, title, html, modelOverride),
            startOffset = startOffset,
            startService = true
        )
    }

    private fun startPlayback(
        request: PlaybackRequest,
        startOffset: Int,
        startService: Boolean
    ) {
        currentRequest = request
        val articleId = request.articleId
        val title = request.title
        val html = request.html
        val modelOverride = request.modelOverride
        if (!hasReadableArticleText(html)) {
            _state.value = ArticleTtsState(error = appContext.getString(R.string.tts_no_article_text))
            currentRequest = null
            scheduleWarmRelease()
            return
        }
        val document = TtsTextDocumentFactory.fromHtml(title, html)
        val segments = document.segmentsFrom(startOffset)
        if (segments.isEmpty()) {
            _state.value = ArticleTtsState(error = appContext.getString(R.string.tts_no_article_text))
            currentRequest = null
            scheduleWarmRelease()
            return
        }
        if (!requestAudioFocus()) {
            _state.value = ArticleTtsState(error = appContext.getString(R.string.tts_audio_focus_unavailable))
            currentRequest = null
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
            totalChunks = segments.size,
            currentRange = segments.first().range
        )
        if (startService && !playbackService.start()) {
            abandonAudioFocus()
            _state.value = ArticleTtsState(
                error = appContext.getString(R.string.tts_background_playback_failed)
            )
            currentRequest = null
            scheduleWarmRelease()
            return
        }
        playbackJob = scope.launch {
            try {
                val language = withContext(Dispatchers.Default) {
                    languageDetector.detect(segments.take(2).joinToString(" ") { it.text })
                }
                if (version != playbackVersion) return@launch
                val model = resolveArticleTtsModel(
                    modelOverride = modelOverride,
                    settingsModel = preferences.getTtsModelForLanguage(language),
                    language = language,
                    statuses = modelManager.statuses.value,
                    supportsArm64 = Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")
                )
                if (version != playbackVersion) return@launch
                _state.value = _state.value.copy(
                    model = model,
                    isPreparing = true,
                    error = null
                )
                runCatchingCancellable {
                    if (model == TtsModel.ANDROID) {
                        speakWithAndroid(segments, language, version)
                    } else {
                        speakWithNeuralTts(model, segments, language, version)
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
                        runCatchingCancellable { speakWithAndroid(segments, language, version) }
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

    override fun seekTo(textOffset: Int) {
        val request = currentRequest ?: return
        stopPlayback(clearState = false, clearRequest = false)
        startPlayback(
            request = request,
            startOffset = textOffset,
            startService = false
        )
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

    private fun stopPlayback(clearState: Boolean = true, clearRequest: Boolean = true) {
        playbackVersion++
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.runCatching { stop() }
        androidTts?.runCatching { stop() }
        androidTts?.runCatching { shutdown() }
        androidTts = null
        abandonAudioFocus()
        resumeSignal.complete(Unit)
        if (clearState) _state.value = ArticleTtsState()
        if (clearRequest) currentRequest = null
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
        currentRequest = null
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

    private suspend fun speakWithNeuralTts(
        model: TtsModel,
        segments: List<TtsTextSegment>,
        language: String,
        version: Int
    ) {
        coroutineScope {
            val settings = preferences.getTtsAdvancedSettings()
            val modelPreparation = async(Dispatchers.Default) {
                neuralTts.prepare(model, settings)
            }
            modelPreparation.await()
            if (version != playbackVersion) return@coroutineScope
            val speed = preferences.getTtsSpeed()
            var audio = withContext(Dispatchers.Default) {
                neuralTts.generate(model, segments.first().text, speed, language, settings)
            }
            if (version != playbackVersion) return@coroutineScope
            segments.forEachIndexed { index, segment ->
                if (version != playbackVersion) return@coroutineScope
                val nextAudio = segments.getOrNull(index + 1)?.let { nextSegment ->
                    async(Dispatchers.Default) {
                        neuralTts.generate(model, nextSegment.text, speed, language, settings)
                    }
                }
                resumeSignal.await()
                if (version != playbackVersion) return@coroutineScope
                _state.value = _state.value.copy(
                    isPreparing = false,
                    isPlaying = !_state.value.isPaused,
                    currentChunk = index,
                    currentRange = segment.range
                )
                playSamples(audio.samples, audio.sampleRate)
                audio = nextAudio?.await() ?: return@coroutineScope
            }
        }
        if (version != playbackVersion) return
        abandonAudioFocus()
        _state.value = ArticleTtsState()
        currentRequest = null
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

    private suspend fun speakWithAndroid(
        segments: List<TtsTextSegment>,
        language: String,
        version: Int
    ) {
        val tts = initializeAndroidTts()
        try {
            if (version != playbackVersion) return
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
            if (version != playbackVersion) return
            segments.forEachIndexed { index, segment ->
                resumeSignal.await()
                if (version != playbackVersion) return
                _state.value = _state.value.copy(
                    isPreparing = false,
                    isPlaying = !_state.value.isPaused,
                    currentChunk = index,
                    currentRange = segment.range
                )
                while (!speakChunk(tts, segment, version)) resumeSignal.await()
            }
            if (version != playbackVersion) return
            abandonAudioFocus()
            _state.value = ArticleTtsState()
            currentRequest = null
        } finally {
            if (androidTts === tts) {
                androidTts = null
                tts.runCatching { shutdown() }
            }
        }
    }

    private suspend fun speakChunk(
        tts: TextToSpeech,
        segment: TtsTextSegment,
        version: Int
    ): Boolean =
        suspendCancellableCoroutine { continuation ->
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit

            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                if (id != utteranceId) return
                val localStart = start.coerceIn(0, segment.text.length)
                val localEnd = end.coerceIn(localStart, segment.text.length)
                if (localEnd > localStart) {
                    scope.launch {
                        if (version != playbackVersion || _state.value.articleId == null) return@launch
                        _state.value = _state.value.copy(
                            currentRange = TtsTextRange(
                                segment.range.start + localStart,
                                segment.range.start + localEnd
                            )
                        )
                    }
                }
            }

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
        val result = tts.speak(segment.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
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

    private data class PlaybackRequest(
        val articleId: Long,
        val title: String,
        val html: String,
        val modelOverride: TtsModel?
    )

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
