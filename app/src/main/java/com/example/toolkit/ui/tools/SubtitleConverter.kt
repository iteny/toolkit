package com.example.toolkit.ui.tools

import android.content.Context
import java.util.Locale
import kotlin.math.max

private data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

private const val MAX_SUBTITLE_BYTES = 16L * 1024 * 1024

internal fun convertSubtitleFile(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
): ConvertOutcome {
    val text = fileConvertDecodeText(
        fileConvertReadBytes(context, source.uri, MAX_SUBTITLE_BYTES),
    )
    val cues = when (source.format.id) {
        "srt" -> parseSrt(text)
        "vtt" -> parseVtt(text)
        "ass" -> parseAss(text)
        "lrc" -> parseLrc(text)
        else -> error(ConvertFormats.unsupportedReason(source.format))
    }

    if (cues.isEmpty()) error("没有解析到字幕条目")
    val output = when (target.id) {
        "srt" -> toSrt(cues)
        "vtt" -> toVtt(cues)
        "ass" -> toAss(cues)
        "lrc" -> toLrc(cues)
        else -> error("暂不支持字幕转 ${target.label}")
    }

    return ConvertOutcome(
        listOf(
            ConvertedFile(
                name = fileConvertOutputName(source.name, target),
                mime = target.mime,
                bytes = output.toByteArray(Charsets.UTF_8),
            ),
        ),
    )
}

/* ------------------------------------------------------------------ */
/* SRT / VTT                                                          */
/* ------------------------------------------------------------------ */

private fun parseSrt(text: String): List<SubtitleCue> {
    val blocks = normalize(text).split(Regex("\\n\\s*\\n"))
    val cues = ArrayList<SubtitleCue>()
    blocks.forEach { block ->
        val lines = block.lines().filter { it.isNotBlank() }
        val timeIndex = lines.indexOfFirst { it.contains("-->") }
        if (timeIndex < 0) return@forEach
        val (start, end) = parseTimedRange(lines[timeIndex]) ?: return@forEach
        val content = lines.drop(timeIndex + 1)
            .joinToString("\n")
            .let(::cleanSubtitleText)
        if (content.isNotEmpty()) cues += SubtitleCue(start, end, content)
    }
    return cues.sortedBy { it.startMs }
}

private fun parseVtt(text: String): List<SubtitleCue> {
    val body = normalize(text)
        .replace(Regex("(?m)^WEBVTT.*$"), "")
        .replace(Regex("(?s)NOTE\\s.*?(?=\\n\\s*\\n|$)"), "")
    val blocks = body.split(Regex("\\n\\s*\\n"))
    val cues = ArrayList<SubtitleCue>()
    blocks.forEach { block ->
        val lines = block.lines().filter { it.isNotBlank() }
        val timeIndex = lines.indexOfFirst { it.contains("-->") }
        if (timeIndex < 0) return@forEach
        val (start, end) = parseTimedRange(lines[timeIndex]) ?: return@forEach
        val content = lines.drop(timeIndex + 1)
            .joinToString("\n")
            .let(::cleanSubtitleText)
        if (content.isNotEmpty()) cues += SubtitleCue(start, end, content)
    }
    return cues.sortedBy { it.startMs }
}

private fun parseTimedRange(line: String): Pair<Long, Long>? {
    val parts = line.split("-->")
    if (parts.size < 2) return null
    val start = parseClockTime(parts[0].trim()) ?: return null
    val end = parseClockTime(parts[1].trim().substringBefore(' ').trim()) ?: return null
    return start to end
}

private fun parseClockTime(value: String): Long? {
    val match = Regex("^(\\d{1,2}):(\\d{1,2}):(\\d{1,2})[.,](\\d{1,3})$").find(value)
        ?: Regex("^(\\d{1,2}):(\\d{1,2})[.,](\\d{1,3})$").find(value)
    if (match == null) return null

    return if (match.groupValues.size == 5) {
        val hours = match.groupValues[1].toLongOrNull() ?: 0
        val minutes = match.groupValues[2].toLongOrNull() ?: 0
        val seconds = match.groupValues[3].toLongOrNull() ?: 0
        val millis = match.groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: 0
        ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    } else {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0
        val seconds = match.groupValues[2].toLongOrNull() ?: 0
        val millis = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
        (minutes * 60 + seconds) * 1000 + millis
    }
}

/* ------------------------------------------------------------------ */
/* ASS / SSA                                                          */
/* ------------------------------------------------------------------ */

private fun parseAss(text: String): List<SubtitleCue> {
    val lines = normalize(text).lines()
    var inEvents = false
    var timeIndex = 1
    var textIndex = 9
    var fieldCount = 10
    val cues = ArrayList<SubtitleCue>()

    lines.forEach { raw ->
        val line = raw.trim()
        if (line.startsWith("[") && line.endsWith("]")) {
            inEvents = line.equals("[Events]", ignoreCase = true)
            return@forEach
        }
        if (!inEvents) return@forEach

        if (line.startsWith("Format:", ignoreCase = true)) {
            val fields = line.substringAfter(':').split(',').map { it.trim() }
            fieldCount = max(fields.size, 2)
            fields.forEachIndexed { index, name ->
                when (name.lowercase(Locale.US)) {
                    "start" -> timeIndex = index
                    "text" -> textIndex = index
                }
            }
            return@forEach
        }

        if (!line.startsWith("Dialogue:", ignoreCase = true)) return@forEach
        val body = line.substringAfter(':').trim()
        val parts = body.split(',', limit = fieldCount)
        if (parts.size <= max(timeIndex, textIndex)) return@forEach
        val start = parseAssTime(parts[timeIndex]) ?: return@forEach
        val end = parseAssTime(parts.getOrNull(timeIndex + 1).orEmpty()) ?: return@forEach
        val content = parts.drop(textIndex)
            .joinToString(",")
            .replace("\\N", "\n", ignoreCase = true)
            .replace("\\n", "\n", ignoreCase = true)
            .replace(Regex("\\{[^}]*}"), "")
            .let(::cleanSubtitleText)
        if (content.isNotEmpty()) cues += SubtitleCue(start, end, content)
    }
    return cues.sortedBy { it.startMs }
}

