package com.example.toolkit

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.File

@RunWith(AndroidJUnit4::class)
class Jpeg2000Test {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun jp2DecodesThroughFfmpegAndConvertsToPng() = runBlocking {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("image/sample.jp2")
            .use { it.readBytes() }
        val file = File(context.cacheDir, "sample.jp2").apply { writeBytes(bytes) }
        val source = PickedFile(
            uri = Uri.fromFile(file),
            name = "sample.jp2",
            size = bytes.size.toLong(),
            mime = "image/jp2",
            format = ConvertFormats.detect("sample.jp2", "image/jp2"),
        )
        assertEquals(ConvertFormats.JP2, source.format)
        assertTrue(ConvertFormats.targets(source.format).any { it.id == "png" })

        val outcome = fileConvertRun(context, source, ConvertFormats.PNG, 90)
        val png = outcome.files.first().bytes
        assertEquals(0x89.toByte(), png[0])
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width >= 200)
        assertTrue(bitmap.height >= 120)
        bitmap.recycle()
    }
}
