package com.example.toolkit.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.toolkit.ui.theme.GlassShadow

/** Same curve repeated throughout the prototype's CSS. */
val EmphasizedEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** Symmetric ease-in-out used by the drifting background blobs. */
val FloatEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * Makes a full-screen overlay a hit target so taps on its empty background
 * cannot fall through to the page behind it.
 */
fun Modifier.blockTaps(): Modifier = pointerInput(Unit) {
    detectTapGestures(onTap = {})
}

private val GlassTop = Color.White.copy(alpha = 0.68f)
private val GlassMiddle = Color.White.copy(alpha = 0.40f)
private val GlassBottom = Color.White.copy(alpha = 0.52f)

/**
 * The liquid-glass surface from the design: a soft translucent gradient,
 * an inner highlight, a faint prismatic rim and a slow moving sheen.
 */
@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp,
    shimmer: Boolean = true,
    shadowElevation: Dp = 12.dp,
    shadowColor: Color = GlassShadow,
    frost: Float = 0f,
): Modifier {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    val topAlpha = (GlassTop.alpha + frost).coerceAtMost(1f)
    val middleAlpha = (GlassMiddle.alpha + frost).coerceAtMost(1f)
    val bottomAlpha = (GlassBottom.alpha + frost).coerceAtMost(1f)

    val shimmerState = if (shimmer) {
        val transition = rememberInfiniteTransition(label = "glass")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "glassSheen",
        )
    } else {
        null
    }

    val radiusPx = with(density) { cornerRadius.toPx() }
    val borderPx = with(density) { 1.dp.toPx() }
    val topHighlightPx = with(density) { 1.5.dp.toPx() }

    return this
        .drawBehind {
            // A hand-rolled soft shadow. Modifier.shadow darkens the interior of
            // translucent surfaces, which the design's frosted glass must avoid.
            if (shadowElevation > 0.dp) {
                val w = size.width
                val h = size.height
                val elevationPx = shadowElevation.toPx()
                val spread = elevationPx * 2.3f
                val dy = elevationPx * 0.32f
                val steps = 12
                val inner = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, 0f, w, h),
                            cornerRadius = CornerRadius(radiusPx),
                        ),
                    )
                }
                for (i in steps downTo 1) {
                    val grow = spread * i / steps
                    val outer = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(
                                    -grow,
                                    -grow + dy,
                                    w + grow,
                                    h + grow + dy,
                                ),
                                cornerRadius = CornerRadius(radiusPx + grow),
                            ),
                        )
                    }
                    val ring = Path().apply {
                        op(outer, inner, PathOperation.Difference)
                    }
                    val falloff = 1f - ((i - 1).toFloat() / steps) * 0.72f
                    drawPath(
                        path = ring,
                        color = shadowColor.copy(
                            alpha = shadowColor.alpha * 0.30f / steps * falloff,
                        ),
                    )
                }
            }
        }
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = topAlpha),
                0.52f to Color.White.copy(alpha = middleAlpha),
                1f to Color.White.copy(alpha = bottomAlpha),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
            shape = shape,
        )
        .drawBehind {
            val w = size.width
            val h = size.height

            if (shimmer) {
                val progress = shimmerState?.value ?: 0f
                val startX = -w * 1.15f + w * 1.85f * progress
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.24f to Color.Transparent,
                        0.42f to Color.White.copy(alpha = 0.55f),
                        0.50f to Color.White.copy(alpha = 0.12f),
                        0.68f to Color.Transparent,
                        1f to Color.Transparent,
                        start = Offset(startX, -h * 0.2f),
                        end = Offset(startX + w * 1.7f, h * 1.2f),
                    ),
                    size = Size(w, h),
                )
            }

            // Inset highlights along the top and bottom edges.
            drawLine(
                color = Color.White.copy(alpha = 0.98f),
                start = Offset(0.8f, topHighlightPx / 2f),
                end = Offset(w - 0.8f, topHighlightPx / 2f),
                strokeWidth = topHighlightPx,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.50f),
                start = Offset(2f, h - topHighlightPx / 2f),
                end = Offset(w - 2f, h - topHighlightPx / 2f),
                strokeWidth = topHighlightPx,
                cap = StrokeCap.Round,
            )

            // Subtle colour dispersion along the border.
            drawRoundRect(
                brush = Brush.sweepGradient(
                    0f to Color.White.copy(alpha = 0.95f),
                    0.22f to Color(0xFFAFD7FF).copy(alpha = 0.55f),
                    0.44f to Color(0xFFFFBEF0).copy(alpha = 0.50f),
                    0.66f to Color(0xFFBEFEEE).copy(alpha = 0.55f),
                    0.88f to Color.White.copy(alpha = 0.85f),
                    1f to Color.White.copy(alpha = 0.95f),
                ),
                topLeft = Offset(borderPx / 2f, borderPx / 2f),
                size = Size(w - borderPx, h - borderPx),
                cornerRadius = CornerRadius((radiusPx - borderPx / 2f).coerceAtLeast(0f)),
                style = Stroke(width = borderPx),
            )
        }
}

