package com.example.toolkit.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.IdentityHashMap
import kotlin.math.max

private const val MAX_PDF_SPLIT_PAGES = 100
private const val MAX_PDF_SPLIT_BYTES = 128L * 1024 * 1024
private const val MAX_PDF_MERGE_BYTES = 256L * 1024 * 1024

private const val PDF_COMPRESS_MAX_SIDE = 2000
private const val PDF_COMPRESS_MIN_PIXELS = 180_000
private const val PDF_COMPRESS_MAX_PIXELS = 48_000_000

internal fun mergePdfFiles(
    context: Context,
    sources: List<PickedFile>,
): ConvertOutcome {
    if (sources.size < 2) error("合并 PDF 至少需要两个文件")
    ensurePdfBox(context)
    val temps = ArrayList<File>()
    try {
        val output = ByteArrayOutputStream()
        val merger = PDFMergerUtility().apply {
            destinationStream = output
        }
        sources.forEach { source ->
            val temp = copyPdfToCache(context, source)
            temps += temp
            merger.addSource(temp)
        }
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        val bytes = output.toByteArray()
        if (bytes.isEmpty()) error("合并后的 PDF 为空")
        if (bytes.size > MAX_PDF_MERGE_BYTES) error("合并后的 PDF 超过 256 MB")
        val base = fileConvertBaseName(sources.first().name)
        return ConvertOutcome(
            listOf(
                ConvertedFile(
                    name = "${base}_merged.pdf",
                    mime = "application/pdf",
                    bytes = bytes,
                ),
            ),
        )
    } finally {
        temps.forEach { it.delete() }
    }
}

internal fun splitPdf(context: Context, source: PickedFile): ConvertOutcome {
    ensurePdfBox(context)
    val temp = copyPdfToCache(context, source)
    var document: PDDocument? = null
    val splitDocuments = ArrayList<PDDocument>()
    try {
        document = PDDocument.load(temp)
        val splitter = Splitter().apply { setSplitAtPage(1) }
        splitDocuments += splitter.split(document)

        val base = fileConvertBaseName(source.name)
        val files = ArrayList<ConvertedFile>()
        var totalBytes = 0L
        splitDocuments.take(MAX_PDF_SPLIT_PAGES).forEachIndexed { index, pageDocument ->
            val output = ByteArrayOutputStream()
            pageDocument.save(output)
            val bytes = output.toByteArray()
            totalBytes += bytes.size
            if (totalBytes > MAX_PDF_SPLIT_BYTES) {
                error("拆分结果超过 128 MB")
            }
            files += ConvertedFile(
                name = "${base}_p${index + 1}.pdf",
                mime = "application/pdf",
                bytes = bytes,
            )
        }
        if (files.isEmpty()) error("PDF 没有可拆分的页面")
        val notice = if (splitDocuments.size > MAX_PDF_SPLIT_PAGES) {
            "PDF 共 ${splitDocuments.size} 页，已拆分前 $MAX_PDF_SPLIT_PAGES 页"
        } else {
            null
        }
        return ConvertOutcome(files, notice)
    } finally {
        splitDocuments.forEach { runCatching { it.close() } }
        runCatching { document?.close() }
        temp.delete()
    }
}

/**
 * Re-encodes embedded raster images as JPEG and rewrites the document.
 * Text, vector art and transparency are left untouched; pages without
 * re-compressible images are simply kept as they are.
 */
