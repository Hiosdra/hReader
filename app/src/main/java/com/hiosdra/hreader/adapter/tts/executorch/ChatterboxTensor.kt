package com.hiosdra.hreader.adapter.tts.executorch

import org.pytorch.executorch.DType
import org.pytorch.executorch.Tensor

internal fun floatTensor(values: FloatArray, shape: LongArray): Tensor =
    Tensor.fromBlob(values, shape)

internal fun longTensor(values: LongArray, shape: LongArray): Tensor =
    Tensor.fromBlob(values, shape)

internal fun halfTensor(values: FloatArray, shape: LongArray): Tensor =
    Tensor.fromBlob(ShortArray(values.size) { index -> values[index].toHalfBits() }, shape)

internal fun scalarLongTensor(value: Long): Tensor =
    longTensor(longArrayOf(value), longArrayOf())

internal fun Tensor.floatValues(): FloatArray = when (dtype()) {
    DType.FLOAT -> getDataAsFloatArray()
    DType.HALF -> getDataAsShortArray().let { values ->
        FloatArray(values.size) { index -> values[index].toFloatValue() }
    }
    else -> error("Expected a floating point tensor, got ${dtype()}")
}

internal fun Tensor.longValues(): LongArray = when (dtype()) {
    DType.INT64 -> getDataAsLongArray()
    DType.INT32 -> getDataAsIntArray().map(Int::toLong).toLongArray()
    else -> error("Expected an integer tensor, got ${dtype()}")
}

private fun Float.toHalfBits(): Short {
    val bits = toRawBits()
    val sign = (bits ushr 16) and 0x8000
    val absolute = bits and 0x7fffffff
    if (absolute >= 0x7f800000) {
        return (sign or if (absolute and 0x7fffff == 0) 0x7c00 else 0x7e00).toShort()
    }
    val exponent = ((absolute ushr 23) and 0xff) - 127
    val mantissa = bits and 0x7fffff
    if (exponent < -14) {
        if (exponent < -24) return sign.toShort()
        val shift = -exponent - 1
        val fullMantissa = mantissa or 0x800000
        var rounded = fullMantissa ushr shift
        val remainder = fullMantissa and ((1 shl shift) - 1)
        val halfway = 1 shl (shift - 1)
        if (remainder > halfway || remainder == halfway && rounded and 1 != 0) rounded++
        return (sign or rounded).toShort()
    }
    if (exponent >= 31) {
        return (sign or 0x7c00).toShort()
    }
    var roundedMantissa = (mantissa + 0x1000) ushr 13
    var roundedExponent = exponent + 15
    if (roundedMantissa == 0x400) {
        roundedMantissa = 0
        roundedExponent++
    }
    return (sign or (roundedExponent shl 10) or roundedMantissa).toShort()
}

private fun Short.toFloatValue(): Float {
    val bits = toInt() and 0xffff
    val sign = (bits and 0x8000) shl 16
    val exponent = (bits ushr 10) and 0x1f
    val mantissa = bits and 0x3ff
    val floatBits = when (exponent) {
        0 -> if (mantissa == 0) {
            sign
        } else {
            var normalizedMantissa = mantissa
            var normalizedExponent = -14
            while ((normalizedMantissa and 0x400) == 0) {
                normalizedMantissa = normalizedMantissa shl 1
                normalizedExponent--
            }
            val exponentBits = (normalizedExponent + 127) shl 23
            sign or exponentBits or ((normalizedMantissa and 0x3ff) shl 13)
        }
        0x1f -> sign or 0x7f800000 or (mantissa shl 13)
        else -> sign or ((exponent + 112) shl 23) or (mantissa shl 13)
    }
    return Float.fromBits(floatBits)
}
