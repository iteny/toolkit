package com.example.toolkit

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.toolkit.ui.tools.ConvertFormats
import com.example.toolkit.ui.tools.PickedFile
import com.example.toolkit.ui.tools.RawDecoder
import com.example.toolkit.ui.tools.fileConvertRun
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.max

/**
 * Uses the CC0 Kodak DC50 sample from raw.pixls.us to verify the LibRaw JNI
 * bridge and the RAW conversion pipeline.
 */
@RunWith(AndroidJUnit4::class)
class RawDecoderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    private fun sampleBytes(): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("raw/kodak_dc50.kdc")
            .use { it.readBytes() }

    @Test
    fun decodesKodakRawSample() {
        val bitmap = RawDecoder.decode(sampleBytes(), 1200)
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width > 0)
        assertTrue(bitmap.height > 0)
        assertTrue(max(bitmap.width, bitmap.height) <= 1200)
        bitmap.recycle()
    }

    @Test
    fun rawOffersImageTargetsAndConvertsToPng() = runBlocking {
        assertTrue(ConvertFormats.targets(ConvertFormats.RAW).any { it.id == "png" })

        val bytes = sampleBytes()
        val file = File(context.cacheDir, "kodak_dc50.kdc").apply { writeBytes(bytes) }
        val source = PickedFile(
            uri = Uri.fromFile(file),
            name = "kodak_dc50.kdc",
            size = bytes.size.toLong(),
            mime = "image/x-raw",
            format = ConvertFormats.RAW,
        )
        val outcome = fileConvertRun(context, source, ConvertFormats.PNG, 90)
        val png = outcome.files.first().bytes
        assertEquals(0x89.toByte(), png[0])
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertNotNull(bitmap)
        bitmap!!.recycle()
    }
}
