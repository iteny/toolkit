package com.example.toolkit.ui.tools

import android.content.Context
import android.net.Uri
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.min

/* ------------------------------------------------------------------ */
/* Office / EPUB extraction                                           */
/* ------------------------------------------------------------------ */

private data class TextBlock(
    val text: String,
    val heading: Int = 0,
)

private data class DocumentData(
    val title: String,
    val blocks: List<TextBlock>,
)

private data class SheetData(
    val name: String,
    val rows: List<List<String>>,
)

internal fun convertDocumentFile(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
): ConvertOutcome {
    val document = when (source.format.id) {
        "docx" -> withZipFile(context, source.uri) { extractDocx(it) }
        "odt" -> withZipFile(context, source.uri) { extractOdt(it) }
        "rtf" -> extractRtf(
            fileConvertDecodeText(
                fileConvertReadBytes(context, source.uri, 24L * 1024 * 1024),
            ),
        )

        "hwpx" -> withZipFile(context, source.uri) { extractHwpx(it) }
        "pptx" -> withZipFile(context, source.uri) { extractPptx(it) }
        "odp" -> withZipFile(context, source.uri) { extractOdp(it) }
        "epub" -> withZipFile(context, source.uri) { extractEpub(it) }
        else -> error(ConvertFormats.unsupportedReason(source.format))
    }

    val bytes = when (target.id) {
        "txt" -> documentToText(document).toByteArray(Charsets.UTF_8)
        "html" -> documentToHtml(document).toByteArray(Charsets.UTF_8)
        "pdf" -> textToPdf(documentToText(document))
        else -> error("暂不支持 ${source.format.label} 转 ${target.label}")
    }
    return ConvertOutcome(
        listOf(
            ConvertedFile(
                name = fileConvertOutputName(source.name, target),
                mime = target.mime,
                bytes = bytes,
            ),
        ),
        notice = if (target.id == "pdf") "PDF 仅保留文字内容，不保留原始排版和图片" else null,
    )
}

internal fun convertSpreadsheetFile(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
): ConvertOutcome {
    val sheets = when (source.format.id) {
        "xlsx" -> withZipFile(context, source.uri) { extractXlsx(it) }
        "ods" -> withZipFile(context, source.uri) { extractOds(it) }
        else -> error(ConvertFormats.unsupportedReason(source.format))
    }.filter { it.rows.isNotEmpty() }

    if (sheets.isEmpty()) error("表格中没有可转换的数据")

    if (target.id == "json") {
        val bytes = if (sheets.size == 1) {
            sheetToJson(sheets.first()).toString(2)
        } else {
            val root = JSONObject()
            sheets.forEachIndexed { index, sheet ->
                root.put(
                    sheet.name.ifBlank { "Sheet${index + 1}" },
                    sheetToJson(sheet),
                )
            }
            root.toString(2)
        }.toByteArray(Charsets.UTF_8)
        return ConvertOutcome(
            listOf(
                ConvertedFile(
                    name = fileConvertOutputName(source.name, target),
                    mime = target.mime,
                    bytes = bytes,
                ),
            ),
        )
    }

    val delimiter = if (target.id == "csv") ',' else '\t'
    val base = fileConvertBaseName(source.name)
    val files = sheets.mapIndexed { index, sheet ->
        val name = if (sheets.size == 1) {
            fileConvertOutputName(source.name, target)
        } else {
            val sheetName = sheet.name.ifBlank { "Sheet${index + 1}" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(40)
            "${base}_${sheetName}.${target.id}"
        }
        ConvertedFile(
            name = name,
            mime = target.mime,
            bytes = sheetToDelimited(sheet, delimiter).toByteArray(Charsets.UTF_8),
        )
    }
    return ConvertOutcome(files)
}

/* ------------------------------------------------------------------ */
/* ZIP / XML helpers                                                  */
/* ------------------------------------------------------------------ */

private fun <T> withZipFile(context: Context, uri: Uri, block: (ZipFile) -> T): T {
    val temp = File.createTempFile("fileconvert_", ".zip", context.cacheDir)
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取文件")
        return ZipFile(temp).use(block)
    } finally {
        temp.delete()
    }
}

