package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

internal enum class TtsChunkBoundary(val pauseBeforeMillis: Int) {
    START(0),
    HEADING(450),
    PARAGRAPH(320),
    CONTINUATION(120)
}

internal data class TtsChunk(
    val text: String,
    val boundaryBefore: TtsChunkBoundary
)

internal data class TtsArticleText(
    val chunks: List<TtsChunk>,
    val languageSample: String
)

internal object TtsTextProcessor {
    fun fromHtml(title: String, html: String): TtsArticleText {
        val document = Jsoup.parse(html).apply {
            select(REMOVED_SELECTORS).remove()
        }
        val titleBlocks = normalizedBlocks(
            listOf(ReadableBlock(title, TtsChunkBoundary.HEADING))
        )
        val bodyBlocks = normalizedBlocks(readableBlocks(document.body()))
        val languageSource = bodyBlocks.joinToString(" ", transform = ReadableBlock::text)
        val fallbackLanguageSource = titleBlocks.joinToString(" ", transform = ReadableBlock::text)
        return TtsArticleText(
            chunks = chunkBlocks(titleBlocks + bodyBlocks),
            languageSample = stripLanguageNoise(
                languageSource.ifBlank { fallbackLanguageSource }
            )
        )
    }

    fun forModel(
        model: TtsModel,
        sourceChunks: List<TtsChunk>,
        language: String = ""
    ): List<TtsChunk> {
        val normalizer = when {
            model == TtsModel.COQUI_PL_MAI_FEMALE -> PolishTtsTextNormalizer::normalize
            model == TtsModel.SUPERTONIC && language.substringBefore('-').equals("en", true) ->
                EnglishTechnicalTtsTextNormalizer::normalize
            else -> null
        }
        return normalizer?.let { normalize ->
            sourceChunks.flatMap { chunk ->
                chunks(normalize(chunk.text), addTerminalPunctuation = false)
                    .withBoundary(chunk.boundaryBefore)
            }
        } ?: sourceChunks
    }

    private fun normalizedBlocks(blocks: List<ReadableBlock>): List<ReadableBlock> =
        blocks.flatMap { block ->
            normalize(block.text)
                .split(PARAGRAPH_SEPARATOR)
                .filter(String::isNotBlank)
                .map { block.copy(text = it) }
        }

    private fun chunkBlocks(
        blocks: List<ReadableBlock>,
        maxCharacters: Int = DEFAULT_MAX_CHARACTERS
    ): List<TtsChunk> = buildList {
        require(maxCharacters > 0)
        var firstChunk = true
        blocks.forEach { block ->
            chunks(block.text, maxCharacters, addTerminalPunctuation = false)
                .forEachIndexed { index, text ->
                    add(
                        TtsChunk(
                            text = text,
                            boundaryBefore = when {
                                firstChunk -> TtsChunkBoundary.START
                                index == 0 -> block.boundaryBefore
                                else -> TtsChunkBoundary.CONTINUATION
                            }
                        )
                    )
                    firstChunk = false
                }
            }
    }

    private fun List<String>.withBoundary(boundary: TtsChunkBoundary): List<TtsChunk> =
        mapIndexed { index, text ->
            TtsChunk(
                text = text,
                boundaryBefore = if (index == 0) boundary else TtsChunkBoundary.CONTINUATION
            )
        }

