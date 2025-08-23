package com.hiosdra.hreader.util

import org.junit.Test
import org.junit.Assert.*

class BionicReadingProcessorTest {
    
    @Test
    fun `test basic word processing`() {
        val result = BionicReadingProcessor.processTextToBionic("hello world")
        assertEquals("<strong>he</strong>llo <strong>wo</strong>rld", result)
    }
    
    @Test
    fun `test short words`() {
        val result = BionicReadingProcessor.processTextToBionic("a to be or")
        assertEquals("a <strong>t</strong>o <strong>b</strong>e <strong>o</strong>r", result)
    }
    
    @Test
    fun `test longer words`() {
        val result = BionicReadingProcessor.processTextToBionic("reading comprehension")
        assertEquals("<strong>rea</strong>ding <strong>com</strong>prehension", result)
    }
    
    @Test
    fun `test HTML tags are preserved`() {
        val result = BionicReadingProcessor.processTextToBionic("<p>hello <strong>world</strong></p>")
        assertEquals("<p><strong>he</strong>llo <strong><strong>wo</strong>rld</strong></p>", result)
    }
    
    @Test
    fun `test code blocks are not processed`() {
        val result = BionicReadingProcessor.processTextToBionic("<pre>function hello() { return world; }</pre>")
        assertEquals("<pre>function hello() { return world; }</pre>", result)
    }
    
    @Test
    fun `test SVG elements are not processed`() {
        val result = BionicReadingProcessor.processTextToBionic("<svg>hello world</svg>")
        assertEquals("<svg>hello world</svg>", result)
    }
    
    @Test
    fun `test unicode characters`() {
        val result = BionicReadingProcessor.processTextToBionic("café résumé naïve")
        assertEquals("<strong>ca</strong>fé <strong>rés</strong>umé <strong>na</strong>ïve", result)
    }
    
    @Test
    fun `test mixed content with numbers and punctuation`() {
        val result = BionicReadingProcessor.processTextToBionic("Hello, world! This costs $5.99.")
        assertEquals("<strong>He</strong>llo, <strong>wo</strong>rld! <strong>Th</strong>is <strong>co</strong>sts $5.99.", result)
    }
    
    @Test
    fun `test single character words are not processed`() {
        val result = BionicReadingProcessor.processTextToBionic("I am a person")
        assertEquals("I <strong>a</strong>m a <strong>per</strong>son", result)
    }
    
    @Test
    fun `test empty string`() {
        val result = BionicReadingProcessor.processTextToBionic("")
        assertEquals("", result)
    }
    
    @Test
    fun `test whitespace only`() {
        val result = BionicReadingProcessor.processTextToBionic("   ")
        assertEquals("   ", result)
    }
}