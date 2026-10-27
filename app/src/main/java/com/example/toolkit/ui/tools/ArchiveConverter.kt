package com.example.toolkit.ui.tools

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.outputstream.ZipOutputStream as Zip4jOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min

private const val MAX_ARCHIVE_OUTPUT_BYTES = 256L * 1024 * 1024
private const val MAX_ARCHIVE_ENTRIES = 50_000
private const val MAX_ARCHIVE_TOTAL_BYTES = 512L * 1024 * 1024
private const val MIN_SPLIT_BYTES = 64L * 1024
private const val MAX_SPLIT_BYTES = 2L * 1024 * 1024 * 1024
private const val MAX_SPLIT_VOLUMES = 200
private const val DEFAULT_SPLIT_BYTES = 100L * 1024 * 1024

internal data class ArchiveExtractResult(
    val files: Int,
    val directories: Int,
    val bytes: Long,
)

internal data class ArchiveEntryInfo(
    val name: String,
    val size: Long,
)

internal data class ArchiveListing(
    val files: List<ArchiveEntryInfo>,
    val truncated: Boolean,
)

internal data class ArchiveEntryContent(
    val bytes: ByteArray,
    val truncated: Boolean,
)

/** Lists file entries (directories are omitted) without reading their data. */
internal fun listArchiveFiles(
    context: Context,
    source: PickedFile,
    password: String?,
    maxFiles: Int = 1000,
): ArchiveListing {
    val files = ArrayList<ArchiveEntryInfo>()
    var truncated = false
    forEachArchiveEntry(
        context = context,
        source = source,
        password = password,
        selection = null,
        enforceLimits = false,
    ) { name, directory, size, _ ->
        if (directory) return@forEachArchiveEntry
        if (files.size < maxFiles) {
            files += ArchiveEntryInfo(name, size)
        } else {
            truncated = true
        }
    }
    return ArchiveListing(files, truncated)
}

/** Streams only the selected entries; used by tests and future bulk exporters. */
internal fun extractSelectedEntries(
    context: Context,
    source: PickedFile,
    password: String?,
    selection: Set<String>,
    consumer: (name: String, size: Long, input: InputStream) -> Unit,
) {
    forEachArchiveEntry(
        context = context,
        source = source,
        password = password,
        selection = selection,
    ) { name, directory, size, input ->
        if (!directory) consumer(name, size, input)
    }
}

/**
 * Reads a single entry into memory, capped at [maxBytes]. Used for the
 * pre-extraction preview so no temporary files are created.
 */
internal fun readArchiveEntry(
    context: Context,
    source: PickedFile,
    password: String?,
    name: String,
    maxBytes: Long = 8L * 1024 * 1024,
): ArchiveEntryContent? {
    var result: ArchiveEntryContent? = null
    extractSelectedEntries(context, source, password, setOf(name)) { entryName, _, input ->
        if (entryName != name) return@extractSelectedEntries
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        var truncated = false
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (total + count > maxBytes) {
                val remaining = (maxBytes - total).toInt().coerceAtLeast(0)
                if (remaining > 0) output.write(buffer, 0, remaining)
                truncated = true
                break
            }
            output.write(buffer, 0, count)
            total += count
        }
        result = ArchiveEntryContent(output.toByteArray(), truncated)
    }
    return result
}

/** Thrown when an archive needs a password, or the supplied one is wrong. */
internal class ArchivePasswordException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal fun convertArchive(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
    password: String? = null,
    splitSizeBytes: Long? = null,
): ConvertOutcome {
    if (target.id == "extract") error("请在界面选择目标文件夹")
    if (target.id == "zip_enc" && password.isNullOrEmpty()) {
        throw ArchivePasswordException("请先输入加密密码")
    }
    if (target.id == "zip_split") {
        return convertArchiveToSplitZip(context, source, password, splitSizeBytes)
    }

    val output = File.createTempFile("archive_", ".${if (target.id == "zip_enc") "zip" else target.id}", context.cacheDir)
    try {
        val sink: ArchiveSink = when (target.id) {
            "zip" -> ZipSink(output)
            "zip_enc" -> ZipEncSink(output, password.orEmpty())
            "7z" -> SevenZSink(output)
            "tar" -> TarSink(output)
            "tgz" -> TarGzSink(output)
            else -> error("暂不支持输出 ${target.label}")
        }
        sink.use {
            forEachArchiveEntry(context, source, password) { name, directory, size, input ->
                if (directory) {
                    it.addDirectory(name)
                } else {
                    it.addFile(name, size, input)
                }
            }
        }

        if (!output.exists() || output.length() == 0L) error("压缩包没有生成输出")
        if (output.length() > MAX_ARCHIVE_OUTPUT_BYTES) {
            error("输出压缩包超过 256 MB")
        }
        return ConvertOutcome(
            listOf(
                ConvertedFile(
                    name = fileConvertOutputName(source.name, target),
                    mime = target.mime,
                    bytes = output.readBytes(),
                ),
            ),
            notice = if (target.id == "zip_enc") "已使用 AES-256 加密，请妥善保管密码" else null,
        )
    } finally {
        output.delete()
    }
}

