package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal fun decodeCustomImage(
    data: ByteArray,
    formatId: String,
    maxDimension: Int,
): Bitmap? {
    val bitmap = when (formatId) {
        "qoi" -> decodeQoi(data)
        "tga" -> decodeTga(data)
        "pnm" -> decodePnm(data)
        "ico" -> decodeIco(data)
        "hdr" -> decodeHdr(data)
        "dds" -> decodeDds(data)
        "pcx" -> decodePcx(data)
        "psd" -> decodePsd(data)
        "tiff" -> decodeTiff(data)
        else -> null
    } ?: return null
    return imageScaleForLimit(bitmap, maxDimension)
}

/* ------------------------------------------------------------------ */
/* QOI                                                                */
/* ------------------------------------------------------------------ */

private fun decodeQoi(data: ByteArray): Bitmap? {
    if (data.size < 14 ||
        imageByte(data, 0) != 'q'.code ||
        imageByte(data, 1) != 'o'.code ||
        imageByte(data, 2) != 'i'.code ||
        imageByte(data, 3) != 'f'.code
    ) {
        return null
    }

    val width = imageI32Be(data, 4)
    val height = imageI32Be(data, 8)
    if (width <= 0 || height <= 0 || width.toLong() * height > 40_000_000L) return null
    val channels = imageByte(data, 12)
    val pixels = IntArray(width * height)
    val index = IntArray(64)
    var px = imageArgb(255, 0, 0, 0)
    var offset = 14
    var run = 0

    for (pixelIndex in pixels.indices) {
        if (run > 0) {
            run -= 1
        } else {
            if (offset >= data.size) return null
            val first = imageByte(data, offset++)
            when {
                first == 0xFE -> {
                    if (offset + 3 > data.size) return null
                    val red = imageByte(data, offset++)
                    val green = imageByte(data, offset++)
                    val blue = imageByte(data, offset++)
                    px = imageArgb((px ushr 24) and 0xFF, red, green, blue)
                }

                first == 0xFF -> {
                    if (offset + 4 > data.size) return null
                    val red = imageByte(data, offset++)
                    val green = imageByte(data, offset++)
                    val blue = imageByte(data, offset++)
                    val alpha = imageByte(data, offset++)
                    px = imageArgb(alpha, red, green, blue)
                }

                first and 0xC0 == 0x00 -> px = index[first and 0x3F].let {
                    if (channels == 3) it or 0xFF000000.toInt() else it
                }

                first and 0xC0 == 0x40 -> {
                    var red = (px shr 16) and 0xFF
                    var green = (px shr 8) and 0xFF
                    var blue = px and 0xFF
                    red += ((first shr 4) and 0x03) - 2
                    green += ((first shr 2) and 0x03) - 2
                    blue += (first and 0x03) - 2
                    px = imageArgb((px ushr 24) and 0xFF, red, green, blue)
                }

                first and 0xC0 == 0x80 -> {
                    if (offset >= data.size) return null
                    val second = imageByte(data, offset++)
                    val dg = (first and 0x3F) - 32
                    var red = (px shr 16) and 0xFF
                    var green = (px shr 8) and 0xFF
                    var blue = px and 0xFF
                    red += dg + ((second shr 4) and 0x0F) - 8
                    green += dg
                    blue += dg + (second and 0x0F) - 8
                    px = imageArgb((px ushr 24) and 0xFF, red, green, blue)
                }

                else -> {
                    run = first and 0x3F
                }
            }
            index[qoiHash(px)] = px
        }
        pixels[pixelIndex] = px
    }
    return imageBitmap(width, height, pixels)
}

private fun qoiHash(px: Int): Int {
    val red = (px shr 16) and 0xFF
    val green = (px shr 8) and 0xFF
    val blue = px and 0xFF
    val alpha = (px ushr 24) and 0xFF
    return (red * 3 + green * 5 + blue * 7 + alpha * 11) % 64
}

/* ------------------------------------------------------------------ */
/* TGA                                                                */
/* ------------------------------------------------------------------ */

