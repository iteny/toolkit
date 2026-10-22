package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.components.ToolIconTile
import com.example.toolkit.ui.design.liquidGlass
import com.example.toolkit.ui.icons.GlyphIcon
import com.example.toolkit.ui.model.ToolItem
import com.example.toolkit.ui.theme.AppleBlue
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink2
import com.example.toolkit.ui.theme.Ink3
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val CropPreviewShape = RoundedCornerShape(20.dp)
private val CropPanelShape = RoundedCornerShape(20.dp)
private val CropPreviewHeight = 338.dp
private val CropPreviewBackground = Color(0xFF10131B)

private data class CropRatio(val label: String, val value: Float?)

private val cropRatios = listOf(
    CropRatio("自由", null),
    CropRatio("1:1", 1f),
    CropRatio("4:3", 4f / 3f),
    CropRatio("16:9", 16f / 9f),
)

@Composable
fun ImageCropTool(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aspect by remember { mutableStateOf<Float?>(null) }
    var pendingSaveBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val cropState = remember { mutableStateOf(Rect.Zero) }
    val imageRectState = remember { mutableStateOf(Rect.Zero) }
    val aspectState = rememberUpdatedState(aspect)

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            sourceUri = uri
            error = null
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg"),
    ) { uri ->
        val bitmap = pendingSaveBitmap
        pendingSaveBitmap = null
        if (uri != null && bitmap != null) {
            scope.launch {
                val ok = saveBitmapToUri(context, bitmap, uri)
                saving = false
                Toast.makeText(
                    context,
                    if (ok) "已保存" else "保存失败，请重试",
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
        val decoded = decodeImage(context, uri, maxDimension = 2048)
        if (decoded == null) {
            error = "图片读取失败，请换一张试试"
            loading = false
        } else {
            sourceBitmap = decoded
            aspect = null
            loading = false
        }
    }

    val currentImageRect = imageRectState.value
    LaunchedEffect(sourceBitmap, currentImageRect, aspect) {
        if (sourceBitmap != null && currentImageRect.width > 0f) {
            cropState.value = centeredCropRect(currentImageRect, aspect)
        }
    }

    fun openPicker() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun cropSource(): Bitmap? {
        val bitmap = sourceBitmap ?: return null
        val imageRect = imageRectState.value
        val crop = cropState.value
        if (imageRect.width < 2f || crop.width < 2f || crop.height < 2f) return null

        val scaleX = bitmap.width / imageRect.width
        val scaleY = bitmap.height / imageRect.height
        val left = ((crop.left - imageRect.left) * scaleX)
            .roundToInt()
            .coerceIn(0, bitmap.width - 1)
        val top = ((crop.top - imageRect.top) * scaleY)
            .roundToInt()
            .coerceIn(0, bitmap.height - 1)
        val right = ((crop.right - imageRect.left) * scaleX)
            .roundToInt()
            .coerceIn(left + 1, bitmap.width)
        val bottom = ((crop.bottom - imageRect.top) * scaleY)
            .roundToInt()
            .coerceIn(top + 1, bitmap.height)

        return runCatching {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        }.getOrNull()
    }

    fun saveImage() {
        if (saving) return
        val cropped = cropSource()
        if (cropped == null) {
            Toast.makeText(context, "裁剪失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        saving = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                val ok = saveBitmapToGallery(
                    context = context,
                    bitmap = cropped,
                    fileName = imageFileName("crop"),
                )
                saving = false
                Toast.makeText(
                    context,
                    if (ok) "已保存到相册" else "保存失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            pendingSaveBitmap = cropped
            createDocument.launch(imageFileName("crop"))
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
                    text = "图片裁剪",
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    text = "选择图片，拖动四角调整裁剪区域",
                    color = Ink3,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (bitmap == null) {
            EmptyCropState(
                color = tool.color,
                loading = loading,
                onPick = { openPicker() },
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CropPreviewHeight)
                    .clip(CropPreviewShape)
                    .background(CropPreviewBackground)
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CropPreviewShape),
            ) {
                val previewWidthPx = constraints.maxWidth.toFloat()
                val previewHeightPx = constraints.maxHeight.toFloat()
                val scale = min(
                    previewWidthPx / bitmap.width,
                    previewHeightPx / bitmap.height,
                )
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                val imageRect = Rect(
                    left = (previewWidthPx - drawWidth) / 2f,
                    top = (previewHeightPx - drawHeight) / 2f,
                    right = (previewWidthPx + drawWidth) / 2f,
                    bottom = (previewHeightPx + drawHeight) / 2f,
                )
                SideEffect { imageRectState.value = imageRect }

                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "图片裁剪预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )

                CropOverlay(
                    cropState = cropState,
                    imageRectState = imageRectState,
                    aspectState = aspectState,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(12.dp))

            CropRatioSelector(
                selected = aspect,
                onSelect = { aspect = it },
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
                ImageToolTextButton(
                    text = "重置",
                    onClick = {
                        cropState.value = centeredCropRect(imageRectState.value, aspect)
                    },
                )
                Spacer(Modifier.weight(1f))
                ImageToolPrimaryButton(
                    text = "保存图片",
                    loading = saving,
                    onClick = { saveImage() },
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = if (aspect == null) {
                    "自由裁剪，可拖动四角或移动裁剪框"
                } else {
                    "已锁定 ${ratioLabel(aspect)} 比例"
                },
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
                color = Color(0xFFD9433F),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyCropState(
    color: Color,
    loading: Boolean,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CropPanelShape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    0f to Color(0xFFF8FBFF),
                    1f to Color(0xFFFDF5FB),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.90f), CropPanelShape)
            .padding(horizontal = 18.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            key = "crop",
            color = color,
            modifier = Modifier.size(36.dp),
            strokeWidth = 1.4f,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "选择一张图片开始裁剪",
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "支持 JPG、PNG 等常见格式",
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
private fun CropRatioSelector(
    selected: Float?,
    onSelect: (Float?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cropRatios.forEach { ratio ->
            val isSelected = if (ratio.value == null) {
                selected == null
            } else {
                selected == ratio.value
            }
            val shape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(shape)
                    .background(
                        if (isSelected) {
                            AppleBlue
                        } else {
                            Color.White.copy(alpha = 0.72f)
                        },
                    )
                    .border(
                        1.dp,
                        if (isSelected) {
                            Color.Transparent
                        } else {
                            Color.White.copy(alpha = 0.90f)
                        },
                        shape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(ratio.value) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ratio.label,
                    color = if (isSelected) Color.White else Ink2,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CropOverlay(
    cropState: MutableState<Rect>,
    imageRectState: State<Rect>,
    aspectState: State<Float?>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cornerTouchPx = with(density) { 30.dp.toPx() }
    val minSizePx = with(density) { 44.dp.toPx() }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()

                val bounds = imageRectState.value
                val startCrop = cropState.value
                val handle = if (bounds.width > 0f) {
                    hitTestCropHandle(down.position, startCrop, cornerTouchPx)
                } else {
                    CropHandle.None
                }

                if (handle == CropHandle.None) {
                    // Still claim the gesture so the parent scroll doesn't move
                    // the page while the user is working on the preview.
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.all { !it.pressed }) break
                    }
                } else {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        cropState.value = updateCropRect(
                            start = startCrop,
                            handle = handle,
                            dx = dx,
                            dy = dy,
                            bounds = bounds,
                            minSize = minSizePx,
                            aspect = aspectState.value,
                        )
                        change.consume()
                    }
                }
            }
        },
    ) {
        val crop = cropState.value
        if (crop.width <= 0f || crop.height <= 0f) return@Canvas

        val scrim = Color.Black.copy(alpha = 0.50f)
        val w = size.width
        val h = size.height

        // Darken everything outside the crop.
        drawRect(scrim, Offset.Zero, Size(w, crop.top.coerceIn(0f, h)))
        drawRect(
            scrim,
            Offset(0f, crop.bottom),
            Size(w, (h - crop.bottom).coerceAtLeast(0f)),
        )
        drawRect(
            scrim,
            Offset(0f, crop.top),
            Size(crop.left.coerceIn(0f, w), crop.height),
        )
        drawRect(
            scrim,
            Offset(crop.right, crop.top),
            Size((w - crop.right).coerceAtLeast(0f), crop.height),
        )

        // Crop border.
        drawRect(
            color = Color.White.copy(alpha = 0.95f),
            topLeft = crop.topLeft,
            size = crop.size,
            style = Stroke(width = 1.5.dp.toPx()),
        )

        // Rule of thirds.
        val thirdsColor = Color.White.copy(alpha = 0.34f)
        val thirdStroke = 1.dp.toPx()
        val thirdW = crop.width / 3f
        val thirdH = crop.height / 3f
        drawLine(
            thirdsColor,
            Offset(crop.left + thirdW, crop.top),
            Offset(crop.left + thirdW, crop.bottom),
            thirdStroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            thirdsColor,
            Offset(crop.left + thirdW * 2f, crop.top),
            Offset(crop.left + thirdW * 2f, crop.bottom),
            thirdStroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            thirdsColor,
            Offset(crop.left, crop.top + thirdH),
            Offset(crop.right, crop.top + thirdH),
            thirdStroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            thirdsColor,
            Offset(crop.left, crop.top + thirdH * 2f),
            Offset(crop.right, crop.top + thirdH * 2f),
            thirdStroke,
            cap = StrokeCap.Round,
        )

        // Corner handles.
        drawCorner(crop.left, crop.top, 1f, 1f)
        drawCorner(crop.right, crop.top, -1f, 1f)
        drawCorner(crop.left, crop.bottom, 1f, -1f)
        drawCorner(crop.right, crop.bottom, -1f, -1f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCorner(
    x: Float,
    y: Float,
    dx: Float,
    dy: Float,
) {
    val length = 20.dp.toPx()
    val white = Color.White
    val under = Color.Black.copy(alpha = 0.35f)
    val stroke = 3.dp.toPx()
    val underStroke = stroke + 2.dp.toPx()

    val xEnd = x + dx * length
    val yEnd = y + dy * length

    drawLine(under, Offset(x, y), Offset(xEnd, y), underStroke, cap = StrokeCap.Round)
    drawLine(under, Offset(x, y), Offset(x, yEnd), underStroke, cap = StrokeCap.Round)
    drawLine(white, Offset(x, y), Offset(xEnd, y), stroke, cap = StrokeCap.Round)
    drawLine(white, Offset(x, y), Offset(x, yEnd), stroke, cap = StrokeCap.Round)
}

private enum class CropHandle {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
    Move,
    None,
}

private fun hitTestCropHandle(
    position: Offset,
    crop: Rect,
    touch: Float,
): CropHandle {
    if (crop.width <= 0f) return CropHandle.None
    val nearLeft = abs(position.x - crop.left) <= touch
    val nearRight = abs(position.x - crop.right) <= touch
    val nearTop = abs(position.y - crop.top) <= touch
    val nearBottom = abs(position.y - crop.bottom) <= touch

    return when {
        nearLeft && nearTop -> CropHandle.TopLeft
        nearRight && nearTop -> CropHandle.TopRight
        nearLeft && nearBottom -> CropHandle.BottomLeft
        nearRight && nearBottom -> CropHandle.BottomRight
        crop.contains(position) -> CropHandle.Move
        else -> CropHandle.None
    }
}

private fun updateCropRect(
    start: Rect,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    bounds: Rect,
    minSize: Float,
    aspect: Float?,
): Rect {
    if (handle == CropHandle.None) return start

    if (handle == CropHandle.Move) {
        val width = start.width
        val height = start.height
        val left = (start.left + dx).coerceIn(bounds.left, (bounds.right - width).coerceAtLeast(bounds.left))
        val top = (start.top + dy).coerceIn(bounds.top, (bounds.bottom - height).coerceAtLeast(bounds.top))
        return Rect(left, top, left + width, top + height)
    }

    if (aspect != null) {
        return resizeWithAspect(start, handle, dx, dy, bounds, minSize, aspect)
    }

    var left = start.left
    var top = start.top
    var right = start.right
    var bottom = start.bottom

    when (handle) {
        CropHandle.TopLeft -> {
            left = (left + dx).coerceIn(bounds.left, right - minSize)
            top = (top + dy).coerceIn(bounds.top, bottom - minSize)
        }

        CropHandle.TopRight -> {
            right = (right + dx).coerceIn(left + minSize, bounds.right)
            top = (top + dy).coerceIn(bounds.top, bottom - minSize)
        }

        CropHandle.BottomLeft -> {
            left = (left + dx).coerceIn(bounds.left, right - minSize)
            bottom = (bottom + dy).coerceIn(top + minSize, bounds.bottom)
        }

        CropHandle.BottomRight -> {
            right = (right + dx).coerceIn(left + minSize, bounds.right)
            bottom = (bottom + dy).coerceIn(top + minSize, bounds.bottom)
        }

        else -> Unit
    }

    return Rect(left, top, right, bottom)
}

private fun resizeWithAspect(
    start: Rect,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    bounds: Rect,
    minSize: Float,
    aspect: Float,
): Rect {
    val isLeft = handle == CropHandle.TopLeft || handle == CropHandle.BottomLeft
    val isTop = handle == CropHandle.TopLeft || handle == CropHandle.TopRight

    val anchorX = if (isLeft) start.right else start.left
    val anchorY = if (isTop) start.bottom else start.top
    val signX = if (isLeft) -1f else 1f
    val signY = if (isTop) -1f else 1f

    val movingX = if (isLeft) start.left + dx else start.right + dx
    val movingY = if (isTop) start.top + dy else start.bottom + dy

    var width = ((movingX - anchorX) * signX).coerceAtLeast(1f)
    var height = ((movingY - anchorY) * signY).coerceAtLeast(1f)

    if (width / height > aspect) {
        height = width / aspect
    } else {
        width = height * aspect
    }

    val maxWidthPx = if (signX > 0f) bounds.right - anchorX else anchorX - bounds.left
    val maxHeightPx = if (signY > 0f) bounds.bottom - anchorY else anchorY - bounds.top
    if (width > maxWidthPx) {
        width = maxWidthPx.coerceAtLeast(1f)
        height = width / aspect
    }
    if (height > maxHeightPx) {
        height = maxHeightPx.coerceAtLeast(1f)
        width = height * aspect
    }

    width = width.coerceAtLeast(minSize.coerceAtMost(maxWidthPx))
    height = height.coerceAtLeast(minSize.coerceAtMost(maxHeightPx))

    val left = if (signX > 0f) anchorX else anchorX - width
    val top = if (signY > 0f) anchorY else anchorY - height
    return Rect(left, top, left + width, top + height)
}

private fun centeredCropRect(bounds: Rect, aspect: Float?): Rect {
    if (aspect == null || bounds.width <= 0f || bounds.height <= 0f) return bounds
    val imageAspect = bounds.width / bounds.height
    val width: Float
    val height: Float
    if (imageAspect > aspect) {
        height = bounds.height
        width = height * aspect
    } else {
        width = bounds.width
        height = width / aspect
    }
    val left = bounds.left + (bounds.width - width) / 2f
    val top = bounds.top + (bounds.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun ratioLabel(aspect: Float?): String {
    if (aspect == null) return "自由"
    return when {
        abs(aspect - 1f) < 0.01f -> "1:1"
        abs(aspect - 4f / 3f) < 0.01f -> "4:3"
        abs(aspect - 16f / 9f) < 0.01f -> "16:9"
        else -> String.format("%.2f", aspect)
    }
}
