package com.hiosdra.hreader.core.application.tts

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Tag
import java.text.BreakIterator
import java.util.Locale

private fun Char.isTtsWhitespace(): Boolean = isWhitespace() || Character.isSpaceChar(this)

data class TtsTextRange(
    val start: Int,
    val endExclusive: Int
) {
    init {
        require(start >= 0)
        require(endExclusive >= start)
    }

    fun intersects(other: TtsTextRange): Boolean =
        start < other.endExclusive && other.start < endExclusive
}

data class TtsTextSegment(
    val range: TtsTextRange,
    val text: String
)

data class TtsTextDocument(
    val text: String,
    val segments: List<TtsTextSegment>,
    val titleRange: TtsTextRange?,
    val bodyRange: TtsTextRange?
) {
    fun segmentsFrom(offset: Int): List<TtsTextSegment> {
        if (segments.isEmpty()) return emptyList()
        val target = snapToReadableOffset(offset)
        val firstIndex = segments.indexOfFirst { it.range.endExclusive > target }
        if (firstIndex < 0) return emptyList()
        return segments.drop(firstIndex).mapIndexed { index, segment ->
            if (index != 0 || target <= segment.range.start) {
                segment
            } else {
                val start = target.coerceAtMost(segment.range.endExclusive)
                TtsTextSegment(
                    range = TtsTextRange(start, segment.range.endExclusive),
                    text = text.substring(start, segment.range.endExclusive)
                )
            }
        }
    }

    fun snapToReadableOffset(offset: Int): Int {
        val clamped = offset.coerceIn(0, text.length)
        for (index in clamped until text.length) {
            if (!text[index].isTtsWhitespace()) return index
        }
        return text.length
    }
}

internal object TtsTextDocumentFactory {
    private const val TTS_START_ATTRIBUTE = "data-hreader-tts-start"
    private const val TTS_END_ATTRIBUTE = "data-hreader-tts-end"
    private val collapsibleWhitespace = Regex("[\\s\\p{Z}]+")

    private val excludedTags = setOf(
        "script",
        "style",
        "nav",
        "footer",
        "aside",
        "noscript",
        "figure"
    )

    fun fromHtml(title: String, html: String): TtsTextDocument {
        val body = readableBodyText(html)
        val normalizedTitle = normalize(title)
        val text = listOf(normalizedTitle, body)
            .filter(String::isNotBlank)
            .joinToString(". ")
        val titleRange = normalizedTitle.takeIf(String::isNotBlank)?.let {
            TtsTextRange(0, it.length)
        }
        val bodyRange = body.takeIf(String::isNotBlank)?.let {
            val start = if (normalizedTitle.isBlank()) 0 else normalizedTitle.length + 2
            TtsTextRange(start, start + it.length)
        }
        return TtsTextDocument(
            text = text,
            segments = segments(text),
            titleRange = titleRange,
            bodyRange = bodyRange
        )
    }

    fun fromText(text: String, maxCharacters: Int = 350): List<TtsTextSegment> =
        segments(normalize(text), maxCharacters)

