package com.example.toolkit.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The prototype stores every icon as inline SVG. These are the same drawables
 * expressed as path data so they can be rendered crisply at any size.
 */
sealed interface VShape {
    data class VPath(val d: String) : VShape
    data class VCircle(val cx: Float, val cy: Float, val r: Float) : VShape
    data class VRect(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val rx: Float = 0f,
    ) : VShape

    data class VEllipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : VShape
}

private fun p(d: String) = VShape.VPath(d)
private fun c(cx: Float, cy: Float, r: Float) = VShape.VCircle(cx, cy, r)
private fun r(x: Float, y: Float, w: Float, h: Float, rx: Float = 0f) =
    VShape.VRect(x, y, w, h, rx)

private fun e(cx: Float, cy: Float, rx: Float, ry: Float) = VShape.VEllipse(cx, cy, rx, ry)

private val sharedCalc = listOf(
    r(5f, 3.4f, 14f, 17.2f, 2.8f),
    p("M8.4 7.6h7.2M8.6 12h.01M12 12h.01M15.4 12h.01M8.6 16.2h.01M12 16.2h.01M15.4 16.2h.01"),
)

private val sharedAudio = listOf(
    p("M4.4 10.4v3.2M8.2 7.4v9.2M12 4.8v14.4M15.8 8.6v6.8M19.6 10.8v2.4"),
)

private val sharedVideo = listOf(
    r(3.4f, 5.4f, 17.2f, 13.2f, 3f),
    p("M10.4 9.6l4.4 2.4-4.4 2.4V9.6Z"),
)

