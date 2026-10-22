package com.example.toolkit.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.design.EmphasizedEasing
import com.example.toolkit.ui.design.blockTaps
import com.example.toolkit.ui.icons.GlyphIcon
import com.example.toolkit.ui.icons.WeChatLogo
import com.example.toolkit.ui.theme.DangerRed
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink3
import com.example.toolkit.ui.theme.Ink4
import com.example.toolkit.ui.theme.WeChatGreen

private val SheetTopShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

/** Mask + bottom login sheet. */
@Composable
fun LoginOverlay(
    visible: Boolean,
    busy: Boolean,
    navigationBarBottom: Dp,
    onWeChatLogin: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(280)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E243C).copy(alpha = 0.22f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = tween(440, easing = EmphasizedEasing),
                initialOffsetY = { it },
            ),
            exit = slideOutVertically(
                animationSpec = tween(340, easing = EmphasizedEasing),
                targetOffsetY = { it },
            ),
        ) {
            LoginSheet(
                busy = busy,
                navigationBarBottom = navigationBarBottom,
                onWeChatLogin = onWeChatLogin,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun LoginSheet(
    busy: Boolean,
    navigationBarBottom: Dp,
    onWeChatLogin: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.95f else 0.98f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .blockTaps()
            .shadow(
                30.dp,
                SheetTopShape,
                clip = false,
                ambientColor = Color(0x401E2850),
                spotColor = Color(0x401E2850),
            )
            .clip(SheetTopShape)
            .background(Color(0xFFFFFDF9).copy(alpha = sheetAlpha))
            .border(1.dp, Color.White.copy(alpha = 0.92f), SheetTopShape)
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 10.dp,
                bottom = navigationBarBottom + 28.dp,
            ),
    ) {
        Box(
            Modifier
                .padding(bottom = 18.dp)
                .width(38.dp)
                .height(4.5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF46506E).copy(alpha = 0.20f))
                .align(Alignment.CenterHorizontally),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "登录",
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.weight(1f))
            SheetCloseButton(onClick = onDismiss)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "登录后可同步收藏与使用记录",
            color = Ink3,
            fontSize = 13.sp,
            letterSpacing = (-0.1).sp,
        )
        Spacer(Modifier.height(20.dp))

        WeChatLoginButton(busy = busy, onClick = onWeChatLogin)

        Spacer(Modifier.height(14.dp))
        Text(
            text = "登录即表示同意《用户协议》与《隐私政策》",
            modifier = Modifier.fillMaxWidth(),
            color = Ink4,
            fontSize = 11.sp,
            letterSpacing = (-0.1).sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to Color(0xFF96A5BE).copy(alpha = 0.42f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .shadow(
                        6.dp,
                        RoundedCornerShape(14.dp),
                        clip = false,
                        ambientColor = Color(0x401E2850),
                        spotColor = Color(0x401E2850),
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                    .padding(8.dp),
            ) {
                DecorativeQrCode(Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "扫码添加客服微信",
                color = Ink3,
                fontSize = 11.5.sp,
                letterSpacing = (-0.1).sp,
            )
        }
    }
}

@Composable
private fun SheetCloseButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed) {
            Color(0xFF78849B).copy(alpha = 0.22f)
        } else {
            Color(0xFF78849B).copy(alpha = 0.14f)
        },
        animationSpec = tween(200),
        label = "closeBackground",
    )
    Box(
        modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = "关闭" }
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            key = "close",
            color = Color(0xFF46506E).copy(alpha = 0.75f),
            modifier = Modifier.size(11.dp),
            strokeWidth = 2.6f,
        )
    }
}

@Composable
private fun WeChatLoginButton(
    busy: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(160, easing = EmphasizedEasing),
        label = "wechatPress",
    )
    val background by animateColorAsState(
        targetValue = if (pressed) Color(0xFF06B457) else WeChatGreen,
        animationSpec = tween(200),
        label = "wechatBackground",
    )
    val shape = RoundedCornerShape(15.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (busy) 0.75f else 1f
            }
            .shadow(
                10.dp,
                shape,
                clip = false,
                ambientColor = WeChatGreen.copy(alpha = 0.80f),
                spotColor = WeChatGreen.copy(alpha = 0.80f),
            )
            .clip(shape)
            .background(background)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.28f),
                    start = Offset(2f, 1f),
                    end = Offset(size.width - 2f, 1f),
                    strokeWidth = 1.5f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color(0xFF005A2D).copy(alpha = 0.25f),
                    start = Offset(2f, size.height - 1f),
                    end = Offset(size.width - 2f, size.height - 1f),
                    strokeWidth = 1.5f,
                    cap = StrokeCap.Round,
                )
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !busy,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        WeChatLogo(Modifier.size(24.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            text = if (busy) "正在登录…" else "微信一键登录",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
    }
}