private fun decodeTga(data: ByteArray): Bitmap? {
    if (data.size < 18) return null
    val idLength = imageByte(data, 0)
    val colorMapType = imageByte(data, 1)
    val imageType = imageByte(data, 2)
    val colorMapLength = imageU16Le(data, 5)
    val colorMapDepth = imageByte(data, 7)
    val width = imageU16Le(data, 12)
    val height = imageU16Le(data, 14)
    val depth = imageByte(data, 16)
    val descriptor = imageByte(data, 17)
    if (width <= 0 || height <= 0 || width.toLong() * height > 40_000_000L) return null

    var offset = 18 + idLength
    var palette: IntArray? = null
    if (colorMapType == 1) {
        val entryBytes = (colorMapDepth + 7) / 8
        if (entryBytes <= 0 || offset + colorMapLength * entryBytes > data.size) return null
        palette = IntArray(colorMapLength)
        for (index in 0 until colorMapLength) {
            palette[index] = readTgaColor(data, offset, colorMapDepth)
            offset += entryBytes
        }
    }

    val topOrigin = descriptor and 0x20 != 0
    val rightOrigin = descriptor and 0x10 != 0
    val pixels = IntArray(width * height)
    var column = 0
    var row = 0

    fun write(color: Int) {
        if (column >= width) {
            column = 0
            row += 1
        }
        if (row >= height) return
        val x = if (rightOrigin) width - 1 - column else column
        val y = if (topOrigin) row else height - 1 - row
        pixels[y * width + x] = color
        column += 1
    }

    fun readColor(): Int? {
        if (offset >= data.size) return null
        val color = when (imageType) {
            1, 9 -> {
                val bytes = when (depth) {
                    8 -> 1
                    16 -> 2
                    else -> return null
                }
                if (offset + bytes > data.size) return null
                val value = if (bytes == 1) imageByte(data, offset) else imageU16Le(data, offset)
                offset += bytes
                palette?.getOrNull(value) ?: 0
            }

            2, 10 -> when (depth) {
                16 -> {
                    if (offset + 2 > data.size) return null
                    val value = imageU16Le(data, offset)
                    offset += 2
                    imageArgb(
                        255,
                        ((value shr 10) and 0x1F) shl 3,
                        ((value shr 5) and 0x1F) shl 3,
                        (value and 0x1F) shl 3,
                    )
                }

                24, 32 -> {
                    val bytes = depth / 8
                    if (offset + bytes > data.size) return null
                    val blue = imageByte(data, offset)
                    val green = imageByte(data, offset + 1)
                    val red = imageByte(data, offset + 2)
                    val alpha = if (depth == 32) imageByte(data, offset + 3) else 255
                    offset += bytes
                    imageArgb(alpha, red, green, blue)
                }

                else -> return null
            }

            3, 11 -> {
                if (depth != 8 || offset >= data.size) return null
                val value = imageByte(data, offset++)
                imageArgb(255, value, value, value)
            }

            else -> return null
        }
        return color
    }

    if (imageType == 9 || imageType == 10 || imageType == 11) {
        while (row < height) {
            if (offset >= data.size) return null
            val header = imageByte(data, offset++)
            val count = (header and 0x7F) + 1
            if (header and 0x80 != 0) {
                val color = readColor() ?: return null
                repeat(count) { write(color) }
            } else {
                repeat(count) {
                    val color = readColor() ?: return null
                    write(color)
                }
            }
        }
    } else {
        repeat(width * height) {
            val color = readColor() ?: return null
            write(color)
        }
    }
    return imageBitmap(width, height, pixels)
}

private fun readTgaColor(data: ByteArray, offset: Int, depth: Int): Int = when (depth) {
    15, 16 -> {
        val value = imageU16Le(data, offset)
        val alpha = if (depth == 16 && value and 0x8000 != 0) 255 else 255
        imageArgb(
            alpha,
            ((value shr 10) and 0x1F) shl 3,
            ((value shr 5) and 0x1F) shl 3,
            (value and 0x1F) shl 3,
        )
    }

    24 -> imageArgb(255, imageByte(data, offset + 2), imageByte(data, offset + 1), imageByte(data, offset))
    32 -> imageArgb(
        imageByte(data, offset + 3),
        imageByte(data, offset + 2),
        imageByte(data, offset + 1),
        imageByte(data, offset),
    )

    else -> 0
}

/* ------------------------------------------------------------------ */
/* PNM                                                                */
/* ------------------------------------------------------------------ */

private class PnmReader(val data: ByteArray) {
    var offset = 0

