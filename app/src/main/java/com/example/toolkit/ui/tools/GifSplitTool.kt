@file:Suppress("DEPRECATION")

package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Movie
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas as ComposeCanvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private val SplitPreviewShape = RoundedCornerShape(20.dp)
private val SplitPanelShape = RoundedCornerShape(20.dp)
private const val SplitMaxGifBytes = 30 * 1024 * 1024

private data class SplitFrame(
    val previewTimeMs: Int,
    val delayMs: Int,
    val thumbnail: ImageBitmap?,
)

@Composable
fun GifSplitTool(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBytes by remember { mutableStateOf<ByteArray?>(null) }
    var document by remember { mutableStateOf<GifDocument?>(null) }
    var frames by remember { mutableStateOf<List<SplitFrame>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf(0) }
    var gifWidth by remember { mutableStateOf(0) }
    var gifHeight by remember { mutableStateOf(0) }
    var sourceMime by remember { mutableStateOf("image/gif") }

    var loading by remember { mutableStateOf(false) }
    var previewLoading by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var savingCurrent by remember { mutableStateOf(false) }
    var savingAll by remember { mutableStateOf(false) }
    var progressCurrent by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSaveBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            sourceUri = uri
            sourceBytes = null
            document = null
            frames = emptyList()
            preview = null
            error = null
        }
    }

    val createPng = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val bitmap = pendingSaveBitmap
        pendingSaveBitmap = null
        if (uri != null && bitmap != null) {
            scope.launch {
                val ok = savePngToUri(context, bitmap, uri)
                bitmap.recycle()
                savingCurrent = false
                Toast.makeText(
                    context,
                    if (ok) "当前帧已保存" else "保存失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            bitmap?.recycle()
            savingCurrent = false
        }
    }

    val createZip = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val bytes = sourceBytes
        val currentFrames = frames
        if (uri != null && bytes != null && currentFrames.isNotEmpty()) {
            savingAll = true
            progressCurrent = 0
            progressTotal = currentFrames.size
            scope.launch {
                val saved = saveAllFramesToZip(
                    context = context,
                    uri = uri,
                    gifBytes = bytes,
                    frames = currentFrames,
                    onProgress = { current, total ->
                        progressCurrent = current
                        progressTotal = total
                    },
                )
                savingAll = false
                Toast.makeText(
                    context,
                    if (saved > 0) "已导出 $saved 帧 ZIP" else "导出失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            savingAll = false
        }
    }

    LaunchedEffect(sourceUri) {
        val uri = sourceUri ?: return@LaunchedEffect
        loading = true
        error = null
        preview = null

        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        }
        if (bytes == null) {
            error = "无法读取 GIF 文件"
            loading = false
            return@LaunchedEffect
        }
        if (bytes.size > SplitMaxGifBytes) {
            error = "GIF 文件过大，请选择 30MB 以内的文件"
            loading = false
            return@LaunchedEffect
        }
        if (
            bytes.size < 6 ||
            String(bytes, 0, 6, Charsets.US_ASCII) !in setOf("GIF87a", "GIF89a")
        ) {
            error = "请选择 GIF 动图"
            loading = false
            return@LaunchedEffect
        }

        val doc = withContext(Dispatchers.Default) { runCatching { parseGif(bytes) }.getOrNull() }
        if (doc == null) {
            error = "GIF 解析失败，请换一个文件"
            loading = false
            return@LaunchedEffect
        }

        val thumbs = withContext(Dispatchers.Default) {
            runCatching { renderSplitThumbnails(bytes, doc.frames, 150) }
                .getOrElse { List(doc.frames.size) { null } }
        }
        val splitFrames = doc.frames.mapIndexed { index, frame ->
            SplitFrame(
                previewTimeMs = frame.originalTimeMs + (frame.delayMs / 2).coerceAtLeast(1),
                delayMs = frame.delayMs,
                thumbnail = thumbs.getOrNull(index),
            )
        }
        val width = (doc.header[6].toInt() and 0xFF) or
            ((doc.header[7].toInt() and 0xFF) shl 8)
        val height = (doc.header[8].toInt() and 0xFF) or
            ((doc.header[9].toInt() and 0xFF) shl 8)

        sourceBytes = bytes
        document = doc
        frames = splitFrames
        selectedIndex = 0
        gifWidth = width
        gifHeight = height
        sourceMime = context.contentResolver.getType(uri) ?: "image/gif"
        loading = false
    }

    LaunchedEffect(selectedIndex, sourceBytes) {
        val bytes = sourceBytes
        val frame = frames.getOrNull(selectedIndex)
        if (bytes == null || frame == null) {
            preview = null
            previewLoading = false
            return@LaunchedEffect
        }
        previewLoading = true
        preview = withContext(Dispatchers.Default) {
            runCatching { renderSplitPreview(bytes, frame.previewTimeMs, 640) }.getOrNull()
        }
        previewLoading = false
    }

    fun openPicker() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun saveCurrentFrame() {
        if (savingCurrent || savingAll) return
        val bytes = sourceBytes ?: return
        val frame = frames.getOrNull(selectedIndex) ?: return
        savingCurrent = true
        scope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                runCatching { renderSplitFrameFull(bytes, frame.previewTimeMs) }.getOrNull()
            }
            if (bitmap == null) {
                savingCurrent = false
                Toast.makeText(context, "当前帧生成失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val name = "${frameBaseName()}_frame_${(selectedIndex + 1).toString().padStart(3, '0')}.png"
                val ok = savePngToGallery(context, bitmap, name)
                bitmap.recycle()
                savingCurrent = false
                Toast.makeText(
                    context,
                    if (ok) "当前帧已保存到相册" else "保存失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                pendingSaveBitmap = bitmap
                createPng.launch(
                    "${frameBaseName()}_frame_${(selectedIndex + 1).toString().padStart(3, '0')}.png",
                )
            }
        }
    }

    fun saveAllFrames() {
        if (savingCurrent || savingAll) return
        val bytes = sourceBytes ?: return
        val currentFrames = frames
        if (currentFrames.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            savingAll = true
            progressCurrent = 0
            progressTotal = currentFrames.size
            scope.launch {
                val saved = saveAllFramesToGallery(
                    context = context,
                    gifBytes = bytes,
                    frames = currentFrames,
                    baseName = frameBaseName(),
                    onProgress = { current, total ->
                        progressCurrent = current
                        progressTotal = total
                    },
                )
                savingAll = false
                Toast.makeText(
                    context,
                    if (saved > 0) "已保存 $saved 帧到相册" else "保存失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            createZip.launch("${frameBaseName()}.zip")
        }
    }

    val totalMs = frames.sumOf { it.delayMs }
    val loopText = when (val loop = document?.loopCount) {
        null -> "不循环"
        0 -> "无限循环"
        else -> "循环 $loop 次"
    }
    val selectedFrame = frames.getOrNull(selectedIndex)
    val busy = savingCurrent || savingAll

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = 26.dp)
            .padding(16.dp),
    ) {
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
                Text(
                    text = "GIF帧拆分",
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    text = "提取 GIF 中的每一帧为独立 PNG",
                    color = Ink3,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (frames.isEmpty()) {
            EmptySplitState(
                color = tool.color,
                loading = loading,
                onPick = { openPicker() },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(SplitPreviewShape)
                    .background(Color(0xFFF4F7FC))
                    .border(1.dp, Color.White.copy(alpha = 0.86f), SplitPreviewShape),
                contentAlignment = Alignment.Center,
            ) {
                SplitCheckerboard(Modifier.fillMaxSize())
                when {
                    preview != null -> Image(
                        bitmap = preview!!,
                        contentDescription = "当前帧预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )

                    previewLoading -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AppleBlue,
                        strokeWidth = 2.dp,
                    )

                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlyphIcon(
                            key = "video",
                            color = Ink3.copy(alpha = 0.55f),
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 1.4f,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "当前帧预览",
                            color = Ink3,
                            fontSize = 11.sp,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.48f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "第 ${selectedIndex + 1} 帧 / 共 ${frames.size} 帧",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$gifWidth × $gifHeight · $sourceMime",
                    color = Ink3,
                    fontSize = 11.5.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "共 ${frames.size} 帧 · ${formatDuration(totalMs)}",
                    color = Ink3,
                    fontSize = 11.5.sp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = loopText,
                    color = Ink3,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "点击缩略图查看单帧",
                    color = Ink3,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            frames.chunked(3).forEachIndexed { rowIndex, rowFrames ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    rowFrames.forEachIndexed { columnIndex, frame ->
                        val index = rowIndex * 3 + columnIndex
                        SplitFrameTile(
                            index = index,
                            frame = frame,
                            selected = index == selectedIndex,
                            onClick = { selectedIndex = index },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - rowFrames.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(7.dp))
            }

            Spacer(Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImageToolSecondaryButton(
                    text = "重新选择",
                    onClick = { openPicker() },
                )
                if (!savingCurrent) {
                    ImageToolTextButton(
                        text = "保存当前帧",
                        onClick = { saveCurrentFrame() },
                    )
                } else {
                    Text(
                        text = "保存中…",
                        color = Ink3,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                ImageToolPrimaryButton(
                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        "保存全部帧"
                    } else {
                        "导出 ZIP"
                    },
                    enabled = !savingCurrent,
                    loading = savingAll,
                    onClick = { saveAllFrames() },
                )
            }

            if (savingAll && progressTotal > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "正在保存 $progressCurrent / $progressTotal …",
                    modifier = Modifier.fillMaxWidth(),
                    color = AppleBlue,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "逐帧导出为 PNG，透明区域会保留；文件保存在 Pictures/河马工具箱"
                } else {
                    "Android 9 及以下将导出包含全部 PNG 帧的 ZIP"
                },
                modifier = Modifier.fillMaxWidth(),
                color = Ink3,
                fontSize = 11.sp,
                lineHeight = 15.sp,
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
    }
}

@Composable
private fun EmptySplitState(
    color: Color,
    loading: Boolean,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SplitPanelShape)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFF8FBFF),
                    1f to Color(0xFFFDF5FB),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.90f), SplitPanelShape)
            .padding(horizontal = 18.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            key = "video",
            color = color,
            modifier = Modifier.size(38.dp),
            strokeWidth = 1.4f,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "选择一个 GIF 动画开始拆分",
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "支持 GIF 动图，逐帧导出独立 PNG",
            color = Ink3,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(18.dp))
        ImageToolPrimaryButton(
            text = "选择 GIF",
            enabled = !loading,
            loading = loading,
            onClick = onPick,
        )
    }
}

@Composable
private fun SplitFrameTile(
    index: Int,
    frame: SplitFrame,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(Color(0xFFF4F7FC))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) AppleBlue else Color.White.copy(alpha = 0.94f),
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (frame.thumbnail != null) {
                Image(
                    bitmap = frame.thumbnail,
                    contentDescription = "第 ${index + 1} 帧",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                GlyphIcon(
                    key = "video",
                    color = Ink3.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 1.4f,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = "${frame.delayMs}ms",
            color = if (selected) AppleBlue else Ink3,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun SplitCheckerboard(modifier: Modifier = Modifier) {
    ComposeCanvas(modifier) {
        val cell = 10.dp.toPx()
        val light = Color(0xFFF7F8FB)
        val dark = Color(0xFFE4E8F0)
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = 0f
            var col = 0
            while (x < size.width) {
                drawRect(
                    color = if ((row + col) % 2 == 0) light else dark,
                    topLeft = Offset(x, y),
                    size = Size(cell, cell),
                )
                x += cell
                col += 1
            }
            y += cell
            row += 1
        }
    }
}

private suspend fun saveAllFramesToGallery(
    context: android.content.Context,
    gifBytes: ByteArray,
    frames: List<SplitFrame>,
    baseName: String,
    onProgress: (Int, Int) -> Unit,
): Int = withContext(Dispatchers.IO) {
    val movie = runCatching { Movie.decodeByteArray(gifBytes, 0, gifBytes.size) }.getOrNull()
        ?: return@withContext -1
    var saved = 0
    frames.forEachIndexed { index, frame ->
        val bitmap = runCatching {
            renderMovieAtTime(movie, frame.previewTimeMs, null, null)
        }.getOrNull()
        if (bitmap != null) {
            val name = "${baseName}_frame_${(index + 1).toString().padStart(3, '0')}.png"
            if (savePngToGallery(context, bitmap, name)) saved += 1
            bitmap.recycle()
        }
        withContext(Dispatchers.Main) { onProgress(index + 1, frames.size) }
    }
    saved
}

private suspend fun saveAllFramesToZip(
    context: android.content.Context,
    uri: Uri,
    gifBytes: ByteArray,
    frames: List<SplitFrame>,
    onProgress: (Int, Int) -> Unit,
): Int = withContext(Dispatchers.IO) {
    val movie = runCatching { Movie.decodeByteArray(gifBytes, 0, gifBytes.size) }.getOrNull()
        ?: return@withContext 0
    var saved = 0
    try {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                frames.forEachIndexed { index, frame ->
                    val bitmap = runCatching {
                        renderMovieAtTime(movie, frame.previewTimeMs, null, null)
                    }.getOrNull()
                    if (bitmap != null) {
                        zip.putNextEntry(
                            ZipEntry("frame_${(index + 1).toString().padStart(3, '0')}.png"),
                        )
                        if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)) saved += 1
                        bitmap.recycle()
                        zip.closeEntry()
                    }
                    withContext(Dispatchers.Main) { onProgress(index + 1, frames.size) }
                }
            }
        }
    } catch (_: Throwable) {
        return@withContext saved
    }
    saved
}

