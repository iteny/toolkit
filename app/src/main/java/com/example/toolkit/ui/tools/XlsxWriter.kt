package com.example.toolkit.ui.tools

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a small, valid single-sheet XLSX without Apache POI. Values are
 * stored as inline strings unless they look like numbers or booleans.
 */
internal fun buildXlsx(rows: List<List<String>>, rawSheetName: String): ByteArray {
    val sheetName = sanitizeSheetName(rawSheetName)
    val limitedRows = rows.take(20_000).map { row -> row.take(256) }
    val maxColumns = limitedRows.maxOfOrNull { it.size } ?: 0

    val sheet = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<sheetData>")
        limitedRows.forEachIndexed { rowIndex, row ->
            append("<row r=\"").append(rowIndex + 1).append("\">")
            for (columnIndex in 0 until maxColumns) {
                val value = row.getOrNull(columnIndex).orEmpty().take(32_767)
                if (value.isEmpty()) continue
                val reference = "${columnName(columnIndex)}${rowIndex + 1}"
                when {
                    value.equals("true", ignoreCase = true) ->
                        append("<c r=\"").append(reference).append("\" t=\"b\"><v>1</v></c>")

                    value.equals("false", ignoreCase = true) ->
                        append("<c r=\"").append(reference).append("\" t=\"b\"><v>0</v></c>")

                    NUMBER.matches(value) ->
                        append("<c r=\"").append(reference).append("\"><v>").append(value).append("</v></c>")

                    else -> append("<c r=\"").append(reference)
                        .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escapeXml(value))
                        .append("</t></is></c>")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>
            """.trimIndent().toByteArray(),
        )
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
            """.trimIndent().toByteArray(),
        )
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        zip.write(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="${escapeXml(sheetName)}" sheetId="1" r:id="rId1"/></sheets>
            </workbook>
            """.trimIndent().toByteArray(),
        )
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zip.write(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
            """.trimIndent().toByteArray(),
        )
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/styles.xml"))
        zip.write(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
              <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
              <borders count="1"><border/></borders>
              <cellStyleXfs count="1"><xf/></cellStyleXfs>
              <cellXfs count="1"><xf xfId="0"/></cellXfs>
            </styleSheet>
            """.trimIndent().toByteArray(),
        )
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        zip.write(sheet.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    return output.toByteArray()
}

private val NUMBER = Regex("^-?(?:0|[1-9]\\d*)(?:\\.\\d+)?$")

private fun sanitizeSheetName(raw: String): String {
    val value = raw.ifBlank { "Sheet1" }
        .replace(Regex("[\\\\/:*?\\[\\]]"), "_")
        .trim()
        .take(31)
    return value.ifBlank { "Sheet1" }
}

private fun columnName(index: Int): String {
    var value = index + 1
    val builder = StringBuilder()
    while (value > 0) {
        val remainder = (value - 1) % 26
        builder.append(('A' + remainder))
        value = (value - 1) / 26
    }
    return builder.reverse().toString()
}

private fun escapeXml(value: String): String = buildString {
    value.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            '\t', '\n', '\r' -> append(char)
            else -> if (char.code >= 0x20) append(char)
        }
    }
}