    fun token(): String? {
        while (offset < data.size) {
            val value = imageByte(data, offset)
            if (value == '#'.code) {
                while (offset < data.size && imageByte(data, offset) != '\n'.code) offset += 1
                continue
            }
            if (value == 9 || value == 10 || value == 13 || value == 32) {
                offset += 1
                continue
            }
            break
        }
        val start = offset
        while (offset < data.size) {
            val value = imageByte(data, offset)
            if (value == 9 || value == 10 || value == 13 || value == 32 || value == '#'.code) break
            offset += 1
        }
        if (offset <= start) return null
        return String(data, start, offset - start, Charsets.US_ASCII)
    }

    fun skipBinaryDelimiter() {
        if (offset < data.size && imageByte(data, offset) == '\r'.code) {
            offset += 1
            if (offset < data.size && imageByte(data, offset) == '\n'.code) offset += 1
        } else if (offset < data.size && imageByte(data, offset) == '\n'.code) {
            offset += 1
        } else if (offset < data.size) {
            offset += 1
        }
    }
}

private fun decodePnm(data: ByteArray): Bitmap? {
    val reader = PnmReader(data)
    val magic = reader.token() ?: return null
    if (!magic.startsWith("P")) return null
    val format = magic.substring(1).toIntOrNull() ?: return null
    if (format !in 1..6) return null

    val width = reader.token()?.toIntOrNull() ?: return null
    val height = reader.token()?.toIntOrNull() ?: return null
    if (width <= 0 || height <= 0 || width.toLong() * height > 40_000_000L) return null
    val maxValue = if (format in 2..3 || format in 5..6) {
        reader.token()?.toIntOrNull() ?: return null
    } else {
        1
    }
    if (maxValue <= 0) return null
    if (format >= 4) reader.skipBinaryDelimiter()

    val pixels = IntArray(width * height)
    when (format) {
        1, 4 -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val black = if (format == 1) {
                        (reader.token()?.toIntOrNull() ?: 0) != 0
                    } else {
                        val rowBytes = (width + 7) / 8
                        val index = reader.offset + y * rowBytes + x / 8
                        val bit = (imageByte(data, index) shr (7 - (x % 8))) and 1
                        bit == 1
                    }
                    pixels[y * width + x] = if (black) imageArgb(255, 0, 0, 0) else imageArgb(255, 255, 255, 255)
                }
            }
        }

        2, 5 -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val sample = if (format == 2) {
                        reader.token()?.toIntOrNull() ?: 0
                    } else {
                        readPnmSample(data, reader, maxValue)
                    }
                    val gray = (sample * 255 / maxValue).coerceIn(0, 255)
                    pixels[y * width + x] = imageArgb(255, gray, gray, gray)
                }
            }
        }

        3, 6 -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val red = if (format == 3) {
                        reader.token()?.toIntOrNull() ?: 0
                    } else {
                        readPnmSample(data, reader, maxValue)
                    }
                    val green = if (format == 3) {
                        reader.token()?.toIntOrNull() ?: 0
                    } else {
                        readPnmSample(data, reader, maxValue)
                    }
                    val blue = if (format == 3) {
                        reader.token()?.toIntOrNull() ?: 0
                    } else {
                        readPnmSample(data, reader, maxValue)
                    }
                    pixels[y * width + x] = imageArgb(
                        255,
                        red * 255 / maxValue,
                        green * 255 / maxValue,
                        blue * 255 / maxValue,
                    )
                }
            }
        }
    }
    return imageBitmap(width, height, pixels)
}

private fun readPnmSample(data: ByteArray, reader: PnmReader, maxValue: Int): Int {
    if (maxValue > 255) {
        if (reader.offset + 2 > data.size) return 0
        val value = imageU16Be(data, reader.offset)
        reader.offset += 2
        return value
    }
    if (reader.offset >= data.size) return 0
    return imageByte(data, reader.offset++)
}

/* ------------------------------------------------------------------ */
/* ICO / CUR                                                          */
/* ------------------------------------------------------------------ */

