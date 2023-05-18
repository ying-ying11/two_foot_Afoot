package com.flyn.sarcopenia_project.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun ByteArray?.toHexString(separator: String = " "): String {
    if (this == null) return ""
    return this.joinToString(separator = separator) { "%02x".format(it) }
}

internal fun ByteArray.toShortArray(): ShortArray {
    return ShortArray(this.size / 2) {
        ByteBuffer.wrap(this.sliceArray(it * 2..it * 2 + 1))
            .order(ByteOrder.LITTLE_ENDIAN).short
    }
}

internal fun calibrate(data: ShortArray, weight: FloatArray, bias: FloatArray, direct: FloatArray): FloatArray {
    return data.mapIndexed{ index, value ->
        ((value.toFloat() / 4095f * 3.3f + bias[index]) / weight[index])+direct[index]
    }.toFloatArray()
}