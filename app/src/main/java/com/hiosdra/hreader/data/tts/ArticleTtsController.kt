package com.hiosdra.hreader.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private val _state = MutableStateFlow(ArticleTtsState())
    val state: StateFlow<ArticleTtsState> = _state.asStateFlow()
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var androidTts: TextToSpeech? = null

    fun play(articleId: Long, title: String, html: String) {
        stop()
        val chunks = TtsTextProcessor.fromHtml(title, html)
        if (chunks.isEmpty()) {
            _state.value = ArticleTtsState(error = "There is no article text to read.")
            return
        }
        playbackJob = scope.launch {
            val requestedModel = preferences.getTtsModel()
            val available = modelManager.statuses.value[requestedModel] == TtsModelStatus.Available
            val model = if (available && Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")) {
                requestedModel
            } else {
                TtsModel.ANDROID
            }
            _state.value = ArticleTtsState(
                articleId = articleId,
                title = title,
                model = model,
                isPreparing = true,
                totalChunks = chunks.size
            )
            runCatching {
                if (model == TtsModel.ANDROID) {
                    speakWithAndroid(chunks)
                } else {
                    speakWithSherpa(model, chunks)
                }
            }.onFailure {
                if (it !is CancellationException && model != TtsModel.ANDROID) {
                    _state.value = _state.value.copy(
                        model = TtsModel.ANDROID,
                        isPreparing = true,
                        error = "Neural voice unavailable; using Android TTS."
                    )
                    runCatching { speakWithAndroid(chunks) }
                } else if (it !is CancellationException) {
                    _state.value = _state.value.copy(
                        isPreparing = false,
                        isPlaying = false,
                        error = it.message ?: "Speech playback failed."
                    )
                }
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.runCatching { stop() }
        audioTrack?.release()
        audioTrack = null
        androidTts?.stop()
        sherpa.release()
        _state.value = ArticleTtsState()
    }

    private suspend fun speakWithSherpa(model: TtsModel, chunks: List<String>) {
        chunks.forEachIndexed { index, chunk ->
            val audio = withContext(Dispatchers.Default) {
                sherpa.generate(model, chunk, preferences.getTtsSpeed())
            }
            _state.value = _state.value.copy(
                isPreparing = false,
                isPlaying = true,
                currentChunk = index
            )
            playSamples(audio.samples, audio.sampleRate)
        }
        _state.value = ArticleTtsState()
    }

    private suspend fun playSamples(samples: FloatArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
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
            .setBufferSizeInBytes(minBuffer.coerceAtLeast(sampleRate))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        audioTrack = track
        track.play()
        var offset = 0
        while (offset < samples.size) {
            val written = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
            check(written >= 0) { "Audio playback failed ($written)" }
            offset += written
        }
        track.stop()
        track.release()
        audioTrack = null
    }

    private suspend fun speakWithAndroid(chunks: List<String>) {
        val tts = initializeAndroidTts()
        tts.setSpeechRate(preferences.getTtsSpeed())
        tts.language = if (preferences.getTtsModel() == TtsModel.GOSIA) {
            Locale.forLanguageTag("pl-PL")
        } else {
            Locale.getDefault()
        }
        chunks.forEachIndexed { index, chunk ->
            _state.value = _state.value.copy(
                isPreparing = false,
                isPlaying = true,
                currentChunk = index
            )
            speakChunk(tts, chunk)
        }
        _state.value = ArticleTtsState()
    }

    private suspend fun speakChunk(tts: TextToSpeech, text: String) = suspendCancellableCoroutine { continuation ->
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit

            override fun onDone(id: String?) {
                if (id == utteranceId && continuation.isActive) continuation.resume(Unit)
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
}