private fun decodeIco(data: ByteArray): Bitmap? {
    if (data.size < 6 || imageU16Le(data, 0) != 0) return null
    val type = imageU16Le(data, 2)
    if (type != 1 && type != 2) return null
    val count = imageU16Le(data, 4)
    if (count <= 0 || 6 + count * 16 > data.size) return null

    var bestOffset = -1
    var bestSize = -1
    var bestBits = -1
    for (index in 0 until count) {
        val entry = 6 + index * 16
        val width = imageByte(data, entry).let { if (it == 0) 256 else it }
        val height = imageByte(data, entry + 1).let { if (it == 0) 256 else it }
        val bits = imageU16Le(data, entry + 6)
        val bytes = imageI32Le(data, entry + 8)
        val offset = imageI32Le(data, entry + 12)
        if (offset < 0 || bytes <= 0 || offset + bytes > data.size) continue
        val area = width * height
        if (area > bestSize || (area == bestSize && bits > bestBits)) {
            bestSize = area
            bestBits = bits
            bestOffset = offset
        }
    }
    if (bestOffset < 0) return null

    if (bestOffset + 8 <= data.size &&
        imageByte(data, bestOffset) == 0x89 &&
        imageByte(data, bestOffset + 1) == 0x50 &&
        imageByte(data, bestOffset + 2) == 0x4E &&
        imageByte(data, bestOffset + 3) == 0x47
    ) {
        return BitmapFactory.decodeByteArray(data, bestOffset, data.size - bestOffset)
    }

    val headerSize = imageI32Le(data, bestOffset)
    if (headerSize < 40 || bestOffset + headerSize > data.size) return null
    val width = imageI32Le(data, bestOffset + 4)
    val dibHeight = imageI32Le(data, bestOffset + 8)
    val height = abs(dibHeight) / 2
    val bitCount = imageU16Le(data, bestOffset + 14)
    val compression = imageI32Le(data, bestOffset + 16)
    if (width <= 0 || height <= 0 || compression != 0) return null
    if (width.toLong() * height > 40_000_000L) return null

    val colorCount = if (bitCount <= 8) {
        val used = imageI32Le(data, bestOffset + 32)
        if (used > 0) used else 1 shl bitCount
    } else {
        0
    }
    val paletteOffset = bestOffset + headerSize
    val xorOffset = paletteOffset + colorCount * 4
    val xorRowSize = ((width * bitCount + 31) / 32) * 4
    val maskOffset = xorOffset + xorRowSize * height
    val maskRowSize = ((width + 31) / 32) * 4
    if (xorOffset + xorRowSize * height > data.size) return null

    val palette = IntArray(colorCount)
    for (index in 0 until colorCount) {
        val base = paletteOffset + index * 4
        if (base + 3 >= data.size) break
        palette[index] = imageArgb(255, imageByte(data, base + 2), imageByte(data, base + 1), imageByte(data, base))
    }

    val pixels = IntArray(width * height)
    var hasAlpha = false
    for (y in 0 until height) {
        val sourceRow = height - 1 - y
        for (x in 0 until width) {
            val maskBit = if (maskOffset + sourceRow * maskRowSize + x / 8 < data.size) {
                (imageByte(data, maskOffset + sourceRow * maskRowSize + x / 8) shr (7 - (x % 8))) and 1
            } else {
                0
            }
            val color = when (bitCount) {
                32 -> {
                    val base = xorOffset + sourceRow * xorRowSize + x * 4
                    val alpha = imageByte(data, base + 3)
                    if (alpha != 0) hasAlpha = true
                    imageArgb(if (alpha == 0 && maskBit == 1) 0 else alpha, imageByte(data, base + 2), imageByte(data, base + 1), imageByte(data, base))
                }

                24 -> {
                    val base = xorOffset + sourceRow * xorRowSize + x * 3
                    imageArgb(if (maskBit == 1) 0 else 255, imageByte(data, base + 2), imageByte(data, base + 1), imageByte(data, base))
                }

                8, 4, 1 -> {
                    val base = xorOffset + sourceRow * xorRowSize
                    val index = when (bitCount) {
                        8 -> imageByte(data, base + x)
                        4 -> {
                            val value = imageByte(data, base + x / 2)
                            if (x % 2 == 0) value shr 4 else value and 0x0F
                        }

                        else -> {
                            val value = imageByte(data, base + x / 8)
                            (value shr (7 - (x % 8))) and 1
                        }
                    }
                    val color = palette.getOrNull(index) ?: imageArgb(255, 0, 0, 0)
                    if (maskBit == 1) {
                        hasAlpha = true
                        color and 0x00FFFFFF
                    } else {
                        color
                    }
                }

                else -> return null
            }
            pixels[y * width + x] = color
        }
    }
    if (!hasAlpha) {
        for (index in pixels.indices) {
            if (pixels[index] ushr 24 == 0) pixels[index] = pixels[index] or 0xFF000000.toInt()
        }
    }
    return imageBitmap(width, height, pixels)
}