    fun chunks(
        text: String,
        maxCharacters: Int = DEFAULT_MAX_CHARACTERS,
        addTerminalPunctuation: Boolean = true
    ): List<String> {
        require(maxCharacters > 0)
        val normalized = normalize(text, addTerminalPunctuation)
        if (normalized.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var buffer = StringBuilder()

        normalized.split(PARAGRAPH_SEPARATOR).filter(String::isNotBlank).forEach { paragraph ->
            val pieces = sentenceParts(paragraph, maxCharacters)
            pieces.forEachIndexed { index, piece ->
                val separator = when {
                    buffer.isEmpty() -> ""
                    index == 0 -> PARAGRAPH_SEPARATOR
                    else -> " "
                }
                if (
                    buffer.isNotEmpty() &&
                    buffer.length + separator.length + piece.length > maxCharacters
                ) {
                    result += buffer.toString()
                    buffer = StringBuilder()
                }
                if (buffer.isNotEmpty()) {
                    buffer.append(if (index == 0) PARAGRAPH_SEPARATOR else ' ')
                }
                buffer.append(piece)
            }
        }
        if (buffer.isNotEmpty()) result += buffer.toString()
        return result
    }

    private fun sentenceParts(paragraph: String, maxCharacters: Int): List<String> {
        val iterator = BreakIterator.getSentenceInstance(Locale.ROOT).apply { setText(paragraph) }
        val sentences = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            paragraph.substring(start, end).trim().takeIf(String::isNotBlank)?.let(sentences::add)
            start = end
            end = iterator.next()
        }
        if (start < paragraph.length) {
            paragraph.substring(start).trim().takeIf(String::isNotBlank)?.let(sentences::add)
        }
        return mergeAbbreviatedSentences(sentences).flatMap { splitLongSentence(it, maxCharacters) }
    }

    private fun mergeAbbreviatedSentences(sentences: List<String>): List<String> {
        val result = mutableListOf<String>()
        sentences.forEach { sentence ->
            val previous = result.lastOrNull()
            if (previous != null && endsWithAbbreviation(previous)) {
                result[result.lastIndex] = "$previous $sentence"
            } else {
                result += sentence
            }
        }
        return result
    }

    private fun splitLongSentence(sentence: String, maxCharacters: Int): List<String> {
        if (sentence.length <= maxCharacters) return listOf(sentence)
        val result = mutableListOf<String>()
        var remaining = sentence
        while (remaining.length > maxCharacters) {
            val splitAt = findSplitIndex(remaining, maxCharacters)
            result += remaining.substring(0, splitAt).trim()
            remaining = remaining.substring(splitAt).trimStart()
        }
        if (remaining.isNotEmpty()) result += remaining
        return result
    }

    private fun findSplitIndex(text: String, maxCharacters: Int): Int {
        val limit = safeCharacterBoundary(text, maxCharacters)
        val punctuation = text.substring(0, limit).indexOfLast { it in ",;:" }
        if (punctuation > 0) return punctuation + 1
        val whitespace = text.lastIndexOf(' ', limit - 1)
        return if (whitespace > 0) whitespace else limit
    }

    private fun safeCharacterBoundary(text: String, index: Int): Int {
        var boundary = index.coerceIn(1, text.length)
        if (boundary < text.length && Character.isLowSurrogate(text[boundary])) boundary--
        return boundary.coerceAtLeast(1)
    }

    private fun readableBlocks(body: Element): List<ReadableBlock> {
        val blocks = mutableListOf<ReadableBlock>()

        fun appendInline(node: Node, output: StringBuilder) {
            when (node) {
                is TextNode -> output.append(node.getWholeText())
                is Element -> {
                    if (node.tagName().equals("br", ignoreCase = true)) {
                        output.append('\n')
                    } else {
                        node.childNodes().forEach { appendInline(it, output) }
                    }
                }
                else -> node.childNodes().forEach { appendInline(it, output) }
            }
        }

        fun visitContainer(container: Node) {
            val looseText = StringBuilder()

            fun flushLooseText() {
                if (looseText.isNotBlank()) {
                    blocks += ReadableBlock(looseText.toString(), TtsChunkBoundary.PARAGRAPH)
                    looseText.clear()
                }
            }

            container.childNodes().forEach { node ->
                if (node is Element) {
                    val tag = node.tagName().lowercase(Locale.ROOT)
                    when {
                        tag in REMOVED_TAGS -> Unit
                        tag == "br" -> looseText.append('\n')
                        tag in BLOCK_ELEMENTS -> {
                            flushLooseText()
                            if (node.children().any {
                                    it.tagName().lowercase(Locale.ROOT) in BLOCK_ELEMENTS
                                }) {
                                visitContainer(node)
                            } else {
                                blocks += ReadableBlock(node.text(), blockBoundary(tag))
                            }
                        }
                        else -> appendInline(node, looseText)
                    }
                } else if (node is TextNode) {
                    looseText.append(node.getWholeText())
                } else {
                    node.childNodes().forEach { appendInline(it, looseText) }
                }
            }
            flushLooseText()
        }

        visitContainer(body)
        return blocks
    }

