package com.example.toolkit.ui.tools

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.components.ToolIconTile
import com.example.toolkit.ui.design.liquidGlass
import com.example.toolkit.ui.icons.GlyphIcon
import com.example.toolkit.ui.model.ToolItem
import com.example.toolkit.ui.theme.AppleBlue
import com.example.toolkit.ui.theme.DangerRed
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_INPUT_BYTES = 80L * 1024 * 1024
private const val MAX_MEDIA_INPUT_BYTES = 512L * 1024 * 1024
private const val MAX_ARCHIVE_INPUT_BYTES = 512L * 1024 * 1024
private const val MAX_BATCH_FILES = 20

internal fun fileConvertCommonTargets(files: List<PickedFile>): List<ConvertFormat> {
    if (files.isEmpty()) return emptyList()
    val lists = files.map { ConvertFormats.targets(it.format) }
    val common = lists.first().filter { candidate ->
        lists.all { targets -> targets.any { it.id == candidate.id } }
    }
    return if (files.size < 2) common.filter { it.id != "merge_pdf" } else common
}

private fun uniqueOutputNames(files: List<ConvertedFile>): List<ConvertedFile> {
    val used = HashSet<String>()
    return files.map { file ->
        var name = file.name
        var suffix = 2
        while (!used.add(name)) {
            val base = name.substringBeforeLast('.', name)
            val extension = name.substringAfterLast('.', "")
            name = if (extension.isBlank()) {
                "${base}_$suffix"
            } else {
                "${base}_$suffix.$extension"
            }
            suffix += 1
        }
        file.copy(name = name)
    }
}

private data class PendingSave(
    val bytes: ByteArray,
    val name: String,
    val mime: String,
)

/**
 * Filee-style local converter: pick a file, choose only the target formats that
 * make sense for it, convert on-device and save with the system document picker.
 */