    fun chunks(text: String, maxCharacters: Int = 350): List<String> {
        require(maxCharacters > 0)
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()
        val iterator = BreakIterator.getSentenceInstance(Locale.ROOT).apply { setText(normalized) }
        val result = mutableListOf<String>()
        var buffer = StringBuilder()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = normalized.substring(start, end).trim()
            if (buffer.isNotEmpty() && buffer.length + sentence.length + 1 > maxCharacters) {
                result += buffer.toString()
                buffer = StringBuilder()
            }
            if (sentence.length > maxCharacters) {
                if (buffer.isNotEmpty()) {
                    result += buffer.toString()
                    buffer = StringBuilder()
                }
                result += sentence.chunked(maxCharacters)
            } else {
                if (buffer.isNotEmpty()) buffer.append(' ')
                buffer.append(sentence)
            }
            start = end
            end = iterator.next()
        }
        if (buffer.isNotEmpty()) result += buffer.toString()
        return result
    }

    fun annotateHtml(title: String, html: String): String {
        if (html.isBlank()) return html
        val document = fromHtml(title, html)
        val bodyRange = document.bodyRange ?: return html
        val source = Jsoup.parseBodyFragment(html)
        source.outputSettings().prettyPrint(false)
        source.select("[$TTS_START_ATTRIBUTE], [$TTS_END_ATTRIBUTE]").forEach { element ->
            element.removeAttr(TTS_START_ATTRIBUTE)
            element.removeAttr(TTS_END_ATTRIBUTE)
        }
        val bodyText = readableBodyText(html)
        var searchFrom = 0
        textNodes(source.body()).forEach { textNode ->
            val projection = normalizedWithBoundaries(textNode.wholeText)
            val nodeText = projection.text
            if (nodeText.isBlank()) return@forEach
            val localStart = bodyText.indexOf(nodeText, searchFrom)
            if (localStart < 0) return@forEach
            val localEnd = localStart + nodeText.length
            val nodeStart = bodyRange.start + localStart
            val nodeEnd = bodyRange.start + localEnd
            val replacements = mutableListOf<Node>()
            var rawCursor = 0
            document.segments
                .filter { it.range.start < nodeEnd && it.range.endExclusive > nodeStart }
                .forEach { segment ->
                    val segmentStart = maxOf(segment.range.start, nodeStart) - bodyRange.start - localStart
                    val segmentEnd = minOf(segment.range.endExclusive, nodeEnd) - bodyRange.start - localStart
                    val rawStart = projection.boundaries[segmentStart]
                    val rawEnd = projection.boundaries[segmentEnd]
                    if (rawCursor < rawStart) {
                        replacements.add(TextNode(textNode.wholeText.substring(rawCursor, rawStart)))
                    }
                    if (rawEnd > rawStart) {
                        val marker = Element(Tag.valueOf("span"), textNode.baseUri())
                            .attr(TTS_START_ATTRIBUTE, (bodyRange.start + localStart + segmentStart).toString())
                            .attr(TTS_END_ATTRIBUTE, (bodyRange.start + localStart + segmentEnd).toString())
                        marker.appendChild(
                            TextNode(textNode.wholeText.substring(rawStart, rawEnd))
                        )
                        replacements.add(marker)
                        rawCursor = rawEnd
                    }
            }
            if (rawCursor < textNode.wholeText.length) {
                replacements.add(TextNode(textNode.wholeText.substring(rawCursor)))
            }
            replaceTextNode(textNode, replacements)
            searchFrom = localEnd
        }
        return source.body().html()
    }

    private fun readableBodyText(html: String): String {
        val document = Jsoup.parse(html).apply {
            select(excludedTags.joinToString(", ")).remove()
        }
        return normalize(document.text())
    }

    private fun textNodes(node: Node): List<TextNode> {
        if (node is Element && node.tagName() in excludedTags) return emptyList()
        if (node is TextNode) return listOf(node)
        return node.childNodes().flatMap(::textNodes)
    }

    private fun replaceTextNode(textNode: TextNode, replacements: List<Node>) {
        val first = replacements.firstOrNull() ?: return
        textNode.replaceWith(first)
        var previous = first
        replacements.drop(1).forEach { replacement ->
            previous.after(replacement)
            previous = replacement
        }
    }

    private fun normalizedWithBoundaries(value: String): NormalizedText {
        val normalized = StringBuilder()
        val starts = mutableListOf<Int>()
        val ends = mutableListOf<Int>()
        var index = 0
        while (index < value.length) {
            if (value[index].isTtsWhitespace()) {
                if (normalized.isNotEmpty() && normalized.last() != ' ') {
                    val whitespaceStart = index
                    while (index < value.length && value[index].isTtsWhitespace()) index += 1
                    normalized.append(' ')
                    starts += whitespaceStart
                    ends += index
                } else {
                    while (index < value.length && value[index].isTtsWhitespace()) index += 1
                }
            } else {
                normalized.append(value[index])
                starts += index
                index += 1
                ends += index
            }
        }
        if (normalized.lastOrNull() == ' ') {
            normalized.deleteCharAt(normalized.lastIndex)
            starts.removeAt(starts.lastIndex)
            ends.removeAt(ends.lastIndex)
        }
        val boundaries = IntArray(normalized.length + 1)
        if (normalized.isEmpty()) {
            boundaries[0] = value.length
        } else {
            boundaries[0] = starts.first()
            ends.forEachIndexed { charIndex, rawEnd -> boundaries[charIndex + 1] = rawEnd }
        }
        return NormalizedText(normalized.toString(), boundaries)
    }

    private data class NormalizedText(
        val text: String,
        val boundaries: IntArray
    )

    private fun segments(text: String, maxCharacters: Int = 350): List<TtsTextSegment> {
        require(maxCharacters > 0)
        if (text.isBlank()) return emptyList()
        val iterator = BreakIterator.getSentenceInstance(Locale.ROOT).apply { setText(text) }
        val result = mutableListOf<TtsTextSegment>()
        var sentenceStart = iterator.first()
        var sentenceEnd = iterator.next()
        while (sentenceEnd != BreakIterator.DONE) {
            appendSentence(result, text, sentenceStart, sentenceEnd, maxCharacters)
            sentenceStart = sentenceEnd
            sentenceEnd = iterator.next()
        }
        if (result.isEmpty()) appendSentence(result, text, 0, text.length, maxCharacters)
        return result
    }

    private fun appendSentence(
        result: MutableList<TtsTextSegment>,
        text: String,
        rawStart: Int,
        rawEnd: Int,
        maxCharacters: Int
    ) {
        var start = rawStart
        while (start < rawEnd && text[start].isTtsWhitespace()) start += 1
        var end = rawEnd
        while (end > start && text[end - 1].isTtsWhitespace()) end -= 1
        while (start < end) {
            var candidateEnd = minOf(start + maxCharacters, end)
            if (candidateEnd < end) {
                val lastSpace = text.lastIndexOf(' ', candidateEnd - 1)
                if (lastSpace > start) candidateEnd = lastSpace
            }
            result += TtsTextSegment(
                range = TtsTextRange(start, candidateEnd),
                text = text.substring(start, candidateEnd)
            )
            start = candidateEnd
            while (start < end && text[start].isTtsWhitespace()) start += 1
        }
    }

    private fun normalize(value: String): String =
        value.replace(collapsibleWhitespace, " ").trim()
}
