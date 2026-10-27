package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import kotlin.math.min

private class TiffEntry(
    val type: Int,
    val count: Int,
    val valueOffset: Int,
)

private class TiffReader(val data: ByteArray) {
    val little: Boolean = data.size >= 2 && data[0] == 'I'.code.toByte()
    val valid: Boolean = data.size >= 8 &&
        ((little && data[0] == 'I'.code.toByte() && data[1] == 'I'.code.toByte()) ||
            (!little && data[0] == 'M'.code.toByte() && data[1] == 'M'.code.toByte())) &&
        u16(2) == 42

    fun u16(offset: Int): Int =
        if (little) imageU16Le(data, offset) else imageU16Be(data, offset)

    fun u32(offset: Int): Long =
        if (little) imageU32Le(data, offset) else imageU32Be(data, offset)

    fun numbers(entry: TiffEntry): LongArray {
        val bytes = rawBytes(entry)
        return when (entry.type) {
            1, 6, 7 -> LongArray(entry.count) { index ->
                imageByte(bytes, index).toLong()
            }

            3, 8 -> LongArray(entry.count) { index ->
                val value = if (little) imageU16Le(bytes, index * 2) else imageU16Be(bytes, index * 2)
                if (entry.type == 8) value.toShort().toLong() else value.toLong()
            }

            4, 9 -> LongArray(entry.count) { index ->
                val value = if (little) imageI32Le(bytes, index * 4) else imageI32Be(bytes, index * 4)
                value.toLong()
            }

            else -> LongArray(0)
        }
    }

    fun rawBytes(entry: TiffEntry): ByteArray {
        val size = typeSize(entry.type) * entry.count
        if (size <= 0) return ByteArray(0)
        val field = entry.valueOffset
        return if (size <= 4) {
            data.copyOfRange(field, min(field + size, data.size))
        } else {
            val offset = u32(field).toInt()
            if (offset < 0 || offset >= data.size) return ByteArray(0)
            data.copyOfRange(offset, min(offset + size, data.size))
        }
    }

    private fun typeSize(type: Int): Int = when (type) {
        1, 2, 6, 7 -> 1
        3, 8 -> 2
        4, 9, 11 -> 4
        5, 10, 12 -> 8
        else -> 0
    }
}

/**
 * Baseline TIFF reader: strips and tiles, uncompressed / LZW / PackBits /
 * Deflate, RGB / RGBA / grayscale / palette / CMYK, 8- and 16-bit samples.
 */