/** Transparent dismiss layer plus the small account dropdown. */
@Composable
fun UserMenuOverlay(
    visible: Boolean,
    userName: String,
    topOffset: Dp,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (visible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        val density = LocalDensity.current
        val offsetPx = with(density) { 6.dp.roundToPx() }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = topOffset, end = 14.dp),
            enter = fadeIn(tween(200)) +
                scaleIn(
                    animationSpec = tween(240, easing = EmphasizedEasing),
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ) +
                slideInVertically(
                    animationSpec = tween(240, easing = EmphasizedEasing),
                    initialOffsetY = { -offsetPx },
                ),
            exit = fadeOut(tween(150)) +
                scaleOut(
                    animationSpec = tween(180, easing = EmphasizedEasing),
                    targetScale = 0.96f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ) +
                slideOutVertically(
                    animationSpec = tween(180, easing = EmphasizedEasing),
                    targetOffsetY = { -offsetPx },
                ),
        ) {
            Column(
                modifier = Modifier
                    .width(184.dp)
                    .blockTaps()
                    .shadow(
                        16.dp,
                        RoundedCornerShape(16.dp),
                        clip = false,
                        ambientColor = Color(0x401E2850),
                        spotColor = Color(0x401E2850),
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFFDF9).copy(alpha = 0.97f))
                    .border(1.dp, Color.White.copy(alpha = 0.90f), RoundedCornerShape(16.dp))
                    .padding(6.dp),
            ) {
                Text(
                    text = userName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 9.dp),
                    color = Ink3,
                    fontSize = 12.sp,
                    letterSpacing = (-0.1).sp,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFB4C3DC).copy(alpha = 0.35f)),
                )
                Spacer(Modifier.height(4.dp))
                LogoutRow(onClick = onLogout)
            }
        }
    }
}

@Composable
private fun LogoutRow(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed) DangerRed.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(180),
        label = "logoutBackground",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "退出登录",
            color = DangerRed,
            fontSize = 14.sp,
            letterSpacing = (-0.2).sp,
        )
    }
}

/* ------------------------------------------------------------------ */
/* Decorative customer-service QR                                      */
/* ------------------------------------------------------------------ */

private const val QR_MODULES = 25

private val qrCells: List<Pair<Int, Int>> by lazy {
    var seed = 20261023L
    fun random(): Double {
        seed = (seed * 16807L) % 2147483647L
        return seed.toDouble() / 2147483647.0
    }

    fun inFinder(x: Int, y: Int): Boolean =
        (x < 8 && y < 8) ||
            (x >= QR_MODULES - 8 && y < 8) ||
            (x < 8 && y >= QR_MODULES - 8)

    buildList {
        for (y in 0 until QR_MODULES) {
            for (x in 0 until QR_MODULES) {
                if (inFinder(x, y)) continue
                if (random() > 0.5) add(x to y)
            }
        }
    }
}

@Composable
fun DecorativeQrCode(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "客服二维码" }) {
        val cell = size.minDimension / QR_MODULES
        val ink = Color(0xFF14161C)

        qrCells.forEach { (x, y) ->
            drawRect(
                color = ink,
                topLeft = Offset(x * cell, y * cell),
                size = Size(cell, cell),
            )
        }

        fun finder(offsetX: Int, offsetY: Int) {
            drawRect(
                color = ink,
                topLeft = Offset(offsetX * cell, offsetY * cell),
                size = Size(7 * cell, 7 * cell),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset((offsetX + 1) * cell, (offsetY + 1) * cell),
                size = Size(5 * cell, 5 * cell),
            )
            drawRect(
                color = ink,
                topLeft = Offset((offsetX + 2) * cell, (offsetY + 2) * cell),
                size = Size(3 * cell, 3 * cell),
            )
        }

        finder(0, 0)
        finder(QR_MODULES - 7, 0)
        finder(0, QR_MODULES - 7)
    }
}