/* ------------------------------------------------------------------ */
/* HDR (Radiance RGBE)                                                */
/* ------------------------------------------------------------------ */

private fun decodeHdr(data: ByteArray): Bitmap? {
    var offset = 0
    var resolution: String? = null
    while (offset < data.size) {
        val lineStart = offset
        while (offset < data.size && imageByte(data, offset) != '\n'.code) offset += 1
        val line = String(data, lineStart, offset - lineStart, Charsets.US_ASCII).trim()
        offset += 1
        if (line.isEmpty()) {
            if (offset < data.size) {
                val resolutionStart = offset
                while (offset < data.size && imageByte(data, offset) != '\n'.code) offset += 1
                resolution = String(data, resolutionStart, offset - resolutionStart, Charsets.US_ASCII).trim()
                offset += 1
            }
            break
        }
        if (line.contains("+X") || line.contains("-Y")) resolution = line
    }
    val match = Regex("[-+]Y\\s+(\\d+)\\s+[-+]X\\s+(\\d+)").find(resolution.orEmpty()) ?: return null
    val height = match.groupValues[1].toIntOrNull() ?: return null
    val width = match.groupValues[2].toIntOrNull() ?: return null
    if (width <= 0 || height <= 0 || width.toLong() * height > 40_000_000L) return null

    val rgbe = ByteArray(width * height * 4)
    var target = 0
    if (width in 8..32767 && offset + 4 <= data.size &&
        imageByte(data, offset) == 2 && imageByte(data, offset + 1) == 2
    ) {
        offset += 4
        val row = ByteArray(width * 4)
        repeat(height) {
            for (channel in 0 until 4) {
                var x = 0
                while (x < width) {
                    if (offset >= data.size) return null
                    val count = imageByte(data, offset++)
                    if (count > 128) {
                        val run = count - 128
                        if (offset >= data.size) return null
                        val value = data[offset++]
                        repeat(run.coerceAtMost(width - x)) {
                            row[channel * width + x] = value
                            x += 1
                        }
                    } else {
                        repeat(count.coerceAtMost(width - x)) {
                            if (offset >= data.size) return null
                            row[channel * width + x] = data[offset++]
                            x += 1
                        }
                    }
                }
            }
            repeat(width) { x ->
                rgbe[target++] = row[x]
                rgbe[target++] = row[width + x]
                rgbe[target++] = row[width * 2 + x]
                rgbe[target++] = row[width * 3 + x]
            }
        }
    } else {
        val bytes = width * height * 4
        if (offset + bytes > data.size) return null
        data.copyInto(rgbe, 0, offset, offset + bytes)
    }

    val pixels = IntArray(width * height)
    for (index in pixels.indices) {
        val red = imageByte(rgbe, index * 4)
        val green = imageByte(rgbe, index * 4 + 1)
        val blue = imageByte(rgbe, index * 4 + 2)
        val exponent = imageByte(rgbe, index * 4 + 3)
        if (exponent == 0) {
            pixels[index] = imageArgb(255, 0, 0, 0)
        } else {
            val scale = 2.0.pow(exponent - 136)
            pixels[index] = imageArgb(
                255,
                (red * scale).toInt(),
                (green * scale).toInt(),
                (blue * scale).toInt(),
            )
        }
    }
    return imageBitmap(width, height, pixels)
}

/* ------------------------------------------------------------------ */
/* DDS                                                                */
/* ------------------------------------------------------------------ */