/** The milky fade behind the status bar. */
@Composable
fun TopFade(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(70.dp)
            .blockTaps()
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.72f),
                    0.54f to Color.White.copy(alpha = 0.28f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

private data class BlobSpec(
    val color: Color,
    val sizeDp: Float,
    val leftDp: Float,
    val topDp: Float,
    val deltaXDp: Float,
    val deltaYDp: Float,
    val startScale: Float,
    val midScale: Float,
)

/**
 * Full screen moving pastel blobs behind the glass. A radial gradient fades
 * each circle out, giving the same soft, out-of-focus look as the CSS blur.
 */
@Composable
fun FlowingBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "background")

    @Composable
    fun progress(durationMillis: Int, label: String) = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FloatEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = label,
    )

    val p1 = progress(9000, "blob1")
    val p2 = progress(11000, "blob2")
    val p3 = progress(10000, "blob3")
    val p4 = progress(12500, "blob4")
    val p5 = progress(13500, "blob5")

    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    0f to Color(0xFFF8FBFF),
                    0.44f to Color(0xFFFDF5FB),
                    1f to Color(0xFFF2FBFA),
                    start = Offset(size.width, 0f),
                    end = Offset(0f, size.height),
                ),
            )

            fun drawBlob(spec: BlobSpec, progress: Float) {
                val sizePx = with(density) { spec.sizeDp.dp.toPx() }
                val leftPx = with(density) { spec.leftDp.dp.toPx() }
                val topPx = with(density) { spec.topDp.dp.toPx() }
                val dxPx = with(density) { spec.deltaXDp.dp.toPx() }
                val dyPx = with(density) { spec.deltaYDp.dp.toPx() }

                val scale = spec.startScale + (spec.midScale - spec.startScale) * progress
                val center = Offset(
                    leftPx + sizePx / 2f + dxPx * progress,
                    topPx + sizePx / 2f + dyPx * progress,
                )
                val radius = sizePx / 2f * scale

                drawCircle(
                    brush = Brush.radialGradient(
                        0f to spec.color.copy(alpha = 0.88f),
                        0.42f to spec.color.copy(alpha = 0.56f),
                        0.74f to spec.color.copy(alpha = 0.20f),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }

            // Positions mirror the 390 x 844 reference device, using its raw
            // top/left offsets so the composition keeps the same balance.
            val perDp = density.density
            val b1 = BlobSpec(Color(0xFFA6C6FF), 300f, -70f, -60f, 34f, 42f, 1f, 1.12f)
            val b2 = BlobSpec(Color(0xFFD8B6FF), 290f, widthPx / perDp - 200f, 40f, -42f, 28f, 1f, 1.08f)
            val b3 = BlobSpec(Color(0xFFFFBDDB), 330f, -110f, heightPx / perDp - 440f, 28f, -36f, 1.06f, 0.98f)
            val b4 = BlobSpec(Color(0xFFA4EEE3), 270f, widthPx / perDp - 200f, heightPx / perDp - 220f, 34f, 42f, 1f, 1.12f)
            val b5 = BlobSpec(Color(0xFFFFE0A6), 230f, 80f, 290f, -42f, 28f, 1f, 1.08f)

            drawBlob(b1, p1.value)
            drawBlob(b2, p2.value)
            drawBlob(b3, p3.value)
            drawBlob(b4, p4.value)
            drawBlob(b5, p5.value)
        }
    }
}

/** Soft white veil used for the detail page when real backdrop blur is absent. */
@Composable
fun DetailBackdrop(modifier: Modifier = Modifier) {
    val alpha = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        0.82f
    } else {
        0.97f
    }
    Box(
        modifier.background(
            Brush.verticalGradient(
                0f to Color(0xFFF8FBFF).copy(alpha = alpha),
                0.44f to Color(0xFFFDF5FB).copy(alpha = alpha),
                1f to Color(0xFFF2FBFA).copy(alpha = alpha),
            ),
        ),
    )
}