internal fun extractArchiveToTree(
    context: Context,
    source: PickedFile,
    treeUri: Uri,
    password: String? = null,
    selection: Set<String>? = null,
): ArchiveExtractResult {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("无法访问目标文件夹")
    return extractArchiveToDocumentFile(context, source, root, password, selection)
}

internal fun extractArchiveToDocumentFile(
    context: Context,
    source: PickedFile,
    root: DocumentFile,
    password: String? = null,
    selection: Set<String>? = null,
): ArchiveExtractResult {
    var files = 0
    var directories = 0
    var bytes = 0L

    forEachArchiveEntry(context, source, password, selection) { name, directory, size, input ->
        val parts = sanitizeArchivePath(name) ?: return@forEachArchiveEntry
        if (directory) {
            ensureArchiveDirectory(root, parts) { directories += 1 }
            return@forEachArchiveEntry
        }

        val parent = ensureArchiveDirectory(root, parts.dropLast(1)) { directories += 1 }
        val fileName = parts.last()
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        var document = parent.createFile(mime, fileName)
            ?: error("无法创建文件：$name")
        if (document.name != fileName) {
            val renamed = runCatching { document.renameTo(fileName) }.getOrDefault(false)
            if (renamed) {
                document = parent.findFile(fileName) ?: document
            }
        }
        val output = if (document.uri.scheme == "file") {
            document.uri.path?.let { FileOutputStream(it) }
        } else {
            context.contentResolver.openOutputStream(document.uri)
        } ?: error("无法写入文件：$name")
        output.use { copied ->
            val count = copyLimited(input, output, size)
            bytes += count
            if (bytes > MAX_ARCHIVE_TOTAL_BYTES) {
                error("解压内容超过 512 MB")
            }
        }
        files += 1
    }

    return ArchiveExtractResult(files, directories, bytes)
}

/* ------------------------------------------------------------------ */
/* Archive entry iteration                                            */
/* ------------------------------------------------------------------ */