private fun renderSplitThumbnails(
    bytes: ByteArray,
    frames: List<GifFrameData>,
    maxDimension: Int,
): List<ImageBitmap?> {
    val movie = runCatching { Movie.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        ?: return List(frames.size) { null }
    return frames.map { frame ->
        val previewTime = frame.originalTimeMs + (frame.delayMs / 2).coerceAtLeast(1)
        runCatching {
            renderMovieAtTime(
                movie = movie,
                timeMs = previewTime,
                maxDimension = maxDimension,
                backgroundColor = 0xFFF4F7FC.toInt(),
            )?.asImageBitmap()
        }.getOrNull()
    }
}

private fun renderSplitPreview(
    bytes: ByteArray,
    timeMs: Int,
    maxDimension: Int,
): ImageBitmap? {
    val movie = runCatching { Movie.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        ?: return null
    return runCatching {
        renderMovieAtTime(movie, timeMs, maxDimension, null)?.asImageBitmap()
    }.getOrNull()
}

private fun renderSplitFrameFull(
    bytes: ByteArray,
    timeMs: Int,
): Bitmap? {
    val movie = runCatching { Movie.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        ?: return null
    return runCatching { renderMovieAtTime(movie, timeMs, null, null) }.getOrNull()
}

private fun renderMovieAtTime(
    movie: Movie,
    timeMs: Int,
    maxDimension: Int?,
    backgroundColor: Int?,
): Bitmap? {
    val width = movie.width()
    val height = movie.height()
    if (width <= 0 || height <= 0) return null

    val scale = if (maxDimension != null) {
        maxDimension.toFloat() / max(width, height)
    } else {
        1f
    }
    val outWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val outHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    if (backgroundColor != null) canvas.drawColor(backgroundColor)

    val duration = movie.duration()
    movie.setTime(if (duration > 0) timeMs % duration else 0)
    canvas.scale(outWidth / width.toFloat(), outHeight / height.toFloat())
    movie.draw(canvas, 0f, 0f)
    return bitmap
}

private fun frameBaseName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "gif_frames_$stamp"
}

private fun formatDuration(ms: Int): String {
    return if (ms >= 1000) {
        String.format(Locale.US, "%.1f s", ms / 1000.0)
    } else {
        "$ms ms"
    }
}