private fun decodeDds(data: ByteArray): Bitmap? {
    if (data.size < 128 ||
        imageByte(data, 0) != 'D'.code ||
        imageByte(data, 1) != 'D'.code ||
        imageByte(data, 2) != 'S'.code ||
        imageByte(data, 3) != ' '.code
    ) {
        return null
    }
    val height = imageI32Le(data, 12)
    val width = imageI32Le(data, 16)
    if (width <= 0 || height <= 0 || width.toLong() * height > 40_000_000L) return null
    val fourCc = String(data, 84, 4, Charsets.US_ASCII)
    val bitCount = imageI32Le(data, 88)
    val pixels = IntArray(width * height)
    var offset = 128

    when (fourCc) {
        "DXT1", "DXT3", "DXT5" -> {
            val blockSize = if (fourCc == "DXT1") 8 else 16
            for (blockY in 0 until (height + 3) / 4) {
                for (blockX in 0 until (width + 3) / 4) {
                    if (offset + blockSize > data.size) return null
                    val colors = decodeDxtColors(data, offset + (if (fourCc == "DXT1") 0 else 8), fourCc == "DXT1")
                    val alphas = when (fourCc) {
                        "DXT3" -> decodeDxt3Alpha(data, offset)
                        "DXT5" -> decodeDxt5Alpha(data, offset + 8)
                        else -> null
                    }
                    val colorOffset = if (fourCc == "DXT1") 4 else 12
                    val indices = if (fourCc == "DXT1") {
                        imageI32Le(data, offset + 4)
                    } else {
                        imageI32Le(data, offset + 12)
                    }
                    var index = 0
                    for (y in 0 until 4) {
                        for (x in 0 until 4) {
                            val px = blockX * 4 + x
                            val py = blockY * 4 + y
                            if (px < width && py < height) {
                                val code = (indices shr (index * 2)) and 0x03
                                var color = colors[code]
                                if (alphas != null) {
                                    color = (color and 0x00FFFFFF) or (alphas[index] shl 24)
                                } else if (fourCc == "DXT1" && code == 3 && colors.size >= 4 && colors[3] == 0) {
                                    color = 0
                                }
                                pixels[py * width + px] = color
                            }
                            index += 1
                        }
                    }
                    offset += blockSize
                }
            }
        }

        "\u0000\u0000\u0000\u0000" -> {
            if (bitCount != 32 && bitCount != 24) return null
            val bytesPerPixel = bitCount / 8
            val pitch = ((width * bitCount + 31) / 32) * 4
            for (y in 0 until height) {
                val row = offset + (height - 1 - y) * pitch
                for (x in 0 until width) {
                    val base = row + x * bytesPerPixel
                    if (base + bytesPerPixel > data.size) return null
                    val blue = imageByte(data, base)
                    val green = imageByte(data, base + 1)
                    val red = imageByte(data, base + 2)
                    val alpha = if (bytesPerPixel == 4) imageByte(data, base + 3) else 255
                    pixels[y * width + x] = imageArgb(alpha, red, green, blue)
                }
            }
        }

        else -> return null
    }
    return imageBitmap(width, height, pixels)
}

private fun decodeDxtColors(data: ByteArray, offset: Int, dxt1: Boolean): IntArray {
    val c0 = imageU16Le(data, offset)
    val c1 = imageU16Le(data, offset + 2)
    val colors = IntArray(4)
    fun unpack(value: Int): Int {
        val red = ((value shr 11) and 0x1F)
        val green = ((value shr 5) and 0x3F)
        val blue = value and 0x1F
        return imageArgb(
            255,
            (red shl 3) or (red shr 2),
            (green shl 2) or (green shr 4),
            (blue shl 3) or (blue shr 2),
        )
    }

    colors[0] = unpack(c0)
    colors[1] = unpack(c1)
    if (!dxt1 || c0 > c1) {
        colors[2] = imageArgb(
            255,
            (((colors[0] shr 16) and 0xFF) + ((colors[1] shr 16) and 0xFF)) / 2,
            (((colors[0] shr 8) and 0xFF) + ((colors[1] shr 8) and 0xFF)) / 2,
            ((colors[0] and 0xFF) + (colors[1] and 0xFF)) / 2,
        )
        colors[3] = imageArgb(
            255,
            (((colors[0] shr 16) and 0xFF) + 2 * ((colors[1] shr 16) and 0xFF)) / 3,
            (((colors[0] shr 8) and 0xFF) + 2 * ((colors[1] shr 8) and 0xFF)) / 3,
            ((colors[0] and 0xFF) + 2 * (colors[1] and 0xFF)) / 3,
        )
    } else {
        colors[2] = imageArgb(
            255,
            (((colors[0] shr 16) and 0xFF) + ((colors[1] shr 16) and 0xFF)) / 2,
            (((colors[0] shr 8) and 0xFF) + ((colors[1] shr 8) and 0xFF)) / 2,
            ((colors[0] and 0xFF) + (colors[1] and 0xFF)) / 2,
        )
        colors[3] = 0
    }
    return colors
}

