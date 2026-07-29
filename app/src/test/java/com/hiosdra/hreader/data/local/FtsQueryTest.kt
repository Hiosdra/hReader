package com.hiosdra.hreader.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `every token searches as a prefix`() {
        assertEquals("clim* chan*", buildFtsMatchQuery("clim chan"))
    }

    @Test
    fun `strips the punctuation FTS would read as syntax`() {
        assertEquals("don* t* panic*", buildFtsMatchQuery("don't panic!"))
        assertEquals("cost*", buildFtsMatchQuery("-cost"))
        assertEquals("a* b*", buildFtsMatchQuery("\"a\" (b)"))
    }

    @Test
    fun `operators are searched for as words`() {
        assertEquals("cats* AND* dogs*", buildFtsMatchQuery("cats AND dogs"))
    }

    @Test
    fun `keeps letters outside ascii`() {
        assertEquals("zażółć* gęślą*", buildFtsMatchQuery("zażółć gęślą"))
    }

    @Test
    fun `nothing searchable means no search`() {
        assertNull(buildFtsMatchQuery(""))
        assertNull(buildFtsMatchQuery("   "))
        assertNull(buildFtsMatchQuery("--- \"\" ()"))
    }

    @Test
    fun `like pattern is lowercased and wrapped`() {
        assertEquals("%the verge%", buildLikePattern("  The Verge  "))
    }
}
