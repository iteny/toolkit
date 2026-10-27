package com.example.toolkit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.design.DetailBackdrop
import com.example.toolkit.ui.design.EmphasizedEasing
import com.example.toolkit.ui.design.blockTaps
import com.example.toolkit.ui.design.liquidGlass
import com.example.toolkit.ui.icons.GlyphIcon
import com.example.toolkit.ui.model.ToolItem
import com.example.toolkit.ui.theme.AppleBlue
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink3
import com.example.toolkit.ui.theme.SoftShadow
import com.example.toolkit.ui.tools.FileConvertTool
import com.example.toolkit.ui.tools.GifCompressTool
import com.example.toolkit.ui.tools.GifFrameTool
import com.example.toolkit.ui.tools.GifSplitTool
import com.example.toolkit.ui.tools.ImageBase64Tool
import com.example.toolkit.ui.tools.ImageCropTool
import com.example.toolkit.ui.tools.ImageMosaicTool
import com.example.toolkit.ui.tools.InvertImageTool
import com.example.toolkit.ui.tools.LicensesTool
import com.example.toolkit.ui.tools.PngBackgroundTool
import com.example.toolkit.ui.tools.PrivacyTool
import kotlinx.coroutines.launch

/** Right-to-left tool page, mirroring the prototype's sliding `.detail`. */
@Composable
fun DetailOverlay(
    visible: Boolean,
    tool: ToolItem?,
    isFavorite: Boolean,
    statusBarTop: Dp,
    navigationBarBottom: Dp,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the last tool around while the slide-out animation is running.
    var shownTool by remember { mutableStateOf(tool) }
    LaunchedEffect(tool) {
        if (tool != null) shownTool = tool
    }
    val visibleTool = tool ?: shownTool

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = slideInHorizontally(
            animationSpec = tween(420, easing = EmphasizedEasing),
            initialOffsetX = { it },
        ),
        exit = slideOutHorizontally(
            animationSpec = tween(360, easing = EmphasizedEasing),
            targetOffsetX = { it },
        ),
    ) {
        if (visibleTool != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .blockTaps(),
            ) {
                DetailBackdrop(Modifier.fillMaxSize())

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 14.dp,
                            end = 14.dp,
                            top = statusBarTop + 8.dp + 54.dp + 16.dp,
                            bottom = navigationBarBottom + 110.dp,
                        ),
                ) {
                    if (visibleTool.name == "图片马赛克") {
                        ImageMosaicTool(
                            tool = visibleTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (visibleTool.name == "图片裁剪") {
                        ImageCropTool(
                            tool = visibleTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (visibleTool.name == "图片转Base64") {
                        ImageBase64Tool(
                            tool = visibleTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (visibleTool.name == "GIF帧修改") {
                        GifFrameTool(
                            tool = visibleTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (visibleTool.name == "GIF帧拆分") {
                        GifSplitTool(
                            tool = visibleTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (visibleTool.name == "GIF压缩") {
                        GifCompressTool(
                            tool = visibleTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (visibleTool.name == "PNG背景色") {
                        PngBackgroundTool(
                            tool = visibleTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (visibleTool.name == "图片反相/反色") {
                        InvertImageTool(
                            tool = visibleTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (visibleTool.name == "文件格式转换") {
                        FileConvertTool(
                            tool = visibleTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (visibleTool.name == "开源许可") {
                        LicensesTool(
                            tool = visibleTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (visibleTool.name == "隐私政策") {
                        PrivacyTool(
                            tool = visibleTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(cornerRadius = 26.dp)
                                .padding(horizontal = 22.dp, vertical = 46.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ToolIconTile(
                                color = visibleTool.color,
                                iconKey = visibleTool.iconKey,
                                size = 66.dp,
                                corner = 21.dp,
                                iconSize = 31.dp,
                                strokeWidth = 1.55f,
                                tintAlpha = 0.28f,
                            )
                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = visibleTool.name,
                                color = Ink,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.3).sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "工具页面 · 待实现",
                                color = Ink3,
                                fontSize = 13.sp,
                                lineHeight = 21.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // Keep the bar above the scrollable body for both z-order and
                // touch handling, otherwise the full-screen body swallows taps
                // on the back button.
                DetailTopBar(
                    title = visibleTool.name,
                    topInset = statusBarTop,
                    isFavorite = isFavorite,
                    favoriteColor = visibleTool.color,
                    onBack = onBack,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    title: String,
    topInset: Dp,
    isFavorite: Boolean,
    favoriteColor: Color,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = topInset + 8.dp)
            .height(54.dp)
            .liquidGlass(cornerRadius = 20.dp, shadowElevation = 10.dp)
            .blockTaps()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackButton(onClick = onBack)
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TopFavoriteButton(
            isFavorite = isFavorite,
            color = favoriteColor,
            onToggle = onToggleFavorite,
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressedScale = 0.92f)
    Box(
        modifier = Modifier
            .size(34.dp)
            .semantics { contentDescription = "返回" }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                2.dp,
                CircleShape,
                clip = false,
                ambientColor = SoftShadow,
                spotColor = SoftShadow,
            )
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.62f))
            .border(1.dp, Color.White.copy(alpha = 0.90f), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            key = "back",
            color = AppleBlue,
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.2f,
        )
    }
}

@Composable
private fun TopFavoriteButton(
    isFavorite: Boolean,
    color: Color,
    onToggle: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(180, easing = EmphasizedEasing),
        label = "favPress",
    )
    val iconColor by animateColorAsState(
        targetValue = if (isFavorite) color else Color(0xFF7C8496),
        animationSpec = tween(200),
        label = "favColor",
    )
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(34.dp)
            .semantics {
                contentDescription = if (isFavorite) "取消收藏" else "收藏"
            }
            .graphicsLayer {
                val value = pressScale * pop.value
                scaleX = value
                scaleY = value
            }
            .shadow(
                2.dp,
                CircleShape,
                clip = false,
                ambientColor = SoftShadow,
                spotColor = SoftShadow,
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.94f),
                    1f to Color.White.copy(alpha = 0.58f),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.92f), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    onToggle()
                    scope.launch {
                        pop.snapTo(0.84f)
                        pop.animateTo(
                            targetValue = 1f,
                            animationSpec = keyframes {
                                durationMillis = 400
                                1.12f at 220
                                1f at 400
                            },
                        )
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            key = "bookmark",
            color = iconColor,
            modifier = Modifier.size(17.dp),
            strokeWidth = 1.8f,
            fillColor = if (isFavorite) iconColor.copy(alpha = 0.92f) else null,
        )
    }
}