val ToolGlyphs: Map<String, List<VShape>> = mapOf(
    /* ---------- category icons ---------- */
    "catDaily" to listOf(
        c(12f, 12f, 3.8f),
        p("M12 2.8v2.2M12 19v2.2M2.8 12h2.2M19 12h2.2M5.6 5.6l1.6 1.6M16.8 16.8l1.6 1.6M18.4 5.6l-1.6 1.6M7.2 16.8l-1.6 1.6"),
    ),
    "catQuery" to listOf(c(10.6f, 10.6f, 6.2f), p("M15.2 15.2 20 20")),
    "catText" to listOf(p("M4.4 7.2h15.2M4.4 12h10.4M4.4 16.8h13.6")),
    "catConvert" to listOf(p("M4 8.4h12.6l-3.2-3.2M20 15.6H7.4l3.2 3.2")),
    "catCalc" to sharedCalc,
    "catImage" to listOf(
        r(3.6f, 5.2f, 16.8f, 13.6f, 2.6f),
        p("M3.6 15.4l4.4-4.4 3.8 3.8 2.8-2.8 5 5"),
    ),
    "catVideo" to sharedVideo,
    "catAudio" to sharedAudio,
    "catCrypto" to listOf(
        r(4.8f, 10.4f, 14.4f, 9.6f, 2.8f),
        p("M8.4 10.4V8.2a3.6 3.6 0 0 1 7.2 0v2.2"),
        p("M12 14.4v2"),
    ),
    "catMore" to listOf(p("M6.4 12h.01M12 12h.01M17.6 12h.01")),

    /* ---------- tool icons ---------- */
    "compress" to listOf(
        r(3.6f, 4.6f, 16.8f, 14.8f, 2.6f),
        p("M9.4 9.2 12 11.8l2.6-2.6M12 11.8v4.6"),
    ),
    "convert" to listOf(p("M4 8.4h12.6l-3.2-3.2M20 15.6H7.4l3.2 3.2")),
    "crop" to listOf(p("M6.4 2.8v14.8h14.8"), p("M2.8 6.4h14.8v14.8")),
    "person" to listOf(
        r(4.2f, 3.4f, 15.6f, 17.2f, 2.6f),
        c(12f, 10.2f, 2.5f),
        p("M7.8 17.8c.6-2.2 2.2-3.4 4.2-3.4s3.6 1.2 4.2 3.4"),
    ),
    "ocr" to listOf(
        p("M6.4 3.6h7.2l4 4v12.2a1.6 1.6 0 0 1-1.6 1.6H6.4a1.6 1.6 0 0 1-1.6-1.6V5.2a1.6 1.6 0 0 1 1.6-1.6Z"),
        p("M13.4 3.6v4.2h4.2M8.4 13h7.2M8.4 16.6h4.6"),
    ),
    "hash" to listOf(p("M9.4 3.8 7.8 20.2M16.2 3.8l-1.6 16.4M4.4 8.6h15.2M4 15.4h15.2")),
    "letter" to listOf(p("M4.8 18 10 5.8l5.2 12.2M6.9 13.6h6.2")),
    "translate" to listOf(
        p("M3.6 5.6h9M8.1 3.4v2.2M10.4 5.6c0 3.7-2.5 6.8-6.6 8.2M5.5 9.4c1.2 2.5 3 4.1 5.6 4.9"),
        p("M12.8 20.4l3.6-9.2 3.6 9.2M14.3 17.1h4.4"),
    ),
    "calc" to sharedCalc,
    "currency" to listOf(
        c(12f, 12f, 8.4f),
        p("M9.2 7.8l2.8 3.6 2.8-3.6M12 11.4v5M9.8 12.9h4.4M9.8 15h4.4"),
    ),
    "home" to listOf(
        p("M3.8 10.2 12 3.6l8.2 6.6v9.2a1.6 1.6 0 0 1-1.6 1.6h-4.3v-6.2H9.7v6.2H5.4a1.6 1.6 0 0 1-1.6-1.6v-9.2Z"),
    ),
    "qr" to listOf(
        r(3.6f, 3.6f, 6.2f, 6.2f, 1.4f),
        r(14.2f, 3.6f, 6.2f, 6.2f, 1.4f),
        r(3.6f, 14.2f, 6.2f, 6.2f, 1.4f),
        p("M14.2 14.2h2.6v2.6M20.4 14.2v6.2h-6.2"),
    ),
    "json" to listOf(
        p("M9.8 3.8c-2.1 0-2.6 1.1-2.6 2.9v1.8c0 1.6-.8 2.6-2.4 3.1 1.6.5 2.4 1.5 2.4 3.1v2c0 1.8.5 2.9 2.6 2.9"),
        p("M14.2 3.8c2.1 0 2.6 1.1 2.6 2.9v1.8c0 1.6.8 2.6 2.4 3.1-1.6.5-2.4 1.5-2.4 3.1v2c0 1.8-.5 2.9-2.6 2.9"),
    ),
    "code" to listOf(p("M9.2 7.6 5 12l4.2 4.4M14.8 7.6 19 12l-4.2 4.4")),
    "lock" to listOf(
        r(4.8f, 10.4f, 14.4f, 9.6f, 2.8f),
        p("M8.4 10.4V8.2a3.6 3.6 0 0 1 7.2 0v2.2"),
        p("M12 14.4v2"),
    ),
    "palette" to listOf(
        p("M12 3.6a8.4 8.4 0 1 0 0 16.8c1.3 0 1.9-.9 1.9-1.9 0-1.5-1-1.9-1-3.1 0-1.1.9-1.9 2-1.9h1.5a3.9 3.9 0 0 0 3.9-3.9c0-3.1-3.8-5.7-8.3-5.7Z"),
        p("M8 9.6h.01M11.6 7.2h.01M7.4 14h.01"),
    ),
    "swatch" to listOf(
        r(3.6f, 4.6f, 7f, 14.8f, 2.2f),
        r(13.4f, 4.6f, 7f, 14.8f, 2.2f),
        p("M13.4 11.4h7"),
    ),
    "contrast" to listOf(c(12f, 12f, 8.4f), p("M12 3.6v16.8")),
    "dropper" to listOf(
        p("M12 3.4c2.6 3.1 4.9 6 4.9 8.6a4.9 4.9 0 0 1-9.8 0c0-2.6 2.3-5.5 4.9-8.6Z"),
        p("M10.3 13.3a1.7 1.7 0 0 0 1.7 1.7"),
    ),
    "clock" to listOf(c(12f, 12f, 8.4f), p("M12 7.4V12l3.2 2.1")),
    "calendar" to listOf(
        r(3.8f, 5.6f, 16.4f, 14.8f, 2.8f),
        p("M3.8 10.4h16.4M8.2 3.4v4M15.8 3.4v4"),
    ),
    "globe" to listOf(
        c(12f, 12f, 7.6f),
        p("M4.4 12h15.2M12 4.4c1.9 2 3 4.9 3 7.6s-1.1 5.6-3 7.6c-1.9-2-3-4.9-3-7.6s1.1-5.6 3-7.6Z"),
    ),
    "timer" to listOf(
        c(12f, 13.2f, 7.2f),
        p("M12 9.8v3.4l2.4 1.6M9.4 3.4h5.2"),
    ),
    "dice" to listOf(
        r(4f, 4f, 16f, 16f, 4.2f),
        p("M9 9h.01M15 15h.01M15 9h.01M9 15h.01"),
    ),
    "shield" to listOf(
        p("M12 3.6 5.2 6.4v5c0 3.8 2.8 6.8 6.8 8.5 4-1.7 6.8-4.7 6.8-8.5v-5L12 3.6Z"),
        p("M9.4 11.8l1.9 1.9 3.4-3.4"),
    ),
    "gauge" to listOf(
        p("M4 17.4a8.2 8.2 0 1 1 16 0"),
        p("M12 17.4l4.4-5.4"),
        p("M4 17.4h2.2M17.8 17.4H20"),
    ),
    "server" to listOf(
        r(3.6f, 3.8f, 16.8f, 6.8f, 2.2f),
        r(3.6f, 13.4f, 16.8f, 6.8f, 2.2f),
        p("M7 7.2h.01M7 16.8h.01"),
    ),
    "plug" to listOf(
        p("M9 3.6v4M15 3.6v4"),
        p("M6 7.6h12v3.8a6 6 0 0 1-12 0V7.6Z"),
        p("M12 17.4v3"),
    ),

    /* ---------- exclusive icons ---------- */
    "ai" to listOf(
        p("M12 3.4l1.9 5.1 5.1 1.9-5.1 1.9L12 17.4l-1.9-5.1L5 10.4l5.1-1.9L12 3.4Z"),
        p("M18.4 15.2l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8.8-2.2Z"),
    ),
    "eraser" to listOf(
        p("M9 20.4H20.4"),
        p("M3.8 16.6l9.4-9.4a2 2 0 0 1 2.8 0l1.6 1.6a2 2 0 0 1 0 2.8l-8.2 8.2H5a1.2 1.2 0 0 1-1.2-1.2v-2Z"),
    ),
    "video" to sharedVideo,
    "rename" to listOf(
        p("M3.6 12h9.6"),
        p("M10.6 8.6 14 12l-3.4 3.4"),
        p("M16.6 4.4h2.6a1.2 1.2 0 0 1 1.2 1.2v12.8a1.2 1.2 0 0 1-1.2 1.2h-2.6"),
    ),
    "pdf" to listOf(
        p("M6.4 3.6h7.2l4 4v12.2a1.6 1.6 0 0 1-1.6 1.6H6.4a1.6 1.6 0 0 1-1.6-1.6V5.2a1.6 1.6 0 0 1 1.6-1.6Z"),
        p("M13.4 3.6v4.2h4.2"),
        p("M8.6 17.4v-4.2h1.5a1.2 1.2 0 0 1 0 2.4H8.6"),
    ),
    "audio" to sharedAudio,
    "filter" to listOf(
        p("M3.8 5.4h16.4l-6.2 7.4v5.6l-4 2.2v-7.8L3.8 5.4Z"),
    ),
    "table" to listOf(
        r(3.6f, 4.6f, 16.8f, 14.8f, 2.6f),
        p("M3.6 9.6h16.8M3.6 14.6h16.8M9.6 9.6v9.8"),
    ),

    /* ---------- chrome icons ---------- */
    "bookmark" to listOf(
        p("M6.4 3.6h11.2a1 1 0 0 1 1 1v15.6L12 15.6l-6.6 4.6V4.6a1 1 0 0 1 1-1Z"),
    ),
    "chevron" to listOf(p("M9.5 5.5 16 12l-6.5 6.5")),
    "back" to listOf(p("M14.5 5.5 8 12l6.5 6.5")),
    "search" to listOf(c(10.6f, 10.6f, 6.1f), p("M15.2 15.2 20 20")),
    "close" to listOf(p("M6.5 6.5 17.5 17.5M17.5 6.5 6.5 17.5")),
    "avatar" to listOf(
        c(12f, 8.6f, 3.5f),
        p("M4.9 20c.9-3.7 3.6-5.8 7.1-5.8s6.2 2.1 7.1 5.8"),
    ),
    "grid" to listOf(
        r(3.8f, 3.8f, 6.8f, 6.8f, 1.8f),
        r(13.4f, 3.8f, 6.8f, 6.8f, 1.8f),
        r(3.8f, 13.4f, 6.8f, 6.8f, 1.8f),
        r(13.4f, 13.4f, 6.8f, 6.8f, 1.8f),
    ),
)

