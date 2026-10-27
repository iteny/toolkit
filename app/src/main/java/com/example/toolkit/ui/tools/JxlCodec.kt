package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import com.awxkee.jxlcoder.JxlChannelsConfiguration
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlCompressionOption
import com.awxkee.jxlcoder.JxlDecodingSpeed
import com.awxkee.jxlcoder.JxlEffort
import com.awxkee.jxlcoder.PreferredColorConfig
import kotlin.math.max

/**
 * Thin wrapper around libjxl (via jxl-coder) so the rest of the converter
 * does not need to know about the native API details.
 */
internal object JxlCodec {

    fun decode(bytes: ByteArray, maxDimension: Int = 4096): Bitmap? = runCatching {
        val bitmap = JxlCoder.decode(bytes, PreferredColorConfig.RGBA_8888)
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) {
            bitmap
        } else {
            val scale = maxDimension.toFloat() / longest
            val width = max(1, (bitmap.width * scale).toInt())
            val height = max(1, (bitmap.height * scale).toInt())
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            if (scaled !== bitmap) bitmap.recycle()
            scaled
        }
    }.getOrNull()

    fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val channels = if (bitmap.hasAlpha()) {
            JxlChannelsConfiguration.RGBA
        } else {
            JxlChannelsConfiguration.RGB
        }
        return JxlCoder.encode(
            bitmap,
            channels,
            JxlCompressionOption.LOSSY,
            JxlEffort.HARE,
            quality.coerceIn(50, 100),
            JxlDecodingSpeed.FAST,
        )
    }

    fun looksLikeJxl(bytes: ByteArray): Boolean = runCatching { JxlCoder.isJXL(bytes) }.getOrDefault(false)
}
