package com.example.toolkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.toolkit.ui.tools.ConvertFormats
import com.example.toolkit.ui.tools.PickedFile
import com.example.toolkit.ui.tools.encodeIco
import com.example.toolkit.ui.tools.encodeTiff
import com.example.toolkit.ui.tools.fileConvertRun
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class CustomImageFormatsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    private fun picked(name: String, bytes: ByteArray, mime: String = "application/octet-stream"): PickedFile {
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

    private fun toPng(source: PickedFile): ByteArray = runBlocking {
        fileConvertRun(context, source, ConvertFormats.PNG, 90).files.first().bytes
    }

    private fun assertPng(bytes: ByteArray) {
        assertTrue(bytes.size > 16)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1])
        assertEquals(0x4E.toByte(), bytes[2])
        assertEquals(0x47.toByte(), bytes[3])
    }

    private fun smallBitmap(width: Int = 4, height: Int = 4): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, Color.rgb(40 + x * 30, 80 + y * 20, 150))
            }
        }
        return bitmap
    }

    @Test
    fun tgaDecodes() {
        val width = 2
        val height = 2
        val header = ByteArray(18)
        header[2] = 2
        header[12] = width.toByte()
        header[14] = height.toByte()
        header[16] = 24
        header[17] = 0x20
        val pixels = byteArrayOf(
            0, 0, 255.toByte(), 0, 255.toByte(), 0,
            255.toByte(), 0, 0, 255.toByte(), 255.toByte(), 255.toByte(),
        )
        assertPng(toPng(picked("custom.tga", header + pixels, "image/x-tga")))
    }

    @Test
    fun pnmDecodes() {
        val body = "P6\n2 2\n255\n".toByteArray() + byteArrayOf(
            255.toByte(), 0, 0, 0, 255.toByte(), 0,
            0, 0, 255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
        )
        assertPng(toPng(picked("custom.ppm", body, "image/x-portable-pixmap")))
    }

    @Test
    fun qoiDecodes() {
        val header = ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN).apply {
            put("qoif".toByteArray())
            putInt(2)
            putInt(2)
            put(4)
            put(0)
        }.array()
        val pixels = byteArrayOf(
            0xFF.toByte(), 255.toByte(), 0, 0, 255.toByte(),
            0xC2.toByte(),
        )
        assertPng(toPng(picked("custom.qoi", header + pixels, "image/qoi")))
    }

    @Test
    fun psdDecodesComposite() {
        val output = ByteArrayOutputStream()
        output.write("8BPS".toByteArray())
        writeShortBe(output, 1)
        repeat(6) { output.write(0) }
        writeShortBe(output, 3)
        writeIntBe(output, 2)
        writeIntBe(output, 2)
        writeShortBe(output, 8)
        writeShortBe(output, 3)
        writeIntBe(output, 0)
        writeIntBe(output, 0)
        writeIntBe(output, 0)
        writeShortBe(output, 0)
        output.write(byteArrayOf(255.toByte(), 0, 0, 255.toByte()))
        output.write(byteArrayOf(0, 255.toByte(), 0, 255.toByte()))
        output.write(byteArrayOf(0, 0, 255.toByte(), 255.toByte()))
        assertPng(toPng(picked("custom.psd", output.toByteArray(), "image/vnd.adobe.photoshop")))
    }

    @Test
    fun psdRleDecodes() {
        val output = ByteArrayOutputStream()
        output.write("8BPS".toByteArray())
        writeShortBe(output, 1)
        repeat(6) { output.write(0) }
        writeShortBe(output, 3)
        writeIntBe(output, 2)
        writeIntBe(output, 2)
        writeShortBe(output, 8)
        writeShortBe(output, 3)
        writeIntBe(output, 0)
        writeIntBe(output, 0)
        writeIntBe(output, 0)
        writeShortBe(output, 1)
        repeat(6) { writeShortBe(output, 3) }
        // Two rows per channel, PackBits literal packets.
        output.write(byteArrayOf(1, 255.toByte(), 0))
        output.write(byteArrayOf(1, 0, 255.toByte()))
        output.write(byteArrayOf(1, 0, 255.toByte()))
        output.write(byteArrayOf(1, 255.toByte(), 0))
        output.write(byteArrayOf(1, 0, 0))
        output.write(byteArrayOf(1, 0, 255.toByte()))
        assertPng(toPng(picked("custom-rle.psd", output.toByteArray(), "image/vnd.adobe.photoshop")))
    }

    @Test
    fun tiffRoundTripAndOutput() {
        val bitmap = smallBitmap()
        val tiff = encodeTiff(bitmap)
        bitmap.recycle()
        val source = picked("custom.tif", tiff, "image/tiff")
        assertPng(toPng(source))

        val pngSource = picked("custom-source.png", pngBytes(smallBitmap()), "image/png")
        val converted = runBlocking {
            fileConvertRun(context, pngSource, ConvertFormats.TIFF, 90).files.first().bytes
        }
        assertEquals("II", String(converted, 0, 2, Charsets.US_ASCII))
        assertPng(toPng(picked("custom-output.tif", converted, "image/tiff")))
    }

    @Test
    fun compressedTiffDecodes() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        listOf(
            "tiff_lzw.tif",
            "tiff_packbits.tif",
            "tiff_deflate.tif",
            "tiff_none.tif",
        ).forEach { name ->
            val bytes = assets.open(name).use { it.readBytes() }
            assertPng(toPng(picked(name, bytes, "image/tiff")))
        }
    }

    @Test
    fun icoRoundTripAndOutput() {
        val ico = encodeIco(smallBitmap())
        assertPng(toPng(picked("custom.ico", ico, "image/x-icon")))

        val pngSource = picked("custom-icon.png", pngBytes(smallBitmap()), "image/png")
        val converted = runBlocking {
            fileConvertRun(context, pngSource, ConvertFormats.ICO, 90).files.first().bytes
        }
        assertEquals(0.toByte(), converted[0])
        assertEquals(1.toByte(), converted[2])
        assertPng(toPng(picked("custom-output.ico", converted, "image/x-icon")))
    }

    @Test
    fun hdrDecodes() {
        val header = "#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y 2 +X 2\n".toByteArray()
        val body = byteArrayOf(
            128.toByte(), 128.toByte(), 128.toByte(), 129.toByte(),
            128.toByte(), 0, 0, 129.toByte(),
            0, 128.toByte(), 0, 129.toByte(),
            0, 0, 128.toByte(), 129.toByte(),
        )
        assertPng(toPng(picked("custom.hdr", header + body, "image/vnd.radiance")))
    }

    @Test
    fun ddsUncompressedDecodes() {
        val header = ByteArray(128)
        header[0] = 'D'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = 'S'.code.toByte()
        header[3] = ' '.code.toByte()
        putIntLe(header, 12, 2)
        putIntLe(header, 16, 2)
        putIntLe(header, 88, 32)
        putIntLe(header, 92, 0x00FF0000)
        putIntLe(header, 96, 0x0000FF00)
        putIntLe(header, 100, 0x000000FF)
        putIntLe(header, 104, 0xFF000000.toInt())
        val pixels = ByteArray(16) {
            when (it % 4) {
                0 -> 255.toByte()
                1 -> 0
                2 -> 0
                else -> 255.toByte()
            }
        }
        assertPng(toPng(picked("custom.dds", header + pixels, "image/vnd-ms.dds")))
    }

    @Test
    fun pcxDecodes() {
        val header = ByteArray(128)
        header[0] = 0x0A
        header[1] = 1
        header[3] = 8
        putShortLe(header, 8, 1)
        putShortLe(header, 10, 1)
        header[65] = 1
        putShortLe(header, 66, 2)
        val data = byteArrayOf(0, 1, 2, 3)
        val palette = ByteArray(769)
        palette[0] = 0x0C
        for (index in 0 until 4) {
            palette[1 + index * 3] = (index * 60).toByte()
            palette[2 + index * 3] = (255 - index * 50).toByte()
            palette[3 + index * 3] = (index * 30).toByte()
        }
        assertPng(toPng(picked("custom.pcx", header + data + palette, "image/x-pcx")))
    }

    private fun pngBytes(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }.toByteArray()

    private fun writeShortBe(output: ByteArrayOutputStream, value: Int) {
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    private fun writeIntBe(output: ByteArrayOutputStream, value: Int) {
        output.write((value ushr 24) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    private fun putShortLe(data: ByteArray, offset: Int, value: Int) {
        data[offset] = value.toByte()
        data[offset + 1] = (value ushr 8).toByte()
    }

    private fun putIntLe(data: ByteArray, offset: Int, value: Int) {
        data[offset] = value.toByte()
        data[offset + 1] = (value ushr 8).toByte()
        data[offset + 2] = (value ushr 16).toByte()
        data[offset + 3] = (value ushr 24).toByte()
    }
}