private fun forEachArchiveEntry(
    context: Context,
    source: PickedFile,
    password: String?,
    selection: Set<String>? = null,
    enforceLimits: Boolean = true,
    action: (name: String, directory: Boolean, size: Long, input: InputStream) -> Unit,
) {
    var entries = 0
    var total = 0L

    fun run(name: String, directory: Boolean, size: Long, input: InputStream) {
        if (selection != null && (directory || name !in selection)) return
        if (enforceLimits) {
            entries += 1
            if (entries > MAX_ARCHIVE_ENTRIES) error("压缩包条目超过 50000 个")
            if (size > 0) {
                total += size
                if (total > MAX_ARCHIVE_TOTAL_BYTES) error("压缩包解压后超过 512 MB")
            }
        }
        action(name, directory, size, input)
    }

    when (source.format.id) {
        "zip" -> forEachZipEntry(context, source, password) { name, directory, size, input ->
            run(name, directory, size, input)
        }

        "tar" -> openTar(context, source) { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                run(entry.name, entry.isDirectory, entry.size, tar)
            }
        }

        "tgz" -> context.contentResolver.openInputStream(source.uri)?.use { input ->
            GzipCompressorInputStream(BufferedInputStream(input)).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        run(entry.name, entry.isDirectory, entry.size, tar)
                    }
                }
            }
        } ?: error("无法读取 TAR.GZ")

        "tbz2" -> context.contentResolver.openInputStream(source.uri)?.use { input ->
            BZip2CompressorInputStream(BufferedInputStream(input)).use { bzip ->
                TarArchiveInputStream(bzip).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        run(entry.name, entry.isDirectory, entry.size, tar)
                    }
                }
            }
        } ?: error("无法读取 TAR.BZ2")

        "txz" -> context.contentResolver.openInputStream(source.uri)?.use { input ->
            XZCompressorInputStream(BufferedInputStream(input)).use { xz ->
                TarArchiveInputStream(xz).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        run(entry.name, entry.isDirectory, entry.size, tar)
                    }
                }
            }
        } ?: error("无法读取 TAR.XZ")

        "gz" -> context.contentResolver.openInputStream(source.uri)?.use { input ->
            GzipCompressorInputStream(BufferedInputStream(input)).use { gzip ->
                run(singleStreamName(source.name, ".gz"), false, -1, gzip)
            }
        } ?: error("无法读取 GZ")

        "bz2" -> context.contentResolver.openInputStream(source.uri)?.use { input ->
            BZip2CompressorInputStream(BufferedInputStream(input)).use { bzip ->
                run(singleStreamName(source.name, ".bz2"), false, -1, bzip)
            }
        } ?: error("无法读取 BZ2")

        "xz" -> context.contentResolver.openInputStream(source.uri)?.use { input ->
            XZCompressorInputStream(BufferedInputStream(input)).use { xz ->
                run(singleStreamName(source.name, ".xz"), false, -1, xz)
            }
        } ?: error("无法读取 XZ")

        "7z" -> {
            val temp = copyToTemp(context, source, ".7z")
            try {
                val sevenZ = openSevenZ(temp, password)
                sevenZ.use { archive ->
                    val input = object : InputStream() {
                        override fun read(): Int = archive.read()
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                            archive.read(buffer, offset, length)
                    }
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        run(entry.name, entry.isDirectory, entry.size, input)
                    }
                }
            } catch (error: Exception) {
                throw mapArchiveError(error, "7Z")
            } finally {
                temp.delete()
            }
        }

        "rar" -> {
            val temp = copyToTemp(context, source, ".rar")
            try {
                Archive(temp).use { archive ->
                    if (!password.isNullOrEmpty()) archive.setPassword(password)
                    var header = archive.nextFileHeader()
                    while (header != null) {
                        run(
                            header.fileName.replace('\\', '/'),
                            header.isDirectory,
                            header.fullUnpackSize,
                            archive.getInputStream(header),
                        )
                        header = archive.nextFileHeader()
                    }
                }
            } catch (error: Exception) {
                throw mapArchiveError(error, "RAR")
            } finally {
                temp.delete()
            }
        }

        else -> error(ConvertFormats.unsupportedReason(source.format))
    }
}

/* ------------------------------------------------------------------ */
/* Split volumes                                                      */
/* ------------------------------------------------------------------ */

private fun convertArchiveToSplitZip(
    context: Context,
    source: PickedFile,
    password: String?,
    splitSizeBytes: Long?,
): ConvertOutcome {
    val partSize = splitSizeBytes ?: DEFAULT_SPLIT_BYTES
    if (partSize < MIN_SPLIT_BYTES) error("分卷大小不能小于 64 KB")
    if (partSize > MAX_SPLIT_BYTES) error("分卷大小不能超过 2 GB")

    val workDir = File(context.cacheDir, "split_${System.nanoTime()}")
    val inputDir = File(workDir, "input")
    if (!inputDir.mkdirs()) error("无法创建分卷临时目录")
    try {
        var written = 0L
        forEachArchiveEntry(context, source, password) { name, directory, size, input ->
            val parts = sanitizeArchivePath(name) ?: return@forEachArchiveEntry
            val file = File(inputDir, parts.joinToString(File.separator))
            if (directory) {
                file.mkdirs()
                return@forEachArchiveEntry
            }
            val parent = file.parentFile ?: error("无法创建目录")
            if (!parent.exists() && !parent.mkdirs()) error("无法创建目录")
            file.outputStream().use { output ->
                written += copyLimited(input, output, size)
                if (written > MAX_ARCHIVE_TOTAL_BYTES) {
                    error("压缩包解压后超过 512 MB")
                }
            }
        }

        val base = fileConvertBaseName(source.name)
        val zipFile = File(workDir, "${base}_split.zip")
        Zip4jFile(zipFile).createSplitZipFileFromFolder(
            inputDir,
            ZipParameters().apply {
                setIncludeRootFolder(false)
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
            },
            true,
            partSize,
        )

        val volumes = workDir
            .listFiles { file -> file.isFile && file.name.startsWith("${base}_split.") }
            .orEmpty()
            .sortedBy { file ->
                val extension = file.extension.lowercase()
                if (extension == "zip") {
                    Int.MAX_VALUE
                } else {
                    extension.drop(1).toIntOrNull() ?: (Int.MAX_VALUE - 1)
                }
            }
        if (volumes.isEmpty()) error("分卷生成失败")
        if (volumes.size > MAX_SPLIT_VOLUMES) error("分卷数量超过 $MAX_SPLIT_VOLUMES 个")
        val total = volumes.sumOf { it.length() }
        if (total > MAX_ARCHIVE_OUTPUT_BYTES) {
            error("分卷总大小超过 256 MB，请增大每个分卷的大小")
        }
        val files = volumes.map { volume ->
            ConvertedFile(
                name = volume.name,
                mime = "application/zip",
                bytes = volume.readBytes(),
            )
        }
        return ConvertOutcome(
            files = files,
            notice = "已分成 ${files.size} 个分卷，保存时请保持文件名不变",
        )
    } finally {
        workDir.deleteRecursively()
    }
}