private fun newParser(input: InputStream): XmlPullParser {
    val factory = XmlPullParserFactory.newInstance()
    factory.isNamespaceAware = true
    return factory.newPullParser().apply { setInput(input, null) }
}

private fun attr(parser: XmlPullParser, name: String): String? {
    for (index in 0 until parser.attributeCount) {
        if (parser.getAttributeName(index) == name) return parser.getAttributeValue(index)
    }
    return null
}

private fun readEntry(zip: ZipFile, name: String): String? =
    zip.getEntry(name)?.let { entry ->
        zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

/* ------------------------------------------------------------------ */
/* DOCX                                                               */
/* ------------------------------------------------------------------ */

private fun extractDocx(zip: ZipFile): DocumentData {
    val entry = zip.getEntry("word/document.xml")
        ?: error("DOCX 缺少 word/document.xml")
    val blocks = ArrayList<TextBlock>()

    zip.getInputStream(entry).use { input ->
        val parser = newParser(input)
        var paragraph = StringBuilder()
        var heading = 0
        var inText = false
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "p" -> {
                        paragraph = StringBuilder()
                        heading = 0
                    }

                    "pStyle" -> heading = headingFromStyle(attr(parser, "val").orEmpty())
                    "outlineLvl" -> {
                        val level = attr(parser, "val")?.toIntOrNull()
                        if (level != null && level in 0..5) heading = level + 1
                    }

                    "t" -> inText = true
                    "tab" -> paragraph.append('\t')
                    "br", "cr" -> paragraph.append('\n')
                }

                XmlPullParser.TEXT -> if (inText) paragraph.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "p" -> {
                        val text = paragraph.toString().trim()
                        if (text.isNotEmpty()) blocks += TextBlock(text, heading)
                    }
                }
            }
            event = parser.next()
        }
    }
    return DocumentData("Word 文档", blocks)
}

private fun headingFromStyle(style: String): Int {
    val lower = style.lowercase(Locale.US)
    if (lower.contains("title")) return 1
    if (!lower.contains("heading") && !lower.contains("标题")) return 0
    return lower.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 6) ?: 1
}

/* ------------------------------------------------------------------ */
/* ODT / ODP                                                          */
/* ------------------------------------------------------------------ */

private fun extractOdt(zip: ZipFile): DocumentData {
    val entry = zip.getEntry("content.xml") ?: error("ODT 缺少 content.xml")
    val blocks = ArrayList<TextBlock>()

    zip.getInputStream(entry).use { input ->
        val parser = newParser(input)
        var paragraph = StringBuilder()
        var heading = 0
        var inParagraph = false
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "h" -> {
                        paragraph = StringBuilder()
                        heading = attr(parser, "outline-level")?.toIntOrNull()?.coerceIn(1, 6) ?: 1
                        inParagraph = true
                    }

                    "p" -> {
                        paragraph = StringBuilder()
                        heading = 0
                        inParagraph = true
                    }

                    "s" -> if (inParagraph) {
                        val count = attr(parser, "c")?.toIntOrNull()?.coerceIn(1, 32) ?: 1
                        repeat(count) { paragraph.append(' ') }
                    }

                    "tab" -> if (inParagraph) paragraph.append('\t')
                    "line-break" -> if (inParagraph) paragraph.append('\n')
                }

                XmlPullParser.TEXT -> if (inParagraph) paragraph.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "h", "p" -> {
                        val text = paragraph.toString().trim()
                        if (text.isNotEmpty()) blocks += TextBlock(text, heading)
                        inParagraph = false
                    }
                }
            }
            event = parser.next()
        }
    }
    return DocumentData("OpenDocument 文本", blocks)
}

