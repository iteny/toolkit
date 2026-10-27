package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import kotlin.math.max

/**
 * Decodes camera RAW files (CR2 / CR3 / NEF / ARW / DNG / RAF / RW2 ...)
 * through LibRaw. The native library is built from the vendored sources in
 * app/src/main/cpp/libraw.
 */
internal object RawDecoder {

    private val loaded: Boolean = runCatching {
        System.loadLibrary("rawdecoder")
    }.isSuccess

    private external fun nativeDecode(bytes: ByteArray, maxDimension: Int): Bitmap?

    fun decode(bytes: ByteArray, maxDimension: Int = 4096): Bitmap? {
        if (!loaded) return null
        val bitmap = runCatching { nativeDecode(bytes, maxDimension) }.getOrNull() ?: return null
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest
        val width = max(1, (bitmap.width * scale).toInt())
        val height = max(1, (bitmap.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
