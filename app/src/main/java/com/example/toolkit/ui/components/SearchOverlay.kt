package com.example.toolkit.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.design.EmphasizedEasing
import com.example.toolkit.ui.design.blockTaps
import com.example.toolkit.ui.model.ToolItem
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink3

/**
 * Search results sheet. It lives underneath the glass app bar and above the
 * page content, exactly like the prototype's `.searchpanel`.
 */
@Composable
fun SearchOverlay(
    visible: Boolean,
    query: String,
    allTools: List<ToolItem>,
    onToolClick: (ToolItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val offsetPx = with(density) { 8.dp.roundToPx() }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(
            animationSpec = tween(280, easing = EmphasizedEasing),
            initialOffsetY = { -offsetPx },
        ),
        exit = fadeOut(tween(150)) + slideOutVertically(
            animationSpec = tween(180, easing = EmphasizedEasing),
            targetOffsetY = { -offsetPx },
        ),
    ) {
        SearchPanel(
            query = query,
            allTools = allTools,
            onToolClick = onToolClick,
        )
    }
}

@Composable
private fun SearchPanel(
    query: String,
    allTools: List<ToolItem>,
    onToolClick: (ToolItem) -> Unit,
) {
    val key = query.trim()
    val results = remember(query) {
        if (key.isEmpty()) {
            allTools
        } else {
            allTools.filter { it.name.contains(key, ignoreCase = true) }
        }
    }
    val gridState = rememberLazyGridState()

    LaunchedEffect(key) {
        gridState.scrollToItem(0)
    }

    Box(
        Modifier
            .fillMaxSize()
            .blockTaps()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFFFFFDF9).copy(
                        alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.90f else 0.97f,
                    ),
                    1f to Color(0xFFFAF7F1).copy(
                        alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.96f else 0.99f,
                    ),
                ),
            ),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 14.dp,
                bottom = 118.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 0.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = if (key.isEmpty()) "全部工具" else "搜索结果",
                        modifier = Modifier.alignByBaseline(),
                        color = Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                    )
                    Box(Modifier.weight(1f))
                    Text(
                        text = results.size.toString(),
                        modifier = Modifier.alignByBaseline(),
                        color = Ink3,
                        fontSize = 12.sp,
                    )
                }
            }

            items(results, key = { it.name }) { tool ->
                ToolButton(
                    tool = tool,
                    onClick = { onToolClick(tool) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (results.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 54.dp),
                    ) {
                        EmptyHint("没有找到相关工具")
                    }
                }
            }
        }
    }
}
