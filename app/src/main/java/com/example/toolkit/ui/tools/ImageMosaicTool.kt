package com.example.toolkit.ui.tools

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.components.ToolIconTile
import com.example.toolkit.ui.design.EmphasizedEasing
import com.example.toolkit.ui.design.liquidGlass
import com.example.toolkit.ui.icons.GlyphIcon
import com.example.toolkit.ui.model.ToolItem
import com.example.toolkit.ui.theme.AppleBlue
import com.example.toolkit.ui.theme.AppleBlueLight
import com.example.toolkit.ui.theme.DangerRed
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink2
import com.example.toolkit.ui.theme.Ink3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val PreviewShape = RoundedCornerShape(20.dp)
private val PanelShape = RoundedCornerShape(20.dp)

@Composable
fun ImageMosaicTool(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mosaicBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var blockSize by rememberSaveable { mutableStateOf(32f) }
    var loading by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
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
        showOriginal = false
        val decoded = decodeImage(context, uri)
        if (decoded == null) {
            error = "图片读取失败，请换一张试试"
            loading = false
        } else {
            sourceBitmap = decoded
            mosaicBitmap = null
            loading = false
        }
    }

    LaunchedEffect(sourceBitmap, blockSize) {
        val source = sourceBitmap ?: return@LaunchedEffect
        processing = true
        delay(55)
        val result = withContext(Dispatchers.Default) {
            buildMosaic(source, blockSize.roundToInt())
        }
        mosaicBitmap = result
        processing = false
    }

    fun openPicker() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun saveImage() {
        val bitmap = mosaicBitmap ?: return
        saving = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                val ok = saveBitmapToGallery(
                    context = context,
                    bitmap = bitmap,
                    fileName = imageFileName("mosaic"),
                )
                saving = false
                Toast.makeText(
                    context,
                    if (ok) "已保存到相册" else "保存失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            pendingSaveBitmap = bitmap
            createDocument.launch(imageFileName("mosaic"))
        }
    }

    val displayBitmap = when {
        showOriginal -> sourceBitmap
        mosaicBitmap != null -> mosaicBitmap
        else -> sourceBitmap
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
                    text = "图片马赛克",
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    text = "选择图片，调整颗粒大小后保存",
                    color = Ink3,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (sourceBitmap == null) {
            EmptyMosaicState(
                color = tool.color,
                loading = loading,
                onPick = { openPicker() },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(PreviewShape)
                    .background(Color(0xFFF0F4FB))
                    .border(1.dp, Color.White.copy(alpha = 0.86f), PreviewShape),
                contentAlignment = Alignment.Center,
            ) {
                displayBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "图片马赛克预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }

                OriginalToggle(
                    showOriginal = showOriginal,
                    onClick = { showOriginal = !showOriginal },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                )

                if (loading || (processing && mosaicBitmap == null)) {
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
                Spacer(Modifier.weight(1f))
                ImageToolPrimaryButton(
                    text = "保存图片",
                    enabled = mosaicBitmap != null && !loading,
                    loading = saving,
                    onClick = { saveImage() },
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "马赛克大小",
                    color = Ink2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (processing) "生成中…" else "${blockSize.roundToInt()} px",
                    color = Ink3,
                    fontSize = 12.sp,
                )
            }
            MosaicSlider(
                value = blockSize,
                onValueChange = {
                    blockSize = it
                    showOriginal = false
                },
            )
            Text(
                text = "向右拖动颗粒更大，向左更细腻",
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
private fun EmptyMosaicState(
    color: Color,
    loading: Boolean,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFF8FBFF),
                    1f to Color(0xFFFDF5FB),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.90f), PanelShape)
            .padding(horizontal = 18.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            key = "catImage",
            color = color,
            modifier = Modifier.size(36.dp),
            strokeWidth = 1.4f,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "选择一张图片开始制作",
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
            .border(
                1.dp,
                Color.White.copy(alpha = 0.92f),
                RoundedCornerShape(10.dp),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (showOriginal) "查看马赛克" else "查看原图",
            color = Ink2,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.1).sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MosaicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val colors = SliderDefaults.colors(
        thumbColor = AppleBlue,
        activeTrackColor = AppleBlue,
        inactiveTrackColor = Color(0xFFDDE4F0),
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = 8f..96f,
        colors = colors,
        modifier = Modifier.fillMaxWidth(),
        thumb = {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .shadow(
                        4.dp,
                        CircleShape,
                        clip = false,
                        ambientColor = Color(0x33323C6E),
                        spotColor = Color(0x33323C6E),
                    )
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, AppleBlue, CircleShape),
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                enabled = true,
                colors = colors,
                drawStopIndicator = null,
            )
        },
    )
}