private fun extractOdp(zip: ZipFile): DocumentData {
    val entry = zip.getEntry("content.xml") ?: error("ODP 缺少 content.xml")
    val blocks = ArrayList<TextBlock>()

    zip.getInputStream(entry).use { input ->
        val parser = newParser(input)
        var page = 0
        var lastPage = 0
        var paragraph = StringBuilder()
        var heading = 0
        var inParagraph = false
        var inPage = false
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "page" -> {
                        page += 1
                        inPage = true
                    }

                    "h" -> if (inPage) {
                        paragraph = StringBuilder()
                        heading = attr(parser, "outline-level")?.toIntOrNull()?.coerceIn(1, 6) ?: 2
                        inParagraph = true
                    }

                    "p" -> if (inPage) {
                        paragraph = StringBuilder()
                        heading = 0
                        inParagraph = true
                    }

                    "s" -> if (inParagraph) {
                        val count = attr(parser, "c")?.toIntOrNull()?.coerceIn(1, 32) ?: 1
                        repeat(count) { paragraph.append(' ') }
                    }

                    "tab" -> if (inParagraph) paragraph.append('\t')
                    "line-break" -> if (inParagraph) paragraph.append('\n')
                }

                XmlPullParser.TEXT -> if (inParagraph) paragraph.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "h", "p" -> {
                        val text = paragraph.toString().trim()
                        if (text.isNotEmpty()) {
                            if (lastPage != page) {
                                blocks += TextBlock("第 $page 页", 2)
                                lastPage = page
                            }
                            blocks += TextBlock(text, heading)
                        }
                        inParagraph = false
                    }
                }
            }
            event = parser.next()
        }
    }
    return DocumentData("OpenDocument 演示文稿", blocks)
}

/* ------------------------------------------------------------------ */
/* PPTX                                                               */
/* ------------------------------------------------------------------ */

private fun extractPptx(zip: ZipFile): DocumentData {
    val slides = zip.entries().asSequence().toList()
        .map { it.name }
        .filter { Regex("^ppt/slides/slide\\d+\\.xml$").matches(it) }
        .sortedBy { it.substringAfterLast("slide").substringBefore('.').toIntOrNull() ?: 0 }

    val blocks = ArrayList<TextBlock>()
    slides.forEachIndexed { index, name ->
        val entry = zip.getEntry(name) ?: return@forEachIndexed
        val paragraphs = ArrayList<String>()
        zip.getInputStream(entry).use { input ->
            val parser = newParser(input)
            var paragraph = StringBuilder()
            var inText = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "p" -> paragraph = StringBuilder()
                        "t" -> inText = true
                    }

                    XmlPullParser.TEXT -> if (inText) paragraph.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "t" -> inText = false
                        "p" -> {
                            val text = paragraph.toString().trim()
                            if (text.isNotEmpty()) paragraphs += text
                        }
                    }
                }
                event = parser.next()
            }
        }
        if (paragraphs.isNotEmpty()) {
            blocks += TextBlock("第 ${index + 1} 页", 2)
            paragraphs.forEach { blocks += TextBlock(it) }
        }
    }
    return DocumentData("PowerPoint 演示文稿", blocks)
}

/* ------------------------------------------------------------------ */
/* HWPX                                                               */
/* ------------------------------------------------------------------ */

private fun extractHwpx(zip: ZipFile): DocumentData {
    val sections = zip.entries().asSequence().toList()
        .map { it.name }
        .filter { Regex("^Contents/section\\d+\\.xml$").matches(it) }
        .sortedBy { it.substringAfterLast("section").substringBefore('.').toIntOrNull() ?: 0 }

    val blocks = ArrayList<TextBlock>()
    sections.forEach { name ->
        val entry = zip.getEntry(name) ?: return@forEach
        zip.getInputStream(entry).use { input ->
            val parser = newParser(input)
            var paragraph = StringBuilder()
            var inText = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "p" -> paragraph = StringBuilder()
                        "t" -> inText = true
                        "tab" -> paragraph.append('\t')
                        "br" -> paragraph.append('\n')
                    }

                    XmlPullParser.TEXT -> if (inText) paragraph.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "t" -> inText = false
                        "p" -> {
                            val text = paragraph.toString().trim()
                            if (text.isNotEmpty()) blocks += TextBlock(text)
                        }
                    }
                }
                event = parser.next()
            }
        }
    }
    return DocumentData("HWPX 文档", blocks)
}

