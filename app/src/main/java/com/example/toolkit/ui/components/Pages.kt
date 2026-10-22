package com.example.toolkit.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.design.EmphasizedEasing
import com.example.toolkit.ui.design.liquidGlass
import com.example.toolkit.ui.icons.GlyphIcon
import com.example.toolkit.ui.model.ToolCatalog
import com.example.toolkit.ui.model.ToolCategory
import com.example.toolkit.ui.model.ToolItem
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink2

@Composable
fun HomePage(
    categories: List<ToolCategory>,
    onToolClick: (ToolItem) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 24.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = topInset,
            bottom = bottomInset,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            ToolPanel(
                title = category.name,
                tools = category.tools,
                onToolClick = onToolClick,
            )
        }
    }
}

@Composable
fun FavoritesPage(
    favorites: List<ToolItem>,
    onToolClick: (ToolItem) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 24.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = topInset,
            bottom = bottomInset,
        ),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 26.dp)
                    .padding(top = 15.dp, start = 6.dp, end = 6.dp, bottom = 20.dp),
            ) {
                PanelHeader(title = "我的收藏", subtitle = favorites.size.toString())
                if (favorites.isEmpty()) {
                    FavoritesEmpty()
                } else {
                    ToolGrid(tools = favorites, onToolClick = onToolClick)
                }
            }
        }
    }
}

@Composable
fun ExclusivePage(
    onToolClick: (ToolItem) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 24.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = topInset,
            bottom = bottomInset,
        ),
    ) {
        item {
            ToolPanel(
                title = ToolCatalog.exclusiveTitle,
                tools = ToolCatalog.exclusiveTools,
                onToolClick = onToolClick,
            )
        }
    }
}

@Composable
fun CategoryPage(
    categories: List<ToolCategory>,
    selectedId: String,
    onSelectCategory: (String) -> Unit,
    onToolClick: (ToolItem) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 10.dp,
) {
    val selected = categories.firstOrNull { it.id == selectedId } ?: categories.first()
    val listScroll = rememberScrollState()
    val navScroll = rememberScrollState()

    LaunchedEffect(selected.id) {
        listScroll.scrollTo(0)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 14.dp,
                end = 14.dp,
                top = topInset,
                bottom = bottomInset,
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CategoryNav(
            categories = categories,
            selectedId = selected.id,
            onSelect = onSelectCategory,
            modifier = Modifier
                .width(96.dp)
                .liquidGlass(cornerRadius = 22.dp, shadowElevation = 10.dp)
                .verticalScroll(navScroll)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .liquidGlass(cornerRadius = 22.dp, shadowElevation = 10.dp)
                .verticalScroll(listScroll)
                .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
        ) {
            Crossfade(
                targetState = selected,
                animationSpec = tween(190, easing = EmphasizedEasing),
                label = "categoryList",
            ) { category ->
                Column {
                    category.tools.forEach { tool ->
                        CategoryRow(
                            tool = tool,
                            color = category.color,
                            onClick = { onToolClick(tool) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryNav(
    categories: List<ToolCategory>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        categories.forEach { category ->
            CategoryNavItem(
                category = category,
                active = category.id == selectedId,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

@Composable
private fun CategoryNavItem(
    category: ToolCategory,
    active: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interaction, pressedScale = 0.95f)
    val activeAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(200),
        label = "navActive",
    )
    val color by animateColorAsState(
        targetValue = if (active) category.color else Ink2,
        animationSpec = tween(200),
        label = "navColor",
    )

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .drawBehind {
                if (activeAlpha > 0.01f) {
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.96f * activeAlpha),
                            1f to Color.White.copy(alpha = 0.64f * activeAlpha),
                        ),
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.95f * activeAlpha),
                        start = androidx.compose.ui.geometry.Offset(4f, 1f),
                        end = androidx.compose.ui.geometry.Offset(size.width - 4f, 1f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .graphicsLayer {
                    scaleX = pressScale * (1f + 0.06f * activeAlpha)
                    scaleY = pressScale * (1f + 0.06f * activeAlpha)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            GlyphIcon(
                key = category.iconKey,
                color = color,
                modifier = Modifier.size(21.dp),
                strokeWidth = 1.7f,
            )
            Text(
                text = category.name,
                color = color,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.1).sp,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    tool: ToolItem,
    color: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed) Color.White.copy(alpha = 0.80f) else Color.Transparent,
        animationSpec = tween(180),
        label = "rowBackground",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToolIconTile(
            color = color,
            iconKey = tool.iconKey,
            size = 34.dp,
            corner = 11.dp,
            iconSize = 19.dp,
            strokeWidth = 1.7f,
            tintAlpha = 0f,
        )
        Text(
            text = tool.name,
            modifier = Modifier.weight(1f),
            color = Ink,
            fontSize = 13.sp,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        GlyphIcon(
            key = "chevron",
            color = Color(0xFFBFC4D0),
            modifier = Modifier.size(13.dp),
            strokeWidth = 2f,
        )
    }
}
