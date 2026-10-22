package com.example.toolkit.ui.tools

import java.io.ByteArrayOutputStream

/**
 * Minimal block-level GIF editor support. We keep the original compressed
 * frame blocks and palettes, so changing order / deleting / timing frames does
 * not re-quantize the image and does not lose quality.
 */
internal data class GifFrameData(
    val gce: ByteArray?,
    val image: ByteArray,
    val delayMs: Int,
    val originalTimeMs: Int,
)

internal data class GifDocument(
    val header: ByteArray,
    val extensions: List<ByteArray>,
    val frames: List<GifFrameData>,
    val loopCount: Int?,
)

internal fun parseGif(bytes: ByteArray): GifDocument? {
    if (bytes.size < 14) return null
    val signature = String(bytes, 0, 6, Charsets.US_ASCII)
    if (signature != "GIF87a" && signature != "GIF89a") return null

    val packed = bytes.u8(10)
    val globalColorTableSize = if (packed and 0x80 != 0) {
        3 * (1 shl ((packed and 0x07) + 1))
    } else {
        0
    }
    val headerEnd = 13 + globalColorTableSize
    if (headerEnd > bytes.size) return null

    val header = bytes.copyOfRange(0, headerEnd)
    val extensions = mutableListOf<ByteArray>()
    val frames = mutableListOf<GifFrameData>()

    var pos = headerEnd
    var pendingGce: ByteArray? = null
    var timeMs = 0

    while (pos < bytes.size) {
        when (bytes.u8(pos)) {
            0x21 -> {
                if (pos + 1 >= bytes.size) return null
                val label = bytes.u8(pos + 1)
                val start = pos
                pos += 2
                pos = skipSubBlocks(bytes, pos) ?: return null
                val extension = bytes.copyOfRange(start, pos)
                if (label == 0xF9) {
                    pendingGce = extension
                } else {
                    extensions.add(extension)
                }
            }

            0x2C -> {
                if (pos + 10 > bytes.size) return null
                val imagePacked = bytes.u8(pos + 9)
                var cursor = pos + 10
                if (imagePacked and 0x80 != 0) {
                    val localTableSize = 3 * (1 shl ((imagePacked and 0x07) + 1))
                    cursor += localTableSize
                    if (cursor >= bytes.size) return null
                }
                // LZW minimum code size
                cursor += 1
                cursor = skipSubBlocks(bytes, cursor) ?: return null
                val imageBlock = bytes.copyOfRange(pos, cursor)
                val rawDelay = pendingGce?.let(::gceDelayMs) ?: 0
                val delay = rawDelay.coerceIn(10, 10_000)
                frames.add(
                    GifFrameData(
                        gce = pendingGce,
                        image = imageBlock,
                        delayMs = delay,
                        originalTimeMs = timeMs,
                    ),
                )
                timeMs += delay
                pendingGce = null
                pos = cursor
            }

            0x3B -> pos = bytes.size

            else -> return null
        }
    }

    if (frames.isEmpty()) return null
    return GifDocument(
        header = header,
        extensions = extensions,
        frames = frames,
        loopCount = parseLoopCount(extensions),
    )
}

internal fun buildGif(
    document: GifDocument,
    frames: List<GifFrameData>,
): ByteArray {
    val output = ByteArrayOutputStream(document.header.size + 4096)
    output.write(document.header)
    document.extensions.forEach(output::write)

    frames.forEach { frame ->
        val gce = (frame.gce ?: defaultGce()).copyOf()
        val delay = (frame.delayMs / 10).coerceIn(0, 0xFFFF)
        if (gce.size >= 6) {
            gce[4] = (delay and 0xFF).toByte()
            gce[5] = ((delay shr 8) and 0xFF).toByte()
        }
        output.write(gce)
        output.write(frame.image)
    }

    output.write(0x3B)
    return output.toByteArray()
}

private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

private fun skipSubBlocks(bytes: ByteArray, start: Int): Int? {
    var pos = start
    while (pos < bytes.size) {
        val length = bytes.u8(pos)
        pos += 1
        if (length == 0) return pos
        pos += length
        if (pos > bytes.size) return null
    }
    return null
}

private fun gceDelayMs(gce: ByteArray): Int {
    if (gce.size < 6) return 0
    val centiseconds = gce.u8(4) or (gce.u8(5) shl 8)
    return centiseconds * 10
}

private fun parseLoopCount(extensions: List<ByteArray>): Int? {
    extensions.forEach { extension ->
        if (
            extension.size >= 19 &&
            extension.u8(0) == 0x21 &&
            extension.u8(1) == 0xFF &&
            extension.u8(2) == 0x0B &&
            String(extension, 3, 11, Charsets.US_ASCII) == "NETSCAPE2.0" &&
            extension.u8(14) == 0x03 &&
            extension.u8(15) == 0x01
        ) {
            return extension.u8(16) or (extension.u8(17) shl 8)
        }
    }
    return null
}

private fun defaultGce(): ByteArray = byteArrayOf(
    0x21,
    0xF9.toByte(),
    0x04,
    0x04, // disposal = do not dispose
    0x00,
    0x00,
    0x00,
    0x00,
)
