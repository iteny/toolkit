package com.example.toolkit.ui.tools

import android.graphics.Bitmap
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

private val InvertPreviewShape = RoundedCornerShape(20.dp)
private val InvertPanelShape = RoundedCornerShape(20.dp)

private enum class InvertMode(val label: String, val description: String) {
    Rgb("RGB 反相", "R / G / B 取反，Alpha 保持不变"),
    Grayscale("灰度反相", "先转灰度再反相，Alpha 保持不变"),
}

@Composable
fun InvertImageTool(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceMime by remember { mutableStateOf("image/png") }
    var mode by remember { mutableStateOf(InvertMode.Rgb) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSaveBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            sourceUri = uri
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
        processing = true
        result = null
        showOriginal = false
        val decoded = decodeImage(context, uri, maxDimension = 2048)
        if (decoded == null) {
            error = "图片读取失败，请换一张试试"
            loading = false
            processing = false
            return@LaunchedEffect
        }
        sourceBitmap = decoded
        sourceMime = context.contentResolver.getType(uri) ?: "image/png"
        loading = false
    }

    LaunchedEffect(sourceBitmap, mode) {
        val source = sourceBitmap ?: return@LaunchedEffect
        processing = true
        val inverted = withContext(Dispatchers.Default) {
            runCatching { invertBitmap(source, grayscale = mode == InvertMode.Grayscale) }
                .getOrNull()
        }
        result = inverted
        processing = false
        if (inverted == null) error = "处理失败，请重试"
    }

    fun openPicker() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun savePng() {
        val bitmap = result ?: return
        if (saving || processing || loading) return
        saving = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                val ok = savePngToGallery(context, bitmap, invertFileName())
                saving = false
                Toast.makeText(
                    context,
                    if (ok) "PNG 已保存到相册" else "保存失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            pendingSaveBitmap = bitmap
            createPng.launch(invertFileName())
        }
    }

    val bitmap = sourceBitmap
    val display = if (showOriginal) bitmap else result

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
                    text = "图片反相/反色",
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    text = "一键生成反相图片，保留透明通道",
                    color = Ink3,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (bitmap == null) {
            EmptyInvertState(
                color = tool.color,
                loading = loading,
                onPick = { openPicker() },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(InvertPreviewShape)
                    .background(Color(0xFFF4F7FC))
                    .border(1.dp, Color.White.copy(alpha = 0.86f), InvertPreviewShape),
                contentAlignment = Alignment.Center,
            ) {
                InvertCheckerboard(Modifier.fillMaxSize())
                if (display != null) {
                    Image(
                        bitmap = display.asImageBitmap(),
                        contentDescription = "反相图片预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                if (processing || loading) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(22.dp))
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
                InvertOriginalToggle(
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
                    text = "保留透明通道",
                    color = AppleBlue,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InvertMode.entries.forEach { item ->
                    InvertModeChip(
                        mode = item,
                        selected = item == mode,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = item },
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = mode.description,
                modifier = Modifier.fillMaxWidth(),
                color = Ink3,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )

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
                    text = "保存 PNG",
                    enabled = result != null && !processing && !loading,
                    loading = saving,
                    onClick = { savePng() },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "导出为 PNG，透明区域和 Alpha 通道会保留",
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
private fun EmptyInvertState(
    color: Color,
    loading: Boolean,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InvertPanelShape)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFF8FBFF),
                    1f to Color(0xFFFDF5FB),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.90f), InvertPanelShape)
            .padding(horizontal = 18.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            key = "contrast",
            color = color,
            modifier = Modifier.size(38.dp),
            strokeWidth = 1.4f,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "选择一张图片开始反相",
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "支持 JPG、PNG 等格式，导出 PNG",
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
private fun InvertModeChip(
    mode: InvertMode,
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
            text = mode.label,
            color = if (selected) Color.White else Ink3,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun InvertOriginalToggle(
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
            color = Ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun InvertCheckerboard(modifier: Modifier = Modifier) {
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

private fun invertBitmap(source: Bitmap, grayscale: Boolean): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    for (index in pixels.indices) {
        val color = pixels[index]
        val alpha = color ushr 24
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF

        val outRed: Int
        val outGreen: Int
        val outBlue: Int
        if (grayscale) {
            val luminance = (red * 299 + green * 587 + blue * 114) / 1000
            val inverted = 255 - luminance
            outRed = inverted
            outGreen = inverted
            outBlue = inverted
        } else {
            outRed = 255 - red
            outGreen = 255 - green
            outBlue = 255 - blue
        }

        pixels[index] = (alpha shl 24) or (outRed shl 16) or (outGreen shl 8) or outBlue
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(pixels, 0, width, 0, 0, width, height)
    return result
}

private fun invertFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "invert_$stamp.png"
}
