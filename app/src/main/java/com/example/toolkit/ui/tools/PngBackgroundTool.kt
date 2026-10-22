package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PngPreviewShape = RoundedCornerShape(20.dp)
private val PngPanelShape = RoundedCornerShape(20.dp)

private val PngBackgroundPresets = listOf(
    Color(0xFFFFFFFF),
    Color(0xFF14161C),
    Color(0xFF0071E3),
    Color(0xFFF2F4F8),
    Color(0xFFFFD9E8),
    Color(0xFFE8E4FF),
    Color(0xFFDDF5EA),
    Color(0xFFFFF3D6),
)

@Composable
fun PngBackgroundTool(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceMime by remember { mutableStateOf("image/png") }
    var hasTransparency by remember { mutableStateOf(false) }
    var backgroundColor by remember { mutableStateOf(Color.White) }
    var hexText by remember { mutableStateOf("FFFFFF") }
    var colorError by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSaveBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            sourceUri = uri
            sourceBitmap = null
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
                saving = false
                Toast.makeText(
                    context,
                    if (ok) "PNG 已保存" else "保存失败，请重试",
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
        showOriginal = false
        val decoded = decodeImage(context, uri, maxDimension = 2048)
        if (decoded == null) {
            error = "图片读取失败，请换一张试试"
            loading = false
            return@LaunchedEffect
        }
        val transparent = withContext(Dispatchers.Default) {
            runCatching { bitmapHasTransparency(decoded) }.getOrDefault(false)
        }
        sourceBitmap = decoded
        sourceMime = context.contentResolver.getType(uri) ?: "image/png"
        hasTransparency = transparent
        loading = false
    }

    fun openPicker() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun selectColor(color: Color, updateHex: Boolean = true) {
        backgroundColor = color
        if (updateHex) hexText = colorHex(color)
        colorError = null
        showOriginal = false
    }

    fun applyHex() {
        val normalized = hexText.trim().removePrefix("#")
        if (
            normalized.length != 6 ||
            normalized.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }
        ) {
            colorError = "请输入 6 位十六进制颜色"
            return
        }
        val parsed = runCatching {
            android.graphics.Color.parseColor("#$normalized")
        }.getOrNull()
        if (parsed == null) {
            colorError = "颜色格式无效"
            return
        }
        selectColor(Color(parsed), updateHex = false)
    }

    fun savePng() {
        val source = sourceBitmap ?: return
        if (saving) return
        saving = true
        scope.launch {
            val flattened = withContext(Dispatchers.Default) {
                runCatching { flattenOnColor(source, backgroundColor.toArgb()) }.getOrNull()
            }
            if (flattened == null) {
                saving = false
                Toast.makeText(context, "生成失败，请重试", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ok = savePngToGallery(
                    context = context,
                    bitmap = flattened,
                    fileName = pngFileName(),
                )
                saving = false
                Toast.makeText(
                    context,
                    if (ok) "PNG 已保存到相册" else "保存失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                pendingSaveBitmap = flattened
                createPng.launch(pngFileName())
            }
        }
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
                    text = "PNG背景色",
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    text = "为透明区域填充背景色并导出 PNG",
                    color = Ink3,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (bitmap == null) {
            EmptyPngState(
                color = tool.color,
                loading = loading,
                onPick = { openPicker() },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(PngPreviewShape)
                    .background(if (showOriginal) Color.White else backgroundColor)
                    .border(1.dp, Color.White.copy(alpha = 0.86f), PngPreviewShape),
                contentAlignment = Alignment.Center,
            ) {
                if (showOriginal) {
                    Checkerboard(Modifier.fillMaxSize())
                }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PNG 背景色预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                OriginalToggle(
                    showOriginal = showOriginal,
                    onClick = { showOriginal = !showOriginal },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                )
            }

            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${bitmap.width} × ${bitmap.height} · $sourceMime",
                    color = Ink3,
                    fontSize = 11.5.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (hasTransparency) "含透明区域" else "无透明像素",
                    color = if (hasTransparency) AppleBlue else Ink3,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hasTransparency) {
                    "保存时会用所选颜色填充透明部分"
                } else {
                    "未检测到透明像素，背景色可能不会改变画面"
                },
                modifier = Modifier.fillMaxWidth(),
                color = Ink3,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "背景色",
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "#${colorHex(backgroundColor)}",
                    color = Ink3,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                PngBackgroundPresets.forEach { color ->
                    ColorSwatch(
                        color = color,
                        selected = color == backgroundColor,
                        onClick = { selectColor(color) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.74f))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.92f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(backgroundColor)
                            .border(1.dp, Color.Black.copy(alpha = 0.10f), CircleShape),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "#",
                        color = Ink3,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    BasicTextField(
                        value = hexText,
                        onValueChange = { raw ->
                            hexText = raw
                                .filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                                .take(6)
                            colorError = null
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Ink,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp,
                        ),
                        cursorBrush = SolidColor(AppleBlue),
                    )
                }
                Spacer(Modifier.width(4.dp))
                ImageToolTextButton(
                    text = "应用",
                    onClick = {
                        applyHex()
                        focusManager.clearFocus()
                    },
                )
            }
            colorError?.let { message ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth(),
                    color = DangerRed,
                    fontSize = 10.5.sp,
                )
            }

            Spacer(Modifier.height(13.dp))

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
                    text = "保存 PNG",
                    loading = saving,
                    onClick = { savePng() },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "导出为不透明 PNG，透明区域将使用所选背景色",
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
private fun EmptyPngState(
    color: Color,
    loading: Boolean,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PngPanelShape)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFF8FBFF),
                    1f to Color(0xFFFDF5FB),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.90f), PngPanelShape)
            .padding(horizontal = 18.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            key = "palette",
            color = color,
            modifier = Modifier.size(38.dp),
            strokeWidth = 1.4f,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "选择一个 PNG 图片开始添加背景色",
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "支持带透明区域的 PNG，保存为不透明 PNG",
            color = Ink3,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
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
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .then(
                if (selected) {
                    Modifier.border(2.dp, AppleBlue, CircleShape)
                } else {
                    Modifier
                },
            )
            .padding(if (selected) 4.dp else 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.Black.copy(alpha = 0.09f), CircleShape),
        )
    }
}

@Composable
private fun Checkerboard(modifier: Modifier = Modifier) {
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

@Composable
private fun OriginalToggle(
    showOriginal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.84f))
            .border(1.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (showOriginal) "查看效果" else "查看原图",
            color = Ink2,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun colorHex(color: Color): String =
    String.format(Locale.US, "%06X", color.toArgb() and 0xFFFFFF)

private fun bitmapHasTransparency(bitmap: Bitmap): Boolean {
    val row = IntArray(bitmap.width)
    for (y in 0 until bitmap.height) {
        bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        for (pixel in row) {
            if ((pixel ushr 24) != 0xFF) return true
        }
    }
    return false
}

private fun flattenOnColor(source: Bitmap, color: Int): Bitmap {
    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(result)
    canvas.drawColor(color)
    canvas.drawBitmap(source, 0f, 0f, null)
    return result
}

private fun pngFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "png_bg_$stamp.png"
}
