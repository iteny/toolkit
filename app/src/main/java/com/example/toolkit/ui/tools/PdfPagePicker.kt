package com.example.toolkit.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.theme.AppleBlue
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.max
import kotlin.math.roundToInt

/* ------------------------------------------------------------------ */
/* PDF page thumbnails                                                */
/* ------------------------------------------------------------------ */

private const val THUMBNAIL_WIDTH_PX = 220
private const val THUMBNAIL_CACHE_ENTRIES = 40

private object PdfThumbnailCache {
    private val cache = android.util.LruCache<String, Bitmap>(THUMBNAIL_CACHE_ENTRIES)

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

@Composable
internal fun rememberPdfThumbnail(
    loader: PdfPageThumbnailLoader,
    uri: Uri,
    pageIndex: Int,
): Bitmap? {
    val key = "$uri#$pageIndex"
    var bitmap by remember(key) { mutableStateOf(PdfThumbnailCache.get(key)) }

    LaunchedEffect(key) {
        if (bitmap == null) {
            val rendered = loader.render(pageIndex)
            if (rendered != null) {
                PdfThumbnailCache.put(key, rendered)
                bitmap = rendered
            }
        }
    }
    return bitmap
}

internal class PdfPageThumbnailLoader(
    private val context: Context,
    private val uri: Uri,
) : Closeable {
    private val mutex = Mutex()
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    suspend fun open(): Int = mutex.withLock {
        renderer?.let { return it.pageCount }
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
        val created = runCatching { PdfRenderer(fd) }.getOrNull()
        if (created == null) {
            runCatching { fd.close() }
            return 0
        }
        descriptor = fd
        renderer = created
        created.pageCount
    }

    suspend fun render(pageIndex: Int, widthPx: Int = THUMBNAIL_WIDTH_PX): Bitmap? =
        mutex.withLock {
            val current = renderer ?: return null
            if (pageIndex < 0 || pageIndex >= current.pageCount) return null
            val page = runCatching { current.openPage(pageIndex) }.getOrNull() ?: return null
            try {
                val ratio = page.height.toFloat() / max(1, page.width)
                val height = max(1, (widthPx * ratio).roundToInt())
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                withContext(Dispatchers.Default) {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
                bitmap
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { page.close() }
            }
        }

    override fun close() {
        kotlinx.coroutines.runBlocking {
            mutex.withLock {
                runCatching { renderer?.close() }
                runCatching { descriptor?.close() }
                renderer = null
                descriptor = null
            }
        }
    }
}

@Composable
internal fun PdfPagePicker(
    uri: Uri,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onSelectAll: (Int) -> Unit,
    onInvert: (Int) -> Unit,
    onClear: () -> Unit,
    gridHeight: Int = 430,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val loader = remember(uri) { PdfPageThumbnailLoader(context, uri) }
    var pageCount by remember(uri) { mutableStateOf(0) }

    DisposableEffect(loader) {
        onDispose { loader.close() }
    }
    LaunchedEffect(loader) {
        pageCount = loader.open()
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "已选 ${selected.size} / 共 $pageCount 页",
                color = Ink3,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            PickerAction("全选") { onSelectAll(pageCount) }
            PickerAction("反选") { onInvert(pageCount) }
            PickerAction("清空") { onClear() }
        }
        Spacer(Modifier.height(7.dp))
        if (pageCount <= 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.60f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "正在读取页面…",
                    color = Ink3,
                    fontSize = 11.sp,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pageCount) { index ->
                    val pageNumber = index + 1
                    PdfPageThumbnail(
                        loader = loader,
                        uri = uri,
                        pageIndex = index,
                        pageNumber = pageNumber,
                        selected = pageNumber in selected,
                        onClick = { onToggle(pageNumber) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerAction(
    text: String,
    onClick: () -> Unit,
) {    Text(
        text = text,
        color = AppleBlue,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun PdfPageThumbnail(
    loader: PdfPageThumbnailLoader,
    uri: Uri,
    pageIndex: Int,
    pageNumber: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bitmap = rememberPdfThumbnail(loader, uri, pageIndex)

    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.72f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AppleBlue else Color.White.copy(alpha = 0.92f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "第 $pageNumber 页",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(7.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFFF2F5FA)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "...",
                    color = Ink3,
                    fontSize = 12.sp,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Text(
                text = pageNumber.toString(),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(AppleBlue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Reorder                                                            */
/* ------------------------------------------------------------------ */

@Composable
internal fun PdfPageReorderList(
    uri: Uri,
    order: List<Int>,
    onOrderChange: (List<Int>) -> Unit,
    listHeight: Int = 430,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val loader = remember(uri) { PdfPageThumbnailLoader(context, uri) }
    var pageCount by remember(uri) { mutableStateOf(0) }

    DisposableEffect(loader) {
        onDispose { loader.close() }
    }
    LaunchedEffect(loader) {
        pageCount = loader.open()
    }
    LaunchedEffect(pageCount, order) {
        if (pageCount > 0) {
            val expected = (1..pageCount).toSet()
            if (order.size != pageCount || order.toSet() != expected) {
                onOrderChange((1..pageCount).toList())
            }
        }
    }

    fun move(from: Int, to: Int) {
        if (from !in order.indices || to !in order.indices || from == to) return
        val updated = order.toMutableList()
        val page = updated.removeAt(from)
        updated.add(to, page)
        onOrderChange(updated)
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "共 $pageCount 页 · 位置 1 将成为 PDF 第一页",
                color = Ink3,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            PickerAction("重置") {
                if (pageCount > 0) onOrderChange((1..pageCount).toList())
            }
        }
        Spacer(Modifier.height(7.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(listHeight.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(order) { index, page ->
                PdfReorderRow(
                    loader = loader,
                    uri = uri,
                    index = index,
                    page = page,
                    pageCount = order.size,
                    onMoveUp = { move(index, index - 1) },
                    onMoveDown = { move(index, index + 1) },
                )
            }
        }
    }
}

@Composable
private fun PdfReorderRow(
    loader: PdfPageThumbnailLoader,
    uri: Uri,
    index: Int,
    page: Int,
    pageCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val bitmap = rememberPdfThumbnail(loader, uri, page - 1)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.64f))
            .border(1.dp, Color.White.copy(alpha = 0.92f), shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF2F5FA)),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "第 $page 页",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = "...",
                    color = Ink3,
                    fontSize = 10.sp,
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "第 $page 页",
                color = Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (page == index + 1) {
                    "位置 ${index + 1}"
                } else {
                    "位置 ${index + 1} · 原第 $page 页"
                },
                color = Ink3,
                fontSize = 9.5.sp,
            )
        }
        ReorderArrow(
            text = "↑",
            enabled = index > 0,
            onClick = onMoveUp,
        )
        Spacer(Modifier.width(4.dp))
        ReorderArrow(
            text = "↓",
            enabled = index < pageCount - 1,
            onClick = onMoveDown,
        )
    }
}

@Composable
private fun ReorderArrow(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.92f else 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) AppleBlue else Ink3.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
