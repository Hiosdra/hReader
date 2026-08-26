package com.hiosdra.hreader.adapter.tts.executorch

import com.hiosdra.hreader.adapter.tts.NeuralTtsEngine
import com.hiosdra.hreader.adapter.tts.ChatterboxModelFiles
import com.hiosdra.hreader.adapter.tts.TtsAudio
import com.hiosdra.hreader.adapter.tts.TtsModelManager
import com.hiosdra.hreader.adapter.tts.TtsModelPackageCatalog
import com.hiosdra.hreader.adapter.tts.isComplete
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog
import org.pytorch.executorch.Tensor
import java.io.File
import java.util.Locale
import java.util.Random
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

internal class ChatterboxTtsEngine(
    private val modelManager: TtsModelManager
) : NeuralTtsEngine {
    private val runtime = ChatterboxExecuTorchRuntime()
    private var loadedRoot: File? = null
    private var loadedThreads: Int? = null
    private var conditionals: ChatterboxConditionals? = null
    private var tokenizer: ChatterboxTokenizer? = null

    override val supportedModels: Set<TtsModel> = TtsModelCatalog.models
        .filter { it == TtsModel.CHATTERBOX_EXECUTORCH }
        .filter { TtsModelPackageCatalog.packageFor(it) != null }
        .toSet()

    @Synchronized
    override fun prepare(model: TtsModel, settings: TtsAdvancedSettings) {
        check(model in supportedModels) { "Unsupported Chatterbox model: ${model.name}" }
        val root = modelManager.directory(model).absoluteFile
        val modelPackage = checkNotNull(TtsModelPackageCatalog.packageFor(model)) {
            "No model package registered for ${model.name}"
        }
        check(modelPackage.isComplete(root)) { "Chatterbox model is not installed" }
        val files = modelPackage.engineFiles as? ChatterboxModelFiles
            ?: error("Chatterbox model package has an incompatible engine configuration")
        if (loadedRoot != root) {
            conditionals = ChatterboxConditionals.load(File(root, files.conditionals))
            tokenizer = ChatterboxTokenizer(File(root, files.tokenizer))
        }
        if (loadedRoot != root || loadedThreads != settings.numThreads) {
            runtime.load(root, settings.numThreads)
            loadedRoot = root
            loadedThreads = settings.numThreads
        }
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
        val activeConditionals = checkNotNull(conditionals)
        val activeTokenizer = checkNotNull(tokenizer)
        val languageCode = language.lowercase(Locale.ROOT).substringBefore('-')
        val normalizedText = normalizeText(text)
        val random = Random(seedFor(normalizedText, languageCode))
        return runtime.withModules { modules ->
            val speechTokens = generateSpeechTokens(
                modules = modules,
                tokenizer = activeTokenizer,
                conditionals = activeConditionals,
                text = normalizedText,
                language = languageCode,
                random = random
            )
            val mel = generateMel(
                modules = modules,
                conditionals = activeConditionals,
                speechTokens = speechTokens,
                random = random
            )
            val samples = generateWaveform(modules, mel, random)
            TtsAudio(
                samples = adjustSpeed(samples, speed),
                sampleRate = ChatterboxExecuTorchModel.sampleRate
            )
        }
    }

    @Synchronized
    override fun release() {
        runtime.close()
        loadedRoot = null
        loadedThreads = null
        conditionals = null
        tokenizer = null
    }

    private fun generateSpeechTokens(
        modules: Map<ChatterboxExecuTorchModule, org.pytorch.executorch.Module>,
        tokenizer: ChatterboxTokenizer,
        conditionals: ChatterboxConditionals,
        text: String,
        language: String,
        random: Random
    ): List<Int> {
        val textTokens = LongArray(ChatterboxExecuTorchModel.textSequenceLength)
        textTokens[0] = ChatterboxExecuTorchModel.textStartToken.toLong()
        val encoded = tokenizer.encode(text, language)
        val textLength = min(encoded.size, ChatterboxExecuTorchModel.maxTextTokens)
        System.arraycopy(encoded, 0, textTokens, 1, textLength)
        textTokens[ChatterboxExecuTorchModel.textSequenceLength - 1] = 0L

        val conditioningTokens = LongArray(ChatterboxExecuTorchModel.conditioningSpeechTokens)
        System.arraycopy(
            conditionals.conditioningSpeechTokens,
            0,
            conditioningTokens,
            0,
            min(conditionals.conditioningSpeechTokens.size, conditioningTokens.size)
        )
        val conditionSpeechEmbedding = runtime.forward(
            modules.getValue(ChatterboxExecuTorchModule.T3_COND_SPEECH_EMB),
            longTensor(conditioningTokens, longArrayOf(1, conditioningTokens.size.toLong()))
        ).first()
        val conditionEmbedding = runtime.forward(
            modules.getValue(ChatterboxExecuTorchModule.T3_COND_ENCODER),
            floatTensor(conditionals.speakerEmbedding, longArrayOf(1, conditionals.speakerEmbedding.size.toLong())),
            conditionSpeechEmbedding,
            floatTensor(floatArrayOf(0.5f), longArrayOf(1, 1, 1))
        ).first()
        val prefill = runtime.forward(
            modules.getValue(ChatterboxExecuTorchModule.T3_PREFILL),
            halfTensor(conditionEmbedding.floatValues(), conditionEmbedding.shape()),
            longTensor(textTokens, longArrayOf(1, textTokens.size.toLong()))
        )
        check(prefill.size >= 2) { "Chatterbox prefill returned too few outputs" }
        var logits = prefill[0].floatValues()
        var kvCache = splitKv(prefill[1])
        val speechTokens = ArrayList<Int>()
        for (step in 0 until ChatterboxExecuTorchModel.maxGeneratedSpeechTokens) {
            val token = sampleToken(logits, speechTokens, random)
            if (token == ChatterboxExecuTorchModel.speechEndToken) break
            speechTokens += token
            val decoded = runtime.forward(
                modules.getValue(ChatterboxExecuTorchModule.T3_DECODE),
                longTensor(longArrayOf(token.toLong()), longArrayOf(1, 1)),
                scalarLongTensor((step + 1).toLong()),
                kvCache.key,
                kvCache.value
            )
            check(decoded.size >= 3) { "Chatterbox decode returned too few outputs" }
            logits = decoded[0].floatValues()
            kvCache = KvCache(decoded[1], decoded[2])
        }
        check(speechTokens.isNotEmpty()) { "Chatterbox generated no speech tokens" }
        return speechTokens
    }

    private fun generateMel(
        modules: Map<ChatterboxExecuTorchModule, org.pytorch.executorch.Module>,
        conditionals: ChatterboxConditionals,
        speechTokens: List<Int>,
        random: Random
    ): MelData {
        val speechTokenValues = LongArray(ChatterboxExecuTorchModel.maxSpeechTokens)
        speechTokens.forEachIndexed { index, token -> speechTokenValues[index] = token.toLong() }
        val promptTokenValues = LongArray(PROMPT_TOKEN_LENGTH)
        System.arraycopy(
            conditionals.promptTokens,
            0,
            promptTokenValues,
            0,
            min(conditionals.promptTokens.size, promptTokenValues.size)
        )
        val promptTokenLength = conditionals.promptTokenLength
            .coerceIn(0, PROMPT_TOKEN_LENGTH.toLong())
        val encoded = runtime.forward(
            modules.getValue(ChatterboxExecuTorchModule.S3GEN_ENCODER),
            longTensor(speechTokenValues, longArrayOf(1, speechTokenValues.size.toLong())),
            longTensor(longArrayOf(speechTokens.size.toLong()), longArrayOf(1)),
            longTensor(promptTokenValues, longArrayOf(1, promptTokenValues.size.toLong())),
            longTensor(longArrayOf(promptTokenLength), longArrayOf(1)),
            floatTensor(conditionals.xVector, longArrayOf(1, conditionals.xVector.size.toLong()))
        )
        check(encoded.size >= 4) { "Chatterbox S3Gen encoder returned too few outputs" }
        val h = encoded[0]
        val hValues = h.floatValues()
        val hShape = h.shape()
        check(hShape.size == 3 && hShape[0] == 1L && hShape[2] == MEL_CHANNELS.toLong()) {
            "Unexpected Chatterbox S3Gen shape: ${hShape.contentToString()}"
        }
        val hFrames = hShape[1].toInt()
        val hLength = encoded[1].longValues().first().toInt()
            .coerceIn(0, min(hFrames, ChatterboxExecuTorchModel.cfmMelLength))
        val promptMelLength = encoded[3].longValues().first().toInt()
            .coerceIn(0, ChatterboxExecuTorchModel.cfmMelLength)
        val embedding = encoded[2].floatValues()
        val cfmSize = MEL_CHANNELS * ChatterboxExecuTorchModel.cfmMelLength
        val hPadded = FloatArray(cfmSize)
        for (frame in 0 until min(hLength, ChatterboxExecuTorchModel.cfmMelLength)) {
            for (channel in 0 until MEL_CHANNELS) {
                hPadded[channel * ChatterboxExecuTorchModel.cfmMelLength + frame] =
                    hValues[frame * MEL_CHANNELS + channel]
            }
        }
        val condMel = FloatArray(cfmSize)
        val promptFrames = min(promptMelLength, conditionals.promptFeatureFrames)
        for (frame in 0 until promptFrames) {
            for (channel in 0 until MEL_CHANNELS) {
                condMel[channel * ChatterboxExecuTorchModel.cfmMelLength + frame] =
                    conditionals.promptFeatures[frame * MEL_CHANNELS + channel]
            }
        }
        val mask = FloatArray(ChatterboxExecuTorchModel.cfmMelLength)
        java.util.Arrays.fill(mask, 0, hLength.coerceAtMost(mask.size), 1f)
        val z = FloatArray(cfmSize) { random.nextGaussian().toFloat() }
        val cfmModule = modules.getValue(ChatterboxExecuTorchModule.CFM_STEP)
        val zeroEmbedding = FloatArray(embedding.size)
        val zeroMel = FloatArray(cfmSize)
        val zeroH = FloatArray(cfmSize)
        repeat(2) { step ->
            val x = duplicateBatch(z)
            val batchMask = duplicateBatch(mask)
            val batchH = concatBatch(hPadded, zeroH)
            val batchEmbedding = concatBatch(embedding, zeroEmbedding)
            val batchCond = concatBatch(condMel, zeroMel)
            val time = if (step == 0) 0f else 0.5f
            val endTime = if (step == 0) 0.5f else 1f
            val output = runtime.forward(
                cfmModule,
                floatTensor(x, longArrayOf(2, MEL_CHANNELS.toLong(), ChatterboxExecuTorchModel.cfmMelLength.toLong())),
                floatTensor(batchMask, longArrayOf(2, 1, ChatterboxExecuTorchModel.cfmMelLength.toLong())),
                floatTensor(batchH, longArrayOf(2, MEL_CHANNELS.toLong(), ChatterboxExecuTorchModel.cfmMelLength.toLong())),
                floatTensor(floatArrayOf(time, time), longArrayOf(2)),
                floatTensor(batchEmbedding, longArrayOf(2, embedding.size.toLong())),
                floatTensor(batchCond, longArrayOf(2, MEL_CHANNELS.toLong(), ChatterboxExecuTorchModel.cfmMelLength.toLong())),
                floatTensor(floatArrayOf(endTime, endTime), longArrayOf(2))
            ).first().floatValues()
            check(output.size >= cfmSize * 2) { "Chatterbox CFM returned too few samples" }
            for (index in z.indices) {
                z[index] += 0.5f * (1.7f * output[index] - 0.7f * output[cfmSize + index])
            }
        }
        val speechFrames = (hLength - promptMelLength).coerceAtLeast(0)
        check(speechFrames > 0) { "Chatterbox generated an empty mel sequence" }
        val mel = FloatArray(MEL_CHANNELS * speechFrames)
        for (channel in 0 until MEL_CHANNELS) {
            System.arraycopy(
                z,
                channel * ChatterboxExecuTorchModel.cfmMelLength + promptMelLength,
                mel,
                channel * speechFrames,
                speechFrames
            )
        }
        return MelData(mel, speechFrames)
    }

    private fun generateWaveform(
        modules: Map<ChatterboxExecuTorchModule, org.pytorch.executorch.Module>,
        mel: MelData,
        random: Random
    ): FloatArray {
        val hifiModule = modules.getValue(ChatterboxExecuTorchModule.HIFIGAN)
        val result = FloatArray(mel.frames * ChatterboxExecuTorchModel.hifiUpsample)
        val chunks = ceil(mel.frames / ChatterboxExecuTorchModel.hifiMelLength.toDouble()).toInt()
        val chunkMel = MEL_CHANNELS * ChatterboxExecuTorchModel.hifiMelLength
        val chunkAudio = ChatterboxExecuTorchModel.hifiMelLength * ChatterboxExecuTorchModel.hifiUpsample
        for (chunk in 0 until chunks) {
            val frameOffset = chunk * ChatterboxExecuTorchModel.hifiMelLength
            val frameCount = min(
                ChatterboxExecuTorchModel.hifiMelLength,
                mel.frames - frameOffset
            )
            val melInput = FloatArray(chunkMel)
            for (channel in 0 until MEL_CHANNELS) {
                System.arraycopy(
                    mel.values,
                    channel * mel.frames + frameOffset,
                    melInput,
                    channel * ChatterboxExecuTorchModel.hifiMelLength,
                    frameCount
                )
            }
            val noise = FloatArray(ChatterboxExecuTorchModel.hifiHarmonics * chunkAudio) {
                random.nextGaussian().toFloat()
            }
            val phase = FloatArray(ChatterboxExecuTorchModel.hifiHarmonics)
            val output = runtime.forward(
                hifiModule,
                floatTensor(melInput, longArrayOf(1, MEL_CHANNELS.toLong(), ChatterboxExecuTorchModel.hifiMelLength.toLong())),
                floatTensor(
                    noise,
                    longArrayOf(1, ChatterboxExecuTorchModel.hifiHarmonics.toLong(), chunkAudio.toLong())
                ),
                floatTensor(phase, longArrayOf(1, ChatterboxExecuTorchModel.hifiHarmonics.toLong(), 1))
            ).first().floatValues()
            val outputOffset = frameOffset * ChatterboxExecuTorchModel.hifiUpsample
            val sampleCount = frameCount * ChatterboxExecuTorchModel.hifiUpsample
            check(output.size >= sampleCount) { "Chatterbox HiFiGAN returned too few samples" }
            System.arraycopy(output, 0, result, outputOffset, sampleCount)
        }
        return result
    }

    private fun splitKv(tensor: Tensor): KvCache {
        check(tensor.dtype() == org.pytorch.executorch.DType.HALF) {
            "Expected a FP16 Chatterbox KV cache"
        }
        val values = tensor.getDataAsShortArray()
        val partSize = ChatterboxExecuTorchModel.layers * ChatterboxExecuTorchModel.heads *
            ChatterboxExecuTorchModel.maxKvLength * ChatterboxExecuTorchModel.headDimension
        check(values.size == partSize * 2) { "Unexpected Chatterbox KV cache size: ${values.size}" }
        val keyBuffer = Tensor.allocateHalfBuffer(partSize)
        val valueBuffer = Tensor.allocateHalfBuffer(partSize)
        keyBuffer.put(values, 0, partSize).position(0)
        valueBuffer.put(values, partSize, partSize).position(0)
        return KvCache(
            key = Tensor.fromBlob(keyBuffer, ChatterboxExecuTorchModel.kvShape),
            value = Tensor.fromBlob(valueBuffer, ChatterboxExecuTorchModel.kvShape),
            buffers = listOf(keyBuffer, valueBuffer)
        )
    }

    private fun sampleToken(logits: FloatArray, previous: List<Int>, random: Random): Int {
        val adjusted = logits.copyOf()
        previous.takeLast(8).toSet().forEach { token ->
            if (token !in adjusted.indices) return@forEach
            adjusted[token] = if (adjusted[token] > 0f) {
                adjusted[token] / REPETITION_PENALTY
            } else {
                adjusted[token] * REPETITION_PENALTY
            }
        }
        val scaled = FloatArray(adjusted.size)
        var maximum = Float.NEGATIVE_INFINITY
        for (index in adjusted.indices) {
            scaled[index] = adjusted[index] / TEMPERATURE
            if (scaled[index] > maximum) maximum = scaled[index]
        }
        val exponentials = DoubleArray(scaled.size)
        var top = 0.0
        for (index in scaled.indices) {
            val probability = if (scaled[index].isFinite()) {
                exp((scaled[index] - maximum).toDouble())
            } else {
                0.0
            }
            exponentials[index] = probability
            if (probability > top) top = probability
        }
        val minimum = top * MIN_P
        var filteredTotal = 0.0
        for (probability in exponentials) if (probability >= minimum) filteredTotal += probability
        if (filteredTotal <= 0.0) return scaled.indices.maxByOrNull { scaled[it] } ?: 0
        var target = random.nextDouble() * filteredTotal
        for (index in exponentials.indices) {
            if (exponentials[index] < minimum) continue
            target -= exponentials[index]
            if (target <= 0.0) return index
        }
        return exponentials.indices.lastOrNull { exponentials[it] >= minimum } ?: 0
    }

    private fun adjustSpeed(samples: FloatArray, speed: Float): FloatArray {
        val safeSpeed = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
        if (safeSpeed == 1f || samples.isEmpty()) return samples
        val output = FloatArray(max(1, ceil(samples.size / safeSpeed).toInt()))
        output.indices.forEach { index ->
            val source = index * safeSpeed
            val lower = source.toInt().coerceAtMost(samples.lastIndex)
            val upper = (lower + 1).coerceAtMost(samples.lastIndex)
            val fraction = source - lower
            output[index] = samples[lower] + (samples[upper] - samples[lower]) * fraction
        }
        return output
    }

    private fun normalizeText(text: String): String {
        if (text.isEmpty()) return "You need to add some text for me to talk."
        var normalized = text
        if (normalized.first().isLowerCase()) {
            normalized = normalized.replaceRange(0, 1, normalized.first().uppercase())
        }
        normalized = normalized.trim().split(WHITESPACE).joinToString(" ")
        PUNCTUATION_REPLACEMENTS.forEach { (old, new) ->
            normalized = normalized.replace(old, new)
        }
        normalized = normalized.trimEnd(' ')
        if (normalized.isEmpty() || normalized.last() !in SENTENCE_ENDERS) {
            normalized += "."
        }
        return normalized
    }

    private fun seedFor(text: String, language: String): Long =
        text.fold(language.hashCode().toLong()) { seed, character -> seed * 31 + character.code }

    private data class KvCache(
        val key: Tensor,
        val value: Tensor,
        val buffers: List<java.nio.ShortBuffer> = emptyList()
    )

    private data class MelData(
        val values: FloatArray,
        val frames: Int
    )

    private companion object {
        const val MEL_CHANNELS = 80
        const val PROMPT_TOKEN_LENGTH = 75
        const val TEMPERATURE = 0.8f
        const val MIN_P = 0.05
        const val REPETITION_PENALTY = 2f
        val WHITESPACE = Regex("\\s+")
        val PUNCTUATION_REPLACEMENTS = listOf(
            "..." to ", ",
            "…" to ", ",
            ":" to ",",
            " - " to ", ",
            ";" to ", ",
            "—" to "-",
            "–" to "-",
            " ," to ",",
            "“" to "\"",
            "”" to "\"",
            "‘" to "'",
            "’" to "'"
        )
        val SENTENCE_ENDERS = setOf('.', '!', '?', '-', ',', '、', '，', '。', '？', '！')
    }
}

private fun duplicateBatch(values: FloatArray): FloatArray =
    FloatArray(values.size * 2).also {
        System.arraycopy(values, 0, it, 0, values.size)
        System.arraycopy(values, 0, it, values.size, values.size)
    }

private fun concatBatch(first: FloatArray, second: FloatArray): FloatArray =
    FloatArray(first.size + second.size).also {
        System.arraycopy(first, 0, it, 0, first.size)
        System.arraycopy(second, 0, it, first.size, second.size)
    }
