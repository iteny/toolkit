package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Basic PSD reader: reads the merged/composite image, not individual layers.
 * Supports 8-bit RGB, grayscale and CMYK with raw or PackBits compression.
 */
internal fun decodePsd(data: ByteArray): Bitmap? = runCatching {
    if (data.size < 26) return null
    if (String(data, 0, 4, Charsets.US_ASCII) != "8BPS") return null

    val version = imageU16Be(data, 4)
    if (version != 1) return null
    val channels = imageU16Be(data, 12)
    val height = imageU32Be(data, 14).toInt()
    val width = imageU32Be(data, 18).toInt()
    val depth = imageU16Be(data, 22)
    val colorMode = imageU16Be(data, 24)
    if (width <= 0 || height <= 0 || channels <= 0) return null
    if (width.toLong() * height > 40_000_000L) return null
    if (depth != 8) return null
    if (colorMode !in setOf(1, 3, 4)) return null

    var offset = 26
    fun skipSection() {
        if (offset + 4 > data.size) return
        val length = imageU32Be(data, offset).toInt()
        offset += 4 + length.coerceAtLeast(0)
    }

    skipSection() // color mode data
    skipSection() // image resources
    skipSection() // layer and mask info
    if (offset + 2 > data.size) return null
    val compression = imageU16Be(data, offset)
    offset += 2

    val planes = Array(channels) { ByteArray(width * height) }
    when (compression) {
        0 -> {
            for (channel in 0 until channels) {
                for (y in 0 until height) {
                    if (offset + width > data.size) return null
                    data.copyInto(planes[channel], y * width, offset, offset + width)
                    offset += width
                }
            }
        }

        1 -> {
            val rowCounts = IntArray(channels * height)
            for (index in rowCounts.indices) {
                if (offset + 2 > data.size) return null
                rowCounts[index] = imageU16Be(data, offset)
                offset += 2
            }
            for (channel in 0 until channels) {
                for (y in 0 until height) {
                    val count = rowCounts[channel * height + y]
                    if (offset + count > data.size) return null
                    val row = unpackPsdBits(data, offset, count)
                    offset += count
                    val copy = min(width, row.size)
                    row.copyInto(planes[channel], y * width, 0, copy)
                }
            }
        }

        else -> return null
    }

    val pixels = IntArray(width * height)
    for (index in pixels.indices) {
        pixels[index] = when (colorMode) {
            3 -> {
                if (channels < 3) return null
                val red = imageByte(planes[0], index)
                val green = imageByte(planes[1], index)
                val blue = imageByte(planes[2], index)
                val alpha = if (channels >= 4) imageByte(planes[3], index) else 255
                imageArgb(alpha, red, green, blue)
            }

            1 -> {
                val gray = imageByte(planes[0], index)
                val alpha = if (channels >= 2) imageByte(planes[1], index) else 255
                imageArgb(alpha, gray, gray, gray)
            }

            else -> {
                if (channels < 4) return null
                val cyan = imageByte(planes[0], index)
                val magenta = imageByte(planes[1], index)
                val yellow = imageByte(planes[2], index)
                val black = imageByte(planes[3], index)
                val alpha = if (channels >= 5) imageByte(planes[4], index) else 255
                imageArgb(
                    alpha,
                    255 - min(255, cyan + black),
                    255 - min(255, magenta + black),
                    255 - min(255, yellow + black),
                )
            }
        }
    }
    imageBitmap(width, height, pixels)
}.getOrNull()

private fun unpackPsdBits(data: ByteArray, offset: Int, length: Int): ByteArray {
    val output = ByteArrayOutputStream(length.coerceAtLeast(0))
    var index = offset
    val end = min(offset + length, data.size)
    while (index < end) {
        val control = data[index++].toInt()
        when {
            control in 0..127 -> {
                val count = control + 1
                repeat(count) {
                    if (index < end) output.write(data[index++].toInt())
                }
            }

            control in -127..-1 -> {
                val count = 1 - control
                val value = if (index < end) data[index++].toInt() else 0
                repeat(count) { output.write(value) }
            }

            else -> Unit
        }
    }
    return output.toByteArray()
}
