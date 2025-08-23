package com.hiosdra.hreader.util

object BionicReadingProcessor {
    
    fun processTextToBionic(html: String): String {
        if (html.isBlank()) return html
        
        return processHtmlNodes(html)
    }
    
    private fun processHtmlNodes(html: String): String {
        val result = StringBuilder()
        var i = 0
        
        while (i < html.length) {
            if (html[i] == '<') {
                val tagEnd = html.indexOf('>', i)
                if (tagEnd != -1) {
                    val tag = html.substring(i, tagEnd + 1)
                    val tagLower = tag.lowercase()
                    result.append(tag)
                    i = tagEnd + 1
                    
                    if (tagLower.startsWith("<pre") || 
                        tagLower.startsWith("<code") ||
                        tagLower.startsWith("<script") ||
                        tagLower.startsWith("<style") ||
                        tagLower.startsWith("<svg")) {
                        val closingTag = when {
                            tagLower.startsWith("<pre") -> "</pre>"
                            tagLower.startsWith("<code") -> "</code>" 
                            tagLower.startsWith("<script") -> "</script>"
                            tagLower.startsWith("<style") -> "</style>"
                            tagLower.startsWith("<svg") -> "</svg>"
                            else -> ""
                        }
                        if (closingTag.isNotEmpty()) {
                            val closingIndex = html.indexOf(closingTag, i, ignoreCase = true)
                            if (closingIndex != -1) {
                                result.append(html.substring(i, closingIndex + closingTag.length))
                                i = closingIndex + closingTag.length
                                continue
                            }
                        }
                    }
                } else {
                    result.append(html[i])
                    i++
                }
            } else {
                val nextTag = html.indexOf('<', i)
                val textEnd = if (nextTag == -1) html.length else nextTag
                val textSegment = html.substring(i, textEnd)
                result.append(processBionicText(textSegment))
                i = textEnd
            }
        }
        
        return result.toString()
    }
    
    private fun processBionicText(text: String): String {
        if (text.isBlank()) return text
        
        return text.replace(Regex("\\b([\\p{L}]+)\\b")) { matchResult ->
            val word = matchResult.value
            makeBionicWord(word)
        }
    }
    
    private fun makeBionicWord(word: String): String {
        if (word.length < 2) return word
        
        return when (word.length) {
            2 -> "<strong>${word[0]}</strong>${word.substring(1)}"
            3, 4, 5 -> "<strong>${word.substring(0, 2)}</strong>${word.substring(2)}"
            else -> "<strong>${word.substring(0, 3)}</strong>${word.substring(3)}"
        }
    }
}