private fun parseAssTime(value: String): Long? {
    val match = Regex("^(\\d+):(\\d{1,2}):(\\d{1,2})[.](\\d{1,2})$")
        .find(value.trim()) ?: return null
    val hours = match.groupValues[1].toLongOrNull() ?: return null
    val minutes = match.groupValues[2].toLongOrNull() ?: return null
    val seconds = match.groupValues[3].toLongOrNull() ?: return null
    val centis = match.groupValues[4].padEnd(2, '0').take(2).toLongOrNull() ?: 0
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + centis * 10
}

/* ------------------------------------------------------------------ */
/* LRC                                                                */
/* ------------------------------------------------------------------ */

private fun parseLrc(text: String): List<SubtitleCue> {
    val entries = ArrayList<Pair<Long, String>>()
    normalize(text).lines().forEach { raw ->
        val line = raw.trim()
        val timestamps = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
            .findAll(line)
            .mapNotNull { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val fraction = match.groupValues[3]
                val millis = if (fraction.isBlank()) {
                    0L
                } else {
                    fraction.padEnd(3, '0').take(3).toLongOrNull() ?: 0
                }
                (minutes * 60 + seconds) * 1000 + millis
            }
            .toList()
        if (timestamps.isEmpty()) return@forEach
        val content = line.substringAfterLast(']').trim()
        if (content.isEmpty()) return@forEach
        timestamps.forEach { entries += it to content }
    }

    if (entries.isEmpty()) return emptyList()
    entries.sortBy { it.first }
    return entries.mapIndexed { index, (start, content) ->
        val next = entries.getOrNull(index + 1)?.first
        val end = when {
            next == null -> start + 5000
            next <= start -> start + 2000
            else -> next
        }
        SubtitleCue(start, end, content)
    }
}

/* ------------------------------------------------------------------ */
/* Output                                                             */
/* ------------------------------------------------------------------ */

private fun toSrt(cues: List<SubtitleCue>): String = buildString {
    cues.forEachIndexed { index, cue ->
        append(index + 1).append('\n')
        append(formatSrtTime(cue.startMs))
            .append(" --> ")
            .append(formatSrtTime(cue.endMs))
            .append('\n')
        append(cue.text).append("\n\n")
    }
}

private fun toVtt(cues: List<SubtitleCue>): String = buildString {
    append("WEBVTT\n\n")
    cues.forEach { cue ->
        append(formatVttTime(cue.startMs))
            .append(" --> ")
            .append(formatVttTime(cue.endMs))
            .append('\n')
        append(cue.text).append("\n\n")
    }
}

private fun toAss(cues: List<SubtitleCue>): String = buildString {
    append(
        """
        [Script Info]
        ScriptType: v4.00+
        PlayResX: 1920
        PlayResY: 1080

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,1,2,10,10,10,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text

        """.trimIndent(),
    )
    append('\n')
    cues.forEach { cue ->
        val text = cue.text.replace("\n", "\\N")
        append("Dialogue: 0,")
            .append(formatAssTime(cue.startMs)).append(',')
            .append(formatAssTime(cue.endMs))
            .append(",Default,,0,0,0,,")
            .append(text)
            .append('\n')
    }
}

private fun toLrc(cues: List<SubtitleCue>): String = buildString {
    cues.forEach { cue ->
        val text = cue.text.replace("\r\n", "\n").replace('\r', '\n')
        val stamp = formatLrcTime(cue.startMs)
        text.lines().forEach { line ->
            append('[').append(stamp).append(']').append(line).append('\n')
        }
    }
}

private fun formatSrtTime(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0)
    val hours = safe / 3_600_000
    val minutes = (safe / 60_000) % 60
    val seconds = (safe / 1000) % 60
    val millis = safe % 1000
    return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
}

private fun formatVttTime(milliseconds: Long): String =
    formatSrtTime(milliseconds).replace(',', '.')

private fun formatAssTime(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0)
    val hours = safe / 3_600_000
    val minutes = (safe / 60_000) % 60
    val seconds = (safe / 1000) % 60
    val centis = (safe % 1000) / 10
    return String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
}

private fun formatLrcTime(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0)
    val minutes = safe / 60_000
    val seconds = (safe / 1000) % 60
    val centis = (safe % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
}

/* ------------------------------------------------------------------ */
/* Helpers                                                            */
/* ------------------------------------------------------------------ */

private fun normalize(text: String): String =
    text.removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')

private fun cleanSubtitleText(text: String): String = text
    .replace(Regex("(?i)<br\\s*/?>"), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .trim()
