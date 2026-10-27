package com.example.toolkit

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.toolkit.ui.tools.ConvertFormats
import com.example.toolkit.ui.tools.ArchivePasswordException
import com.example.toolkit.ui.tools.PickedFile
import com.example.toolkit.ui.tools.archiveRequiresPassword
import com.example.toolkit.ui.tools.extractArchiveToDocumentFile
import com.example.toolkit.ui.tools.extractSelectedEntries
import com.example.toolkit.ui.tools.fileConvertRun
import com.example.toolkit.ui.tools.filterArchiveEntries
import com.example.toolkit.ui.tools.listArchiveFiles
import com.example.toolkit.ui.tools.readArchiveEntry
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class ArchiveConverterTest {

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
        ZipArchiveOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                val bytes = content.toByteArray()
                val entry = org.apache.commons.compress.archivers.zip.ZipArchiveEntry(name)
                entry.size = bytes.size.toLong()
                zip.putArchiveEntry(entry)
                zip.write(bytes)
                zip.closeArchiveEntry()
            }
        }
        return output.toByteArray()
    }

    private fun readZip(bytes: ByteArray): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        ZipArchiveInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                result[entry.name] = readEntry(zip, entry.size).toString(Charsets.UTF_8)
            }
        }
        return result
    }

    private fun readEntry(input: InputStream, size: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var remaining = if (size >= 0) size else Long.MAX_VALUE
        while (remaining > 0) {
            val count = input.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
            if (count < 0) break
            output.write(buffer, 0, count)
            if (size >= 0) remaining -= count
        }
        return output.toByteArray()
    }

    private fun convert(source: PickedFile, target: com.example.toolkit.ui.tools.ConvertFormat): ByteArray =
        runBlocking {
            fileConvertRun(context, source, target, 90).files.first().bytes
        }

    @Test
    fun zipRepacksToSevenZipTarAndTarGz() {
        val source = picked(
            "unit.zip",
            zipOf(mapOf("a.txt" to "hello", "dir/b.txt" to "world")),
            "application/zip",
        )

        val targets = listOf(ConvertFormats.SEVEN_Z, ConvertFormats.TAR, ConvertFormats.TAR_GZ)
        targets.forEach { target ->
            val output = convert(source, target)
            assertTrue("${target.label} output is empty", output.isNotEmpty())
            val repacked = picked(
                "unit.${target.id}",
                output,
                target.mime,
            )
            val zipAgain = convert(repacked, ConvertFormats.ZIP)
            val entries = readZip(zipAgain)
            assertEquals("hello", entries["a.txt"])
            assertEquals("world", entries["dir/b.txt"])
        }
    }

    @Test
    fun gzipSingleStreamRepacksToZip() {
        val output = ByteArrayOutputStream()
        GzipCompressorOutputStream(output).use { it.write("gzip content".toByteArray()) }
        val source = picked("unit.txt.gz", output.toByteArray(), "application/gzip")
        val converted = convert(source, ConvertFormats.ZIP)
        assertEquals("gzip content", readZip(converted)["unit.txt"])
    }

    @Test
    fun bzip2AndXzSingleStreamsRepackToZip() {
        val bzip2 = ByteArrayOutputStream()
        org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream(bzip2).use {
            it.write("bzip2 content".toByteArray())
        }
        val bzip2Source = picked("unit.txt.bz2", bzip2.toByteArray(), "application/x-bzip2")
        assertEquals("bzip2 content", readZip(convert(bzip2Source, ConvertFormats.ZIP))["unit.txt"])

        val xz = ByteArrayOutputStream()
        org.apache.commons.compress.compressors.xz.XZCompressorOutputStream(xz).use {
            it.write("xz content".toByteArray())
        }
        val xzSource = picked("unit.txt.xz", xz.toByteArray(), "application/x-xz")
        assertEquals("xz content", readZip(convert(xzSource, ConvertFormats.ZIP))["unit.txt"])
    }

    @Test
    fun extractToDirectoryKeepsStructureAndSkipsTraversal() {
        val evilName = "evil-${System.nanoTime()}.txt"
        val source = picked(
            "extract.zip",
            zipOf(
                mapOf(
                    "a.txt" to "hello",
                    "sub/" to "",
                    "sub/b.txt" to "world",
                    "../$evilName" to "nope",
                ),
            ),
            "application/zip",
        )

        val directory = File(context.cacheDir, "extract-${System.nanoTime()}")
        assertTrue(directory.mkdirs())
        val root = DocumentFile.fromFile(directory)
        val result = extractArchiveToDocumentFile(context, source, root)

        assertEquals(2, result.files)
        assertEquals(1, result.directories)
        val listing = directory.walkTopDown().joinToString("\n") { it.relativeTo(directory).path }
        assertTrue("result=$result\nlisting=$listing", File(directory, "a.txt").exists())
        assertEquals("hello", File(directory, "a.txt").readText())
        assertEquals("world", File(directory, "sub/b.txt").readText())
        assertFalse(File(context.cacheDir, evilName).exists())
    }

    @Test
    fun archiveTargetsAreFiltered() {
        assertEquals(
            listOf("extract", "extract_selected", "zip_enc", "zip_split", "7z", "tar", "tgz"),
            ConvertFormats.targets(ConvertFormats.ZIP).map { it.id },
        )
        assertEquals(
            listOf("extract", "extract_selected", "zip", "zip_enc", "zip_split", "tar", "tgz"),
            ConvertFormats.targets(ConvertFormats.SEVEN_Z).map { it.id },
        )
    }

    private fun encryptedZipOf(entries: Map<String, String>, password: String): ByteArray {
        val output = ByteArrayOutputStream()
        net.lingala.zip4j.io.outputstream.ZipOutputStream(output, password.toCharArray()).use { zip ->
            entries.forEach { (name, content) ->
                val bytes = content.toByteArray()
                zip.putNextEntry(
                    net.lingala.zip4j.model.ZipParameters().apply {
                        fileNameInZip = name
                        isEncryptFiles = true
                        encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                        aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
                        compressionMethod = net.lingala.zip4j.model.enums.CompressionMethod.DEFLATE
                        entrySize = bytes.size.toLong()
                    },
                )
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    @Test
    fun encryptedZipRequiresPasswordAndCanBeCreated() {
        val source = picked(
            "secret.zip",
            encryptedZipOf(mapOf("a.txt" to "hello"), "pw"),
            "application/zip",
        )
        assertTrue(archiveRequiresPassword(context, source))

        val missing = runCatching { convert(source, ConvertFormats.ZIP) }.exceptionOrNull()
        assertTrue("expected password error, got $missing", missing is ArchivePasswordException)

        val wrong = runCatching {
            runBlocking { fileConvertRun(context, source, ConvertFormats.ZIP, 90, "nope") }
        }.exceptionOrNull()
        assertTrue("expected wrong password error, got $wrong", wrong is ArchivePasswordException)

        val plain = runBlocking {
            fileConvertRun(context, source, ConvertFormats.ZIP, 90, "pw").files.first().bytes
        }
        assertEquals("hello", readZip(plain)["a.txt"])

        val encrypted = runBlocking {
            fileConvertRun(
                context,
                picked("plain-for-enc.zip", zipOf(mapOf("b.txt" to "world")), "application/zip"),
                ConvertFormats.ZIP_ENC,
                90,
                "newpw",
            )
        }
        val out = File(context.cacheDir, "enc-out.zip")
        out.writeBytes(encrypted.files.first().bytes)
        val zip = net.lingala.zip4j.ZipFile(out, "newpw".toCharArray())
        assertTrue(zip.isEncrypted)
        val header = zip.fileHeaders.first { !it.isDirectory }
        val text = zip.getInputStream(header).use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals("world", text)
        out.delete()

        // Streams with an unknown size (GZ) still have to produce a valid AES zip.
        val gz = ByteArrayOutputStream()
        GzipCompressorOutputStream(gz).use { it.write("stream".toByteArray()) }
        val gzSource = picked("stream.txt.gz", gz.toByteArray(), "application/gzip")
        val encFromGz = runBlocking {
            fileConvertRun(context, gzSource, ConvertFormats.ZIP_ENC, 90, "gzpass")
                .files.first().bytes
        }
        val gzOut = File(context.cacheDir, "enc-gz.zip")
        gzOut.writeBytes(encFromGz)
        val gzZip = net.lingala.zip4j.ZipFile(gzOut, "gzpass".toCharArray())
        val gzHeader = gzZip.fileHeaders.first { !it.isDirectory }
        assertEquals(
            "stream",
            gzZip.getInputStream(gzHeader).use { it.readBytes().toString(Charsets.UTF_8) },
        )
        gzOut.delete()
    }

    @Test
    fun listsAndExtractsSelectedEntries() {
        val source = picked(
            "select.zip",
            zipOf(
                mapOf(
                    "a.txt" to "hello",
                    "dir/b.txt" to "world",
                    "dir/c.txt" to "third",
                ),
            ),
            "application/zip",
        )
        val listing = listArchiveFiles(context, source, null)
        assertFalse(listing.truncated)
        assertEquals(
            setOf("a.txt", "dir/b.txt", "dir/c.txt"),
            listing.files.map { it.name }.toSet(),
        )

        val collected = LinkedHashMap<String, String>()
        extractSelectedEntries(
            context,
            source,
            null,
            setOf("a.txt", "dir/c.txt"),
        ) { name, _, input ->
            collected[name] = input.readBytes().toString(Charsets.UTF_8)
        }
        assertEquals(setOf("a.txt", "dir/c.txt"), collected.keys)
        assertEquals("hello", collected["a.txt"])
        assertEquals("third", collected["dir/c.txt"])

        val encrypted = picked(
            "enc-select.zip",
            encryptedZipOf(mapOf("s.txt" to "secret"), "pw"),
            "application/zip",
        )
        assertTrue(archiveRequiresPassword(context, encrypted))
        assertEquals(
            listOf("s.txt"),
            listArchiveFiles(context, encrypted, "pw").files.map { it.name },
        )
        val missing = runCatching { listArchiveFiles(context, encrypted, null) }.exceptionOrNull()
        assertTrue("expected password error, got $missing", missing is ArchivePasswordException)
    }

    @Test
    fun filtersAndReadsEntriesForPreview() {
        val source = picked(
            "preview.zip",
            zipOf(
                mapOf(
                    "Alpha.txt" to "hello preview",
                    "beta.bin" to "binary",
                    "docs/gamma.txt" to "gamma",
                ),
            ),
            "application/zip",
        )
        val listing = listArchiveFiles(context, source, null)
        assertEquals(
            listOf("Alpha.txt"),
            filterArchiveEntries(listing.files, "alpha").map { it.name },
        )
        assertEquals(3, filterArchiveEntries(listing.files, "   ").size)
        assertEquals(
            listOf("docs/gamma.txt"),
            filterArchiveEntries(listing.files, "DOCS/").map { it.name },
        )

        val content = readArchiveEntry(context, source, null, "Alpha.txt")
        assertNotNull(content)
        assertEquals("hello preview", content!!.bytes.toString(Charsets.UTF_8))
        assertFalse(content.truncated)

        val truncated = readArchiveEntry(context, source, null, "Alpha.txt", maxBytes = 4)
        assertNotNull(truncated)
        assertEquals(4, truncated!!.bytes.size)
        assertTrue(truncated.truncated)

        assertNull(readArchiveEntry(context, source, null, "missing.txt"))
    }

    @Test
    fun encryptedZipTargetRequiresPassword() {        val source = picked("plain2.zip", zipOf(mapOf("a.txt" to "x")), "application/zip")
        val failure = runCatching { convert(source, ConvertFormats.ZIP_ENC) }.exceptionOrNull()
        assertTrue("expected password error, got $failure", failure is ArchivePasswordException)
    }

    private fun zipOfBytes(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipArchiveOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                val entry = org.apache.commons.compress.archivers.zip.ZipArchiveEntry(name)
                entry.size = bytes.size.toLong()
                zip.putArchiveEntry(entry)
                zip.write(bytes)
                zip.closeArchiveEntry()
            }
        }
        return output.toByteArray()
    }

    @Test
    fun createsSplitZipVolumes() {
        val payload = ByteArray(250_000)
        java.util.Random(9).nextBytes(payload)
        val source = picked(
            "volume.zip",
            zipOfBytes(mapOf("big.bin" to payload, "note.txt" to "hi".toByteArray())),
            "application/zip",
        )

        val result = runBlocking {
            fileConvertRun(
                context,
                source,
                ConvertFormats.ZIP_SPLIT,
                90,
                splitSizeBytes = 100_000L,
            )
        }
        assertTrue("expected multiple volumes, got ${result.files.map { it.name }}", result.files.size >= 3)
        assertTrue(result.files.any { it.name.endsWith(".z01") })
        assertTrue(result.files.any { it.name.endsWith(".zip") })
        assertTrue(result.notice.orEmpty().contains("分卷"))

        val dir = File(context.cacheDir, "split-read")
        dir.mkdirs()
        result.files.forEach { File(dir, it.name).writeBytes(it.bytes) }
        val main = result.files.first { it.name.endsWith(".zip") }
        val zip = net.lingala.zip4j.ZipFile(File(dir, main.name))
        val names = zip.fileHeaders.map { it.fileName }
        assertTrue("missing big.bin in $names", "big.bin" in names)
        assertTrue("missing note.txt in $names", "note.txt" in names)
        dir.deleteRecursively()
    }
}