private fun openTar(context: Context, source: PickedFile, block: (TarArchiveInputStream) -> Unit) {
    context.contentResolver.openInputStream(source.uri)?.use { input ->
        TarArchiveInputStream(BufferedInputStream(input)).use(block)
    } ?: error("无法读取 TAR")
}

/* ------------------------------------------------------------------ */
/* Passwords                                                          */
/* ------------------------------------------------------------------ */

internal fun archiveRequiresPassword(context: Context, source: PickedFile): Boolean = runCatching {
    when (source.format.id) {
        "zip" -> zipRequiresPassword(context, source)
        "7z" -> sevenZRequiresPassword(context, source)
        "rar" -> rarRequiresPassword(context, source)
        else -> false
    }
}.getOrDefault(false)

private fun zipRequiresPassword(context: Context, source: PickedFile): Boolean = runCatching {
    context.contentResolver.openInputStream(source.uri)?.use { input ->
        ZipArchiveInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return@use false
                if (entry.generalPurposeBit.usesEncryption()) return@use true
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
    } ?: false
}.getOrElse {
    val temp = copyToTemp(context, source, ".zip")
    try {
        Zip4jFile(temp).isEncrypted
    } catch (_: Throwable) {
        false
    } finally {
        temp.delete()
    }
}

private fun sevenZRequiresPassword(context: Context, source: PickedFile): Boolean {
    val temp = copyToTemp(context, source, ".7z")
    return try {
        SevenZFile(temp).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                if (!entry.isDirectory && entry.size != 0L) {
                    return try {
                        archive.read(ByteArray(1))
                        false
                    } catch (error: Exception) {
                        isPasswordError(error)
                    }
                }
            }
            false
        }
    } catch (error: Exception) {
        isPasswordError(error)
    } finally {
        temp.delete()
    }
}

private fun rarRequiresPassword(context: Context, source: PickedFile): Boolean {
    val temp = copyToTemp(context, source, ".rar")
    return try {
        Archive(temp).use { it.isPasswordProtected }
    } catch (error: Exception) {
        isPasswordError(error)
    } finally {
        temp.delete()
    }
}