internal fun compressPdf(context: Context, source: PickedFile, quality: Int): ConvertOutcome {
    ensurePdfBox(context)
    val original = copyPdfToCache(context, source)
    val originalSize = original.length()
    var document: PDDocument? = null
    try {
        document = PDDocument.load(original)
        val jpegQuality = quality.coerceIn(45, 95) / 100f
        val replaced = IdentityHashMap<Any, PDImageXObject>()
        var optimized = 0
        var skipped = 0

        for (page in document.pages) {
            val resources = runCatching { page.resources }.getOrNull() ?: continue
            val names = runCatching { resources.xObjectNames.toList() }.getOrDefault(emptyList())
            for (name in names) {
                val xObject = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
                if (xObject !is PDImageXObject) continue
                val key: Any = runCatching { xObject.cosObject }.getOrElse { xObject }
                replaced[key]?.let { existing ->
                    runCatching { resources.put(name, existing) }
                    continue
                }
                if (xObject.isStencil) {
                    skipped += 1
                    continue
                }
                val pixels = xObject.width.toLong() * xObject.height.toLong()
                if (pixels < PDF_COMPRESS_MIN_PIXELS) continue
                if (pixels > PDF_COMPRESS_MAX_PIXELS) {
                    skipped += 1
                    continue
                }

                val longest = max(xObject.width, xObject.height)
                val subsampling = max(1, longest / PDF_COMPRESS_MAX_SIDE)
                val bitmap = runCatching {
                    xObject.getImage(Rect(0, 0, xObject.width, xObject.height), subsampling)
                }.getOrNull() ?: continue
                if (bitmap.hasAlpha() && hasTranslucentPixels(bitmap)) {
                    bitmap.recycle()
                    skipped += 1
                    continue
                }
                val scaled = downscale(bitmap, PDF_COMPRESS_MAX_SIDE)
                val encoded = runCatching {
                    JPEGFactory.createFromImage(document, scaled, jpegQuality)
                }.getOrNull()
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
                if (encoded == null) {
                    skipped += 1
                    continue
                }
                replaced[key] = encoded
                runCatching { resources.put(name, encoded) }
                optimized += 1
            }
        }

        val output = ByteArrayOutputStream()
        document.save(output)
        val compressed = output.toByteArray()
        runCatching { document.close() }
        document = null
        if (compressed.isEmpty()) error("压缩后的 PDF 为空")
        if (compressed.size >= originalSize) {
            if (optimized == 0) {
                error("这个 PDF 没有可重新压缩的图片，体积无法继续减小")
            }
            error("重新压缩后体积没有变小，已保留原文件")
        }
        val base = fileConvertBaseName(source.name)
        val saved = originalSize - compressed.size
        val notice = buildString {
            append("已重新压缩 $optimized 张图片，减少 ${fileConvertFormatSize(saved)}")
            if (skipped > 0) append("，$skipped 张带透明或超大的图片保持原样")
        }
        return ConvertOutcome(
            files = listOf(
                ConvertedFile(
                    name = "${base}_compressed.pdf",
                    mime = "application/pdf",
                    bytes = compressed,
                ),
            ),
            notice = notice,
        )
    } finally {
        runCatching { document?.close() }
        original.delete()
    }
}

private fun hasTranslucentPixels(bitmap: Bitmap): Boolean {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return false
    val stepX = max(1, width / 64)
    val stepY = max(1, height / 64)
    val row = IntArray(width)
    var y = 0
    while (y < height) {
        bitmap.getPixels(row, 0, width, 0, y, width, 1)
        var x = 0
        while (x < width) {
            if (row[x] ushr 24 != 0xFF) return true
            x += stepX
        }
        y += stepY
    }
    return false
}

internal fun rotatePdf(
    context: Context,
    source: PickedFile,
    degrees: Int,
): ConvertOutcome {
    ensurePdfBox(context)
    val normalized = ((degrees % 360) + 360) % 360
    if (normalized == 0 || normalized % 90 != 0) {
        error("旋转角度必须是 90 的倍数")
    }
    val temp = copyPdfToCache(context, source)
    var document: PDDocument? = null
    try {
        document = PDDocument.load(temp)
        val pages = document.numberOfPages
        if (pages <= 0) error("PDF 没有可旋转的页面")
        for (page in document.pages) {
            page.rotation = (page.rotation + normalized) % 360
        }
        val output = ByteArrayOutputStream()
        document.save(output)
        val bytes = output.toByteArray()
        val base = fileConvertBaseName(source.name)
        return ConvertOutcome(
            files = listOf(
                ConvertedFile(
                    name = "${base}_rotated.pdf",
                    mime = "application/pdf",
                    bytes = bytes,
                ),
            ),
            notice = "已顺时针旋转 $normalized°，共 $pages 页",
        )
    } finally {
        runCatching { document?.close() }
        temp.delete()
    }
}

