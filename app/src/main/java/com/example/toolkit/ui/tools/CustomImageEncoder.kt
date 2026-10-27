package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/** Uncompressed RGB TIFF, one strip, 72 dpi. */
internal fun encodeTiff(source: Bitmap): ByteArray {
    val width = source.width
    val height = source.height
    val pixelBytes = width * height * 3
    val ifdOffset = 8
    val entryCount = 13
    val ifdSize = 2 + entryCount * 12 + 4
    val bitsOffset = ifdOffset + ifdSize
    val xResolutionOffset = bitsOffset + 6
    val yResolutionOffset = xResolutionOffset + 8
    val pixelOffset = yResolutionOffset + 8
    val total = pixelOffset + pixelBytes

    val buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put('I'.code.toByte())
    buffer.put('I'.code.toByte())
    buffer.putShort(42)
    buffer.putInt(ifdOffset)
    buffer.putShort(entryCount.toShort())

    fun entry(tag: Int, type: Int, count: Int, value: Int, inlineShort: Boolean = false) {
        buffer.putShort(tag.toShort())
        buffer.putShort(type.toShort())
        buffer.putInt(count)
        if (inlineShort) {
            buffer.putShort(value.toShort())
            buffer.putShort(0)
        } else {
            buffer.putInt(value)
        }
    }

    entry(256, 4, 1, width)
    entry(257, 4, 1, height)
    entry(258, 3, 3, bitsOffset)
    entry(259, 3, 1, 1, inlineShort = true)
    entry(262, 3, 1, 2, inlineShort = true)
    entry(273, 4, 1, pixelOffset)
    entry(277, 3, 1, 3, inlineShort = true)
    entry(278, 4, 1, height)
    entry(279, 4, 1, pixelBytes)
    entry(282, 5, 1, xResolutionOffset)
    entry(283, 5, 1, yResolutionOffset)
    entry(284, 3, 1, 1, inlineShort = true)
    entry(296, 3, 1, 2, inlineShort = true)
    buffer.putInt(0)

    buffer.putShort(8)
    buffer.putShort(8)
    buffer.putShort(8)
    buffer.putInt(72)
    buffer.putInt(1)
    buffer.putInt(72)
    buffer.putInt(1)

    val pixels = IntArray(width)
    for (y in 0 until height) {
        source.getPixels(pixels, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            val color = pixels[x]
            val alpha = (color ushr 24) and 0xFF
            val red = blendTiff((color shr 16) and 0xFF, alpha)
            val green = blendTiff((color shr 8) and 0xFF, alpha)
            val blue = blendTiff(color and 0xFF, alpha)
            buffer.put(red.toByte())
            buffer.put(green.toByte())
            buffer.put(blue.toByte())
        }
    }
    return buffer.array()
}

private fun blendTiff(channel: Int, alpha: Int): Int =
    (channel * alpha + 255 * (255 - alpha)) / 255

/** ICO containing one PNG image, max 256x256. */
internal fun encodeIco(source: Bitmap): ByteArray {
    val largest = max(source.width, source.height)
    val bitmap = if (largest > 256) {
        val scale = 256f / largest
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        source
    }

    try {
        val png = ByteArrayOutputStream().also { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }.toByteArray()
        val result = ByteBuffer.allocate(22 + png.size).order(ByteOrder.LITTLE_ENDIAN)
        result.putShort(0)
        result.putShort(1)
        result.putShort(1)
        result.put(
            if (bitmap.width >= 256) 0.toByte() else bitmap.width.toByte(),
        )
        result.put(
            if (bitmap.height >= 256) 0.toByte() else bitmap.height.toByte(),
        )
        result.put(0.toByte())
        result.put(0.toByte())
        result.putShort(1)
        result.putShort(32)
        result.putInt(png.size)
        result.putInt(22)
        result.put(png)
        return result.array()
    } finally {
        if (bitmap !== source) bitmap.recycle()
    }
}
