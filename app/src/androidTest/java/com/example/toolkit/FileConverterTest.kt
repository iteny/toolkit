package com.example.toolkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.toolkit.ui.tools.ConvertFormats
import com.example.toolkit.ui.tools.ConvertedFile
import com.example.toolkit.ui.tools.JxlCodec
import com.example.toolkit.ui.tools.PickedFile
import com.example.toolkit.ui.tools.fileConvertRun
import com.example.toolkit.ui.tools.fileConvertZip
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileConverterTest {

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

    private fun sampleImage(): ByteArray {
        val bitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(32, 120, 210))
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun startsWith(bytes: ByteArray, signature: ByteArray): Boolean {
        if (bytes.size < signature.size) return false
        return signature.indices.all { bytes[it] == signature[it] }
    }

    @Test
    fun imageTargetsProduceValidFiles() = runBlocking {
        val source = picked("unit.jpg", sampleImage(), "image/jpeg")
        val targets = ConvertFormats.targets(source.format)
        assertEquals(
            listOf("jpg", "png", "webp", "bmp", "tiff", "ico", "jxl", "pdf"),
            targets.map { it.id },
        )

        for (target in targets) {
            val outcome = fileConvertRun(context, source, target, 88)
            assertEquals(1, outcome.files.size)
            val data = outcome.files.first().bytes
            assertTrue("${target.label} should not be empty", data.isNotEmpty())
            when (target.id) {
                "png" -> assertTrue(
                    startsWith(data, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)),
                )

                "webp" -> assertTrue(
                    startsWith(data, "RIFF".toByteArray()) &&
                        String(data, 8, 4, Charsets.US_ASCII) == "WEBP",
                )

                "bmp" -> assertEquals("BM", String(data, 0, 2, Charsets.US_ASCII))
                "tiff" -> assertEquals("II", String(data, 0, 2, Charsets.US_ASCII))
                "ico" -> {
                    assertEquals(0.toByte(), data[0])
                    assertEquals(0.toByte(), data[1])
                    assertEquals(1.toByte(), data[2])
                    assertEquals(0.toByte(), data[3])
                }

                "pdf" -> assertEquals("%PDF", String(data, 0, 4, Charsets.US_ASCII))
                "jxl" -> assertTrue(JxlCodec.looksLikeJxl(data))
            }
        }
    }

    @Test
    fun jxlRoundTripsThroughEncoderAndDecoder() = runBlocking {
        val source = picked("unit-jxl.png", sampleImage(), "image/png")
        val encoded = fileConvertRun(context, source, ConvertFormats.JXL, 85)
            .files.first().bytes
        assertTrue(JxlCodec.looksLikeJxl(encoded))

        val jxlSource = picked("unit-jxl.jxl", encoded, "image/jxl")
        assertEquals(ConvertFormats.JXL, jxlSource.format)
        val decoded = fileConvertRun(context, jxlSource, ConvertFormats.PNG, 90)
            .files.first().bytes
        val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
        assertNotNull(bitmap)
        assertEquals(160, bitmap!!.width)
        assertEquals(90, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun pdfConvertsToImages() = runBlocking {
        val image = picked("unit.jpg", sampleImage(), "image/jpeg")
        val pdf = fileConvertRun(context, image, ConvertFormats.PDF, 90).files.first()
        val pdfSource = picked("unit.pdf", pdf.bytes, "application/pdf")

        for (target in listOf(ConvertFormats.PNG, ConvertFormats.JPG)) {
            val outcome = fileConvertRun(context, pdfSource, target, 85)
            assertTrue(outcome.files.isNotEmpty())
            val data = outcome.files.first().bytes
            if (target.id == "png") {
                assertTrue(startsWith(data, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
            } else {
                assertTrue(startsWith(data, byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
            }
        }
    }

    @Test
    fun markdownAndTextConversions() = runBlocking {
        val markdown = """
            # 文件格式转换

            这是 **重点** 和 `code`。

            - 第一项
            - 第二项
        """.trimIndent()
        val source = picked("unit.md", markdown.toByteArray(), "text/markdown")

        val html = fileConvertRun(context, source, ConvertFormats.HTML, 90)
            .files.first().bytes.toString(Charsets.UTF_8)
        assertTrue(html.contains("<h1>文件格式转换</h1>"))
        assertTrue(html.contains("<strong>重点</strong>"))
        assertTrue(html.contains("<li>第一项</li>"))

        val plain = fileConvertRun(context, source, ConvertFormats.TXT, 90)
            .files.first().bytes.toString(Charsets.UTF_8)
        assertTrue(plain.contains("文件格式转换"))
        assertFalse(plain.contains("# 文件格式转换"))

        val pdf = fileConvertRun(context, source, ConvertFormats.PDF, 90).files.first().bytes
        assertEquals("%PDF", String(pdf, 0, 4, Charsets.US_ASCII))

        // A generated text PDF must be renderable again, which catches broken pages
        // or unembedded fonts on the device.
        val rendered = fileConvertRun(
            context,
            picked("unit-text.pdf", pdf, "application/pdf"),
            ConvertFormats.PNG,
            90,
        )
        assertTrue(rendered.files.isNotEmpty())
        assertTrue(
            startsWith(
                rendered.files.first().bytes,
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            ),
        )

        val textSource = picked("unit.txt", "第一行\n第二行".toByteArray(), "text/plain")
        val textHtml = fileConvertRun(context, textSource, ConvertFormats.HTML, 90)
            .files.first().bytes.toString(Charsets.UTF_8)
        assertTrue(textHtml.contains("<pre"))
        assertTrue(textHtml.contains("第一行"))

        val htmlSource = picked(
            "unit-page.html",
            "<html><body><h1>标题</h1><p>网页正文</p></body></html>".toByteArray(),
            "text/html",
        )
        val htmlPdf = fileConvertRun(context, htmlSource, ConvertFormats.PDF, 90)
            .files.first().bytes
        assertEquals("%PDF", String(htmlPdf, 0, 4, Charsets.US_ASCII))
        val htmlRendered = fileConvertRun(
            context,
            picked("unit-page.pdf", htmlPdf, "application/pdf"),
            ConvertFormats.PNG,
            90,
        )
        assertTrue(htmlRendered.files.isNotEmpty())
    }

    @Test
    fun multiPagePdfExportsEveryPage() = runBlocking {
        val document = PdfDocument()
        for (index in 1..2) {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(300, 400, index).create(),
            )
            page.canvas.drawColor(if (index == 1) Color.WHITE else Color.LTGRAY)
            document.finishPage(page)
        }
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()

        val source = picked("unit-multi.pdf", output.toByteArray(), "application/pdf")
        val outcome = fileConvertRun(context, source, ConvertFormats.PNG, 90)
        assertEquals(2, outcome.files.size)
        outcome.files.forEach { file ->
            assertTrue(
                startsWith(file.bytes, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)),
            )
        }
    }

    @Test
    fun dataConversions() = runBlocking {
        val json = """
            [
              {"name": "张三", "city": "北京"},
              {"name": "李四", "city": "上海"}
            ]
        """.trimIndent()
        val jsonSource = picked("unit.json", json.toByteArray(), "application/json")
        val csv = fileConvertRun(context, jsonSource, ConvertFormats.CSV, 90)
            .files.first().bytes.toString(Charsets.UTF_8)
        assertTrue(csv.contains("张三"))
        assertTrue(csv.contains("北京"))
        assertTrue(csv.lineSequence().first().contains("name"))

        val csvSource = picked("unit.csv", "name,city\n张三,北京\n李四,上海".toByteArray(), "text/csv")
        val converted = fileConvertRun(context, csvSource, ConvertFormats.JSON, 90)
            .files.first().bytes.toString(Charsets.UTF_8)
        val array = JSONArray(converted)
        assertEquals(2, array.length())
        assertEquals("张三", array.getJSONObject(0).getString("name"))

        val csvToTsv = fileConvertRun(context, csvSource, ConvertFormats.TSV, 90)
            .files.first().bytes.toString(Charsets.UTF_8)
        assertTrue(csvToTsv.contains("name\tcity"))

        val tsvSource = picked("unit.tsv", "name\tcity\n张三\t北京".toByteArray(), "text/tab-separated-values")
        val tsvJson = fileConvertRun(context, tsvSource, ConvertFormats.JSON, 90)
            .files.first().bytes.toString(Charsets.UTF_8)
        assertEquals(1, JSONArray(tsvJson).length())

        val tsvToCsv = fileConvertRun(context, tsvSource, ConvertFormats.CSV, 90)
            .files.first().bytes.toString(Charsets.UTF_8)
        assertTrue(tsvToCsv.contains("name,city"))
    }

    @Test
    fun zipBundlesOutputs() {
        val zip = fileConvertZip(
            listOf(
                ConvertedFile("a.txt", "text/plain", "a".toByteArray()),
                ConvertedFile("b.txt", "text/plain", "b".toByteArray()),
            ),
            "unit",
        )
        assertEquals("unit_converted.zip", zip.name)
        assertTrue(zip.bytes.isNotEmpty())
    }
}
