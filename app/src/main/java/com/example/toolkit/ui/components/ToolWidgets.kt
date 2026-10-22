package com.example.toolkit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.design.EmphasizedEasing
import com.example.toolkit.ui.design.liquidGlass
import com.example.toolkit.ui.icons.GlyphIcon
import com.example.toolkit.ui.model.ToolItem
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink2
import com.example.toolkit.ui.theme.Ink3
import com.example.toolkit.ui.theme.Ink4
import com.example.toolkit.ui.theme.SoftShadow

/** Rounded translucent tile that hosts a tool glyph. */
@Composable
fun ToolIconTile(
    color: Color,
    iconKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    corner: Dp = 17.dp,
    iconSize: Dp = 25.dp,
    strokeWidth: Float = 1.7f,
    tintAlpha: Float = 0.26f,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .size(size)
            .shadow(6.dp, shape, clip = false, ambientColor = SoftShadow, spotColor = SoftShadow)
            .clip(shape)
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.88f),
                        1f to Color.White.copy(alpha = 0.48f),
                    ),
                )
                val radius = this.size.minDimension * 0.85f
                drawRect(
                    Brush.radialGradient(
                        0f to color.copy(alpha = tintAlpha),
                        0.68f to Color.Transparent,
                        center = androidx.compose.ui.geometry.Offset(
                            this.size.width * 0.30f,
                            this.size.height * 0.22f,
                        ),
                        radius = radius,
                    ),
                )
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(1.5f, 0.75.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(
                        this.size.width - 1.5f,
                        0.75.dp.toPx(),
                    ),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.92f), shape),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            key = iconKey,
            color = color,
            modifier = Modifier.size(iconSize),
            strokeWidth = strokeWidth,
        )
    }
}

/** One item in the 4-column tool grids. */
@Composable
fun ToolButton(
    tool: ToolItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(180, easing = EmphasizedEasing),
        label = "toolPress",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            ToolIconTile(color = tool.color, iconKey = tool.iconKey)
        }
        Spacer(Modifier.size(7.dp))
        Text(
            text = tool.name,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1.dp),
            color = Ink2,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 4-column grid that keeps the design's 20 dp row gap and 3 dp column gap. */
@Composable
fun ToolGrid(
    tools: List<ToolItem>,
    onToolClick: (ToolItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        tools.chunked(4).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                rowTools.forEach { tool ->
                    Box(Modifier.weight(1f)) {
                        ToolButton(
                            tool = tool,
                            onClick = { onToolClick(tool) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                repeat(4 - rowTools.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Header shared by the home / exclusive / favorites panels. */
@Composable
fun PanelHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            modifier = Modifier.alignByBaseline(),
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = subtitle,
            modifier = Modifier.alignByBaseline(),
            color = Ink3,
            fontSize = 12.sp,
        )
    }
}

/** A glass panel containing a header and its tool grid. */
@Composable
fun ToolPanel(
    title: String,
    tools: List<ToolItem>,
    onToolClick: (ToolItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = 26.dp)
            .padding(top = 15.dp, start = 6.dp, end = 6.dp, bottom = 20.dp),
    ) {
        PanelHeader(title = title, subtitle = tools.size.toString())
        ToolGrid(tools = tools, onToolClick = onToolClick)
    }
}

/** Empty state on the favorites page. */
@Composable
fun FavoritesEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            key = "bookmark",
            color = Ink4.copy(alpha = 0.85f),
            modifier = Modifier.size(30.dp),
            strokeWidth = 1.4f,
        )
        Spacer(Modifier.size(14.dp))
        Text(
            text = "还没有收藏",
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "在工具页点右下角的书签即可收藏",
            color = Ink3,
            fontSize = 12.5.sp,
        )
    }
}

/** Micro helper for empty tool results. */
@Composable
fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = Ink3,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        letterSpacing = (-0.1).sp,
    )
}

/** Small gradient fill shared by pressed/active pills. */
val ActivePillBrush = Brush.linearGradient(
    0f to Color.White,
    1f to Color(0xFFF1F6FF),
)
