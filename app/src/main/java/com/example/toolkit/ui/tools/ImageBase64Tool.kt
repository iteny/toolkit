package com.example.toolkit.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.components.ToolIconTile
import com.example.toolkit.ui.design.EmphasizedEasing
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
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Base64PreviewShape = RoundedCornerShape(20.dp)
private val Base64PanelShape = RoundedCornerShape(20.dp)
private const val CopyLimit = 900_000

private data class Base64Payload(
    val data: String,
    val mime: String,
    val byteCount: Int,
)

@Composable
fun ImageBase64Tool(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceMime by remember { mutableStateOf("image/jpeg") }
    var loading by remember { mutableStateOf(false) }
    var encoding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var compressed by remember { mutableStateOf(false) }
    var includePrefix by remember { mutableStateOf(true) }
    var payload by remember { mutableStateOf<String?>(null) }
    var payloadMime by remember { mutableStateOf("image/jpeg") }
    var encodedBytes by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSavePayload by remember { mutableStateOf<String?>(null) }
    var pendingSavePrefix by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            sourceUri = uri
            sourceBitmap = null
            payload = null
            error = null
        }
    }

    val createText = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val text = pendingSavePayload
        val prefix = pendingSavePrefix
        pendingSavePayload = null
        pendingSavePrefix = ""
        if (uri != null && text != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            OutputStreamWriter(output, Charsets.UTF_8).buffered().use { writer ->
                                if (prefix.isNotEmpty()) writer.write(prefix)
                                writer.write(text)
                            }
                        } != null
                    }.getOrDefault(false)
                }
                saving = false
                Toast.makeText(
                    context,
                    if (ok) "TXT 已保存" else "保存失败，请重试",
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
        val decoded = decodeImage(context, uri, maxDimension = 1400)
        if (decoded == null) {
            error = "图片读取失败，请换一张试试"
            loading = false
        } else {
            sourceBitmap = decoded
            sourceMime = context.contentResolver.getType(uri) ?: "image/jpeg"
            loading = false
        }
    }

    LaunchedEffect(sourceUri, sourceBitmap, compressed) {
        val uri = sourceUri ?: return@LaunchedEffect
        val bitmap = sourceBitmap ?: return@LaunchedEffect
        encoding = true
        error = null
        payload = null
        val result = runCatching {
            if (compressed) {
                encodeCompressedBase64(bitmap)
            } else {
                readOriginalBase64(context, uri)
            }
        }.getOrNull()
        if (result == null) {
            encodedBytes = 0
            error = "Base64 生成失败，请重试"
        } else {
            payload = result.data
            payloadMime = result.mime
            encodedBytes = result.byteCount
        }
        encoding = false
    }

    fun openPicker() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    val prefix = if (includePrefix && payload != null) {
        "data:${payloadMime};base64,"
    } else {
        ""
    }
    val totalChars = (payload?.length ?: 0) + prefix.length
    val rawPreview = if (payload != null) {
        prefix + payload!!.take((1400 - prefix.length).coerceAtLeast(0))
    } else {
        null
    }
    val formattedPreview = rawPreview?.let { source ->
        val block = source.chunked(54).joinToString("\n")
        if (totalChars > source.length) "$block\n…" else block
    }

    fun copyBase64() {
        val value = payload ?: return
        if (totalChars > CopyLimit) {
            Toast.makeText(
                context,
                "内容较大，建议使用压缩模式或保存 TXT",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val text = if (includePrefix) {
            "data:${payloadMime};base64,$value"
        } else {
            value
        }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return
            clipboard.setPrimaryClip(ClipData.newPlainText("Base64", text))
            Toast.makeText(context, "Base64 已复制", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(
                context,
                "复制失败，内容可能过大，请保存 TXT",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun saveTxt() {
        val value = payload ?: return
        if (saving) return
        saving = true
        pendingSavePayload = value
        pendingSavePrefix = if (includePrefix) "data:${payloadMime};base64," else ""
        createText.launch(base64TextFileName())
    }

    val bitmap = sourceBitmap

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
                    text = "图片转Base64",
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    text = "选择图片，生成并复制 Base64 编码",
                    color = Ink3,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (bitmap == null) {
            EmptyBase64State(
                color = tool.color,
                loading = loading,
                onPick = { openPicker() },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(Base64PreviewShape)
                    .background(Color(0xFFF0F4FB))
                    .border(1.dp, Color.White.copy(alpha = 0.86f), Base64PreviewShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "图片 Base64 预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${bitmap.width} × ${bitmap.height} · $sourceMime",
                modifier = Modifier.fillMaxWidth(),
                color = Ink3,
                fontSize = 11.5.sp,
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Base64ModeChip(
                    text = "原图",
                    selected = !compressed,
                    modifier = Modifier.weight(1f),
                    onClick = { compressed = false },
                )
                Base64ModeChip(
                    text = "压缩后",
                    selected = compressed,
                    modifier = Modifier.weight(1f),
                    onClick = { compressed = true },
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = if (compressed) {
                    "最长边 1400px · JPEG 85%"
                } else {
                    "保留原始文件编码"
                },
                modifier = Modifier.fillMaxWidth(),
                color = Ink3,
                fontSize = 11.sp,
            )

            Spacer(Modifier.height(12.dp))

            PrefixTool(
                checked = includePrefix,
                onCheckedChange = { includePrefix = it },
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Base64 结果",
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        encoding -> "生成中…"
                        payload != null -> "${formatCount(totalChars)} 字符 · ${formatBytes(encodedBytes)}"
                        else -> ""
                    },
                    color = Ink3,
                    fontSize = 11.5.sp,
                )
            }
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 128.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF4F7FC))
                    .border(1.dp, Color.White.copy(alpha = 0.88f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    encoding -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AppleBlue,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "正在生成 Base64…",
                            color = Ink3,
                            fontSize = 12.sp,
                        )
                    }

                    formattedPreview != null -> Text(
                        text = formattedPreview,
                        modifier = Modifier.fillMaxWidth(),
                        color = Ink2,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis,
                    )

                    else -> Text(
                        text = "选择图片后自动生成",
                        color = Ink3,
                        fontSize = 12.sp,
                    )
                }
            }

            if (!encoding && payload != null && totalChars > CopyLimit) {
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "内容较大，复制到剪贴板可能失败，建议使用压缩模式或保存 TXT。",
                    modifier = Modifier.fillMaxWidth(),
                    color = DangerRed.copy(alpha = 0.80f),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImageToolSecondaryButton(
                    text = "重新选择",
                    onClick = { openPicker() },
                )
                if (payload != null && !encoding) {
                    Spacer(Modifier.width(2.dp))
                    ImageToolTextButton(
                        text = if (saving) "保存中…" else "保存 TXT",
                        onClick = { saveTxt() },
                    )
                }
                Spacer(Modifier.weight(1f))
                ImageToolPrimaryButton(
                    text = "复制 Base64",
                    enabled = payload != null && !encoding,
                    onClick = { copyBase64() },
                )
            }
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
private fun EmptyBase64State(
    color: Color,
    loading: Boolean,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Base64PanelShape)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFF8FBFF),
                    1f to Color(0xFFFDF5FB),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.90f), Base64PanelShape)
            .padding(horizontal = 18.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            key = "hash",
            color = color,
            modifier = Modifier.size(36.dp),
            strokeWidth = 1.4f,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "选择一张图片开始转换",
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "支持 JPG、PNG、WebP 等常见格式",
            color = Ink3,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(18.dp))
        ImageToolPrimaryButton(
            text = "选择图片",
            enabled = !loading,
            loading = loading,
            onClick = onPick,
        )
    }
}