private fun VShape.toPath(): Path = when (this) {
    is VShape.VPath -> PathParser().parsePathString(d).toPath()

    is VShape.VCircle -> Path().apply {
        addOval(Rect(cx - r, cy - r, cx + r, cy + r))
    }

    is VShape.VRect -> Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(x, y, x + w, y + h),
                cornerRadius = CornerRadius(rx),
            ),
        )
    }

    is VShape.VEllipse -> Path().apply {
        addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry))
    }
}

private fun DrawScope.drawGlyphPaths(
    paths: List<Path>,
    color: Color,
    strokeWidth: Float,
    fillColor: Color?,
) {
    val side = size.minDimension
    if (side <= 0f) return
    val factor = side / 24f
    translate((size.width - side) / 2f, (size.height - side) / 2f) {
        scale(factor, factor, pivot = Offset.Zero) {
            paths.forEach { path ->
                if (fillColor != null) drawPath(path, fillColor)
                if (strokeWidth > 0f) {
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun GlyphIcon(
    key: String,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 1.7f,
    fillColor: Color? = null,
) {
    val shapes = ToolGlyphs[key] ?: return
    val paths = remember(shapes) { shapes.map(VShape::toPath) }
    Canvas(modifier) {
        drawGlyphPaths(paths, color, strokeWidth, fillColor)
    }
}

/** The little hippo brand mark. */
@Composable
fun HippoLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = size.minDimension
        val factor = side / 24f
        translate((size.width - side) / 2f, (size.height - side) / 2f) {
            scale(factor, factor, pivot = Offset.Zero) {
                drawCircle(Color(0xFF14161C), 2.3f, Offset(6.5f, 7.9f))
                drawCircle(Color(0xFF14161C), 2.3f, Offset(17.5f, 7.9f))
                drawOval(
                    color = Color(0xFF14161C),
                    topLeft = Offset(4.3f, 7.4f),
                    size = androidx.compose.ui.geometry.Size(15.4f, 13.6f),
                )
                drawCircle(Color.White, 1f, Offset(9.5f, 12.9f))
                drawCircle(Color.White, 1f, Offset(14.5f, 12.9f))
                drawOval(
                    color = Color.White,
                    topLeft = Offset(9.35f, 16.95f),
                    size = androidx.compose.ui.geometry.Size(1.9f, 1.5f),
                )
                drawOval(
                    color = Color.White,
                    topLeft = Offset(12.75f, 16.95f),
                    size = androidx.compose.ui.geometry.Size(1.9f, 1.5f),
                )
            }
        }
    }
}

/** Filled WeChat bubbles used by the login button. */
@Composable
fun WeChatLogo(
    modifier: Modifier = Modifier,
    bubbleColor: Color = Color.White,
    eyeColor: Color = Color(0xFF07C160),
) {
    val bubbles = remember {
        listOf(
            p("M9.3 3.2C5.2 3.2 1.9 6 1.9 9.4c0 1.9 1 3.6 2.7 4.7l-.7 2.2 2.5-1.3c.8.2 1.6.3 2.4.3h.6a5.6 5.6 0 0 1-.2-1.5c0-3.3 3.1-5.9 7-5.9h.5c-.7-2.6-3.6-4.7-7.4-4.7Z"),
            p("M21.9 13.7c0-2.7-2.6-4.9-5.9-4.9s-5.9 2.2-5.9 4.9 2.6 4.9 5.9 4.9c.7 0 1.4-.1 2-.3l2.1 1.1-.5-1.9c1.4-.9 2.3-2.3 2.3-3.8Z"),
        ).map(VShape::toPath)
    }
    Canvas(modifier) {
        val side = size.minDimension
        val factor = side / 24f
        translate((size.width - side) / 2f, (size.height - side) / 2f) {
            scale(factor, factor, pivot = Offset.Zero) {
                bubbles.forEach { drawPath(it, bubbleColor) }
                drawCircle(eyeColor, 0.95f, Offset(6.9f, 8.1f))
                drawCircle(eyeColor, 0.95f, Offset(11.7f, 8.1f))
                drawCircle(eyeColor, 0.8f, Offset(14.4f, 12.6f))
                drawCircle(eyeColor, 0.8f, Offset(18.1f, 12.6f))
            }
        }
    }
}
