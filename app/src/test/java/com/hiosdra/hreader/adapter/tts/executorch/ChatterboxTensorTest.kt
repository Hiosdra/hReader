package com.hiosdra.hreader.adapter.tts.executorch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatterboxTensorTest {
    @Test
    fun `converts float values to and from half tensors`() {
        val values = floatArrayOf(0f, 1f, -2f, 0.0001f, 65_504f, Float.POSITIVE_INFINITY)

        val result = halfTensor(values, longArrayOf(values.size.toLong())).floatValues()

        assertEquals(0f, result[0])
        assertEquals(1f, result[1])
        assertEquals(-2f, result[2])
        assertEquals(values[3], result[3], 0.00001f)
        assertEquals(65_504f, result[4])
        assertTrue(result[5].isInfinite())
    }

    @Test
    fun `preserves the smallest half subnormal`() {
        val smallestHalfSubnormal = Math.scalb(1f, -24)
        val result = halfTensor(floatArrayOf(smallestHalfSubnormal), longArrayOf(1)).floatValues()

        assertEquals(smallestHalfSubnormal, result.single(), 0f)
    }
}
