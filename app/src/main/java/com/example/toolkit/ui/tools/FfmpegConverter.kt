package com.example.toolkit.ui.tools

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val MAX_MEDIA_OUTPUT_BYTES = 256L * 1024 * 1024
private const val MEDIA_TIMEOUT_MS = 15 * 60 * 1000L

internal suspend fun convertMediaWithFfmpeg(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
    quality: Int,
): ConvertOutcome {
    val input = createFfmpegInput(context, source)
    val output = File(context.cacheDir, "ffmpeg_${System.nanoTime()}.${target.id}")
    try {
        val arguments = buildFfmpegArguments(input, output, target, quality)
        val session = withTimeout(MEDIA_TIMEOUT_MS) { executeFfmpeg(arguments) }
        val returnCode = session.returnCode
        if (returnCode == null || !ReturnCode.isSuccess(returnCode)) {
            val detail = session.output.orEmpty().trim().takeLast(1200)
                .ifBlank { session.failStackTrace.orEmpty().trim().takeLast(1200) }
            error(
                if (detail.isBlank()) {
                    "FFmpeg 转换失败，请尝试其他输出格式"
                } else {
                    "FFmpeg 转换失败：$detail"
                },
            )
        }

        if (!output.exists() || output.length() == 0L) {
            error("FFmpeg 没有生成输出文件")
        }
        if (output.length() > MAX_MEDIA_OUTPUT_BYTES) {
            error("输出文件超过 256 MB，请降低质量或缩短时长")
        }

        return ConvertOutcome(
            listOf(
                ConvertedFile(
                    name = fileConvertOutputName(source.name, target),
                    mime = target.mime,
                    bytes = output.readBytes(),
                ),
            ),
        )
    } finally {
        input.delete()
        output.delete()
    }
}

private fun createFfmpegInput(context: Context, source: PickedFile): File {
    val sourceExtension = source.name.substringAfterLast('.', "").lowercase(Locale.US)
    val extension = sourceExtension.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        ?: source.format.id.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        ?: "input"
    val file = File.createTempFile("ffmpeg_in_", ".$extension", context.cacheDir)
    val copied = runCatching {
        context.contentResolver.openInputStream(source.uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } != null
    }.getOrDefault(false)
    if (!copied) {
        file.delete()
        error("无法读取文件")
    }
    return file
}

/** Converts a still image (currently JPEG 2000) to a Bitmap through FFmpeg. */
internal suspend fun decodeImageWithFfmpeg(
    context: Context,
    source: PickedFile,
): android.graphics.Bitmap? {
    val input = createFfmpegInput(context, source)
    val output = File(context.cacheDir, "ffmpeg_img_${System.nanoTime()}.png")
    return try {
        val arguments = arrayOf(
            "-y", "-hide_banner", "-loglevel", "error",
            "-i", input.absolutePath,
            "-frames:v", "1",
            "-c:v", "png",
            "-f", "image2",
            output.absolutePath,
        )
        val session = withTimeout(120_000L) { executeFfmpeg(arguments) }
        if (!ReturnCode.isSuccess(session.returnCode)) return null
        if (!output.exists() || output.length() == 0L) return null
        val bytes = output.readBytes()
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Throwable) {
        null
    } finally {
        input.delete()
        output.delete()
    }
}

private fun buildFfmpegArguments(
    input: File,
    output: File,
    target: ConvertFormat,
    quality: Int,
): Array<String> {
    val args = mutableListOf(
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        input.absolutePath,
    )

    val safeQuality = quality.coerceIn(60, 100)
    val bitrate = (64 + (safeQuality - 60) * 6.4).roundToInt()
    val vp9Crf = (40 - (safeQuality - 60) * 0.5).roundToInt().coerceIn(20, 40)
    val opusBitrate = (64 + (safeQuality - 60) * 3.2).roundToInt()
    val oggQuality = ((safeQuality - 40) / 10).coerceIn(0, 10)
    val aviQuality = (8 - (safeQuality - 60) * 0.1).roundToInt().coerceIn(2, 8)

    when (target.id) {
        "mp3" -> args += listOf(
            "-vn", "-map", "0:a:0", "-c:a", "libmp3lame", "-b:a", "${bitrate}k",
        )

        "m4a" -> args += listOf(
            "-vn", "-map", "0:a:0", "-c:a", "aac", "-b:a", "${bitrate}k",
        )

        "wav" -> args += listOf(
            "-vn", "-map", "0:a:0", "-c:a", "pcm_s16le",
        )

        "flac" -> args += listOf(
            "-vn", "-map", "0:a:0", "-c:a", "flac",
        )

        "ogg" -> args += listOf(
            "-vn", "-map", "0:a:0", "-c:a", "libvorbis", "-q:a", oggQuality.toString(),
        )

        "opus" -> args += listOf(
            "-vn", "-map", "0:a:0", "-c:a", "libopus", "-b:a", "${opusBitrate}k",
        )

        "mp4", "mov" -> {
            args += listOf(
                "-map", "0:v:0", "-map", "0:a?",
                "-c:v", "mpeg4", "-q:v", aviQuality.toString(),
                "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k",
            )
            if (target.id == "mp4") args += listOf("-movflags", "+faststart")
        }

        "mkv" -> args += listOf(
            "-map", "0:v:0", "-map", "0:a?",
            "-c:v", "mpeg4", "-q:v", aviQuality.toString(),
            "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k",
        )

        "webm" -> args += listOf(
            "-map", "0:v:0", "-map", "0:a?",
            "-c:v", "libvpx-vp9", "-crf", vp9Crf.toString(), "-b:v", "0",
            "-c:a", "libopus", "-b:a", "96k",
        )

        "avi" -> args += listOf(
            "-map", "0:v:0", "-map", "0:a?",
            "-c:v", "mpeg4", "-q:v", aviQuality.toString(),
            "-c:a", "libmp3lame", "-b:a", "128k",
        )

        "gif" -> args += listOf(
            "-map", "0:v:0", "-an",
            "-vf", "fps=12,scale=480:-1:flags=lanczos",
            "-loop", "0",
        )

        else -> error("暂不支持输出 ${target.label}")
    }

    args += output.absolutePath
    return args.toTypedArray()
}

private suspend fun executeFfmpeg(arguments: Array<String>): FFmpegSession =
    suspendCancellableCoroutine { continuation ->
        val holder = arrayOfNulls<FFmpegSession>(1)
        val session = FFmpegKit.executeWithArgumentsAsync(arguments) { completed ->
            if (continuation.isActive) continuation.resume(completed)
        }
        holder[0] = session
        continuation.invokeOnCancellation {
            val running = holder[0]
            if (running != null) {
                FFmpegKit.cancel(running.sessionId)
            } else {
                FFmpegKit.cancel()
            }
        }
    }