    private fun blockBoundary(tag: String): TtsChunkBoundary =
        if (tag in HEADING_ELEMENTS) TtsChunkBoundary.HEADING else TtsChunkBoundary.PARAGRAPH

    private data class ReadableBlock(
        val text: String,
        val boundaryBefore: TtsChunkBoundary
    )

    private fun normalize(text: String, addTerminalPunctuation: Boolean = true): String {
        if (text.isBlank()) return ""
        val prepared = removeEmoji(
            Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replace('\u2028', '\n')
                .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
                .replace("…", "...")
                .replace(Regex("[“”„‟]"), "\"")
                .replace(Regex("[‘’‚‛]"), "'")
                .replace('—', ',')
                .replace('–', '-')
                .replace('−', '-')
        )
        return prepared
            .split(Regex("\\n{2,}"))
            .mapNotNull { paragraph ->
                paragraph.replace(Regex("\\s+"), " ").trim()
                    .takeIf(String::isNotBlank)
                    ?.let { paragraphText ->
                        if (addTerminalPunctuation) {
                            ensureTerminalPunctuation(paragraphText)
                        } else {
                            paragraphText
                        }
                    }
            }
            .joinToString(PARAGRAPH_SEPARATOR)
            .replace(Regex("[ \\t]+([,.;:!?])"), "$1")
            .trim()
    }

    private fun ensureTerminalPunctuation(text: String): String {
        if (text.isEmpty()) return text
        return if (TERMINAL_PUNCTUATION.contains(text.last())) text else "$text."
    }

    private fun stripLanguageNoise(text: String): String = text
        .replace(URL_OR_EMAIL, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_LANGUAGE_SAMPLE_LENGTH)

    private fun removeEmoji(text: String): String = buildString {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (codePoint.isEmoji()) append(' ') else appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
        }
    }

    private fun endsWithAbbreviation(text: String): Boolean =
        ABBREVIATION_END.matches(text.trimEnd()) || INITIAL_END.matches(text.trimEnd())

    private fun Int.isEmoji(): Boolean =
        this in 0x1F000..0x1FAFF || this in 0x2600..0x27BF

    private const val DEFAULT_MAX_CHARACTERS = 300
    private const val MAX_LANGUAGE_SAMPLE_LENGTH = 4_000
    private const val PARAGRAPH_SEPARATOR = "\n\n"
    private val TERMINAL_PUNCTUATION = ".!?…"
    private val BLOCK_ELEMENTS = setOf(
        "address", "article", "blockquote", "dd", "div", "dl", "dt", "h1", "h2", "h3",
        "h4", "h5", "h6", "header", "li", "main", "ol", "p", "pre", "section", "table",
        "td", "th", "tr", "ul", "figure", "figcaption"
    )
    private val HEADING_ELEMENTS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val REMOVED_TAGS = setOf("script", "style", "nav", "footer", "aside", "noscript", "form")
    private const val REMOVED_SELECTORS =
        "script, style, nav, footer, aside, noscript, form, img, svg, video, audio, " +
            "#comments, .comments, #comment, .comment, #comment-section, .comment-section, " +
            "#disqus_thread, .disqus_thread, #respond, .respond, [id*=comments], " +
            "[class*=comments], [id*=disqus], [class*=disqus]"
    private val ABBREVIATION_END = Regex(
        "(?i)(?:\\b(?:mr|mrs|ms|dr|prof|sr|jr|st|e\\.g|i\\.e|etc|np|tj|itd|itp|m\\.in|tzw)\\.)$"
    )
    private val INITIAL_END = Regex("\\b[A-ZĄĆĘŁŃÓŚŹŻ]\\.$")
    private val URL_OR_EMAIL = Regex(
        "(?i)(?:https?://|www\\.)\\S+|[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}"
    )
}