private fun isPasswordError(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is PasswordRequiredException) return true
        val name = current.javaClass.name
        if (name.contains("PasswordRequired", ignoreCase = true) ||
            name.contains("UnsupportedRarEncrypted", ignoreCase = true) ||
            name.contains("RarEncrypted", ignoreCase = true)
        ) {
            return true
        }
        val message = current.message.orEmpty()
        if (message.contains("password", ignoreCase = true) ||
            message.contains("encrypt", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

private fun mapArchiveError(error: Throwable, kind: String): Throwable = when {
    error is ArchivePasswordException -> error
    isPasswordError(error) -> ArchivePasswordException(
        "该 $kind 压缩包需要密码，请输入正确密码后重试",
        error,
    )

    else -> error
}

private fun openSevenZ(file: File, password: String?): SevenZFile = if (password.isNullOrEmpty()) {
    SevenZFile(file)
} else {
    SevenZFile(file, password.toCharArray())
}

private fun forEachZipEntry(
    context: Context,
    source: PickedFile,
    password: String?,
    run: (name: String, directory: Boolean, size: Long, input: InputStream) -> Unit,
) {
    val encrypted = zipRequiresPassword(context, source)
    if (!encrypted && password.isNullOrEmpty()) {
        context.contentResolver.openInputStream(source.uri)?.use { input ->
            ZipArchiveInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    run(entry.name, entry.isDirectory, entry.size, zip)
                }
            }
        } ?: error("无法读取 ZIP")
        return
    }

    if (password.isNullOrEmpty()) {
        throw ArchivePasswordException("该 ZIP 压缩包已加密，请输入密码")
    }

    val temp = copyToTemp(context, source, ".zip")
    try {
        val zip = Zip4jFile(temp, password.toCharArray())
        zip.fileHeaders.forEach { header ->
            if (header.isDirectory) {
                run(header.fileName, true, 0L, ByteArrayInputStream(ByteArray(0)))
            } else {
                zip.getInputStream(header).use { input ->
                    run(header.fileName, false, header.uncompressedSize, input)
                }
            }
        }
    } catch (error: ZipException) {
        throw ArchivePasswordException(
            when (error.type) {
                ZipException.Type.WRONG_PASSWORD -> "压缩包密码不正确"
                ZipException.Type.UNKNOWN -> "无法读取加密 ZIP：${error.message}"
                else -> "无法读取加密 ZIP：${error.message}"
            },
            error,
        )
    } finally {
        temp.delete()
    }
}

