package com.hiosdra.hreader.adapter.tts.executorch

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ChatterboxTokenizerTest {
    @Test
    fun `normalizes Polish text and preserves language and spaces`() {
        val tokenizer = ChatterboxTokenizer(tokenizerJson)

        assertArrayEquals(
            longArrayOf(12, 4, 3, 4, 5, 6, 7, 8, 9, 7, 2, 4, 3),
            tokenizer.encode("Zażółć za", "pl")
        )
    }

    @Test
    fun `applies BPE merges in rank order`() {
        val tokenizer = ChatterboxTokenizer(tokenizerJson)

        assertArrayEquals(longArrayOf(12, 13), tokenizer.encode("abc", "pl"))
    }

    private companion object {
        val tokenizerJson = """
            {
              "model": {
                "type": "BPE",
                "vocab": {
                  "[STOP]": 0,
                  "[UNK]": 1,
                  "[SPACE]": 2,
                  "a": 3,
                  "z": 4,
                  "̇": 5,
                  "o": 6,
                  "́": 7,
                  "ł": 8,
                  "c": 9,
                  "b": 10,
                  "[pl]": 12,
                  "abc": 13
                },
                "merges": ["a b", "ab c"]
              },
              "added_tokens": [
                {"id": 2, "content": "[SPACE]", "special": true},
                {"id": 12, "content": "[pl]", "special": true}
              ]
            }
        """.trimIndent()
    }
}