/* ------------------------------------------------------------------ */
/* EPUB                                                               */
/* ------------------------------------------------------------------ */

private fun extractEpub(zip: ZipFile): DocumentData {
    val container = readEntry(zip, "META-INF/container.xml")
    val opfPath = container?.let { extractRootFile(it) }
        ?: zip.entries().asSequence().map { it.name }.firstOrNull { it.endsWith(".opf") }
        ?: error("EPUB 缺少 OPF 文件")

    val opf = readEntry(zip, opfPath) ?: error("EPUB 缺少 $opfPath")
    val opfDir = opfPath.substringBeforeLast('/', "")
    val manifest = LinkedHashMap<String, String>()
    val spine = ArrayList<String>()

    val parser = newParser(opf.byteInputStream(Charsets.UTF_8))
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
            when (parser.name) {
                "item" -> {
                    val id = attr(parser, "id")
                    val href = attr(parser, "href")
                    if (id != null && href != null) {
                        manifest[id] = resolveZipPath(opfDir, decodeHref(href))
                    }
                }

                "itemref" -> attr(parser, "idref")?.let { spine += it }
            }
        }
        event = parser.next()
    }

    val ordered = if (spine.isNotEmpty()) {
        spine.mapNotNull { manifest[it] }
    } else {
        manifest.values.toList()
    }.distinct()

    val blocks = ArrayList<TextBlock>()
    var chapter = 0
    ordered.forEach { path ->
        if (path.endsWith(".ncx") || path.endsWith(".opf")) return@forEach
        val html = readEntry(zip, path) ?: return@forEach
        val text = runCatching {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
        }.getOrDefault("")
            .replace('\u00A0', ' ')
            .replace(Regex("[ \\t]+"), " ")
            .trim()
        if (text.isBlank()) return@forEach
        chapter += 1
        val title = extractHtmlTitle(html) ?: "第 $chapter 章"
        blocks += TextBlock(title, 2)
        text.split(Regex("\\n{2,}")).forEach { part ->
            val value = part.trim()
            if (value.isNotEmpty()) blocks += TextBlock(value)
        }
    }

    if (blocks.isEmpty()) error("EPUB 中没有可提取的文本")
    return DocumentData("EPUB 电子书", blocks)
}

private fun extractRootFile(container: String): String? = runCatching {
    val parser = newParser(container.byteInputStream(Charsets.UTF_8))
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
            return@runCatching attr(parser, "full-path")
        }
        event = parser.next()
    }
    null
}.getOrNull()

private fun decodeHref(href: String): String =
    runCatching { java.net.URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)

private fun extractHtmlTitle(html: String): String? {
    val match = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)
        ?: Regex("(?is)<h[12][^>]*>(.*?)</h[12]>").find(html)
        ?: return null
    return Html.fromHtml(match.groupValues[1], Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .trim()
        .takeIf { it.isNotEmpty() }
}

private fun resolveZipPath(baseDir: String, target: String): String {
    if (target.startsWith("/")) return target.removePrefix("/")
    val parts = (if (baseDir.isBlank()) target else "$baseDir/$target").split('/')
    val out = ArrayDeque<String>()
    for (part in parts) {
        when (part) {
            "", "." -> Unit
            ".." -> out.removeLastOrNull()
            else -> out.addLast(part)
        }
    }
    return out.joinToString("/")
}

/* ------------------------------------------------------------------ */
/* RTF                                                                */
/* ------------------------------------------------------------------ */

