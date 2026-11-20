package com.hiosdra.hreader.adapter.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphPlaybackClockTest {
    private var nowMs = 0L
    private val clock = ParagraphPlaybackClock { nowMs }

    @Test
    fun `counts playing time since the paragraph started`() {
        nowMs = 1_000L
        clock.restart(paused = false)
        nowMs = 2_500L

        assertEquals(1_500L, clock.elapsedMs())
    }

    @Test
    fun `excludes paused time`() {
        clock.restart(paused = false)
        nowMs = 1_000L
        clock.pause()
        nowMs = 60_000L

        assertEquals(1_000L, clock.elapsedMs())

        clock.resume()
        nowMs = 60_500L

        assertEquals(1_500L, clock.elapsedMs())
    }

    @Test
    fun `does not run after restarting while paused`() {
        clock.restart(paused = false)
        nowMs = 2_000L
        clock.pause()
        clock.restart(paused = true)
        nowMs = 30_000L

        assertEquals(0L, clock.elapsedMs())

        clock.resume()
        nowMs = 31_000L

        assertEquals(1_000L, clock.elapsedMs())
    }

    @Test
    fun `ignores repeated pause and resume calls`() {
        clock.restart(paused = false)
        nowMs = 1_000L
        clock.pause()
        clock.pause()
        nowMs = 5_000L
        clock.resume()
        nowMs = 6_000L
        clock.resume()
        nowMs = 7_000L

        assertEquals(3_000L, clock.elapsedMs())
    }
}