internal fun editPdfPages(
    context: Context,
    source: PickedFile,
    rangeText: String,
    keep: Boolean,
): ConvertOutcome {
    ensurePdfBox(context)
    val temp = copyPdfToCache(context, source)
    var document: PDDocument? = null
    try {
        document = PDDocument.load(temp)
        val total = document.numberOfPages
        if (total <= 0) error("PDF 没有可处理的页面")
        val pages = parsePdfPageRange(rangeText, total)
        val base = fileConvertBaseName(source.name)

        if (keep) {
            val output = PDDocument()
            try {
                pages.forEach { page -> output.importPage(document.getPage(page - 1)) }
                val buffer = ByteArrayOutputStream()
                output.save(buffer)
                val bytes = buffer.toByteArray()
                if (bytes.isEmpty()) error("提取后的 PDF 为空")
                return ConvertOutcome(
                    files = listOf(
                        ConvertedFile(
                            name = "${base}_pages.pdf",
                            mime = "application/pdf",
                            bytes = bytes,
                        ),
                    ),
                    notice = "已提取 ${pages.size} 页，共 $total 页",
                )
            } finally {
                runCatching { output.close() }
            }
        }

        val remaining = (1..total).count { it !in pages }
        if (remaining == 0) error("不能删除全部页面")
        pages.sortedDescending().forEach { document.removePage(it - 1) }
        val buffer = ByteArrayOutputStream()
        document.save(buffer)
        val bytes = buffer.toByteArray()
        return ConvertOutcome(
            files = listOf(
                ConvertedFile(
                    name = "${base}_deleted.pdf",
                    mime = "application/pdf",
                    bytes = bytes,
                ),
            ),
            notice = "已删除 ${pages.size} 页，剩余 $remaining 页",
        )
    } finally {
        runCatching { document?.close() }
        temp.delete()
    }
}

/**
 * Inserts selected pages of [insert] into [base] at the beginning or at the end.
 * An empty [pages] list means "all pages of the inserted PDF".
 */
internal fun insertPdfPages(
    context: Context,
    base: PickedFile,
    insert: PickedFile,
    pages: List<Int>,
    atStart: Boolean,
): ConvertOutcome {
    ensurePdfBox(context)
    val baseTemp = copyPdfToCache(context, base)
    val insertTemp = copyPdfToCache(context, insert)
    var baseDocument: PDDocument? = null
    var insertDocument: PDDocument? = null
    val output = PDDocument()
    try {
        val baseDoc = PDDocument.load(baseTemp)
        baseDocument = baseDoc
        val insertDoc = PDDocument.load(insertTemp)
        insertDocument = insertDoc

        val baseTotal = baseDoc.numberOfPages
        if (baseTotal <= 0) error("当前 PDF 没有页面")
        val insertTotal = insertDoc.numberOfPages
        if (insertTotal <= 0) error("要插入的 PDF 没有页面")

        val invalid = pages.firstOrNull { it < 1 || it > insertTotal }
        if (invalid != null) error("插入页码 $invalid 超出范围（共 $insertTotal 页）")
        val selected = pages.distinct().sorted().ifEmpty { (1..insertTotal).toList() }

        val importBase = {
            for (index in 1..baseTotal) output.importPage(baseDoc.getPage(index - 1))
        }
        val importInserted = {
            selected.forEach { page -> output.importPage(insertDoc.getPage(page - 1)) }
        }
        if (atStart) {
            importInserted()
            importBase()
        } else {
            importBase()
            importInserted()
        }

        val buffer = ByteArrayOutputStream()
        output.save(buffer)
        val bytes = buffer.toByteArray()
        if (bytes.isEmpty()) error("插入后的 PDF 为空")
        val total = output.numberOfPages
        val baseName = fileConvertBaseName(base.name)
        return ConvertOutcome(
            files = listOf(
                ConvertedFile(
                    name = "${baseName}_inserted.pdf",
                    mime = "application/pdf",
                    bytes = bytes,
                ),
            ),
            notice = "已插入 ${selected.size} 页" +
                (if (atStart) "到开头" else "到结尾") +
                "，共 $total 页",
        )
    } finally {
        runCatching { output.close() }
        runCatching { insertDocument?.close() }
        runCatching { baseDocument?.close() }
        insertTemp.delete()
        baseTemp.delete()
    }
}

