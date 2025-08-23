package com.hiosdra.hreader.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

object BionicReadingProcessor {

    fun processTextToBionic(html: String): String {
        if (html.isBlank()) return html
        if (!html.contains('<')) return processBionicText(html)
        val doc = Jsoup.parseBodyFragment(html)
        doc.outputSettings().prettyPrint(false)
        traverse(doc.body())
        return doc.body().html()
    }

    private fun traverse(element: Element) {
        val children = element.childNodes().toList()
        for (child in children) {
            when (child) {
                is TextNode -> {
                    val parentTag = child.parent()?.nodeName()?.lowercase()
                    if (parentTag !in setOf("pre", "code", "script", "style", "svg")) {
                        val processed = processBionicText(child.wholeText)
                        if (processed != child.wholeText) {
                            child.after(processed)
                            child.remove()
                        }
                    }
                }
                is Element -> traverse(child)
            }
        }
    }

    private fun processBionicText(text: String): String {
        if (text.isBlank()) return text
        return text.replace(Regex("([\\p{L}\\p{M}]+)")) { match ->
            makeBionicWord(match.value)
        }
    }

    private fun makeBionicWord(wordRaw: String): String = run {
        val word = java.text.Normalizer.normalize(wordRaw, java.text.Normalizer.Form.NFC)
        when (word.length) {
            0, 1 -> word
            2 -> "<strong>${word[0]}</strong>${word.substring(1)}"
            in 3..5 -> "<strong>${word.substring(0, 2)}</strong>${word.substring(2)}"
            else -> "<strong>${word.substring(0, 3)}</strong>${word.substring(3)}"
        }
    }
}
