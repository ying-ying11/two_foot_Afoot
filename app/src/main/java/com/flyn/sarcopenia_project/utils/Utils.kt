package com.flyn.sarcopenia_project.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

private val weight = arrayOf(0.0014f, 0.0008f, 0.0016f, 0.001f, 0.0011f, 0.0013f)
private val bias = arrayOf(0.411f, 0.1106f, 0.6439f, 0.153f, -0.0914f, 0.4504f)

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

internal fun calibrate(data: ShortArray): FloatArray {
    return data.mapIndexed{ index, value ->
        (value.toFloat() / 4095f * 3.6f + bias[index]) / weight[index]
    }.toFloatArray()
}