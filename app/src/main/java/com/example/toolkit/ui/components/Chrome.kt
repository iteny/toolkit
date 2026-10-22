package com.example.toolkit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.design.EmphasizedEasing
import com.example.toolkit.ui.design.blockTaps
import com.example.toolkit.ui.design.liquidGlass
import com.example.toolkit.ui.icons.GlyphIcon
import com.example.toolkit.ui.icons.HippoLogo
import com.example.toolkit.ui.model.AppTab
import com.example.toolkit.ui.theme.AppleBlue
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.SoftShadow
import com.example.toolkit.ui.theme.TabGray

@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(180, easing = EmphasizedEasing),
        label = "pressScale",
    ).value
}

/** Glass app bar with brand, search field and account button. */
@Composable
fun TopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    searchFocused: Boolean,
    onSearchFocusChanged: (Boolean) -> Unit,
    onClearQuery: () -> Unit,
    loggedIn: Boolean,
    onAccountClick: () -> Unit,
    onBrandClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .liquidGlass(cornerRadius = 20.dp, shadowElevation = 10.dp, frost = 0.58f)
            .blockTaps()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // Brand
        val brandInteraction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = brandInteraction,
                    indication = null,
                    onClick = onBrandClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .shadow(
                        3.dp,
                        RoundedCornerShape(9.dp),
                        clip = false,
                        ambientColor = Color(0x47323C6E),
                        spotColor = Color(0x47323C6E),
                    )
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.95f),
                            1f to Color.White.copy(alpha = 0.55f),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.90f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                HippoLogo(Modifier.size(17.dp))
            }
            Text(
                text = "河马工具箱",
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
            )
        }

        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            focused = searchFocused,
            onFocusChanged = onSearchFocusChanged,
            onClear = onClearQuery,
            modifier = Modifier.weight(1f),
        )

        AccountButton(loggedIn = loggedIn, onClick = onAccountClick)
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val background by animateColorAsState(
        targetValue = if (focused) {
            Color.White.copy(alpha = 0.68f)
        } else {
            Color.White.copy(alpha = 0.42f)
        },
        animationSpec = tween(220),
        label = "searchBackground",
    )

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(background, shape)
            .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
            .drawBehind {
                drawLine(
                    color = Color(0xFF323C6E).copy(alpha = 0.10f),
                    start = Offset(4f, 1f),
                    end = Offset(size.width - 4f, 1f),
                    strokeWidth = 1f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.65f),
                    start = Offset(4f, size.height - 1f),
                    end = Offset(size.width - 4f, size.height - 1f),
                    strokeWidth = 1f,
                    cap = StrokeCap.Round,
                )
            }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphIcon(
            key = "search",
            color = Color(0xFF505F82).copy(alpha = 0.70f),
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.2f,
        )

        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索工具",
                    color = Color(0xFF505F82).copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    letterSpacing = (-0.1).sp,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                singleLine = true,
                textStyle = TextStyle(
                    color = Ink,
                    fontSize = 13.sp,
                    letterSpacing = (-0.1).sp,
                ),
                cursorBrush = SolidColor(AppleBlue),
            )
        }

        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(tween(140)) + scaleIn(tween(160), initialScale = 0.8f),
            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.8f),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .semantics { contentDescription = "清除" }
                    .clip(CircleShape)
                    .background(Color(0xFF78849B).copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClear,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    key = "close",
                    color = Color.White,
                    modifier = Modifier.size(9.dp),
                    strokeWidth = 2.6f,
                )
            }
        }
    }
}

@Composable
private fun AccountButton(
    loggedIn: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressedScale = 0.96f)
    val shape = if (loggedIn) CircleShape else RoundedCornerShape(980.dp)

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (loggedIn) Modifier.size(30.dp) else Modifier.height(30.dp))
            .semantics { contentDescription = if (loggedIn) "账户" else "登录" }
            .shadow(
                4.dp,
                shape,
                clip = false,
                ambientColor = AppleBlue.copy(alpha = 0.62f),
                spotColor = AppleBlue.copy(alpha = 0.62f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFF1C92FF).copy(alpha = 0.95f),
                    1f to Color(0xFF0071E3).copy(alpha = 0.98f),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .then(if (loggedIn) Modifier else Modifier.padding(horizontal = 13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (loggedIn) {
            GlyphIcon(
                key = "avatar",
                color = Color.White,
                modifier = Modifier.size(19.dp),
                strokeWidth = 1.9f,
            )
        } else {
            Text(
                text = "登录",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Floating bottom tab bar with a tinted active pill. */
@Composable
fun TabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(64.dp)
            .liquidGlass(
                cornerRadius = 30.dp,
                shadowElevation = 16.dp,
                frost = 0.62f,
            )
            .blockTaps()
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.forEach { tab ->
            TabItem(
                tab = tab,
                active = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun TabItem(
    tab: AppTab,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressedScale = rememberPressScale(interaction, pressedScale = 0.92f)
    val color by animateColorAsState(
        targetValue = if (active) AppleBlue else TabGray,
        animationSpec = tween(220),
        label = "tabColor",
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(220, easing = EmphasizedEasing),
        label = "pillAlpha",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (pillAlpha > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = pillAlpha
                        scaleX = 0.97f + 0.03f * pillAlpha
                        scaleY = 0.97f + 0.03f * pillAlpha
                    }
                    .shadow(
                        4.dp,
                        RoundedCornerShape(24.dp),
                        clip = false,
                        ambientColor = SoftShadow,
                        spotColor = SoftShadow,
                    )
                    .background(ActivePillBrush, RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.85f * pillAlpha),
                        RoundedCornerShape(24.dp),
                    ),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlyphIcon(
                key = tab.iconKey,
                color = color,
                modifier = Modifier
                    .size(23.dp)
                    .graphicsLayer { scaleX = pressedScale; scaleY = pressedScale },
                strokeWidth = 1.6f,
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = tab.label,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