private fun extractRtf(text: String): DocumentData {
    val out = StringBuilder()
    val skipStack = ArrayDeque<Boolean>()
    var skip = false
    var unicodeSkip = 1
    var index = 0

    while (index < text.length) {
        when (val char = text[index]) {
            '{' -> {
                skipStack.addLast(skip)
                index += 1
            }

            '}' -> {
                skip = skipStack.removeLastOrNull() ?: false
                index += 1
            }

            '\\' -> {
                if (index + 1 >= text.length) {
                    index += 1
                    continue
                }
                val next = text[index + 1]
                when {
                    next == '\\' || next == '{' || next == '}' -> {
                        if (!skip) out.append(next)
                        index += 2
                    }

                    next == '\'' -> {
                        val hex = text.substring(index + 2, min(index + 4, text.length))
                        val byte = hex.toIntOrNull(16)?.toByte()
                        if (byte != null && !skip) {
                            out.append(String(byteArrayOf(byte), Charset.forName("windows-1252")))
                        }
                        index += 4
                    }

                    next == '*' -> {
                        skip = true
                        index += 2
                    }

                    next.isLetter() -> {
                        var end = index + 1
                        while (end < text.length && text[end].isLetter()) end += 1
                        val word = text.substring(index + 1, end)
                        var numberEnd = end
                        if (numberEnd < text.length && text[numberEnd] == '-') numberEnd += 1
                        while (numberEnd < text.length && text[numberEnd].isDigit()) numberEnd += 1
                        val number = text.substring(end, numberEnd).toIntOrNull()
                        index = numberEnd

                        if (word in setOf(
                                "fonttbl", "colortbl", "stylesheet", "info", "pict", "object",
                                "header", "footer", "footnote", "annotation",
                            )
                        ) {
                            skip = true
                        } else if (!skip) {
                            when (word) {
                                "par", "line" -> out.append('\n')
                                "tab" -> out.append('\t')
                                "emdash" -> out.append('—')
                                "endash" -> out.append('–')
                                "lquote" -> out.append('‘')
                                "rquote" -> out.append('’')
                                "ldblquote" -> out.append('“')
                                "rdblquote" -> out.append('”')
                                "bullet" -> out.append('•')
                                "uc" -> unicodeSkip = number ?: 1
                                "u" -> {
                                    val code = number ?: 0
                                    val adjusted = if (code < 0) code + 65536 else code
                                    out.append(adjusted.toChar())
                                    var skipped = 0
                                    while (skipped < unicodeSkip && index < text.length) {
                                        if (text[index] == '\\' || text[index] == '{' || text[index] == '}') break
                                        index += 1
                                        skipped += 1
                                    }
                                }
                            }
                        }
                    }

                    else -> index += 2
                }
            }

            '\r', '\n' -> index += 1

            else -> {
                if (!skip) out.append(char)
                index += 1
            }
        }
    }

    val blocks = out.toString()
        .replace(Regex("[ \\t]+\\n"), "\n")
        .split(Regex("\\n{2,}"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { TextBlock(it) }
    return DocumentData("RTF 文档", blocks)
}

/* ------------------------------------------------------------------ */
/* XLSX                                                               */
/* ------------------------------------------------------------------ */

private fun extractXlsx(zip: ZipFile): List<SheetData> {
    val sharedStrings = readEntry(zip, "xl/sharedStrings.xml")
        ?.let { parseSharedStrings(it) }
        ?: emptyList()
    val sheets = resolveWorkbookSheets(zip)
    return sheets.mapNotNull { (name, path) ->
        val entry = zip.getEntry(path) ?: return@mapNotNull null
        val rows = ArrayList<List<String>>()
        zip.getInputStream(entry).use { input ->
            val parser = newParser(input)
            var row = LinkedHashMap<Int, String>()
            var cell = StringBuilder()
            var cellType = ""
            var cellColumn = -1
            var nextColumn = 0
            var inValue = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "c" -> {
                            cell = StringBuilder()
                            cellType = attr(parser, "t").orEmpty()
                            cellColumn = columnIndex(attr(parser, "r"))
                        }

                        "v", "t" -> inValue = true
                    }

                    XmlPullParser.TEXT -> if (inValue) cell.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "v", "t" -> inValue = false
                        "c" -> {
                            val raw = cell.toString()
                            val value = when (cellType) {
                                "s" -> sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                            val column = if (cellColumn >= 0) cellColumn else nextColumn
                            row[column] = value
                            nextColumn = column + 1
                            cellColumn = -1
                        }

                        "row" -> {
                            if (row.isNotEmpty()) {
                                val max = (row.keys.maxOrNull() ?: -1).coerceAtMost(255)
                                rows += (0..max).map { row[it].orEmpty() }
                                if (rows.size >= 20_000) break
                            }
                            row = LinkedHashMap()
                            nextColumn = 0
                        }
                    }
                }
                event = parser.next()
            }
        }
        SheetData(name, rows)
    }
}

