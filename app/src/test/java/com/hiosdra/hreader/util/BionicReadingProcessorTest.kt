package com.hiosdra.hreader.util

import org.junit.Test
import org.junit.Assert.*

class BionicReadingProcessorTest {
    
    @Test
    fun `test basic word processing`() {
        val result = BionicReadingProcessor.processTextToBionic("hello world")
        assertEquals("<b>he</b>llo <b>wo</b>rld", result)
    }
    
    @Test
    fun `test short words`() {
        val result = BionicReadingProcessor.processTextToBionic("a to be or")
        assertEquals("<b>a</b> <b>t</b>o <b>b</b>e <b>o</b>r", result)
    }
    
    @Test
    fun `test longer words`() {
        val result = BionicReadingProcessor.processTextToBionic("reading comprehension")
        assertEquals("<b>rea</b>ding <b>com</b>prehension", result)
    }
    
    @Test
    fun `test HTML tags are preserved`() {
        val result = BionicReadingProcessor.processTextToBionic("<p>hello <strong>world</strong></p>")
        assertEquals("<p><b>he</b>llo <strong><b>wo</b>rld</strong></p>", result)
    }
    
    @Test
    fun `test code blocks are not processed`() {
        val result = BionicReadingProcessor.processTextToBionic("<pre>function hello() { return world; }</pre>")
        assertEquals("<pre>function hello() { return world; }</pre>", result)
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