internal fun decodeTiff(data: ByteArray): Bitmap? = runCatching {
    val reader = TiffReader(data)
    if (!reader.valid) return null

    val ifdOffset = reader.u32(4).toInt()
    if (ifdOffset <= 0 || ifdOffset >= data.size) return null
    val entryCount = reader.u16(ifdOffset)
    if (entryCount <= 0) return null

    val fields = HashMap<Int, TiffEntry>()
    for (index in 0 until entryCount.coerceAtMost(1024)) {
        val offset = ifdOffset + 2 + index * 12
        if (offset + 12 > data.size) break
        val tag = reader.u16(offset)
        fields[tag] = TiffEntry(
            type = reader.u16(offset + 2),
            count = reader.u32(offset + 4).toInt(),
            valueOffset = offset + 8,
        )
    }

    fun values(tag: Int): LongArray = fields[tag]?.let(reader::numbers) ?: LongArray(0)
    fun first(tag: Int, fallback: Int): Int = values(tag).firstOrNull()?.toInt() ?: fallback

    val width = first(256, 0)
    val height = first(257, 0)
    if (width <= 0 || height <= 0) return null
    if (width.toLong() * height > 40_000_000L) return null

    val compression = first(259, 1)
    if (compression !in setOf(1, 5, 8, 32946, 32773)) return null
    val photometric = first(262, 2)
    val defaultSamples = when (photometric) {
        2 -> 3
        5 -> 4
        else -> 1
    }
    val samples = first(277, defaultSamples)
    if (samples <= 0 || samples > 8) return null
    if (first(284, 1) != 1) return null

    val bitsValues = values(258)
    val bits = bitsValues.firstOrNull()?.toInt() ?: 8
    if (bits !in setOf(1, 2, 4, 8, 16)) return null
    if (bits < 8 && samples != 1) return null
    val bytesPerSample = if (bits == 16) 2 else 1
    val packed = bits < 8
    val rowBytes = if (packed) {
        (width * bits + 7) / 8
    } else {
        width * samples * bytesPerSample
    }

    val stripOffsets = values(273)
    val stripCounts = values(279)
    val rowsPerStrip = first(278, height).coerceAtLeast(1)
    val tileOffsets = values(324)
    val tileCounts = values(325)
    val tileWidth = first(322, 0)
    val tileLength = first(323, 0)
    val usingTiles = tileOffsets.isNotEmpty() && tileWidth > 0 && tileLength > 0

    val colorMap = values(320)
    val pixels = IntArray(width * height)
    val byteOrderLittle = reader.little

    fun readSample(segment: ByteArray, rowStart: Int, x: Int, sample: Int): Int {
        return when {
            packed -> {
                val bitIndex = x * bits
                val value = imageByte(segment, rowStart + bitIndex / 8)
                val shift = 8 - bits - (bitIndex % 8)
                (value shr shift) and ((1 shl bits) - 1)
            }

            bits == 16 -> {
                val offset = rowStart + (x * samples + sample) * 2
                val value = if (byteOrderLittle) imageU16Le(segment, offset) else imageU16Be(segment, offset)
                value shr 8
            }

            else -> imageByte(segment, rowStart + x * samples + sample)
        }
    }

    fun colorFor(sampleValues: IntArray): Int {
        return when (photometric) {
            0, 1 -> {
                val raw = sampleValues[0]
                val gray = if (bits < 8) raw * 255 / ((1 shl bits) - 1) else raw
                val value = if (photometric == 0) 255 - gray else gray
                val alpha = if (samples >= 2) sampleValues[1] else 255
                imageArgb(alpha, value, value, value)
            }

            2 -> {
                val alpha = if (samples >= 4) sampleValues[3] else 255
                imageArgb(alpha, sampleValues[0], sampleValues[1], sampleValues[2])
            }

            3 -> {
                if (colorMap.size < 3 * (1 shl bits)) return 0
                val index = sampleValues[0]
                val count = 1 shl bits
                if (index >= count) return 0
                val red = (colorMap[index].toInt() * 255 / 65535).coerceIn(0, 255)
                val green = (colorMap[count + index].toInt() * 255 / 65535).coerceIn(0, 255)
                val blue = (colorMap[count * 2 + index].toInt() * 255 / 65535).coerceIn(0, 255)
                imageArgb(255, red, green, blue)
            }

            5 -> {
                val cyan = sampleValues[0]
                val magenta = sampleValues[1]
                val yellow = sampleValues[2]
                val black = sampleValues[3]
                val alpha = if (samples >= 5) sampleValues[4] else 255
                imageArgb(
                    alpha,
                    255 - min(255, cyan + black),
                    255 - min(255, magenta + black),
                    255 - min(255, yellow + black),
                )
            }

            else -> 0
        }
    }

    fun decodeSegment(
        segment: ByteArray,
        segmentWidth: Int,
        segmentHeight: Int,
        destX: Int,
        destY: Int,
        visibleWidth: Int,
        visibleHeight: Int,
    ) {
        val segmentRowBytes = if (packed) {
            (segmentWidth * bits + 7) / 8
        } else {
            segmentWidth * samples * bytesPerSample
        }
        for (y in 0 until segmentHeight) {
            if (y >= visibleHeight) break
            val rowStart = y * segmentRowBytes
            for (x in 0 until segmentWidth) {
                if (x >= visibleWidth) break
                val sampleValues = IntArray(samples)
                for (sample in 0 until samples) {
                    sampleValues[sample] = readSample(segment, rowStart, x, sample)
                }
                pixels[(destY + y) * width + destX + x] = colorFor(sampleValues)
            }
        }
    }

    if (usingTiles) {
        val across = (width + tileWidth - 1) / tileWidth
        val down = (height + tileLength - 1) / tileLength
        val tileBytes = (tileWidth * samples * bytesPerSample) * tileLength
        for (tileY in 0 until down) {
            for (tileX in 0 until across) {
                val index = tileY * across + tileX
                val offset = tileOffsets.getOrNull(index)?.toInt() ?: break
                val count = tileCounts.getOrNull(index)?.toInt() ?: 0
                if (offset < 0 || count <= 0 || offset + count > data.size) continue
                val raw = data.copyOfRange(offset, offset + count)
                val segment = decompressTiff(raw, compression, tileBytes) ?: continue
                decodeSegment(
                    segment,
                    tileWidth,
                    tileLength,
                    tileX * tileWidth,
                    tileY * tileLength,
                    min(tileWidth, width - tileX * tileWidth),
                    min(tileLength, height - tileY * tileLength),
                )
            }
        }
    } else {
        if (stripOffsets.isEmpty()) return null
        for (strip in stripOffsets.indices) {
            val offset = stripOffsets[strip].toInt()
            val count = stripCounts.getOrNull(strip)?.toInt() ?: 0
            if (offset < 0 || count <= 0 || offset + count > data.size) continue
            val y = strip * rowsPerStrip
            if (y >= height) break
            val rows = min(rowsPerStrip, height - y)
            val raw = data.copyOfRange(offset, offset + count)
            val segment = decompressTiff(raw, compression, rowBytes * rows) ?: continue
            decodeSegment(segment, width, rows, 0, y, width, rows)
        }
    }

    val bitmap = imageBitmap(width, height, pixels) ?: return null
    orientTiff(bitmap, first(274, 1))
}.getOrNull()

