package com.example.toolkit

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.toolkit.ui.tools.ConvertFormats
import com.example.toolkit.ui.tools.PickedFile
import com.example.toolkit.ui.tools.fileConvertRun
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MediaConverterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    private fun picked(name: String, bytes: ByteArray, mime: String): PickedFile {
        val file = File(context.cacheDir, name)
        file.writeBytes(bytes)
        return PickedFile(
            uri = Uri.fromFile(file),
            name = name,
            size = bytes.size.toLong(),
            mime = mime,
            format = ConvertFormats.detect(name, mime),
        )
    }

    private fun wavBytes(seconds: Double = 0.5, sampleRate: Int = 8000): ByteArray {
        val samples = (seconds * sampleRate).toInt()
        val dataSize = samples * 2
        val output = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(dataSize)
        }
        output.write(header.array())
        for (index in 0 until samples) {
            val value = (sin(2 * PI * 440 * index / sampleRate) * 12000).toInt()
            output.write(value and 0xFF)
            output.write((value shr 8) and 0xFF)
        }
        return output.toByteArray()
    }

    private fun convert(source: PickedFile, target: com.example.toolkit.ui.tools.ConvertFormat): ByteArray =
        runBlocking {
            fileConvertRun(context, source, target, 90).files.first().bytes
        }

    @Test
    fun audioFormatsConvert() {
        val source = picked("unit-audio.wav", wavBytes(), "audio/wav")
        val targets = listOf(
            ConvertFormats.MP3,
            ConvertFormats.M4A,
            ConvertFormats.FLAC,
            ConvertFormats.OGG,
            ConvertFormats.OPUS,
        )
        targets.forEach { target ->
            val bytes = convert(source, target)
            assertTrue("${target.label} should not be empty", bytes.isNotEmpty())
            when (target.id) {
                "mp3" -> assertTrue(
                    bytes[0] == 0x49.toByte() || bytes[0] == 0xFF.toByte(),
                )

                "m4a" -> assertEquals("ftyp", String(bytes, 4, 4, Charsets.US_ASCII))
                "flac" -> assertEquals("fLaC", String(bytes, 0, 4, Charsets.US_ASCII))
                "ogg", "opus" -> assertEquals("OggS", String(bytes, 0, 4, Charsets.US_ASCII))
            }
        }
    }

    @Test
    fun videoExtractsAudioAndGif() {
        val generated = File(context.cacheDir, "unit-source.mp4")
        val args = arrayOf(
            "-y", "-hide_banner", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc=duration=1:size=160x120:rate=10",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
            "-c:v", "mpeg4", "-c:a", "aac", "-shortest",
            generated.absolutePath,
        )
        val session = FFmpegKit.executeWithArguments(args)
        assertNotNull(session.returnCode)
        assertTrue("test video generation failed: ${session.output}", ReturnCode.isSuccess(session.returnCode))

        val source = picked("unit-source.mp4", generated.readBytes(), "video/mp4")
        val gif = convert(source, ConvertFormats.VIDEO_GIF)
        assertTrue(gif.size > 6)
        assertEquals("GIF", String(gif, 0, 3, Charsets.US_ASCII))

        val mp3 = convert(source, ConvertFormats.MP3)
        assertTrue(mp3.isNotEmpty())

        val mp4 = convert(source, ConvertFormats.MP4)
        assertEquals("ftyp", String(mp4, 4, 4, Charsets.US_ASCII))
    }
}