@Composable
fun FileConvertTool(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sources by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var target by remember { mutableStateOf<ConvertFormat?>(null) }
    var quality by remember { mutableStateOf(90f) }
    var converting by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<ConvertOutcome?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<PendingSave?>(null) }
    var extracting by remember { mutableStateOf(false) }
    var extractMessage by remember { mutableStateOf<String?>(null) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var archivePassword by remember { mutableStateOf("") }
    var archiveEncrypted by remember { mutableStateOf(false) }
    var archiveListing by remember { mutableStateOf<ArchiveListing?>(null) }
    var archiveEntrySelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var archiveListingLoading by remember { mutableStateOf(false) }
    var archiveListingError by remember { mutableStateOf<String?>(null) }
    var archiveQuery by remember { mutableStateOf("") }
    var archivePreview by remember { mutableStateOf<ArchivePreview?>(null) }
    var archivePreviewLoading by remember { mutableStateOf(false) }
    var archivePreviewName by remember { mutableStateOf<String?>(null) }
    var pdfRotation by remember { mutableStateOf(90) }
    var pdfSelected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pdfOrder by remember { mutableStateOf<List<Int>>(emptyList()) }
    var insertPdf by remember { mutableStateOf<PickedFile?>(null) }
    var insertPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var insertAtStart by remember { mutableStateOf(false) }
    var insertPicking by remember { mutableStateOf(false) }
    var splitSizeMb by remember { mutableStateOf("100") }
    var recent by remember { mutableStateOf<List<RecentConversion>>(emptyList()) }
    val previewState = remember { mutableStateOf<Bitmap?>(null) }
    val preview = previewState.value

    suspend fun loadPickedUris(uris: List<Uri>, mimeHint: String? = null) {
        if (uris.isEmpty()) return
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        val picked = withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                runCatching { fileConvertReadPicked(context, uri, mimeHint) }.getOrNull()
            }
        }
        if (picked.isEmpty()) {
            error = "无法读取所选文件"
            return
        }

        val accepted = ArrayList<PickedFile>()
        var skipped = 0
        for (file in picked.take(MAX_BATCH_FILES)) {
            val isMedia = file.format.kind == ConvertKind.Audio ||
                file.format.kind == ConvertKind.Video
            val isArchive = file.format.kind == ConvertKind.Archive
            val limit = when {
                isMedia -> MAX_MEDIA_INPUT_BYTES
                isArchive -> MAX_ARCHIVE_INPUT_BYTES
                else -> MAX_INPUT_BYTES
            }
            if (file.size > limit) {
                skipped += 1
            } else {
                accepted += file
            }
        }
        if (accepted.isEmpty()) {
            error = "所选文件都过大，无法转换"
            return
        }

        sources = accepted
        val targets = fileConvertCommonTargets(accepted)
        target = targets.firstOrNull { it.id != accepted.first().format.id }
            ?: targets.firstOrNull()
        archivePassword = ""
        pdfSelected = emptySet()
        pdfOrder = emptyList()
        insertPdf = null
        insertPages = emptySet()
        archiveListing = null
        archiveEntrySelection = emptySet()
        archiveListingError = null
        archiveQuery = ""
        archivePreview = null
        archivePreviewName = null
        archiveEncrypted = withContext(Dispatchers.IO) {
            accepted.any { file ->
                file.format.kind == ConvertKind.Archive && archiveRequiresPassword(context, file)
            }
        }
        outcome = null
        progressText = null
        extractMessage = when {
            picked.size > MAX_BATCH_FILES -> "一次最多转换 $MAX_BATCH_FILES 个文件，已选择前 $MAX_BATCH_FILES 个"
            skipped > 0 -> "已跳过 $skipped 个过大的文件"
            else -> null
        }
        error = null
    }

    LaunchedEffect(Unit) {
        recent = withContext(Dispatchers.IO) {
            loadRecentConversions(context)
        }
    }

    LaunchedEffect(IncomingShare.version) {
        val shared = IncomingShare.consume() ?: return@LaunchedEffect
        loadPickedUris(shared.uris, shared.mime)
    }

    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch { loadPickedUris(uris) }
    }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pending = pendingSave
        pendingSave = null
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && pending != null) {
            saving = true
            scope.launch {
                val saved = fileConvertWriteToUri(context, pending.bytes, uri)
                saving = false
                Toast.makeText(
                    context,
                    if (saved) "文件已保存" else "保存失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val pickTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val archives = sources.filter { it.format.kind == ConvertKind.Archive }
        if (uri == null || archives.isEmpty()) {
            extracting = false
            progressText = null
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        rememberExportFolder(context, uri)
        val selectionForExtract = if (target?.id == "extract_selected") {
            val listing = archiveListing
            if (listing != null && !listing.truncated) archiveEntrySelection else null
        } else {
            null
        }
        scope.launch {
            var files = 0
            var directories = 0
            var failures = 0
            var failureMessage: String? = null
            val password = archivePassword.takeIf { it.isNotBlank() }
            archives.forEachIndexed { index, archive ->
                progressText = "解压 ${index + 1}/${archives.size}"
                runCatching {
                    withContext(Dispatchers.IO) {
                        extractArchiveToTree(context, archive, uri, password, selectionForExtract)
                    }
                }.onSuccess { extracted ->
                    files += extracted.files
                    directories += extracted.directories
                }.onFailure { throwable ->
                    failures += 1
                    failureMessage = throwable.message
                    if (throwable is ArchivePasswordException) archiveEncrypted = true
                }
            }
            extracting = false
            progressText = null
            outcome = null
            if (files == 0 && directories == 0) {
                error = failureMessage?.takeIf { it.isNotBlank() }
                    ?: "解压失败，请检查压缩包"
            } else {
                error = null
                extractMessage = buildString {
                    append("已解压 $files 个文件、$directories 个目录")
                    if (failures > 0) append("，$failures 个压缩包失败")
                }
                Toast.makeText(context, extractMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val saveTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val converted = outcome
        if (uri == null || converted == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        rememberExportFolder(context, uri)
        saving = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { saveFilesToTree(context, converted.files, uri) }.getOrDefault(0)
            }
            saving = false
            Toast.makeText(
                context,
                if (saved == converted.files.size) {
                    "$saved 个文件已保存"
                } else {
                    "已保存 $saved/${converted.files.size} 个文件"
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val pickInsertPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        scope.launch {
            insertPicking = true
            val picked = withContext(Dispatchers.IO) {
                runCatching { fileConvertReadPicked(context, uri) }.getOrNull()
            }
            insertPicking = false
            if (picked == null || picked.format.id != "pdf") {
                error = "请选择有效的 PDF 文件"
                return@launch
            }
            if (picked.size > MAX_INPUT_BYTES) {
                error = "PDF 超过 ${MAX_INPUT_BYTES / 1024 / 1024} MB"
                return@launch
            }
            insertPdf = picked
            insertPages = emptySet()
            error = null
        }
    }

    val singleArchive = sources.singleOrNull()
        ?.takeIf { it.format.kind == ConvertKind.Archive }
    LaunchedEffect(
        target?.id,
        singleArchive?.uri,
        archivePassword,
        archiveEncrypted,
    ) {
        if (target?.id != "extract_selected" || singleArchive == null) {
            archivePreview = null
            archivePreviewName = null
            return@LaunchedEffect
        }
        if (archiveEncrypted && archivePassword.isBlank()) {
            archiveListing = null
            archiveEntrySelection = emptySet()
            archiveListingLoading = false
            archiveListingError = null
            return@LaunchedEffect
        }
        if (archiveEncrypted) delay(600)
        archiveListingLoading = true
        archiveListingError = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                listArchiveFiles(
                    context,
                    singleArchive,
                    archivePassword.takeIf { it.isNotBlank() },
                )
            }
        }
        archiveListingLoading = false
        result.onSuccess { listing ->
            archiveListing = listing
            archiveEntrySelection = listing.files.mapTo(HashSet()) { it.name }
            archiveListingError = null
        }.onFailure { throwable ->
            archiveListing = null
            archiveEntrySelection = emptySet()
            archiveListingError = throwable.message ?: "无法读取压缩包内容"
            if (throwable is ArchivePasswordException) archiveEncrypted = true
        }
    }

    LaunchedEffect(outcome) {
        previewState.value = null
        val first = outcome?.files?.firstOrNull()
        if (first != null && first.mime.startsWith("image/")) {
            previewState.value = withContext(Dispatchers.IO) {
                fileConvertDecodePreview(first.bytes)
            }
        }
    }

    fun previewArchiveEntry(entry: ArchiveEntryInfo) {
        val archive = sources.singleOrNull() ?: return
        archivePreviewName = entry.name
        archivePreview = null
        archivePreviewLoading = true
        val password = archivePassword.takeIf { it.isNotBlank() }
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching { readArchiveEntry(context, archive, password, entry.name) }.getOrNull()
            }
            archivePreviewLoading = false
            if (content == null) {
                archivePreview = ArchivePreview(
                    name = entry.name,
                    size = entry.size,
                    message = "无法读取该文件",
                )
                return@launch
            }
            if (content.truncated) {
                archivePreview = ArchivePreview(
                    name = entry.name,
                    size = entry.size,
                    message = "文件超过 8 MB，已跳过预览，可直接勾选解压",
                )
                return@launch
            }
            val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.US)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            val bitmap = if (mime?.startsWith("image/") == true) {
                withContext(Dispatchers.IO) {
                    fileConvertDecodePreview(content.bytes, maxDimension = 900)
                }
            } else {
                null
            }
            val text = if (bitmap == null && (
                    mime?.startsWith("text/") == true ||
                        extension in setOf(
                            "txt", "md", "json", "xml", "csv", "log", "ini",
                            "yml", "yaml", "srt", "vtt", "html", "htm",
                        )
                    )
            ) {
                content.bytes.toString(Charsets.UTF_8).take(4000)
            } else {
                null
            }
            archivePreview = ArchivePreview(
                name = entry.name,
                size = entry.size,
                bitmap = bitmap,
                text = text,
                message = if (bitmap == null && text == null) "该类型暂不支持预览" else null,
            )
        }
    }

    fun openPicker() {
        if (!converting && !extracting) pickFile.launch(arrayOf("*/*"))
    }

    fun startConvert() {
        val pickedFiles = sources
        val format = target ?: return
        if (pickedFiles.isEmpty() || converting || extracting) return

        if (format.id == "zip_enc" && archivePassword.isBlank()) {
            error = "请先输入加密密码"
            return
        }
        if (format.kind == ConvertKind.Archive && format.id != "zip_enc" &&
            archiveEncrypted && archivePassword.isBlank()
        ) {
            error = "该压缩包已加密，请输入密码"
            return
        }
        if ((format.id == "extract_pdf" || format.id == "delete_pdf") && pdfSelected.isEmpty()) {
            error = "请先点选至少一页"
            return
        }
        if (format.id == "reorder_pdf" && pdfOrder.isEmpty()) {
            error = "正在读取页面，请稍候再试"
            return
        }
        val splitMb = splitSizeMb.trim().toIntOrNull()
        if (format.id == "zip_split" && (splitMb == null || splitMb !in 1..2048)) {
            error = "分卷大小需在 1-2048 MB 之间"
            return
        }

        if (format.id == "extract" || format.id == "extract_selected") {
            if (format.id == "extract_selected") {
                if (pickedFiles.size != 1) {
                    error = "选择解压只支持单个压缩包"
                    return
                }
                if (archiveEncrypted && archivePassword.isBlank()) {
                    error = "请输入压缩包密码"
                    return
                }
                val listing = archiveListing
                if (listing == null) {
                    error = archiveListingError ?: "正在读取压缩包内容，请稍候"
                    return
                }
                if (!listing.truncated && archiveEntrySelection.isEmpty()) {
                    error = "请至少选择一个文件"
                    return
                }
            }
            error = null
            extractMessage = null
            extracting = true
            pickTree.launch(rememberedExportFolder(context))
            return
        }

        if (format.id == "merge_pdf") {
            if (pickedFiles.size < 2 || pickedFiles.any { it.format.id != "pdf" }) {
                error = "合并 PDF 需要至少两个 PDF 文件"
                return
            }
            converting = true
            error = null
            outcome = null
            extractMessage = null
            progressText = "合并 PDF"
            scope.launch {
                val result = runCatching { mergePdfFiles(context, pickedFiles) }
                converting = false
                progressText = null
                result.onSuccess { converted ->
                    outcome = converted
                    withContext(Dispatchers.IO) {
                        recordRecentConversion(context, converted.files)
                    }
                    recent = withContext(Dispatchers.IO) {
                        loadRecentConversions(context)
                    }
                }.onFailure { throwable ->
                    error = throwable.message?.takeIf { it.isNotBlank() } ?: "合并失败，请重试"
                }
            }
            return
        }

        if (format.id == "insert_pdf") {
            if (pickedFiles.size != 1) {
                error = "插入 PDF 只支持单个基准 PDF"
                return
            }
            val extra = insertPdf
            if (extra == null) {
                error = "请先选择要插入的 PDF"
                return
            }
            converting = true
            error = null
            outcome = null
            extractMessage = null
            progressText = "插入页面"
            scope.launch {
                val result = runCatching {
                    insertPdfPages(
                        context,
                        pickedFiles.first(),
                        extra,
                        insertPages.sorted(),
                        insertAtStart,
                    )
                }
                converting = false
                progressText = null
                result.onSuccess { converted ->
                    outcome = converted
                    withContext(Dispatchers.IO) {
                        recordRecentConversion(context, converted.files)
                    }
                    recent = withContext(Dispatchers.IO) {
                        loadRecentConversions(context)
                    }
                }.onFailure { throwable ->
                    error = throwable.message?.takeIf { it.isNotBlank() } ?: "插入失败，请重试"
                }
            }
            return
        }

        converting = true
        error = null
        outcome = null
        extractMessage = null
        progressText = "转换 1/${pickedFiles.size}"
        scope.launch {
            val combined = ArrayList<ConvertedFile>()
            val failures = ArrayList<String>()
            val notices = LinkedHashSet<String>()
            pickedFiles.forEachIndexed { index, picked ->
                progressText = "转换 ${index + 1}/${pickedFiles.size}"
                runCatching {
                    fileConvertRun(
                        context,
                        picked,
                        format,
                        quality.roundToInt(),
                        archivePassword.takeIf { it.isNotBlank() },
                        pageRange = pageNumbersToRangeText(pdfSelected),
                        rotation = pdfRotation,
                        splitSizeBytes = splitMb?.toLong()?.times(1024L * 1024L),
                        pageOrder = pdfOrder.joinToString(","),
                    )
                }.onSuccess { converted ->
                    combined += converted.files
                    converted.notice?.takeIf { it.isNotBlank() }?.let { notices += it }
                }.onFailure { throwable ->
                    if (throwable is ArchivePasswordException) archiveEncrypted = true
                    failures += "${picked.name}: ${throwable.message ?: "转换失败"}"
                }
            }
            converting = false
            progressText = null
            if (combined.isEmpty()) {
                error = failures.take(3).joinToString("\n").ifBlank { "没有生成任何文件" }
            } else {
                val normalized = uniqueOutputNames(combined)
                val notice = buildList {
                    if (failures.isNotEmpty()) add("有 ${failures.size} 个文件转换失败")
                    notices.forEach { add(it) }
                }.joinToString("；").ifBlank { null }
                outcome = ConvertOutcome(
                    files = normalized,
                    notice = notice,
                )
                withContext(Dispatchers.IO) {
                    recordRecentConversion(context, normalized)
                }
                recent = withContext(Dispatchers.IO) {
                    loadRecentConversions(context)
                }
            }
        }
    }

    fun launchCreateDocument(name: String, mime: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, name)
        }
        runCatching { createDocument.launch(intent) }.onFailure {
            pendingSave = null
            error = "无法打开系统保存面板"
        }
    }

    fun saveOutcome() {
        val converted = outcome ?: return
        if (saving || converting || converted.files.isEmpty()) return

        val multiImages = converted.files.size > 1 && converted.files.all { it.mime.startsWith("image/") }

        if (converted.files.size == 1) {
            val file = converted.files.first()
            pendingSave = PendingSave(file.bytes, file.name, file.mime)
            launchCreateDocument(file.name, file.mime)
            return
        }

        if (multiImages && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saving = true
            scope.launch {
                var saved = 0
                converted.files.forEach { file ->
                    if (saveBytesToGallery(context, file.bytes, file.name, file.mime)) saved += 1
                }
                saving = false
                Toast.makeText(
                    context,
                    if (saved == converted.files.size) {
                        "$saved 张图片已保存到相册"
                    } else {
                        "已保存 $saved/${converted.files.size} 张图片"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }

        val zip = fileConvertZip(
            converted.files,
            sources.firstOrNull()?.name ?: "files",
        )
        pendingSave = PendingSave(zip.bytes, zip.name, zip.mime)
        launchCreateDocument(zip.name, zip.mime)
    }

    fun shareOutcome() {
        val converted = outcome ?: return
        if (converted.files.isEmpty()) return
        val shared = shareConvertedFiles(context, converted.files)
        if (!shared) {
            Toast.makeText(context, "没有可分享的应用", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = 26.dp)
            .padding(16.dp),
    ) {
        FileConvertHeader(tool)
        Spacer(Modifier.height(16.dp))

        val pickedFiles = sources
        if (pickedFiles.isEmpty()) {
            FileConvertEmptyState(
                color = tool.color,
                onPick = { openPicker() },
            )
        } else {
            FileConvertSourceCard(
                files = pickedFiles,
                color = tool.color,
                onReplace = { openPicker() },
            )
            Spacer(Modifier.height(14.dp))

            val targets = fileConvertCommonTargets(pickedFiles)
            if (targets.isEmpty()) {
                FileConvertUnsupportedCard(
                    format = pickedFiles.first().format,
                    batch = pickedFiles.size > 1,
                )
                Spacer(Modifier.height(12.dp))
                ImageToolSecondaryButton(
                    text = "重新选择",
                    onClick = { openPicker() },
                )
            } else {
                Text(
                    text = "转换为",
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                )
                Spacer(Modifier.height(8.dp))
                FileConvertTargetGrid(
                    targets = targets,
                    selected = target,
                    onSelect = { format ->
                        target = format
                        outcome = null
                        extractMessage = null
                        error = null
                    },
                )

                val selected = target
                if (selected != null) {
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = fileConvertHint(pickedFiles.first(), selected),
                        modifier = Modifier.fillMaxWidth(),
                        color = Ink3,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )

                    if (selected.id in setOf(
                            "jpg", "webp", "jxl", "compress_pdf",
                            "mp3", "m4a", "ogg", "opus",
                            "mp4", "webm", "mkv", "mov", "avi",
                        )
                    ) {
                        Spacer(Modifier.height(6.dp))
                        FileConvertQualitySlider(
                            value = quality,
                            onValueChange = {
                                quality = it
                                outcome = null
                                error = null
                            },
                        )
                    }

                    if (selected.id == "rotate_pdf") {
                        Spacer(Modifier.height(10.dp))
                        FileConvertRotationPicker(
                            value = pdfRotation,
                            onSelect = {
                                pdfRotation = it
                                outcome = null
                                error = null
                            },
                        )
                    }

                    if (selected.id == "reorder_pdf") {
                        Spacer(Modifier.height(10.dp))
                        val pdfFile = pickedFiles.firstOrNull { it.format.id == "pdf" }
                        if (pdfFile != null) {
                            PdfPageReorderList(
                                uri = pdfFile.uri,
                                order = pdfOrder,
                                onOrderChange = {
                                    pdfOrder = it
                                    error = null
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "位置 1 会输出为第一页；批量时同一顺序会应用到每个 PDF",
                                color = Ink3,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }

                    if (selected.id == "insert_pdf") {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "插入的 PDF",
                                    color = Ink3,
                                    fontSize = 11.sp,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = when {
                                        insertPicking -> "正在读取…"
                                        insertPdf != null -> insertPdf?.name.orEmpty()
                                        else -> "尚未选择"
                                    },
                                    color = if (insertPdf == null) Ink3 else Ink,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            ImageToolTextButton(
                                text = if (insertPdf == null) "选择" else "更换",
                                onClick = { pickInsertPdf.launch(arrayOf("application/pdf")) },
                            )
                        }

                        val extra = insertPdf
                        if (extra != null) {
                            Spacer(Modifier.height(8.dp))
                            PdfPagePicker(
                                uri = extra.uri,
                                selected = insertPages,
                                onToggle = { page ->
                                    insertPages = if (page in insertPages) {
                                        insertPages - page
                                    } else {
                                        insertPages + page
                                    }
                                    error = null
                                },
                                onSelectAll = { count ->
                                    insertPages = (1..count).toSet()
                                    error = null
                                },
                                onInvert = { count ->
                                    insertPages = (1..count)
                                        .filter { it !in insertPages }
                                        .toSet()
                                    error = null
                                },
                                onClear = { insertPages = emptySet() },
                                gridHeight = 300,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(false to "插到结尾", true to "插到开头").forEach { (start, label) ->
                                    val active = insertAtStart == start
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(
                                                if (active) AppleBlue else Color.White.copy(alpha = 0.72f),
                                            )
                                            .border(
                                                1.dp,
                                                if (active) AppleBlue else Color.White.copy(alpha = 0.92f),
                                                RoundedCornerShape(11.dp),
                                            )
                                            .clickable { insertAtStart = start }
                                            .padding(vertical = 9.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (active) Color.White else Ink,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = if (insertPages.isEmpty()) {
                                    "未选择页面时插入全部页面；只能对单个基准 PDF 操作"
                                } else {
                                    "已选 ${insertPages.size} 页；只能对单个基准 PDF 操作"
                                },
                                color = Ink3,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }

                    if (selected.id == "extract_pdf" || selected.id == "delete_pdf") {
                        Spacer(Modifier.height(10.dp))
                        val pdfFile = pickedFiles.firstOrNull { it.format.id == "pdf" }
                        if (pdfFile != null) {
                            PdfPagePicker(
                                uri = pdfFile.uri,
                                selected = pdfSelected,
                                onToggle = { page ->
                                    pdfSelected = if (page in pdfSelected) {
                                        pdfSelected - page
                                    } else {
                                        pdfSelected + page
                                    }
                                    error = null
                                },
                                onSelectAll = { count ->
                                    pdfSelected = (1..count).toSet()
                                    error = null
                                },
                                onInvert = { count ->
                                    pdfSelected = (1..count)
                                        .filter { it !in pdfSelected }
                                        .toSet()
                                    error = null
                                },
                                onClear = {
                                    pdfSelected = emptySet()
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (selected.id == "extract_pdf") {
                                    "点选要保留的页面，未选中的页面会被丢弃" +
                                        if (pickedFiles.size > 1) "；批量时页码会应用到每个 PDF" else ""
                                } else {
                                    "点选要删除的页面，其余页面会保留" +
                                        if (pickedFiles.size > 1) "；批量时页码会应用到每个 PDF" else ""
                                },
                                color = Ink3,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }

                    val archiveSelected = pickedFiles.any {
                        it.format.kind == ConvertKind.Archive
                    }
                    if (archiveSelected) {
                        Spacer(Modifier.height(10.dp))
                        FileConvertInputField(
                            value = archivePassword,
                            onValueChange = {
                                archivePassword = it
                                error = null
                            },
                            label = if (selected.id == "zip_enc") "加密密码" else "压缩包密码",
                            hint = when {
                                selected.id == "zip_enc" -> "使用 AES-256 重新打包，密码不能为空"
                                archiveEncrypted -> "已检测到密码保护，需要密码才能读取"
                                else -> "普通压缩包可留空；填写后可用于加密 ZIP"
                            },
                            placeholder = "输入密码",
                            required = selected.id == "zip_enc" || archiveEncrypted,
                            mask = true,
                            keyboardType = KeyboardType.Password,
                        )
                    }

                    if (selected.id == "extract_selected") {
                        Spacer(Modifier.height(10.dp))
                        val listing = archiveListing
                        when {
                            pickedFiles.size != 1 -> Text(
                                text = "选择解压只支持单个压缩包",
                                color = Ink3,
                                fontSize = 10.5.sp,
                            )

                            archiveEncrypted && archivePassword.isBlank() -> Text(
                                text = "输入上面的密码后会自动读取文件列表",
                                color = Ink3,
                                fontSize = 10.5.sp,
                            )

                            archiveListingLoading -> Text(
                                text = "正在读取压缩包内容…",
                                color = Ink3,
                                fontSize = 10.5.sp,
                            )

                            archiveListingError != null -> Text(
                                text = archiveListingError.orEmpty(),
                                color = DangerRed,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp,
                            )

                            listing != null && listing.truncated -> Text(
                                text = "文件超过 ${listing.files.size} 个，本次将解压全部文件",
                                color = Ink3,
                                fontSize = 10.5.sp,
                            )

                            listing != null -> ArchiveEntryPicker(
                                entries = listing.files,
                                selected = archiveEntrySelection,
                                query = archiveQuery,
                                onQueryChange = {
                                    archiveQuery = it
                                    error = null
                                },
                                onToggle = { name ->
                                    archiveEntrySelection = if (name in archiveEntrySelection) {
                                        archiveEntrySelection - name
                                    } else {
                                        archiveEntrySelection + name
                                    }
                                    error = null
                                },
                                onSelectAll = { visible ->
                                    archiveEntrySelection = visible
                                        .mapTo(HashSet()) { it.name }
                                    error = null
                                },
                                onInvert = { visible ->
                                    val next = archiveEntrySelection.toMutableSet()
                                    visible.forEach { entry ->
                                        if (!next.remove(entry.name)) next.add(entry.name)
                                    }
                                    archiveEntrySelection = next
                                    error = null
                                },
                                onPreview = { entry -> previewArchiveEntry(entry) },
                                previewingName = if (archivePreviewLoading) {
                                    archivePreviewName
                                } else {
                                    null
                                },
                            )
                        }

                        if (archivePreviewLoading) {
                            Spacer(Modifier.height(7.dp))
                            Text(
                                text = "正在读取预览…",
                                color = Ink3,
                                fontSize = 10.5.sp,
                            )
                        }
                        archivePreview?.let { preview ->
                            Spacer(Modifier.height(8.dp))
                            ArchivePreviewCard(
                                preview = preview,
                                onClose = {
                                    archivePreview = null
                                    archivePreviewName = null
                                },
                            )
                        }
                    }

                    if (selected.id == "zip_split") {
                        Spacer(Modifier.height(10.dp))
                        FileConvertInputField(
                            value = splitSizeMb,
                            onValueChange = { input ->
                                splitSizeMb = input.filter { it.isDigit() }.take(4)
                                error = null
                            },
                            label = "每个分卷大小",
                            hint = "单位 MB，范围 1-2048；分卷 ZIP 不加密",
                            placeholder = "100",
                            required = true,
                            keyboardType = KeyboardType.Number,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ImageToolSecondaryButton(
                        text = "重新选择",
                        onClick = { openPicker() },
                    )
                    Spacer(Modifier.weight(1f))
                    ImageToolPrimaryButton(
                        text = when {
                            extracting -> progressText ?: "解压中"
                            converting -> progressText ?: "转换中"
                            target?.id == "extract" || target?.id == "extract_selected" ->
                                "选择文件夹并解压"
                            else -> "开始转换"
                        },
                        enabled = target != null && pickedFiles.isNotEmpty() && !converting && !extracting,
                        loading = converting || extracting,
                        onClick = { startConvert() },
                    )
                }
            }
        }

        outcome?.let { converted ->
            Spacer(Modifier.height(14.dp))
            FileConvertResultCard(
                outcome = converted,
                preview = preview,
                saving = saving,
                savedToGallery = converted.files.size > 1 &&
                    converted.files.all { it.mime.startsWith("image/") } &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                onShare = { shareOutcome() },
                onSaveToFolder = {
                    saveTree.launch(rememberedExportFolder(context))
                },
                onSave = { saveOutcome() },
            )
        }

        extractMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                color = AppleBlue,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
        }

        error?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                color = DangerRed,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
        }

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            FileConvertRecentSection(
                entries = recent.take(4),
                onOpen = { entry ->
                    if (!openRecentConversion(context, entry)) {
                        Toast.makeText(context, "没有可打开的应用", Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = { entry ->
                    if (!shareRecentConversion(context, entry)) {
                        Toast.makeText(context, "没有可分享的应用", Toast.LENGTH_SHORT).show()
                    }
                },
                onClear = {
                    clearRecentConversions(context)
                    recent = emptyList()
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "全部转换在本机完成，文件不会上传",
            modifier = Modifier.fillMaxWidth(),
            color = Ink3,
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.1).sp,
        )
    }
}

@Composable
private fun FileConvertHeader(tool: ToolItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ToolIconTile(
            color = tool.color,
            iconKey = tool.iconKey,
            size = 46.dp,
            corner = 15.dp,
            iconSize = 23.dp,
            strokeWidth = 1.6f,
            tintAlpha = 0.24f,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "文件格式转换",
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Spacer(Modifier.width(6.dp))
                FileConvertBadge()
            }
            Text(
                text = "图片、PDF、Office、EPUB、文本、数据、字幕、音视频与压缩包，全部本地转换",
                color = Ink3,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun FileConvertBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppleBlue.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "本地离线",
            color = AppleBlue,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FileConvertEmptyState(
    color: Color,
    onPick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFFFFBF3),
                    1f to Color(0xFFF4F7FC),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.90f), shape)
            .padding(horizontal = 18.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            key = "convert",
            color = color,
            modifier = Modifier.size(38.dp),
            strokeWidth = 1.4f,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "选择文件开始转换",
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "支持图片、PDF、Office、EPUB、文本、数据、字幕、音视频和压缩包",
            color = Ink3,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "ZIP · TIFF · PSD · MP4 · DOCX · EPUB · JSON",
            color = AppleBlue,
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.1).sp,
        )
        Spacer(Modifier.height(18.dp))
        ImageToolPrimaryButton(
            text = "选择文件",
            onClick = onPick,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "支持多选批量转换 · 文件不会上传",
            color = Ink3,
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FileConvertSourceCard(
    files: List<PickedFile>,
    color: Color,
    onReplace: () -> Unit,
) {
    val first = files.first()
    val shape = RoundedCornerShape(16.dp)
    val knownSizes = files.map { it.size }.filter { it >= 0 }
    val totalSize = if (knownSizes.isEmpty()) -1L else knownSizes.sum()
    val formats = files.map { it.format.label }.distinct()
        .let { labels ->
            if (labels.size <= 3) labels.joinToString(" / ")
            else labels.take(3).joinToString(" / ") + " +${labels.size - 3}"
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.64f))
            .border(1.dp, Color.White.copy(alpha = 0.92f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolIconTile(
            color = color,
            iconKey = fileConvertIconKey(first.format),
            size = 42.dp,
            corner = 13.dp,
            iconSize = 21.dp,
            strokeWidth = 1.5f,
            tintAlpha = 0.22f,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (files.size == 1) first.name else "已选择 ${files.size} 个文件",
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (files.size == 1) {
                    "${first.format.label} · ${fileConvertFormatSize(first.size)}"
                } else {
                    "$formats · ${fileConvertFormatSize(totalSize)}"
                },
                color = Ink3,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (files.size > 1) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = files.take(3).joinToString("、") { it.name } +
                        if (files.size > 3) " 等" else "",
                    color = Ink3,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        ImageToolTextButton(
            text = "更换",
            onClick = onReplace,
        )
    }
}

@Composable
private fun FileConvertUnsupportedCard(format: ConvertFormat, batch: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DangerRed.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.90f), shape)
            .padding(14.dp),
    ) {
        Text(
            text = if (batch) {
                "这些文件没有共同的目标格式"
            } else if (format.id == "unknown") {
                "无法识别这个文件"
            } else {
                "暂不支持 ${format.label}"
            },
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = if (batch) {
                "批量转换要求所有文件都能输出到同一种格式。可以分组选择，或先转换成一种通用格式。"
            } else {
                ConvertFormats.unsupportedReason(format)
            },
            color = Ink3,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun FileConvertTargetGrid(
    targets: List<ConvertFormat>,
    selected: ConvertFormat?,
    onSelect: (ConvertFormat) -> Unit,
) {
    val columns = if (targets.size <= 2) targets.size.coerceAtLeast(1) else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        targets.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { format ->
                    FileConvertTargetChip(
                        format = format,
                        selected = selected?.id == format.id,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(format) },
                    )
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FileConvertTargetChip(
    format: ConvertFormat,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(if (selected) AppleBlue else Color.White.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else Color.White.copy(alpha = 0.92f),
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = format.label,
                color = if (selected) Color.White else Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = format.kind.label,
                color = if (selected) Color.White.copy(alpha = 0.78f) else Ink3,
                fontSize = 9.5.sp,
            )
        }
    }
}

@Composable
private fun FileConvertInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    placeholder: String,
    required: Boolean = false,
    mask: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Ink3,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            if (required && value.isBlank()) {
                Text(
                    text = "必填",
                    color = DangerRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Ink,
                fontSize = 13.sp,
                letterSpacing = 0.sp,
            ),
            cursorBrush = SolidColor(AppleBlue),
            visualTransformation = if (mask) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.78f))
                        .border(1.dp, Color.White.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Ink3.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = hint,
            color = Ink3,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun FileConvertRotationPicker(
    value: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(
            text = "旋转方向（顺时针）",
            color = Ink3,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(90, 180, 270).forEach { angle ->
                val selected = value == angle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (selected) AppleBlue else Color.White.copy(alpha = 0.72f),
                        )
                        .border(
                            1.dp,
                            if (selected) AppleBlue else Color.White.copy(alpha = 0.92f),
                            RoundedCornerShape(11.dp),
                        )
                        .clickable { onSelect(angle) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$angle°",
                        color = if (selected) Color.White else Ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileConvertQualitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "输出质量",
                color = Ink3,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${value.roundToInt()}%",
                color = AppleBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 60f..100f,
            steps = 7,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = AppleBlue,
                activeTrackColor = AppleBlue,
                inactiveTrackColor = Color.White.copy(alpha = 0.72f),
            ),
        )
    }
}

@Composable
private fun FileConvertResultCard(
    outcome: ConvertOutcome,
    preview: Bitmap?,
    saving: Boolean,
    savedToGallery: Boolean,
    onShare: () -> Unit,
    onSaveToFolder: () -> Unit,
    onSave: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val files = outcome.files
    val totalBytes = files.sumOf { it.bytes.size.toLong() }
    val first = files.firstOrNull() ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.94f), shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(AppleBlue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = "转换完成",
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (files.size == 1) "1 个文件" else "${files.size} 个文件",
                color = AppleBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (preview != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF4F7FC))
                    .border(1.dp, Color.White.copy(alpha = 0.90f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "转换结果预览",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        Spacer(Modifier.height(9.dp))
        Text(
            text = if (files.size == 1) first.name else "${first.name} 等 ${files.size} 个文件",
            color = Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${first.mime} · ${fileConvertFormatSize(totalBytes)}",
            color = Ink3,
            fontSize = 11.sp,
        )

        outcome.notice?.let { notice ->
            Spacer(Modifier.height(5.dp))
            Text(
                text = notice,
                color = Color(0xFFB26A00),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        if (files.size > 1) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = if (savedToGallery) "将逐张保存到相册" else "将打包为 ZIP 保存",
                color = Ink3,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(11.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ImageToolSecondaryButton(
                text = "分享",
                onClick = onShare,
            )
            if (files.size > 1) {
                Spacer(Modifier.width(8.dp))
                ImageToolTextButton(
                    text = "保存到文件夹",
                    onClick = onSaveToFolder,
                )
            }
            Spacer(Modifier.weight(1f))
            ImageToolPrimaryButton(
                text = when {
                    savedToGallery -> "保存到相册"
                    files.size > 1 -> "保存 ZIP"
                    else -> "保存文件"
                },
                enabled = !saving,
                loading = saving,
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun FileConvertRecentSection(
    entries: List<RecentConversion>,
    onOpen: (RecentConversion) -> Unit,
    onShare: (RecentConversion) -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "最近转换",
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${entries.size} 条",
                color = Ink3,
                fontSize = 10.5.sp,
            )
            ImageToolTextButton(text = "清空", onClick = onClear)
        }
        Spacer(Modifier.height(8.dp))
        entries.forEach { entry ->
            FileConvertRecentRow(
                entry = entry,
                onOpen = { onOpen(entry) },
                onShare = { onShare(entry) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FileConvertRecentRow(
    entry: RecentConversion,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val preview = remember(entry.id) { recentPreview(entry) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.60f))
            .border(1.dp, Color.White.copy(alpha = 0.90f), shape)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFFF4F7FC)),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "最近转换预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                GlyphIcon(
                    key = recentIconKey(entry),
                    color = AppleBlue,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 1.6f,
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                color = Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${entry.count} 个文件 · ${fileConvertFormatSize(entry.size)} · ${recentTime(entry.timestamp)}",
                color = Ink3,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ImageToolTextButton(text = "打开", onClick = onOpen)
        ImageToolTextButton(text = "分享", onClick = onShare)
    }
}

private fun recentIconKey(entry: RecentConversion): String {
    val mime = entry.files.firstOrNull()?.mime.orEmpty()
    return when {
        mime.startsWith("image/") -> "catImage"
        mime.startsWith("video/") -> "video"
        mime.startsWith("audio/") -> "audio"
        mime == "application/pdf" -> "pdf"
        mime.startsWith("text/") -> "catText"
        mime == "application/zip" || mime == "application/x-7z-compressed" -> "compress"
        else -> "convert"
    }
}

private fun recentTime(timestamp: Long): String =
    if (timestamp <= 0L) {
        "刚刚"
    } else {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

private fun fileConvertHint(source: PickedFile, target: ConvertFormat): String = when {
    target.id == source.format.id ->
        if (target.id == "jpg" || target.id == "webp") {
            "重新编码为 ${target.label}，可调整输出质量"
        } else {
            "重新编码为 ${target.label}，保持原始画质"
        }

    target.id == "pdf" && source.format.kind == ConvertKind.Image ->
        "生成单页 A4 PDF，图片等比居中，最长边 4096px"

    target.id == "pdf" && source.format.kind == ConvertKind.Text ->
        "A4 排版，自动换行并分页导出 PDF"

    target.id == "merge_pdf" ->
        "将选中的多个 PDF 按顺序合并为一个 PDF"

    target.id == "split_pdf" ->
        "每个页面拆分为独立 PDF，多页结果会打包保存"

    target.id == "compress_pdf" ->
        "重新压缩 PDF 内的图片并去掉冗余数据；文字和矢量内容保持不变"

    target.id == "rotate_pdf" ->
        "把全部页面顺时针旋转指定角度；扫描件方向不对时使用"

    target.id == "reorder_pdf" ->
        "用箭头调整页面顺序，位置 1 将成为新 PDF 的第一页"

    target.id == "insert_pdf" ->
        "把另一个 PDF 的选中页面插入到当前文件的开头或结尾"

    target.id == "extract_pdf" ->
        "在缩略图中点选要保留的页面，按页码升序生成新 PDF"

    target.id == "delete_pdf" ->
        "在缩略图中点选要删除的页面，其余页面保持不变"

    source.format.id == "pdf" && target.id == "txt" ->
        "提取 PDF 文本层并保留段落；扫描版 PDF 没有文本层时会提示"

    source.format.id == "pdf" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
        "PdfRenderer 逐页导出，最多 24 页；多页图片可直接保存到相册"

    source.format.id == "pdf" ->
        "PdfRenderer 逐页导出，最多 24 页；多页结果会打包为 ZIP"

    target.id == "extract" ->
        "选择目标文件夹后解压全部条目，保留目录结构"

    target.id == "extract_selected" ->
        "勾选要解压的文件，目录结构会自动保留"

    target.id == "zip_enc" ->
        "使用 AES-256 重新打包为加密 ZIP；密码丢失后无法恢复"

    target.id == "zip_split" ->
        "按指定大小生成 .z01、.z02… 和 .zip，保存时请保持文件名不变"

    source.format.kind == ConvertKind.Archive ->
        "重新打包为 ${target.label}，条目内容保持不变"

    target.id == "jxl" ->
        "JPEG XL 有损编码，兼顾画质与体积；大图编码需要等待"

    source.format.kind == ConvertKind.Audio ->
        if (target.id == "wav" || target.id == "flac") {
            "FFmpeg 本地转码为 ${target.label}，无损输出"
        } else {
            "FFmpeg 本地转码为 ${target.label}，可调整输出质量"
        }

    source.format.kind == ConvertKind.Video && target.kind == ConvertKind.Audio ->
        "从视频中提取音轨并转码为 ${target.label}"

    source.format.kind == ConvertKind.Video && target.id == "gif" ->
        "生成 12fps、宽 480px 的 GIF，长视频会生成较大的文件"

    source.format.kind == ConvertKind.Video ->
        "FFmpeg 本地转码为 ${target.label}，可调整质量；长视频需要等待"

    target.id == "tiff" ->
        "导出未压缩 RGB TIFF，兼容常见图片软件"

    target.id == "ico" ->
        "导出 PNG 压缩 ICO，最大 256 × 256"

    target.id == "jpg" ->
        "JPEG 有损压缩，透明区域会填充白色"

    target.id == "webp" ->
        "WebP 有损压缩，保留透明通道"

    target.id == "png" ->
        "PNG 无损导出，保留透明通道"

    target.id == "bmp" ->
        "24 位 BMP，透明区域会填充白色"

    source.format.id in setOf("srt", "vtt", "ass", "lrc") ->
        "转换字幕时间轴与文本，保留每条字幕的起止时间"

    source.format.id in setOf("docx", "odt", "rtf", "hwpx", "pptx", "odp", "epub") && target.id == "pdf" ->
        "提取文档文字并排成 A4 PDF，不保留原始排版、图片和表格样式"

    source.format.id in setOf("docx", "odt", "rtf", "hwpx", "pptx", "odp", "epub") && target.id == "html" ->
        "提取文档文字并生成带基础排版的 UTF-8 HTML"

    source.format.id in setOf("docx", "odt", "rtf", "hwpx", "pptx", "odp", "epub") ->
        "提取文档文字，输出 UTF-8 纯文本"

    source.format.id in setOf("xlsx", "ods") && target.id == "json" ->
        "首行作为表头；多个工作表会输出为按工作表名分组的 JSON"

    source.format.id in setOf("xlsx", "ods") ->
        "每个工作表导出为独立文件，多表时打包保存"

    source.format.id == "xml" && target.id == "json" ->
        "XML 元素和属性转换为 JSON 对象，属性名以 @ 开头"

    source.format.id in setOf("yaml", "toml") && target.id == "json" ->
        "解析 YAML / TOML 并输出 JSON，保留嵌套结构"

    target.id == "jsonl" ->
        "每个数组元素输出为一行 JSON，适合日志和流式数据"

    target.id == "xml" ->
        "生成带 root 根节点的 UTF-8 XML"

    target.id == "html" ->
        "生成 UTF-8 HTML，包含基础排版样式"

    target.id == "txt" ->
        "去除 Markdown 标记或 HTML 标签，输出纯文本"

    target.id == "yaml" ->
        "转换为 YAML，保留嵌套对象、数组和数据类型"

    target.id == "toml" ->
        "转换为 TOML，嵌套对象输出为表，数组对象输出为表数组"

    target.id == "xlsx" ->
        "生成单工作表 XLSX，首行作为表头"

    target.id == "csv" ->
        "JSON 数组 / 对象或 TSV 转换为带表头的 CSV"

    target.id == "tsv" ->
        "转换为制表符分隔的 TSV"

    target.id == "json" ->
        "首行作为表头，转换为 JSON 对象数组"

    else -> target.note
}

private fun fileConvertIconKey(format: ConvertFormat): String = when (format.kind) {
    ConvertKind.Image -> "catImage"
    ConvertKind.Pdf -> "pdf"
    ConvertKind.Text -> "catText"
    ConvertKind.Data -> "json"
    ConvertKind.Office -> "letter"
    ConvertKind.Archive -> "compress"
    ConvertKind.Audio -> "audio"
    ConvertKind.Video -> "video"
    ConvertKind.Ebook -> "ocr"
    ConvertKind.Vector -> "palette"
    ConvertKind.Font -> "letter"
    ConvertKind.Subtitle -> "catText"
    ConvertKind.Other -> "convert"
}

private data class ArchivePreview(
    val name: String,
    val size: Long,
    val bitmap: Bitmap? = null,
    val text: String? = null,
    val message: String? = null,
)

@Composable
private fun ArchivePreviewCard(
    preview: ArchivePreview,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.70f))
            .border(1.dp, Color.White.copy(alpha = 0.94f), shape)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = preview.name,
                    color = Ink,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = fileConvertFormatSize(preview.size),
                    color = Ink3,
                    fontSize = 9.5.sp,
                )
            }
            Text(
                text = "关闭",
                color = AppleBlue,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(7.dp))

        val bitmap = preview.bitmap
        val text = preview.text
        val message = preview.message
        when {
            bitmap != null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFF4F7FC)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "压缩包预览",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            text != null -> Text(
                text = text,
                color = Ink,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )

            else -> Text(
                text = message ?: "该类型暂不支持预览",
                color = Ink3,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
        }
    }
}