private fun orientTiff(source: Bitmap, orientation: Int): Bitmap {
    if (orientation !in 2..8) return source
    val matrix = Matrix()
    val width = source.width.toFloat()
    val height = source.height.toFloat()
    when (orientation) {
        2 -> matrix.setScale(-1f, 1f, width / 2f, height / 2f)
        3 -> matrix.setRotate(180f, width / 2f, height / 2f)
        4 -> matrix.setScale(1f, -1f, width / 2f, height / 2f)
        6 -> matrix.setRotate(90f, width / 2f, height / 2f)
        8 -> matrix.setRotate(-90f, width / 2f, height / 2f)
        else -> return source
    }
    val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (result !== source) source.recycle()
    return result
}

private fun decompressTiff(raw: ByteArray, compression: Int, expected: Int): ByteArray? =
    when (compression) {
        1 -> raw
        5 -> tiffLzw(raw, expected)
        32773 -> tiffPackBits(raw, expected)
        8, 32946 -> tiffDeflate(raw, expected)
        else -> null
    }

private fun tiffPackBits(data: ByteArray, expected: Int): ByteArray? {
    val output = ByteArrayOutputStream(expected.coerceAtLeast(0))
    var index = 0
    while (index < data.size && output.size() < expected) {
        val control = data[index++].toInt()
        when {
            control in 0..127 -> {
                val count = control + 1
                repeat(count) {
                    if (index < data.size) output.write(data[index++].toInt())
                }
            }

            control in -127..-1 -> {
                val count = 1 - control
                val value = if (index < data.size) data[index++].toInt() else 0
                repeat(count) { output.write(value) }
            }

            else -> Unit
        }
    }
    return output.toByteArray()
}

private fun tiffDeflate(data: ByteArray, expected: Int): ByteArray? = runCatching {
    val inflater = Inflater()
    inflater.setInput(data)
    val output = ByteArrayOutputStream(expected.coerceAtLeast(0))
    val buffer = ByteArray(16 * 1024)
    while (!inflater.finished() && output.size() < expected) {
        val count = inflater.inflate(buffer)
        if (count <= 0) break
        output.write(buffer, 0, count)
    }
    inflater.end()
    output.toByteArray()
}.getOrNull()

private fun tiffLzw(data: ByteArray, expected: Int): ByteArray? {
    val output = ByteArrayOutputStream(expected.coerceAtLeast(0))
    val prefix = IntArray(4096)
    val suffix = IntArray(4096)
    val firstChar = IntArray(4096)
    for (index in 0..255) {
        prefix[index] = -1
        suffix[index] = index
        firstChar[index] = index
    }

    var nextCode = 258
    var codeWidth = 9
    var bitPosition = 0
    var oldCode = -1

    fun readCode(): Int? {
        if (bitPosition + codeWidth > data.size * 8) return null
        var code = 0
        repeat(codeWidth) {
            val byteIndex = bitPosition / 8
            val bitIndex = 7 - bitPosition % 8
            val bit = (imageByte(data, byteIndex) shr bitIndex) and 1
            code = (code shl 1) or bit
            bitPosition += 1
        }
        return code
    }

    fun emit(code: Int): Int? {
        if (code < 0 || code >= nextCode) return null
        val stack = IntArray(4096)
        var depth = 0
        var current = code
        while (current >= 258) {
            if (depth >= stack.size) return null
            stack[depth++] = suffix[current]
            current = prefix[current]
            if (current < 0) return null
        }
        stack[depth++] = current
        for (index in depth - 1 downTo 0) output.write(stack[index])
        return firstChar[code]
    }

    while (output.size() < expected) {
        val code = readCode() ?: break
        when {
            code == 257 -> break
            code == 256 -> {
                nextCode = 258
                codeWidth = 9
                oldCode = -1
            }

            oldCode == -1 -> {
                val first = emit(code) ?: return null
                firstChar[code] = first
                oldCode = code
            }

            else -> {
                if (code < nextCode) {
                    val first = emit(code) ?: return null
                    if (nextCode < 4096) {
                        prefix[nextCode] = oldCode
                        suffix[nextCode] = first
                        firstChar[nextCode] = firstChar[oldCode]
                        nextCode += 1
                    }
                } else if (code == nextCode) {
                    val first = firstChar[oldCode]
                    emit(oldCode) ?: return null
                    output.write(first)
                    if (nextCode < 4096) {
                        prefix[nextCode] = oldCode
                        suffix[nextCode] = first
                        firstChar[nextCode] = firstChar[oldCode]
                        nextCode += 1
                    }
                } else {
                    return null
                }
                oldCode = code
                if (nextCode == (1 shl codeWidth) - 1 && codeWidth < 12) {
                    codeWidth += 1
                }
            }
        }
    }
    return output.toByteArray()
}
