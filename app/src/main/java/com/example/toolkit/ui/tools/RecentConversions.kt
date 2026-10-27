package com.example.toolkit.ui.tools

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class RecentFile(
    val name: String,
    val mime: String,
    val path: String,
)

internal data class RecentConversion(
    val id: String,
    val title: String,
    val count: Int,
    val size: Long,
    val timestamp: Long,
    val files: List<RecentFile>,
)

private const val RECENT_PREFS = "file_convert_recent"
private const val RECENT_KEY = "entries"
private const val EXPORT_PREFS = "file_convert_export"
private const val EXPORT_FOLDER_KEY = "last_folder"
private const val MAX_RECENT_ENTRIES = 6
private const val MAX_RECENT_TOTAL_BYTES = 96L * 1024 * 1024
private const val MAX_RECENT_ENTRY_BYTES = 32L * 1024 * 1024

internal fun recordRecentConversion(
    context: Context,
    outputs: List<ConvertedFile>,
): RecentConversion? {
    if (outputs.isEmpty()) return null
    val totalSize = outputs.sumOf { it.bytes.size.toLong() }
    if (totalSize > MAX_RECENT_ENTRY_BYTES) return null

    val id = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    val directory = File(File(context.filesDir, "recent"), id)
    if (!directory.mkdirs()) return null

    val files = ArrayList<RecentFile>()
    val usedNames = HashSet<String>()
    try {
        outputs.forEach { output ->
            var name = sanitizeRecentName(output.name)
            var suffix = 2
            while (!usedNames.add(name)) {
                val base = name.substringBeforeLast('.', name)
                val extension = name.substringAfterLast('.', "")
                name = if (extension.isBlank()) "${base}_$suffix" else "${base}_$suffix.$extension"
                suffix += 1
            }
            val file = File(directory, name)
            file.outputStream().use { it.write(output.bytes) }
            files += RecentFile(name, output.mime, file.absolutePath)
        }
    } catch (_: Throwable) {
        directory.deleteRecursively()
        return null
    }

    val entry = RecentConversion(
        id = id,
        title = if (files.size == 1) files.first().name else "${files.first().name} 等 ${files.size} 个文件",
        count = files.size,
        size = totalSize,
        timestamp = System.currentTimeMillis(),
        files = files,
    )

    val existing = loadRecentConversions(context)
    val merged = (listOf(entry) + existing).toMutableList()
    while (merged.size > MAX_RECENT_ENTRIES) {
        merged.removeAt(merged.lastIndex)
    }
    var total = 0L
    val kept = ArrayList<RecentConversion>()
    merged.forEach { item ->
        if (total + item.size <= MAX_RECENT_TOTAL_BYTES) {
            kept += item
            total += item.size
        }
    }
    val keptIds = kept.mapTo(HashSet()) { it.id }
    merged.filterNot { it.id in keptIds }.forEach { removed ->
        File(File(context.filesDir, "recent"), removed.id).deleteRecursively()
    }
    saveRecentConversions(context, kept)
    return entry
}

