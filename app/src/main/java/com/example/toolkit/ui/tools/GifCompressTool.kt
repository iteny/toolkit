@file:Suppress("DEPRECATION")

package com.example.toolkit.ui.tools

import android.graphics.Movie
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
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
import com.example.toolkit.ui.theme.Ink2
import com.example.toolkit.ui.theme.Ink3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

private val CompressPreviewShape = RoundedCornerShape(20.dp)
private val CompressPanelShape = RoundedCornerShape(20.dp)
private const val CompressMaxGifBytes = 30 * 1024 * 1024

private data class CompressedGif(
    val frames: List<GifFrameData>,
    val bytes: ByteArray,
)

private data class SamplingOption(val label: String, val step: Int)

private val samplingOptions = listOf(
    SamplingOption("不抽帧", 1),
    SamplingOption("1/2", 2),
    SamplingOption("1/3", 3),
    SamplingOption("1/4", 4),
)

@Composable
fun GifCompressTool(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBytes by remember { mutableStateOf<ByteArray?>(null) }
    var document by remember { mutableStateOf<GifDocument?>(null) }
    var sourceMime by remember { mutableStateOf("image/gif") }
    var gifWidth by remember { mutableStateOf(0) }
    var gifHeight by remember { mutableStateOf(0) }
    var originalFrameCount by remember { mutableStateOf(0) }
    var keepEvery by rememberSaveable { mutableIntStateOf(1) }
    var dedupe by rememberSaveable { mutableStateOf(true) }
    var compressed by remember { mutableStateOf<CompressedGif?>(null) }
    var compressedDurationMs by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            sourceUri = uri
            sourceBytes = null
            document = null
            compressed = null
            error = null
        }
    }

    val createGif = rememberLauncherForActivityResult(
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
                    if (ok) "压缩 GIF 已保存" else "保存失败，请重试",
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
        compressed = null
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
        if (bytes.size > CompressMaxGifBytes) {
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
        sourceBytes = bytes
        document = doc
        sourceMime = context.contentResolver.getType(uri) ?: "image/gif"
        gifWidth = (doc.header[6].toInt() and 0xFF) or ((doc.header[7].toInt() and 0xFF) shl 8)
        gifHeight = (doc.header[8].toInt() and 0xFF) or ((doc.header[9].toInt() and 0xFF) shl 8)
        originalFrameCount = doc.frames.size
        loading = false
    }

    LaunchedEffect(document, sourceBytes, keepEvery, dedupe) {
        val doc = document ?: return@LaunchedEffect
        if (sourceBytes == null) return@LaunchedEffect
        processing = true
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val frames = compressGifFrames(doc.frames, keepEvery, dedupe)
                CompressedGif(frames = frames, bytes = buildGif(doc, frames))
            }.getOrNull()
        }
        compressed = result
        compressedDurationMs = result?.frames?.sumOf { it.delayMs } ?: 0
        processing = false
        if (result == null) {
            error = "压缩计算失败，请重试"
        } else {
            error = null
        }
    }

    fun openPicker() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun saveCompressed() {
        val bytes = compressed?.bytes ?: return
        if (saving || processing) return
        saving = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                val ok = saveBytesToGallery(
                    context = context,
                    bytes = bytes,
                    fileName = compressedGifFileName(),
                    mimeType = "image/gif",
                )
                saving = false
                Toast.makeText(
                    context,
                    if (ok) "压缩 GIF 已保存到相册" else "保存失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            pendingSaveBytes = bytes
            createGif.launch(compressedGifFileName())
        }
    }

    val originalSize = sourceBytes?.size ?: 0
    val compressedSize = compressed?.bytes?.size ?: 0
    val reduction = if (originalSize > 0) {
        (originalSize - compressedSize).toFloat() / originalSize * 100f
    } else {
        0f
    }
    val barFraction = if (originalSize > 0) {
        (compressedSize.toFloat() / originalSize).coerceIn(0f, 1f)
    } else {
        0f
    }
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
                    text = "GIF压缩",
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    text = "抽帧与去重，减小 GIF 体积",
                    color = Ink3,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (document == null) {
            EmptyCompressState(
                color = tool.color,
                loading = loading,
                onPick = { openPicker() },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(CompressPreviewShape)
                    .background(Color(0xFFF4F7FC))
                    .border(1.dp, Color.White.copy(alpha = 0.86f), CompressPreviewShape),
                contentAlignment = Alignment.Center,
            ) {
                CompressCheckerboard(Modifier.fillMaxSize())
                val bytes = compressed?.bytes
                if (bytes != null) {
                    CompressedGifPreview(
                        bytes = bytes,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (processing || loading) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.80f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = AppleBlue,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.48f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "压缩后预览",
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
                    text = "$originalFrameCount 帧 → ${compressed?.frames?.size ?: 0} 帧",
                    color = Ink3,
                    fontSize = 11.5.sp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "总时长 ${formatDuration(compressedDurationMs)} · $loopText",
                    color = Ink3,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "原始 ${formatBytes(originalSize)}",
                    color = Ink3,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "压缩后预计 ${formatBytes(compressedSize)}",
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        processing -> "计算中…"
                        abs(reduction) < 0.5f -> "体积基本不变"
                        reduction > 0 -> "-${reduction.toInt()}%"
                        else -> "+${(-reduction).toInt()}%"
                    },
                    color = when {
                        reduction > 0.5f -> AppleBlue
                        reduction < -0.5f -> DangerRed
                        else -> Ink3
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFDDE4F0)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction)
                        .fillMaxHeight()
                        .background(AppleBlue, RoundedCornerShape(4.dp)),
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "抽帧",
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                samplingOptions.forEach { option ->
                    CompressChip(
                        text = option.label,
                        selected = keepEvery == option.step,
                        modifier = Modifier.weight(1f),
                        onClick = { keepEvery = option.step },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    keepEvery == 1 && dedupe -> "保留全部帧，仅合并连续重复帧"
                    keepEvery == 1 -> "保留全部帧，不做压缩"
                    dedupe -> "每 $keepEvery 帧保留 1 帧，并合并连续重复帧，首帧始终保留，总时长保持不变"
                    else -> "每 $keepEvery 帧保留 1 帧，首帧始终保留，总时长保持不变"
                },
                modifier = Modifier.fillMaxWidth(),
                color = Ink3,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )

            Spacer(Modifier.height(10.dp))

            CompressToggle(
                checked = dedupe,
                onCheckedChange = { dedupe = it },
                title = "去除连续重复帧",
                subtitle = "相邻帧画面完全相同时合并停留时间",
            )

            Spacer(Modifier.height(10.dp))

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
                    enabled = compressed != null && !processing,
                    loading = saving,
                    onClick = { saveCompressed() },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "抽帧压缩直接复用原始帧数据，不重新编码画面，因此不会损失画质",
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
private fun EmptyCompressState(
    color: Color,
    loading: Boolean,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CompressPanelShape)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFF8FBFF),
                    1f to Color(0xFFFDF5FB),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.90f), CompressPanelShape)
            .padding(horizontal = 18.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            key = "compress",
            color = color,
            modifier = Modifier.size(38.dp),
            strokeWidth = 1.4f,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "选择一个 GIF 动画开始压缩",
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "通过抽帧和去重减小体积，保留原始画质",
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
private fun CompressChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) AppleBlue else Color.White.copy(alpha = 0.72f))
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.90f),
                shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Ink3,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CompressToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val thumbOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(
            200,
            easing = com.example.toolkit.ui.design.EmphasizedEasing,
        ),
        label = "compressToggleThumb",
    )
    val trackColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) AppleBlue else Color(0xFFD8DFEC),
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "compressToggleTrack",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Ink2,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = Ink3,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(trackColor)
                .padding(3.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .then(
                        Modifier.padding(start = thumbOffset),
                    )
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun CompressCheckerboard(modifier: Modifier = Modifier) {
    Canvas(modifier) {
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

@Composable
private fun CompressedGifPreview(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
) {
    val movie = remember(bytes) {
        runCatching { Movie.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }
    var timeMs by remember { mutableIntStateOf(0) }

    LaunchedEffect(movie) {
        val m = movie ?: return@LaunchedEffect
        val duration = m.duration()
        if (duration <= 0) {
            timeMs = 0
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                timeMs = (((now - start) / 1_000_000L) % duration).toInt()
            }
        }
    }

    Canvas(modifier) {
        val m = movie ?: return@Canvas
        val width = m.width()
        val height = m.height()
        if (width <= 0 || height <= 0) return@Canvas
        val factor = min(size.width / width, size.height / height)
        val dx = (size.width - width * factor) / 2f
        val dy = (size.height - height * factor) / 2f
        m.setTime(timeMs)
        translate(dx, dy) {
            scale(factor, factor, pivot = Offset.Zero) {
                drawIntoCanvas { canvas ->
                    m.draw(canvas.nativeCanvas, 0f, 0f)
                }
            }
        }
    }
}

private fun compressGifFrames(
    frames: List<GifFrameData>,
    keepEvery: Int,
    dedupe: Boolean,
): List<GifFrameData> {
    if (frames.isEmpty()) return frames
    val step = keepEvery.coerceAtLeast(1)
    val result = mutableListOf<GifFrameData>()
    var pendingDelay = 0
    var previousImage: ByteArray? = null

    frames.forEachIndexed { index, frame ->
        val duplicate = dedupe &&
            previousImage != null &&
            frame.image.contentEquals(previousImage)

        when {
            duplicate -> {
                if (result.isNotEmpty()) {
                    val previous = result.removeAt(result.lastIndex)
                    result.add(
                        previous.copy(
                            delayMs = (previous.delayMs + frame.delayMs)
                                .coerceAtMost(655_350),
                        ),
                    )
                } else {
                    result.add(
                        frame.copy(
                            delayMs = (frame.delayMs + pendingDelay).coerceAtMost(655_350),
                        ),
                    )
                    pendingDelay = 0
                }
            }

            index == 0 || index % step == 0 -> {
                result.add(
                    frame.copy(
                        delayMs = (frame.delayMs + pendingDelay).coerceAtMost(655_350),
                    ),
                )
                pendingDelay = 0
            }

            else -> {
                pendingDelay = (pendingDelay + frame.delayMs).coerceAtMost(655_350)
            }
        }
        previousImage = frame.image
    }

    if (pendingDelay > 0) {
        if (result.isNotEmpty()) {
            val previous = result.removeAt(result.lastIndex)
            result.add(
                previous.copy(
                    delayMs = (previous.delayMs + pendingDelay).coerceAtMost(655_350),
                ),
            )
        }
    }

    return result.ifEmpty { listOf(frames.first()) }
}

private fun compressedGifFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "gif_compressed_$stamp.gif"
}

private fun formatDuration(ms: Int): String {
    return if (ms >= 1000) {
        String.format(Locale.US, "%.1f s", ms / 1000.0)
    } else {
        "$ms ms"
    }
}

private fun formatBytes(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