private fun parseSharedStrings(xml: String): List<String> {
    val result = ArrayList<String>()
    val parser = newParser(xml.byteInputStream(Charsets.UTF_8))
    var current = StringBuilder()
    var inText = false
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "si" -> current = StringBuilder()
                "t" -> inText = true
            }

            XmlPullParser.TEXT -> if (inText) current.append(parser.text)
            XmlPullParser.END_TAG -> when (parser.name) {
                "t" -> inText = false
                "si" -> result += current.toString()
            }
        }
        event = parser.next()
    }
    return result
}

private fun resolveWorkbookSheets(zip: ZipFile): List<Pair<String, String>> {
    val workbook = readEntry(zip, "xl/workbook.xml")
    val rels = readEntry(zip, "xl/_rels/workbook.xml.rels")
    if (workbook != null && rels != null) {
        val targets = HashMap<String, String>()
        val relParser = newParser(rels.byteInputStream(Charsets.UTF_8))
        var relEvent = relParser.eventType
        while (relEvent != XmlPullParser.END_DOCUMENT) {
            if (relEvent == XmlPullParser.START_TAG && relParser.name == "Relationship") {
                val id = attr(relParser, "Id")
                val target = attr(relParser, "Target")
                if (id != null && target != null) {
                    targets[id] = resolveZipPath("xl", target)
                }
            }
            relEvent = relParser.next()
        }

        val result = ArrayList<Pair<String, String>>()
        val bookParser = newParser(workbook.byteInputStream(Charsets.UTF_8))
        var bookEvent = bookParser.eventType
        while (bookEvent != XmlPullParser.END_DOCUMENT) {
            if (bookEvent == XmlPullParser.START_TAG && bookParser.name == "sheet") {
                val name = attr(bookParser, "name") ?: "Sheet"
                val relId = attr(bookParser, "id")
                targets[relId]?.let { result += name to it }
            }
            bookEvent = bookParser.next()
        }
        if (result.isNotEmpty()) return result
    }

    return zip.entries().asSequence().toList()
        .map { it.name }
        .filter { Regex("^xl/worksheets/sheet\\d+\\.xml$").matches(it) }
        .sortedBy { it.substringAfterLast("sheet").substringBefore('.').toIntOrNull() ?: 0 }
        .mapIndexed { index, path -> "Sheet${index + 1}" to path }
}

private fun columnIndex(reference: String?): Int {
    if (reference.isNullOrBlank()) return -1
    var value = 0
    for (char in reference) {
        if (char.isLetter()) {
            value = value * 26 + (char.uppercaseChar() - 'A' + 1)
        } else {
            break
        }
    }
    return (value - 1).coerceAtLeast(0).takeIf { value > 0 } ?: -1
}

/* ------------------------------------------------------------------ */
/* ODS                                                                */
/* ------------------------------------------------------------------ */