private fun copyToTemp(context: Context, source: PickedFile, suffix: String): File {
    val file = File.createTempFile("archive_in_", suffix, context.cacheDir)
    context.contentResolver.openInputStream(source.uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: run {
        file.delete()
        error("无法读取压缩包")
    }
    return file
}

private fun singleStreamName(name: String, suffix: String): String {
    val lower = name.lowercase()
    val base = if (lower.endsWith(suffix)) name.dropLast(suffix.length) else name
    return base.ifBlank { "file" }
}

private fun sanitizeArchivePath(name: String): List<String>? {
    val normalized = name.replace('\\', '/').trim()
    if (normalized.startsWith("/") || normalized.contains(':')) return null
    val parts = normalized.split('/').filter { it.isNotBlank() && it != "." }
    if (parts.isEmpty() || parts.any { it == ".." }) return null
    return parts
}

private fun ensureArchiveDirectory(
    root: DocumentFile,
    parts: List<String>,
    onCreated: () -> Unit = {},
): DocumentFile {
    var current = root
    for (part in parts) {
        val existing = current.findFile(part)
        current = when {
            existing == null -> {
                val created = current.createDirectory(part)
                    ?: error("无法创建目录：$part")
                onCreated()
                created
            }

            existing.isDirectory -> existing
            else -> error("目录名冲突：$part")
        }
    }
    return current
}

private fun readAllLimited(input: InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > MAX_ARCHIVE_OUTPUT_BYTES) error("单个条目超过 256 MB")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun copyLimited(input: InputStream, output: OutputStream, size: Long): Long {
    val buffer = ByteArray(32 * 1024)
    var remaining = if (size >= 0) size else Long.MAX_VALUE
    var copied = 0L
    while (remaining > 0) {
        val requested = min(buffer.size.toLong(), remaining).toInt()
        val count = input.read(buffer, 0, requested)
        if (count < 0) break
        output.write(buffer, 0, count)
        copied += count
        if (size >= 0) remaining -= count
    }
    return copied
}

/* ------------------------------------------------------------------ */
/* Writers                                                            */
/* ------------------------------------------------------------------ */

private interface ArchiveSink : Closeable {
    fun addDirectory(name: String)
    fun addFile(name: String, size: Long, input: InputStream)
}

private class ZipSink(file: File) : ArchiveSink {
    private val output = ZipArchiveOutputStream(file)

    override fun addDirectory(name: String) {
        val entry = ZipArchiveEntry(if (name.endsWith("/")) name else "$name/")
        entry.size = 0
        output.putArchiveEntry(entry)
        output.closeArchiveEntry()
    }

    override fun addFile(name: String, size: Long, input: InputStream) {
        if (size > MAX_ARCHIVE_OUTPUT_BYTES) error("单个条目超过 256 MB")
        val entry = ZipArchiveEntry(name)
        if (size > 0) {
            entry.size = size
            output.putArchiveEntry(entry)
            copyLimited(input, output, size)
        } else {
            val bytes = readAllLimited(input)
            entry.size = bytes.size.toLong()
            output.putArchiveEntry(entry)
            output.write(bytes)
        }
        output.closeArchiveEntry()
    }

    override fun close() {
        output.finish()
        output.close()
    }
}

private class ZipEncSink(file: File, password: String) : ArchiveSink {
    private val output = Zip4jOutputStream(FileOutputStream(file), password.toCharArray())

    override fun addDirectory(name: String) {
        val entryName = if (name.endsWith("/")) name else "$name/"
        output.putNextEntry(
            ZipParameters().apply {
                fileNameInZip = entryName
                isEncryptFiles = false
            },
        )
        output.closeEntry()
    }

    override fun addFile(name: String, size: Long, input: InputStream) {
        if (size > MAX_ARCHIVE_OUTPUT_BYTES) error("单个条目超过 256 MB")
        output.putNextEntry(
            ZipParameters().apply {
                fileNameInZip = name
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
                if (size > 0) entrySize = size
            },
        )
        if (size > 0) {
            copyLimited(input, output, size)
        } else {
            input.copyTo(output)
        }
        output.closeEntry()
    }

    override fun close() {
        output.close()
    }
}

private class TarSink(file: File) : ArchiveSink {
    private val output = TarArchiveOutputStream(FileOutputStream(file)).apply {
        setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
    }

    override fun addDirectory(name: String) {
        val entry = TarArchiveEntry(if (name.endsWith("/")) name else "$name/")
        output.putArchiveEntry(entry)
        output.closeArchiveEntry()
    }

    override fun addFile(name: String, size: Long, input: InputStream) {
        if (size > MAX_ARCHIVE_OUTPUT_BYTES) error("单个条目超过 256 MB")
        val entry = TarArchiveEntry(name)
        if (size > 0) {
            entry.size = size
            output.putArchiveEntry(entry)
            copyLimited(input, output, size)
        } else {
            val bytes = readAllLimited(input)
            entry.size = bytes.size.toLong()
            output.putArchiveEntry(entry)
            output.write(bytes)
        }
        output.closeArchiveEntry()
    }

    override fun close() {
        output.finish()
        output.close()
    }
}

private class TarGzSink(file: File) : ArchiveSink {
    private val fileOutput = FileOutputStream(file)
    private val gzip = GzipCompressorOutputStream(fileOutput)
    private val output = TarArchiveOutputStream(gzip).apply {
        setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
    }

    override fun addDirectory(name: String) {
        val entry = TarArchiveEntry(if (name.endsWith("/")) name else "$name/")
        output.putArchiveEntry(entry)
        output.closeArchiveEntry()
    }

    override fun addFile(name: String, size: Long, input: InputStream) {
        if (size > MAX_ARCHIVE_OUTPUT_BYTES) error("单个条目超过 256 MB")
        val entry = TarArchiveEntry(name)
        if (size > 0) {
            entry.size = size
            output.putArchiveEntry(entry)
            copyLimited(input, output, size)
        } else {
            val bytes = readAllLimited(input)
            entry.size = bytes.size.toLong()
            output.putArchiveEntry(entry)
            output.write(bytes)
        }
        output.closeArchiveEntry()
    }

    override fun close() {
        output.finish()
        output.close()
    }
}

private class SevenZSink(file: File) : ArchiveSink {
    private val output = SevenZOutputFile(file).apply {
        setContentCompression(SevenZMethod.LZMA2)
    }

    override fun addDirectory(name: String) {
        val entry = SevenZArchiveEntry()
        entry.name = if (name.endsWith("/")) name else "$name/"
        entry.isDirectory = true
        output.putArchiveEntry(entry)
        output.closeArchiveEntry()
    }

    override fun addFile(name: String, size: Long, input: InputStream) {
        if (size > MAX_ARCHIVE_OUTPUT_BYTES) error("单个条目超过 256 MB")
        val entry = SevenZArchiveEntry()
        entry.name = name
        if (size > 0) {
            entry.size = size
            output.putArchiveEntry(entry)
            copyLimited(input, object : OutputStream() {
                override fun write(value: Int) = output.write(value)
                override fun write(buffer: ByteArray, offset: Int, length: Int) =
                    output.write(buffer, offset, length)
            }, size)
        } else {
            val bytes = readAllLimited(input)
            entry.size = bytes.size.toLong()
            output.putArchiveEntry(entry)
            output.write(bytes)
        }
        output.closeArchiveEntry()
    }

    override fun close() {
        output.close()
    }
}
