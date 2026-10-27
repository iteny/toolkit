package com.example.toolkit

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.toolkit.ui.tools.ConvertFormats
import com.example.toolkit.ui.tools.PickedFile
import com.example.toolkit.ui.tools.fileConvertRun
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DocumentFormatsTest {

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

    private fun zipOf(entries: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun convertText(
        source: PickedFile,
        target: com.example.toolkit.ui.tools.ConvertFormat,
    ): String = runBlocking {
        fileConvertRun(context, source, target, 90)
            .files.first().bytes.toString(Charsets.UTF_8)
    }

    private fun pdfPages(bytes: ByteArray): Int {
        val file = File.createTempFile("doc-pdf-", ".pdf", context.cacheDir)
        return try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun docxExtractsHeadingsAndParagraphs() {
        val docx = zipOf(
            mapOf(
                "word/document.xml" to """
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>标题</w:t></w:r></w:p>
                        <w:p><w:r><w:t>正文</w:t></w:r><w:r><w:tab/><w:t>第二列</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                """.trimIndent(),
            ),
        )
        val source = picked("unit.docx", docx, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        val text = convertText(source, ConvertFormats.TXT)
        assertTrue(text.contains("标题"))
        assertTrue(text.contains("正文"))
        assertTrue(text.contains("第二列"))

        val html = convertText(source, ConvertFormats.HTML)
        assertTrue(html.contains("<h1>标题</h1>"))
        assertTrue(html.contains("<p>正文"))

        val pdf = runBlocking {
            fileConvertRun(context, source, ConvertFormats.PDF, 90).files.first().bytes
        }
        assertTrue(pdfPages(pdf) >= 1)
    }

    @Test
    fun xlsxExtractsSheetCsvAndJson() {
        val xlsx = zipOf(
            mapOf(
                "xl/workbook.xml" to """
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="People" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                """.trimIndent(),
                "xl/_rels/workbook.xml.rels" to """
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>
                """.trimIndent(),
                "xl/sharedStrings.xml" to """
                    <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <si><t>name</t></si><si><t>city</t></si>
                      <si><t>张三</t></si><si><t>北京</t></si>
                    </sst>
                """.trimIndent(),
                "xl/worksheets/sheet1.xml" to """
                    <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <sheetData>
                        <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                        <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="s"><v>3</v></c></row>
                      </sheetData>
                    </worksheet>
                """.trimIndent(),
            ),
        )
        val source = picked("unit.xlsx", xlsx, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        val csv = convertText(source, ConvertFormats.CSV)
        assertTrue(csv.contains("name,city"))
        assertTrue(csv.contains("张三,北京"))

        val json = convertText(source, ConvertFormats.JSON)
        val array = JSONArray(json)
        assertEquals(1, array.length())
        assertEquals("张三", array.getJSONObject(0).getString("name"))
    }

    @Test
    fun pptxExtractsSlideText() {
        val pptx = zipOf(
            mapOf(
                "ppt/slides/slide1.xml" to """
                    <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                           xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                      <p:cSld><p:spTree><p:sp><p:txBody>
                        <a:p><a:r><a:t>第一页标题</a:t></a:r></a:p>
                        <a:p><a:r><a:t>正文一</a:t></a:r></a:p>
                      </p:txBody></p:sp></p:spTree></p:cSld>
                    </p:sld>
                """.trimIndent(),
                "ppt/slides/slide2.xml" to """
                    <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                           xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                      <p:cSld><p:spTree><p:sp><p:txBody>
                        <a:p><a:r><a:t>第二页</a:t></a:r></a:p>
                      </p:txBody></p:sp></p:spTree></p:cSld>
                    </p:sld>
                """.trimIndent(),
            ),
        )
        val source = picked("unit.pptx", pptx, "application/vnd.openxmlformats-officedocument.presentationml.presentation")
        val text = convertText(source, ConvertFormats.TXT)
        assertTrue(text.contains("第 1 页"))
        assertTrue(text.contains("第一页标题"))
        assertTrue(text.contains("第 2 页"))
        assertTrue(text.contains("第二页"))
    }

    @Test
    fun odtOdsOdpExtract() {
        val odt = zipOf(
            mapOf(
                "content.xml" to """
                    <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                             xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                      <office:body><office:text>
                        <text:h text:outline-level="1">标题</text:h>
                        <text:p>正文<text:tab/>第二列</text:p>
                      </office:text></office:body>
                    </office:document-content>
                """.trimIndent(),
            ),
        )
        val odtSource = picked("unit.odt", odt, "application/vnd.oasis.opendocument.text")
        val odtText = convertText(odtSource, ConvertFormats.TXT)
        assertTrue(odtText.contains("标题"))
        assertTrue(odtText.contains("第二列"))

        val ods = zipOf(
            mapOf(
                "content.xml" to """
                    <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                             xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                                             xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                      <office:body><office:spreadsheet>
                        <table:table table:name="Sheet1">
                          <table:table-row>
                            <table:table-cell office:value-type="string"><text:p>name</text:p></table:table-cell>
                            <table:table-cell office:value-type="string"><text:p>city</text:p></table:table-cell>
                          </table:table-row>
                          <table:table-row>
                            <table:table-cell office:value-type="string"><text:p>张三</text:p></table:table-cell>
                            <table:table-cell office:value-type="string"><text:p>北京</text:p></table:table-cell>
                          </table:table-row>
                        </table:table>
                      </office:spreadsheet></office:body>
                    </office:document-content>
                """.trimIndent(),
            ),
        )
        val odsSource = picked("unit.ods", ods, "application/vnd.oasis.opendocument.spreadsheet")
        val odsCsv = convertText(odsSource, ConvertFormats.CSV)
        assertTrue(odsCsv.contains("name,city"))
        assertTrue(odsCsv.contains("张三,北京"))

        val odp = zipOf(
            mapOf(
                "content.xml" to """
                    <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                             xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
                                             xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                      <office:body><office:presentation>
                        <draw:page><draw:frame><draw:text-box><text:p>第一页</text:p></draw:text-box></draw:frame></draw:page>
                        <draw:page><draw:frame><draw:text-box><text:p>第二页</text:p></draw:text-box></draw:frame></draw:page>
                      </office:presentation></office:body>
                    </office:document-content>
                """.trimIndent(),
            ),
        )
        val odpSource = picked("unit.odp", odp, "application/vnd.oasis.opendocument.presentation")
        val odpText = convertText(odpSource, ConvertFormats.TXT)
        assertTrue(odpText.contains("第 1 页"))
        assertTrue(odpText.contains("第一页"))
        assertTrue(odpText.contains("第 2 页"))
    }

    @Test
    fun epubExtractsChapters() {
        val epub = zipOf(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>
                """.trimIndent(),
                "OEBPS/content.opf" to """
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <manifest><item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/></manifest>
                      <spine><itemref idref="c1"/></spine>
                    </package>
                """.trimIndent(),
                "OEBPS/chapter1.xhtml" to """
                    <html><head><title>第一章</title></head>
                    <body><h1>第一章</h1><p>这是正文。</p></body></html>
                """.trimIndent(),
            ),
        )
        val source = picked("unit.epub", epub, "application/epub+zip")
        val text = convertText(source, ConvertFormats.TXT)
        assertTrue(text.contains("第一章"))
        assertTrue(text.contains("这是正文"))

        val html = convertText(source, ConvertFormats.HTML)
        assertTrue(html.contains("<h2>第一章</h2>"))
        assertTrue(html.contains("这是正文"))

        val pdf = runBlocking {
            fileConvertRun(context, source, ConvertFormats.PDF, 90).files.first().bytes
        }
        assertTrue(pdfPages(pdf) >= 1)
    }

    @Test
    fun hwpxExtractsText() {
        val hwpx = zipOf(
            mapOf(
                "Contents/section0.xml" to """
                    <hp:sec xmlns:hp="http://www.hancom.co.kr/hwpml/2016/HwpUnitChar">
                      <hp:p><hp:run><hp:t>第一段</hp:t></hp:run></hp:p>
                      <hp:p><hp:run><hp:t>第二段</hp:t></hp:run></hp:p>
                    </hp:sec>
                """.trimIndent(),
            ),
        )
        val source = picked("unit.hwpx", hwpx, "application/x-hwpx")
        val text = convertText(source, ConvertFormats.TXT)
        assertTrue(text.contains("第一段"))
        assertTrue(text.contains("第二段"))
    }

    @Test
    fun subtitleFormatsRoundTrip() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,000
            你好

            2
            00:00:04,500 --> 00:00:06,000
            世界
        """.trimIndent()
        val source = picked("unit.srt", srt.toByteArray(), "application/x-subrip")

        val vtt = convertText(source, ConvertFormats.VTT)
        assertTrue(vtt.startsWith("WEBVTT"))
        assertTrue(vtt.contains("00:00:01.000 --> 00:00:03.000"))
        assertTrue(vtt.contains("你好"))

        val ass = convertText(source, ConvertFormats.ASS)
        assertTrue(ass.contains("[Events]"))
        assertTrue(ass.contains("0:00:01.00,0:00:03.00"))
        assertTrue(ass.contains("你好"))

        val lrc = convertText(source, ConvertFormats.LRC)
        assertTrue(lrc.contains("[00:01.00]你好"))
        assertTrue(lrc.contains("[00:04.50]世界"))

        val back = picked("unit.vtt", vtt.toByteArray(), "text/vtt")
        val srtBack = convertText(back, ConvertFormats.SRT)
        assertTrue(srtBack.contains("00:00:01,000 --> 00:00:03,000"))
    }

    @Test
    fun svgRendersToPng() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="120" height="60">
              <rect width="120" height="60" fill="#2678d2"/>
              <circle cx="60" cy="30" r="18" fill="#ffffff"/>
            </svg>
        """.trimIndent()
        val source = picked("unit.svg", svg.toByteArray(), "image/svg+xml")
        val output = runBlocking {
            fileConvertRun(context, source, ConvertFormats.PNG, 90).files.first().bytes
        }
        assertTrue(output.size > 16)
        assertEquals(0x89.toByte(), output[0])
        assertEquals(0x50.toByte(), output[1])
        assertEquals(0x4E.toByte(), output[2])
        assertEquals(0x47.toByte(), output[3])
    }

    @Test
    fun csvToXlsxRoundTrip() {
        val csvSource = picked(
            "unit-write.csv",
            "name,city\n张三,北京\n李四,上海".toByteArray(),
            "text/csv",
        )
        val xlsx = runBlocking {
            fileConvertRun(context, csvSource, ConvertFormats.XLSX, 90).files.first().bytes
        }
        val xlsxSource = picked(
            "unit-write.xlsx",
            xlsx,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )
        val csv = convertText(xlsxSource, ConvertFormats.CSV)
        assertTrue(csv.contains("name,city"))
        assertTrue(csv.contains("张三,北京"))
        assertTrue(csv.contains("李四,上海"))
    }

    @Test
    fun jsonLinesAndXmlRoundTrip() {
        val jsonSource = picked(
            "unit-lines.json",
            """[{"name":"张三"},{"name":"李四"}]""".toByteArray(),
            "application/json",
        )
        val jsonl = convertText(jsonSource, ConvertFormats.JSONL)
        assertEquals(2, jsonl.lines().count { it.isNotBlank() })

        val jsonlSource = picked("unit-lines.jsonl", jsonl.toByteArray(), "application/x-ndjson")
        val back = convertText(jsonlSource, ConvertFormats.JSON)
        assertEquals(2, JSONArray(back).length())

        val xml = convertText(jsonSource, ConvertFormats.XML)
        assertTrue(xml.startsWith("<?xml"))
        assertTrue(xml.contains("<root>"))
        assertTrue(xml.contains("<name>张三</name>"))

        val xmlSource = picked(
            "unit-lines.xml",
            "<root><name>张三</name><age>28</age></root>".toByteArray(),
            "application/xml",
        )
        val xmlJson = convertText(xmlSource, ConvertFormats.JSON)
        assertTrue(xmlJson.contains("\"root\""))
        assertTrue(xmlJson.contains("张三"))
        assertTrue(xmlJson.contains("28"))
    }

    @Test
    fun pdfExtractsTextLayer() {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(300, 200, 1).create())
        val paint = Paint().apply {
            textSize = 14f
            color = android.graphics.Color.BLACK
        }
        page.canvas.drawText("Hello PDFBox", 20f, 50f, paint)
        document.finishPage(page)
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()

        val source = picked("unit-text.pdf", output.toByteArray(), "application/pdf")
        val text = convertText(source, ConvertFormats.TXT)
        assertTrue(text.contains("Hello PDFBox"))
    }

    @Test
    fun yamlAndTomlConversions() {
        val yamlSource = picked(
            "unit-data.yaml",
            "name: 张三\nage: 28\nskills:\n  - Kotlin\n  - Compose\n".toByteArray(),
            "application/yaml",
        )
        val json = convertText(yamlSource, ConvertFormats.JSON)
        assertTrue(json.contains("\"name\""))
        assertTrue(json.contains("张三"))
        assertTrue(json.contains("Kotlin"))

        val toml = convertText(yamlSource, ConvertFormats.TOML)
        assertTrue(toml.contains("name = \"张三\""))
        assertTrue(toml.contains("age = 28"))

        val tomlSource = picked("unit-data.toml", toml.toByteArray(), "application/toml")
        val backJson = convertText(tomlSource, ConvertFormats.JSON)
        assertTrue(backJson.contains("张三"))
        assertTrue("TOML=$toml\nJSON=$backJson", backJson.contains("Kotlin"))

        val backYaml = convertText(tomlSource, ConvertFormats.YAML)
        assertTrue(backYaml.contains("name:"))
        assertTrue(backYaml.contains("张三"))
    }

    @Test
    fun rtfExtractsPlainText() {
        val rtf = """{\rtf1\ansi{\fonttbl{\f0 Arial;}}\f0 Hello \b world\b0\par Second line}"""
        val source = picked("unit.rtf", rtf.toByteArray(), "application/rtf")
        val text = convertText(source, ConvertFormats.TXT)
        assertTrue(text.contains("Hello"))
        assertTrue(text.contains("world"))
        assertTrue(text.contains("Second line"))
    }
}
