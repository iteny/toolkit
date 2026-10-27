package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import kotlin.math.max

internal fun imageByte(data: ByteArray, index: Int): Int =
    if (index in data.indices) data[index].toInt() and 0xFF else 0

internal fun imageU16Le(data: ByteArray, index: Int): Int =
    imageByte(data, index) or (imageByte(data, index + 1) shl 8)

internal fun imageU16Be(data: ByteArray, index: Int): Int =
    (imageByte(data, index) shl 8) or imageByte(data, index + 1)

internal fun imageI32Le(data: ByteArray, index: Int): Int =
    imageByte(data, index) or
        (imageByte(data, index + 1) shl 8) or
        (imageByte(data, index + 2) shl 16) or
        (imageByte(data, index + 3) shl 24)

internal fun imageU32Le(data: ByteArray, index: Int): Long =
    (imageU16Le(data, index) or (imageU16Le(data, index + 2) shl 16)).toLong() and 0xFFFFFFFFL

internal fun imageI32Be(data: ByteArray, index: Int): Int =
    (imageByte(data, index) shl 24) or
        (imageByte(data, index + 1) shl 16) or
        (imageByte(data, index + 2) shl 8) or
        imageByte(data, index + 3)

internal fun imageU32Be(data: ByteArray, index: Int): Long =
    imageI32Be(data, index).toLong() and 0xFFFFFFFFL

internal fun imageArgb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    (alpha.coerceIn(0, 255) shl 24) or
        (red.coerceIn(0, 255) shl 16) or
        (green.coerceIn(0, 255) shl 8) or
        blue.coerceIn(0, 255)

internal fun imageClamp(value: Int): Int = value.coerceIn(0, 255)

internal fun imageScaleForLimit(bitmap: Bitmap, maxDimension: Int): Bitmap {
    if (maxDimension <= 0) return bitmap
    val largest = max(bitmap.width, bitmap.height)
    if (largest <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / largest
    val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
    if (scaled !== bitmap) bitmap.recycle()
    return scaled
}

internal fun imageBitmap(width: Int, height: Int, pixels: IntArray): Bitmap? {
    if (width <= 0 || height <= 0 || width.toLong() * height > 40_000_000L) return null
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
