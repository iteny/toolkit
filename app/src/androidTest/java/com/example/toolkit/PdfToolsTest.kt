package com.example.toolkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.toolkit.ui.tools.ConvertFormats
import com.example.toolkit.ui.tools.PickedFile
import com.example.toolkit.ui.tools.compressPdf
import com.example.toolkit.ui.tools.editPdfPages
import com.example.toolkit.ui.tools.fileConvertCommonTargets
import com.example.toolkit.ui.tools.insertPdfPages
import com.example.toolkit.ui.tools.mergePdfFiles
import com.example.toolkit.ui.tools.pageNumbersToRangeText
import com.example.toolkit.ui.tools.parsePdfPageOrder
import com.example.toolkit.ui.tools.parsePdfPageRange
import com.example.toolkit.ui.tools.reorderPdfPages
import com.example.toolkit.ui.tools.rotatePdf
import com.example.toolkit.ui.tools.splitPdf
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class PdfToolsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    private fun picked(name: String, bytes: ByteArray): PickedFile {
        val file = File(context.cacheDir, name)
        file.writeBytes(bytes)
        return PickedFile(
            uri = Uri.fromFile(file),
            name = name,
            size = bytes.size.toLong(),
            mime = "application/pdf",
            format = ConvertFormats.PDF,
        )
    }

    private fun pdfBytes(pages: Int): ByteArray {
        val document = PdfDocument()
        val paint = Paint().apply {
            textSize = 14f
            color = android.graphics.Color.BLACK
        }
        for (index in 1..pages) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(200, 200, index).create())
            page.canvas.drawText("Page $index", 20f, 40f, paint)
            document.finishPage(page)
        }
        return ByteArrayOutputStream().also {
            document.writeTo(it)
            document.close()
        }.toByteArray()
    }

    private fun labeledPdf(prefix: String, pages: Int): ByteArray {
        val document = PdfDocument()
        val paint = Paint().apply {
            textSize = 14f
            color = android.graphics.Color.BLACK
        }
        for (index in 1..pages) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(200, 200, index).create())
            page.canvas.drawText("$prefix $index", 20f, 40f, paint)
            document.finishPage(page)
        }
        return ByteArrayOutputStream().also {
            document.writeTo(it)
            document.close()
        }.toByteArray()
    }

    private fun pageCount(bytes: ByteArray): Int {
        val file = File.createTempFile("pdf-count-", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    return renderer.pageCount
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun mergesMultiplePdfsInOrder() {
        val first = picked("merge-a.pdf", pdfBytes(1))
        val second = picked("merge-b.pdf", pdfBytes(1))
        val merged = mergePdfFiles(context, listOf(first, second))
        assertEquals(1, merged.files.size)
        assertEquals(2, pageCount(merged.files.first().bytes))
    }

    @Test
    fun splitsEveryPage() {
        val source = picked("split.pdf", pdfBytes(2))
        val result = splitPdf(context, source)
        assertEquals(2, result.files.size)
        result.files.forEach { file ->
            assertEquals(1, pageCount(file.bytes))
        }
    }

    @Test
    fun mergeTargetOnlyShowsForMultiplePdfs() {
        val first = picked("target-a.pdf", pdfBytes(1))
        val second = picked("target-b.pdf", pdfBytes(1))
        assertFalse(fileConvertCommonTargets(listOf(first)).any { it.id == "merge_pdf" })
        assertTrue(fileConvertCommonTargets(listOf(first, second)).any { it.id == "merge_pdf" })
        assertTrue(fileConvertCommonTargets(listOf(first)).any { it.id == "split_pdf" })
    }

    @Test
    fun compressesEmbeddedImages() {
        val width = 900
        val height = 900
        val noisy = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val random = Random(7)
        for (y in 0 until height) {
            for (x in 0 until width) {
                noisy.setPixel(x, y, Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)))
            }
        }
        val pdf = PDDocument()
        try {
            val page = PDPage(PDRectangle(595f, 842f))
            pdf.addPage(page)
            val image = LosslessFactory.createFromImage(pdf, noisy)
            PDPageContentStream(pdf, page).use { stream ->
                stream.drawImage(image, 40f, 120f, 515f, 515f)
            }
            val out = ByteArrayOutputStream()
            pdf.save(out)
            val original = out.toByteArray()
            noisy.recycle()

            val source = picked("photo.pdf", original)
            val result = compressPdf(context, source, 60)
            val compressed = result.files.first()
            assertEquals("application/pdf", compressed.mime)
            assertTrue(compressed.bytes.size < original.size)
            assertEquals(1, pageCount(compressed.bytes))
            assertTrue(result.notice.orEmpty().contains("已重新压缩"))
        } finally {
            pdf.close()
        }
    }

    @Test
    fun compressTargetIsOfferedForPdf() {
        val source = picked("target-compress.pdf", pdfBytes(1))
        assertTrue(fileConvertCommonTargets(listOf(source)).any { it.id == "compress_pdf" })
    }

    @Test
    fun rotatesExtractsAndDeletesPages() {
        val source = picked("edit.pdf", pdfBytes(3))

        val rotated = rotatePdf(context, source, 90)
        val rotatedBytes = rotated.files.first().bytes
        assertEquals(3, pageCount(rotatedBytes))
        PDDocument.load(rotatedBytes.inputStream()).use { document ->
            assertEquals(90, document.getPage(0).rotation)
        }

        val extracted = editPdfPages(context, source, "1,3", keep = true)
        assertEquals(2, pageCount(extracted.files.first().bytes))
        assertTrue(extracted.notice.orEmpty().contains("已提取 2 页"))

        val deleted = editPdfPages(context, source, "2", keep = false)
        assertEquals(2, pageCount(deleted.files.first().bytes))
        assertTrue(deleted.notice.orEmpty().contains("剩余 2 页"))

        assertEquals(listOf(1, 2, 4), parsePdfPageRange("1-2, 4", 4))
        assertEquals(listOf(1, 3), parsePdfPageRange("3,1,3", 4))
        val invalid = runCatching { parsePdfPageRange("5", 3) }.exceptionOrNull()
        assertTrue(invalid?.message.orEmpty().contains("超出范围"))
    }

    @Test
    fun pageSelectionCompactsIntoRanges() {
        assertEquals("1-3,5,7-8", pageNumbersToRangeText(setOf(8, 7, 5, 3, 2, 1)))
        assertEquals("4", pageNumbersToRangeText(listOf(4)))
        assertEquals("", pageNumbersToRangeText(emptySet()))
        val selection = setOf(2, 3, 6, 9, 10, 11)
        val text = pageNumbersToRangeText(selection)
        assertEquals("2-3,6,9-11", text)
        assertEquals(
            selection.sorted(),
            parsePdfPageRange(text, 12),
        )
    }

    @Test
    fun insertsPagesAtStartAndEnd() {
        val base = picked("base.pdf", labeledPdf("Base", 2))
        val extra = picked("extra.pdf", labeledPdf("Extra", 3))

        val appended = insertPdfPages(context, base, extra, emptyList(), atStart = false)
        val appendedBytes = appended.files.first().bytes
        assertEquals(5, pageCount(appendedBytes))
        assertTrue(appended.notice.orEmpty().contains("已插入 3 页到结尾"))
        PDDocument.load(appendedBytes.inputStream()).use { document ->
            val text = PDFTextStripper().getText(document)
            val positions = listOf("Base 1", "Base 2", "Extra 1", "Extra 2", "Extra 3")
                .map { text.indexOf(it) }
            assertTrue("unexpected text: $text", positions.all { it >= 0 })
            assertTrue(positions.zipWithNext().all { (left, right) -> left < right })
        }

        val prepended = insertPdfPages(context, base, extra, listOf(2), atStart = true)
        val prependedBytes = prepended.files.first().bytes
        assertEquals(3, pageCount(prependedBytes))
        PDDocument.load(prependedBytes.inputStream()).use { document ->
            val text = PDFTextStripper().getText(document)
            val extra2 = text.indexOf("Extra 2")
            val base1 = text.indexOf("Base 1")
            val base2 = text.indexOf("Base 2")
            assertTrue("unexpected text: $text", extra2 in 0 until base1)
            assertTrue("unexpected text: $text", base1 < base2)
        }

        val invalid = runCatching {
            insertPdfPages(context, base, extra, listOf(9), atStart = false)
        }.exceptionOrNull()
        assertTrue(invalid?.message.orEmpty().contains("超出范围"))
    }

    @Test
    fun reordersPagesInRequestedOrder() {        val source = picked("order.pdf", pdfBytes(3))
        val result = reorderPdfPages(context, source, "3,1,2")
        val bytes = result.files.first().bytes
        assertEquals(3, pageCount(bytes))
        PDDocument.load(bytes.inputStream()).use { document ->
            val text = PDFTextStripper().getText(document)
            val third = text.indexOf("Page 3")
            val first = text.indexOf("Page 1")
            val second = text.indexOf("Page 2")
            assertTrue("unexpected text: $text", third in 0 until first)
            assertTrue("unexpected text: $text", first in 0 until second)
        }

        assertEquals(listOf(2, 1, 3), parsePdfPageOrder("2,1,3", 3))
        val invalid = runCatching { parsePdfPageOrder("1,2", 3) }.exceptionOrNull()
        assertTrue(invalid?.message.orEmpty().contains("不完整"))
        val duplicate = runCatching { parsePdfPageOrder("1,1,2", 3) }.exceptionOrNull()
        assertTrue(duplicate?.message.orEmpty().contains("不完整"))
    }
}
