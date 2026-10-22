@file:Suppress("DEPRECATION")

package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.example.toolkit.ui.theme.Ink2
import com.example.toolkit.ui.theme.Ink3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val GifPreviewShape = RoundedCornerShape(20.dp)
private val GifPanelShape = RoundedCornerShape(20.dp)
private const val MaxGifBytes = 30 * 1024 * 1024

private data class EditableGifFrame(
    val id: Long,
    val gce: ByteArray?,
    val image: ByteArray,
    val delayMs: Int,
    val originalTimeMs: Int,
    val previewTimeMs: Int,
    val thumbnail: ImageBitmap? = null,
)

@Composable
fun GifFrameTool(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var document by remember { mutableStateOf<GifDocument?>(null) }
    var sourceBytes by remember { mutableStateOf<ByteArray?>(null) }
    var frames by remember { mutableStateOf<List<EditableGifFrame>>(emptyList()) }
    var selectedFrameId by remember { mutableStateOf<Long?>(null) }
    var nextFrameId by remember { mutableStateOf(0L) }

    var loading by remember { mutableStateOf(false) }
    var previewLoading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            sourceUri = uri
            document = null
            sourceBytes = null
            frames = emptyList()
            selectedFrameId = null
            preview = null
            error = null
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/gif"),
    ) { uri ->
        val bytes = pendingSaveBytes
        pendingSaveBytes = null
        if (uri != null && bytes != null) {
            scope.launch {
                val ok = saveBytesToUri(context, bytes, uri)
                saving = false
                Toast.makeText(
                    context,
                    if (ok) "GIF 已保存" else "保存失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            saving = false
        }
    }

    LaunchedEffect(sourceUri) {
        val uri = sourceUri ?: return@LaunchedEffect
        loading = true
        error = null
        preview = null
        previewLoading = false

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
        if (bytes.size > MaxGifBytes) {
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
            runCatching { renderGifThumbnails(bytes, doc.frames, 150) }
                .getOrElse { List(doc.frames.size) { null } }
        }
        val editable = doc.frames.mapIndexed { index, frame ->
            EditableGifFrame(
                id = index.toLong(),
                gce = frame.gce,
                image = frame.image,
                delayMs = frame.delayMs,
                originalTimeMs = frame.originalTimeMs,
                previewTimeMs = frame.originalTimeMs + (frame.delayMs / 2).coerceAtLeast(1),
                thumbnail = thumbs.getOrNull(index),
            )
        }

        document = doc
        sourceBytes = bytes
        frames = editable
        nextFrameId = editable.size.toLong()
        selectedFrameId = editable.firstOrNull()?.id
        loading = false
    }

    val selectedFrame = frames.firstOrNull { it.id == selectedFrameId }
    LaunchedEffect(selectedFrame?.id, selectedFrame?.previewTimeMs, sourceBytes) {
        val frame = selectedFrame
        val bytes = sourceBytes
        if (frame == null || bytes == null) {
            preview = null
            previewLoading = false
            return@LaunchedEffect
        }
        previewLoading = true
        preview = withContext(Dispatchers.Default) {
            runCatching { renderGifFrame(bytes, frame.previewTimeMs, 640) }.getOrNull()
        }
        previewLoading = false
    }

    fun openPicker() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun updateSelectedDelay(deltaMs: Int) {
        val id = selectedFrameId ?: return
        frames = frames.map { frame ->
            if (frame.id == id) {
                frame.copy(delayMs = (frame.delayMs + deltaMs).coerceIn(10, 2000))
            } else {
                frame
            }
        }
    }

    fun applyDelayToAll() {
        val delay = selectedFrame?.delayMs ?: return
        frames = frames.map { it.copy(delayMs = delay) }
    }

    fun moveSelected(direction: Int) {
        val index = frames.indexOfFirst { it.id == selectedFrameId }
        if (index < 0) return
        val target = index + direction
        if (target !in frames.indices) return
        val list = frames.toMutableList()
        val frame = list.removeAt(index)
        list.add(target, frame)
        frames = list
    }

    fun duplicateSelected() {
        val index = frames.indexOfFirst { it.id == selectedFrameId }
        if (index < 0) return
        val frame = frames[index]
        val copy = frame.copy(id = nextFrameId)
        nextFrameId += 1
        val list = frames.toMutableList()
        list.add(index + 1, copy)
        frames = list
        selectedFrameId = copy.id
    }

    fun deleteSelected() {
        if (frames.size <= 1) return
        val index = frames.indexOfFirst { it.id == selectedFrameId }
        if (index < 0) return
        val list = frames.toMutableList()
        list.removeAt(index)
        frames = list
        selectedFrameId = list[index.coerceAtMost(list.lastIndex)].id
    }

    fun saveGif() {
        val doc = document ?: return
        if (saving || frames.isEmpty()) return
        saving = true
        val dataFrames = frames.map {
            GifFrameData(
                gce = it.gce,
                image = it.image,
                delayMs = it.delayMs,
                originalTimeMs = it.originalTimeMs,
            )
        }
        scope.launch {
            val bytes = withContext(Dispatchers.Default) {
                runCatching { buildGif(doc, dataFrames) }.getOrNull()
            }
            if (bytes == null) {
                saving = false
                Toast.makeText(context, "GIF 生成失败，请重试", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ok = saveBytesToGallery(
                    context = context,
                    bytes = bytes,
                    fileName = gifFileName(),
                    mimeType = "image/gif",
                )
                saving = false
                Toast.makeText(
                    context,
                    if (ok) "GIF 已保存到相册" else "保存失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                pendingSaveBytes = bytes
                createDocument.launch(gifFileName())
            }
        }
    }

    val selectedIndex = frames.indexOfFirst { it.id == selectedFrameId }.coerceAtLeast(0)
    val totalMs = frames.sumOf { it.delayMs }
    val loopText = when (val loop = document?.loopCount) {
        null -> "不循环"
        0 -> "无限循环"
        else -> "循环 $loop 次"
    }

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
                    text = "GIF帧修改",
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    text = "调整帧顺序、时长与数量",
                    color = Ink3,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (frames.isEmpty()) {
            EmptyGifState(
                color = tool.color,
                loading = loading,
                onPick = { openPicker() },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(GifPreviewShape)
                    .background(Color(0xFFF0F4FB))
                    .border(1.dp, Color.White.copy(alpha = 0.86f), GifPreviewShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    preview != null -> Image(
                        bitmap = preview!!,
                        contentDescription = "GIF 当前帧预览",
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
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 1.4f,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "当前帧预览",
                            color = Ink3,
                            fontSize = 11.5.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "共 ${frames.size} 帧 · ${formatDuration(totalMs)}",
                    color = Ink3,
                    fontSize = 11.5.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = loopText,
                    color = Ink3,
                    fontSize = 11.5.sp,
                )
            }

            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(frames, key = { _, frame -> frame.id }) { index, frame ->
                    GifFrameThumbnail(
                        index = index,
                        frame = frame,
                        selected = frame.id == selectedFrameId,
                        onClick = { selectedFrameId = frame.id },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "第 ${selectedIndex + 1} 帧",
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${selectedFrame?.delayMs ?: 0} ms",
                    color = AppleBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GifStepButton(
                    text = "-10 ms",
                    onClick = { updateSelectedDelay(-10) },
                )
                GifStepButton(
                    text = "+10 ms",
                    onClick = { updateSelectedDelay(10) },
                )
                Spacer(Modifier.weight(1f))
                if (frames.size > 1) {
                    ImageToolTextButton(
                        text = "应用到全部",
                        onClick = { applyDelayToAll() },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GifActionButton(
                    text = "左移",
                    enabled = selectedIndex > 0,
                    modifier = Modifier.weight(1f),
                    onClick = { moveSelected(-1) },
                )
                GifActionButton(
                    text = "右移",
                    enabled = selectedIndex < frames.lastIndex,
                    modifier = Modifier.weight(1f),
                    onClick = { moveSelected(1) },
                )
                GifActionButton(
                    text = "复制",
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = { duplicateSelected() },
                )
                GifActionButton(
                    text = "删除",
                    enabled = frames.size > 1,
                    danger = true,
                    modifier = Modifier.weight(1f),
                    onClick = { deleteSelected() },
                )
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
                    text = "保存 GIF",
                    loading = saving,
                    onClick = { saveGif() },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "仅调整帧顺序、时长与数量，原始帧画质保持不变",
                modifier = Modifier.fillMaxWidth(),
                color = Ink3,
                fontSize = 11.sp,
                letterSpacing = (-0.1).sp,
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
private fun EmptyGifState(
    color: Color,
    loading: Boolean,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GifPanelShape)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFF8FBFF),
                    1f to Color(0xFFFDF5FB),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.90f), GifPanelShape)
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
            text = "选择一个 GIF 动画开始编辑",
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "支持 GIF 动图，可修改顺序、时长和数量",
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
private fun GifFrameThumbnail(
    index: Int,
    frame: EditableGifFrame,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(shape)
                .background(Color.White)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) AppleBlue else Color.White.copy(alpha = 0.95f),
                    shape = shape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
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
                    modifier = Modifier.size(22.dp),
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
private fun GifStepButton(
    text: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .width(78.dp)
            .height(34.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.78f))
            .border(1.dp, Color.White.copy(alpha = 0.92f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AppleBlue,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun GifActionButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = if (enabled) 0.78f else 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.92f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> Ink3.copy(alpha = 0.40f)
                danger -> DangerRed
                else -> AppleBlue
            },
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Suppress("DEPRECATION")
private fun renderGifThumbnails(
    bytes: ByteArray,
    frames: List<GifFrameData>,
    maxDimension: Int,
): List<ImageBitmap?> {
    val movie = runCatching { Movie.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        ?: return List(frames.size) { null }
    return frames.map { frame ->
        val previewTime = frame.originalTimeMs + (frame.delayMs / 2).coerceAtLeast(1)
        runCatching { renderMovieFrame(movie, previewTime, maxDimension) }.getOrNull()
    }
}

@Suppress("DEPRECATION")
private fun renderGifFrame(
    bytes: ByteArray,
    timeMs: Int,
    maxDimension: Int,
): ImageBitmap? {
    val movie = runCatching { Movie.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        ?: return null
    return runCatching { renderMovieFrame(movie, timeMs, maxDimension) }.getOrNull()
}

@Suppress("DEPRECATION")
private fun renderMovieFrame(
    movie: Movie,
    timeMs: Int,
    maxDimension: Int,
): ImageBitmap? {
    val width = movie.width()
    val height = movie.height()
    if (width <= 0 || height <= 0) return null

    val scale = maxDimension.toFloat() / max(width, height)
    val outWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val outHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val duration = movie.duration()
    movie.setTime(if (duration > 0) timeMs % duration else 0)
    canvas.scale(
        outWidth / width.toFloat(),
        outHeight / height.toFloat(),
    )
    movie.draw(canvas, 0f, 0f)
    return bitmap.asImageBitmap()
}

private fun gifFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "gif_$stamp.gif"
}

private fun formatDuration(ms: Int): String {
    return if (ms >= 1000) {
        String.format(Locale.US, "%.1f s", ms / 1000.0)
    } else {
        "$ms ms"
    }
}