internal fun reorderPdfPages(
    context: Context,
    source: PickedFile,
    orderText: String,
): ConvertOutcome {
    ensurePdfBox(context)
    val temp = copyPdfToCache(context, source)
    var document: PDDocument? = null
    try {
        document = PDDocument.load(temp)
        val total = document.numberOfPages
        if (total <= 0) error("PDF 没有可排序的页面")
        val order = parsePdfPageOrder(orderText, total)
        val output = PDDocument()
        try {
            order.forEach { page -> output.importPage(document.getPage(page - 1)) }
            val buffer = ByteArrayOutputStream()
            output.save(buffer)
            val bytes = buffer.toByteArray()
            if (bytes.isEmpty()) error("排序后的 PDF 为空")
            val base = fileConvertBaseName(source.name)
            return ConvertOutcome(
                files = listOf(
                    ConvertedFile(
                        name = "${base}_reordered.pdf",
                        mime = "application/pdf",
                        bytes = bytes,
                    ),
                ),
                notice = "已按新顺序输出 $total 页",
            )
        } finally {
            runCatching { output.close() }
        }
    } finally {
        runCatching { document?.close() }
        temp.delete()
    }
}

internal fun parsePdfPageOrder(text: String, pageCount: Int): List<Int> {
    val order = text.split(',', '，', ';', '；', ' ', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { token -> token.toIntOrNull() ?: error("页码格式不正确：$token") }
    if (order.size != pageCount || order.toSet() != (1..pageCount).toSet()) {
        error("页面顺序不完整，需要包含 1-$pageCount 的每一页")
    }
    return order
}

/**
 * Parses ranges such as "1-3,5" or "2,4-6" into ascending, de-duplicated
 * 1-based page numbers.
 */
internal fun parsePdfPageRange(text: String, pageCount: Int): List<Int> {
    val pages = sortedSetOf<Int>()
    text.split(',', '，', ';', '；', '、', ' ', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { token ->
            val dash = token.indexOf('-')
            if (dash > 0 && dash < token.lastIndex) {
                val start = token.substring(0, dash).trim().toIntOrNull()
                val end = token.substring(dash + 1).trim().toIntOrNull()
                if (start == null || end == null) error("页码格式不正确：$token")
                val range = if (start <= end) start..end else end..start
                range.forEach { pages += it }
            } else {
                val page = token.toIntOrNull() ?: error("页码格式不正确：$token")
                pages += page
            }
        }
    if (pages.isEmpty()) error("请输入页码范围，例如 1-3,5")
    val invalid = pages.firstOrNull { it < 1 || it > pageCount }
    if (invalid != null) error("页码 $invalid 超出范围（共 $pageCount 页）")
    return pages.toList()
}

/**
 * Compacts a set of 1-based page numbers into a range string such as
 * "1-3,5,7-8" so the existing parser can consume the UI selection.
 */
internal fun pageNumbersToRangeText(pages: Collection<Int>): String {
    val sorted = pages.filter { it >= 1 }.distinct().sorted()
    if (sorted.isEmpty()) return ""
    val output = StringBuilder()
    var start = sorted.first()
    var previous = start

    fun appendRange() {
        if (output.isNotEmpty()) output.append(',')
        if (start == previous) {
            output.append(start)
        } else {
            output.append(start).append('-').append(previous)
        }
    }

    for (page in sorted.drop(1)) {
        if (page == previous + 1) {
            previous = page
        } else {
            appendRange()
            start = page
            previous = page
        }
    }
    appendRange()
    return output.toString()
}

private fun downscale(bitmap: Bitmap, maxSide: Int): Bitmap {    val longest = max(bitmap.width, bitmap.height)
    if (longest <= maxSide) return bitmap
    val scale = maxSide.toFloat() / longest
    val width = max(1, (bitmap.width * scale).toInt())
    val height = max(1, (bitmap.height * scale).toInt())
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

private fun copyPdfToCache(context: Context, source: PickedFile): File {
    val file = File.createTempFile("pdf_tool_", ".pdf", context.cacheDir)
    val copied = runCatching {
        context.contentResolver.openInputStream(source.uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } != null
    }.getOrDefault(false)
    if (!copied) {
        file.delete()
        error("无法读取 PDF 文件")
    }
    return file
}
