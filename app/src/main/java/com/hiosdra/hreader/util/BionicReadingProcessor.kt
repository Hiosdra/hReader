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
                    result.append(tag)
                    i = tagEnd + 1
                    
                    if (tag.lowercase().startsWith("<pre") || 
                        tag.lowercase().startsWith("<code") ||
                        tag.lowercase().startsWith("<script") ||
                        tag.lowercase().startsWith("<style")) {
                        val closingTag = when {
                            tag.lowercase().startsWith("<pre") -> "</pre>"
                            tag.lowercase().startsWith("<code") -> "</code>" 
                            tag.lowercase().startsWith("<script") -> "</script>"
                            tag.lowercase().startsWith("<style") -> "</style>"
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
        
        return text.replace(Regex("\\b([a-zA-Z]+)\\b")) { matchResult ->
            val word = matchResult.value
            makeBionicWord(word)
        }
    }
    
    private fun makeBionicWord(word: String): String {
        return when (word.length) {
            1, 2 -> "<b>${word[0]}</b>${word.substring(1)}"
            3, 4, 5 -> "<b>${word.substring(0, 2)}</b>${word.substring(2)}"
            else -> "<b>${word.substring(0, 3)}</b>${word.substring(3)}"
        }
    }
}