private fun extractOds(zip: ZipFile): List<SheetData> {
    val entry = zip.getEntry("content.xml") ?: error("ODS 缺少 content.xml")
    val sheets = ArrayList<SheetData>()

    zip.getInputStream(entry).use { input ->
        val parser = newParser(input)
        var sheetName = ""
        var rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        var rowRepeat = 1
        var cell = StringBuilder()
        var cellRepeat = 1
        var cellValue: String? = null
        var inCell = false
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "table" -> {
                        sheetName = attr(parser, "name").orEmpty()
                        rows = ArrayList()
                    }

                    "table-row" -> {
                        row = ArrayList()
                        rowRepeat = attr(parser, "number-rows-repeated")
                            ?.toIntOrNull()?.coerceIn(1, 200) ?: 1
                    }

                    "table-cell" -> {
                        cell = StringBuilder()
                        cellRepeat = attr(parser, "number-columns-repeated")
                            ?.toIntOrNull()?.coerceIn(1, 64) ?: 1
                        val type = attr(parser, "value-type")
                        cellValue = when (type) {
                            "float", "currency", "percentage" -> attr(parser, "value")
                            "date" -> attr(parser, "date-value")
                            "time" -> attr(parser, "time-value")
                            "boolean" -> attr(parser, "boolean-value")
                            "string" -> attr(parser, "string-value")
                            else -> null
                        }
                        inCell = true
                    }

                    "p" -> if (inCell && cell.isNotEmpty()) cell.append('\n')
                    "s" -> if (inCell) {
                        val count = attr(parser, "c")?.toIntOrNull()?.coerceIn(1, 32) ?: 1
                        repeat(count) { cell.append(' ') }
                    }

                    "tab" -> if (inCell) cell.append('\t')
                    "line-break" -> if (inCell) cell.append('\n')
                }

                XmlPullParser.TEXT -> if (inCell) cell.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "table-cell" -> {
                        val value = cellValue ?: cell.toString().trim()
                        repeat(cellRepeat) {
                            if (row.size < 256) row += value
                        }
                        inCell = false
                        cellValue = null
                    }

                    "table-row" -> {
                        repeat(rowRepeat) {
                            if (rows.size < 20_000) rows += row.map { it }
                        }
                        row = ArrayList()
                    }

                    "table" -> {
                        if (rows.isNotEmpty()) sheets += SheetData(sheetName, rows)
                    }
                }
            }
            event = parser.next()
        }
    }
    return sheets
}

/* ------------------------------------------------------------------ */
/* Sheet serialisation                                                */
/* ------------------------------------------------------------------ */

private fun sheetToJson(sheet: SheetData): JSONArray {
    if (sheet.rows.isEmpty()) return JSONArray()
    val headers = sheet.rows.first().mapIndexed { index, value ->
        value.trim().ifBlank { "column_${index + 1}" }
    }
    val unique = ArrayList<String>()
    val seen = HashMap<String, Int>()
    headers.forEach { header ->
        val count = (seen[header] ?: 0) + 1
        seen[header] = count
        unique += if (count == 1) header else "${header}_$count"
    }

    val array = JSONArray()
    sheet.rows.drop(1).forEach { row ->
        if (row.all { it.isBlank() }) return@forEach
        val obj = JSONObject()
        unique.forEachIndexed { index, header ->
            obj.put(header, row.getOrNull(index).orEmpty())
        }
        array.put(obj)
    }
    return array
}

private fun sheetToDelimited(sheet: SheetData, delimiter: Char): String {
    val lineEnding = if (delimiter == '\t') "\n" else "\r\n"
    return sheet.rows.joinToString(lineEnding) { row ->
        row.joinToString(delimiter.toString()) { cell -> escapeDelimited(cell, delimiter) }
    }
}

private fun escapeDelimited(value: String, delimiter: Char): String =
    if (value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }

/* ------------------------------------------------------------------ */
/* Document serialisation                                             */
/* ------------------------------------------------------------------ */

private fun documentToText(document: DocumentData): String =
    document.blocks.joinToString("\n\n") { it.text }.trim()

private fun documentToHtml(document: DocumentData): String = buildString {
    append(
        """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${escapeHtml(document.title)}</title>
        <style>
        body { margin: 0; padding: 28px; background: #f7f8fb; color: #14161c; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; line-height: 1.7; }
        main { max-width: 820px; margin: 0 auto; padding: 28px 32px; background: #fff; border-radius: 18px; box-shadow: 0 12px 40px rgba(50,60,110,.08); }
        h1, h2, h3, h4, h5, h6 { margin: 1.3em 0 .5em; }
        p { margin: .6em 0; }
        </style>
        </head>
        <body><main>
        """.trimIndent(),
    )
    append('\n')
    document.blocks.forEach { block ->
        val tag = if (block.heading in 1..6) "h${block.heading}" else "p"
        append("<$tag>")
        append(escapeHtml(block.text).replace("\n", "<br/>"))
        append("</$tag>\n")
    }
    append("</main></body></html>")
}

private fun escapeHtml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
