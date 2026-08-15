package com.hiosdra.hreader.data.opml

import com.hiosdra.hreader.data.model.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlTest {

    @Test
    fun `flattens the category tree an export nests`() {
        val xml = """
            <opml version="2.0"><body>
              <outline text="News">
                <outline type="rss" text="The Verge" xmlUrl="https://verge.com/rss" htmlUrl="https://verge.com"/>
              </outline>
              <outline type="rss" title="LWN" xmlUrl="https://lwn.net/rss"/>
            </body></opml>
        """.trimIndent()

        val feeds = parseOpml(xml)

        assertEquals(listOf("https://verge.com/rss", "https://lwn.net/rss"), feeds.map { it.feedUrl })
        assertEquals(listOf("The Verge", "LWN"), feeds.map { it.title })
        assertEquals("https://verge.com", feeds.first().siteUrl)
    }

    @Test
    fun `folders without a feed address are not subscriptions`() {
        val xml = """<opml><body><outline text="Just a folder"/></body></opml>"""

        assertTrue(parseOpml(xml).isEmpty())
    }

    @Test
    fun `falls back to the address when the file names nothing`() {
        val xml = """<opml><body><outline xmlUrl="https://example.com/feed"/></body></opml>"""

        assertEquals("https://example.com/feed", parseOpml(xml).single().title)
    }

    @Test
    fun `the same feed listed twice is subscribed to once`() {
        val xml = """
            <opml><body>
              <outline type="rss" text="A" xmlUrl="https://example.com/feed"/>
              <outline type="rss" text="A again" xmlUrl="https://example.com/feed"/>
            </body></opml>
        """.trimIndent()

        assertEquals(1, parseOpml(xml).size)
    }

    @Test
    fun `rubbish in gives nothing out rather than an exception`() {
        assertTrue(parseOpml("").isEmpty())
        assertTrue(parseOpml("not xml at all").isEmpty())
    }

    @Test
    fun `what is written can be read back`() {
        val feeds = listOf(
            Feed(1, "Ampersand & Co", "https://amp.example", "https://amp.example/rss"),
            Feed(2, "Quote \"Unquote\"", null, "https://q.example/rss")
        )

        val parsed = parseOpml(buildOpml(feeds, "hReader subscriptions"))

        assertEquals(listOf("Ampersand & Co", "Quote \"Unquote\""), parsed.map { it.title })
        assertEquals(feeds.map { it.feedUrl }, parsed.map { it.feedUrl })
        assertEquals("https://amp.example", parsed.first().siteUrl)
    }
}