@Composable
private fun Base64ModeChip(
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
            color = if (selected) Color.White else Ink2,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PrefixTool(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        animationSpec = tween(200, easing = EmphasizedEasing),
        label = "prefixThumb",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) AppleBlue else Color(0xFFD8DFEC),
        animationSpec = tween(200),
        label = "prefixTrack",
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
                text = "包含 data URI 前缀",
                color = Ink2,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "data:image/...;base64,",
                color = Ink3,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
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
                    .offset(x = thumbOffset)
                    .size(20.dp)
                    .shadow(
                        2.dp,
                        CircleShape,
                        clip = false,
                        ambientColor = Color(0x33323C6E),
                        spotColor = Color(0x33323C6E),
                    )
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

private suspend fun readOriginalBase64(
    context: Context,
    uri: Uri,
): Base64Payload? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        if (bytes.isEmpty()) return@runCatching null
        Base64Payload(
            data = Base64.encodeToString(bytes, Base64.NO_WRAP),
            mime = resolver.getType(uri) ?: "image/jpeg",
            byteCount = bytes.size,
        )
    }.getOrNull()
}

private suspend fun encodeCompressedBase64(source: Bitmap): Base64Payload? =
    withContext(Dispatchers.Default) {
        runCatching {
            if (source.width <= 0 || source.height <= 0) return@runCatching null
            val flattened = Bitmap.createBitmap(
                source.width,
                source.height,
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(flattened)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(source, 0f, 0f, null)

            val output = ByteArrayOutputStream()
            val written = flattened.compress(Bitmap.CompressFormat.JPEG, 85, output)
            flattened.recycle()
            if (!written) return@runCatching null
            val bytes = output.toByteArray()
            Base64Payload(
                data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                mime = "image/jpeg",
                byteCount = bytes.size,
            )
        }.getOrNull()
    }

private fun base64TextFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "base64_$stamp.txt"
}

private fun formatCount(value: Int): String =
    String.format(Locale.US, "%,d", value)

private fun formatBytes(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