private fun decodeDxt3Alpha(data: ByteArray, offset: Int): IntArray {
    val values = IntArray(16)
    for (index in 0 until 16) {
        val value = imageByte(data, offset + index / 2)
        values[index] = if (index % 2 == 0) (value and 0x0F) * 17 else (value shr 4) * 17
    }
    return values
}

private fun decodeDxt5Alpha(data: ByteArray, offset: Int): IntArray {
    val a0 = imageByte(data, offset)
    val a1 = imageByte(data, offset + 1)
    val table = IntArray(8)
    table[0] = a0
    table[1] = a1
    if (a0 > a1) {
        for (index in 2..7) table[index] = ((7 - index) * a0 + (index - 1) * a1) / 6
    } else {
        for (index in 2..5) table[index] = ((5 - index) * a0 + (index - 1) * a1) / 4
        table[6] = 0
        table[7] = 255
    }

    var bits = 0L
    for (index in 0 until 6) bits = bits or ((imageByte(data, offset + 2 + index).toLong()) shl (index * 8))
    val values = IntArray(16)
    for (index in 0 until 16) {
        values[index] = table[((bits shr (index * 3)) and 0x07).toInt()]
    }
    return values
}

/* ------------------------------------------------------------------ */
/* PCX                                                                */
/* ------------------------------------------------------------------ */

private fun decodePcx(data: ByteArray): Bitmap? {
    if (data.size < 128 || imageByte(data, 0) != 0x0A) return null
    if (imageByte(data, 1) != 1) return null
    val bitsPerPixel = imageByte(data, 3)
    val xMin = imageU16Le(data, 4)
    val yMin = imageU16Le(data, 6)
    val xMax = imageU16Le(data, 8)
    val yMax = imageU16Le(data, 10)
    val width = xMax - xMin + 1
    val height = yMax - yMin + 1
    val planes = imageByte(data, 65)
    val bytesPerLine = imageU16Le(data, 66)
    if (width <= 0 || height <= 0 || bitsPerPixel != 8) return null
    if (planes !in setOf(1, 3, 4) || width.toLong() * height > 40_000_000L) return null
    if (bytesPerLine <= 0) return null

    var end = data.size
    var palette: ByteArray? = null
    if (planes == 1 && data.size > 769 && imageByte(data, data.size - 769) == 0x0C) {
        palette = data.copyOfRange(data.size - 768, data.size)
        end = data.size - 769
    }

    val needed = bytesPerLine * planes * height
    val decoded = ByteArray(needed)
    var source = 128
    var target = 0
    while (target < needed && source < end) {
        val value = imageByte(data, source++)
        if (value and 0xC0 == 0xC0) {
            val count = value and 0x3F
            if (source >= end) break
            val repeated = imageByte(data, source++)
            repeat(count) {
                if (target < needed) decoded[target++] = repeated.toByte()
            }
        } else {
            decoded[target++] = value.toByte()
        }
    }
    if (target < needed) return null

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = y * bytesPerLine * planes
        for (x in 0 until width) {
            val color = when (planes) {
                1 -> {
                    val index = imageByte(decoded, row + x)
                    val paletteData = palette ?: return null
                    imageArgb(255, imageByte(paletteData, index * 3), imageByte(paletteData, index * 3 + 1), imageByte(paletteData, index * 3 + 2))
                }

                3 -> imageArgb(
                    255,
                    imageByte(decoded, row + x),
                    imageByte(decoded, row + bytesPerLine + x),
                    imageByte(decoded, row + bytesPerLine * 2 + x),
                )

                else -> imageArgb(
                    imageByte(decoded, row + bytesPerLine * 3 + x),
                    imageByte(decoded, row + x),
                    imageByte(decoded, row + bytesPerLine + x),
                    imageByte(decoded, row + bytesPerLine * 2 + x),
                )
            }
            pixels[y * width + x] = color
        }
    }
    return imageBitmap(width, height, pixels)
}