internal fun loadRecentConversions(context: Context): List<RecentConversion> {
    val raw = context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
        .getString(RECENT_KEY, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val files = ArrayList<RecentFile>()
                val fileArray = item.optJSONArray("files") ?: JSONArray()
                for (fileIndex in 0 until fileArray.length()) {
                    val file = fileArray.optJSONObject(fileIndex) ?: continue
                    val path = file.optString("path")
                    if (path.isBlank() || !File(path).exists()) continue
                    files += RecentFile(
                        name = file.optString("name"),
                        mime = file.optString("mime"),
                        path = path,
                    )
                }
                if (files.isEmpty()) continue
                add(
                    RecentConversion(
                        id = item.optString("id"),
                        title = item.optString("title", files.first().name),
                        count = files.size,
                        size = item.optLong("size", files.sumOf { File(it.path).length() }),
                        timestamp = item.optLong("timestamp", 0L),
                        files = files,
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun clearRecentConversions(context: Context) {
    loadRecentConversions(context).forEach { entry ->
        File(File(context.filesDir, "recent"), entry.id).deleteRecursively()
    }
    context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(RECENT_KEY)
        .apply()
}

internal fun shareRecentConversion(context: Context, entry: RecentConversion): Boolean =
    shareRecentFiles(context, entry.files)

internal fun openRecentConversion(context: Context, entry: RecentConversion): Boolean {
    val file = entry.files.firstOrNull() ?: return false
    val uri = recentUri(context, file) ?: return false
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, file.mime.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        context.startActivity(Intent.createChooser(intent, "打开文件"))
        true
    }.getOrDefault(false)
}

internal fun rememberedExportFolder(context: Context): Uri? =
    context.getSharedPreferences(EXPORT_PREFS, Context.MODE_PRIVATE)
        .getString(EXPORT_FOLDER_KEY, null)
        ?.let { raw -> runCatching { Uri.parse(raw) }.getOrNull() }

internal fun rememberExportFolder(context: Context, uri: Uri) {
    context.getSharedPreferences(EXPORT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(EXPORT_FOLDER_KEY, uri.toString())
        .apply()
}

/** Writes every converted file into a SAF tree, keeping the original names. */
internal fun saveFilesToTree(
    context: Context,
    files: List<ConvertedFile>,
    treeUri: Uri,
): Int {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
    var saved = 0
    val usedNames = HashSet<String>()
    files.forEachIndexed { index, file ->
        var name = sanitizeRecentName(file.name).ifBlank { "file_${index + 1}" }
        var suffix = 2
        while (!usedNames.add(name)) {
            val base = name.substringBeforeLast('.', name)
            val extension = name.substringAfterLast('.', "")
            name = if (extension.isBlank()) "${base}_$suffix" else "${base}_$suffix.$extension"
            suffix += 1
        }
        val extension = name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        var document = root.createFile(mime, name) ?: return@forEachIndexed
        if (document.name != name) {
            val renamed = runCatching { document.renameTo(name) }.getOrDefault(false)
            if (renamed) {
                document = root.findFile(name) ?: document
            }
        }
        val written = runCatching {
            context.contentResolver.openOutputStream(document.uri)?.use {
                it.write(file.bytes)
            } != null
        }.getOrDefault(false)
        if (written) saved += 1
    }
    return saved
}

internal fun shareConvertedFiles(context: Context, outputs: List<ConvertedFile>): Boolean {    if (outputs.isEmpty()) return false
    val shareRoot = File(context.cacheDir, "share")
    shareRoot.deleteRecursively()
    if (!shareRoot.mkdirs()) return false
    val files = ArrayList<RecentFile>()
    val usedNames = HashSet<String>()
    outputs.forEachIndexed { index, output ->
        var name = sanitizeRecentName(output.name).ifBlank { "file_${index + 1}" }
        var suffix = 2
        while (!usedNames.add(name)) {
            val base = name.substringBeforeLast('.', name)
            val extension = name.substringAfterLast('.', "")
            name = if (extension.isBlank()) "${base}_$suffix" else "${base}_$suffix.$extension"
            suffix += 1
        }
        val file = File(shareRoot, name)
        runCatching { file.outputStream().use { it.write(output.bytes) } }
            .onSuccess { files += RecentFile(name, output.mime, file.absolutePath) }
    }
    return shareRecentFiles(context, files)
}

internal fun recentPreview(entry: RecentConversion, maxDimension: Int = 96): Bitmap? {
    val file = entry.files.firstOrNull { it.mime.startsWith("image/") } ?: return null
    val path = file.path
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
        sample *= 2
    }
    return runCatching {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }.getOrNull()
}

private fun shareRecentFiles(context: Context, files: List<RecentFile>): Boolean {
    if (files.isEmpty()) return false
    val uris = files.mapNotNull { recentUri(context, it) }
    if (uris.isEmpty()) return false
    val mime = if (files.map { it.mime }.distinct().size == 1) {
        files.first().mime.ifBlank { "*/*" }
    } else {
        "*/*"
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        context.startActivity(Intent.createChooser(intent, "分享文件"))
        true
    }.getOrDefault(false)
}

private fun recentUri(context: Context, file: RecentFile): Uri? = runCatching {
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(file.path),
    )
}.getOrNull()

private fun saveRecentConversions(context: Context, entries: List<RecentConversion>) {
    val array = JSONArray()
    entries.forEach { entry ->
        val item = JSONObject()
        item.put("id", entry.id)
        item.put("title", entry.title)
        item.put("size", entry.size)
        item.put("timestamp", entry.timestamp)
        val files = JSONArray()
        entry.files.forEach { file ->
            files.put(JSONObject().apply {
                put("name", file.name)
                put("mime", file.mime)
                put("path", file.path)
            })
        }
        item.put("files", files)
        array.put(item)
    }
    context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(RECENT_KEY, array.toString())
        .apply()
}

private fun sanitizeRecentName(name: String): String =
    name.substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(120)
        .ifBlank { "file" }
