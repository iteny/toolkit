package com.example.toolkit.ui.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Decode a picked image, downscaling it so the editor stays memory friendly. */
internal suspend fun decodeImage(
    context: Context,
    uri: Uri,
    maxDimension: Int = 1600,
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(context, uri, maxDimension)
        } else {
            decodeLegacy(context, uri, maxDimension)
        }
    }.getOrNull()
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeWithImageDecoder(
    context: Context,
    uri: Uri,
    maxDimension: Int,
): Bitmap? {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val width = info.size.width
        val height = info.size.height
        val largest = max(width, height)
        if (largest > maxDimension && largest > 0) {
            val scale = maxDimension.toFloat() / largest
            decoder.setTargetSize(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1),
            )
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
    }
}

private fun decodeLegacy(
    context: Context,
    uri: Uri,
    maxDimension: Int,
): Bitmap? {
    val resolver = context.contentResolver

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    var bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null

    val orientation = resolver.openInputStream(uri)?.use { input ->
        runCatching {
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL

    if (orientation != ExifInterface.ORIENTATION_NORMAL) {
        bitmap = applyExifOrientation(bitmap, orientation)
    }

    if (max(bitmap.width, bitmap.height) > maxDimension) {
        val scale = maxDimension.toFloat() / max(bitmap.width, bitmap.height)
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        bitmap = scaled
    }
    return bitmap
}

private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sample = 1
    val largest = max(width, height)
    while (largest / sample > maxDimension) {
        sample *= 2
    }
    return sample
}

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    val width = bitmap.width.toFloat()
    val height = bitmap.height.toFloat()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
            matrix.setScale(-1f, 1f, width / 2f, height / 2f)

        ExifInterface.ORIENTATION_ROTATE_180 ->
            matrix.setRotate(180f, width / 2f, height / 2f)

        ExifInterface.ORIENTATION_FLIP_VERTICAL ->
            matrix.setScale(1f, -1f, width / 2f, height / 2f)

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f, width / 2f, height / 2f)
            matrix.postScale(-1f, 1f, height / 2f, width / 2f)
        }

        ExifInterface.ORIENTATION_ROTATE_90 ->
            matrix.setRotate(90f, width / 2f, height / 2f)

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f, width / 2f, height / 2f)
            matrix.postScale(-1f, 1f, height / 2f, width / 2f)
        }

        ExifInterface.ORIENTATION_ROTATE_270 ->
            matrix.setRotate(-90f, width / 2f, height / 2f)

        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * Average colours into a tiny bitmap, then scale it back up without filtering.
 * The result is the classic hard-edged mosaic effect.
 */
internal fun buildMosaic(source: Bitmap, blockSize: Int): Bitmap {
    val block = blockSize.coerceIn(2, 256)
    val smallWidth = (source.width / block).coerceAtLeast(1)
    val smallHeight = (source.height / block).coerceAtLeast(1)

    val small = if (smallWidth == source.width && smallHeight == source.height) {
        source
    } else {
        Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
    }

    val result = Bitmap.createScaledBitmap(small, source.width, source.height, false)
    if (small !== source && small !== result) small.recycle()
    return result
}

internal fun imageFileName(prefix: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "${prefix}_$stamp.jpg"
}

/** One-tap save into Pictures/河马工具箱 (Android 10+). */
internal suspend fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "${Environment.DIRECTORY_PICTURES}/河马工具箱",
        )
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    var uri: Uri? = null
    try {
        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        val written = resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        } ?: false
        if (!written) {
            resolver.delete(uri, null, null)
            return@withContext false
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: Throwable) {
        uri?.let { runCatching { resolver.delete(it, null, null) } }
        false
    }
}

/** SAF fallback for Android 9 and lower. */
internal suspend fun saveBitmapToUri(
    context: Context,
    bitmap: Bitmap,
    uri: Uri,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        } ?: false
    }.getOrDefault(false)
}

/** One-tap save of raw bytes (e.g. GIF) into Pictures/河马工具箱. */
internal suspend fun saveBytesToGallery(
    context: Context,
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "${Environment.DIRECTORY_PICTURES}/河马工具箱",
        )
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    var uri: Uri? = null
    try {
        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: return@withContext false
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: Throwable) {
        uri?.let { runCatching { resolver.delete(it, null, null) } }
        false
    }
}

internal suspend fun saveBytesToUri(
    context: Context,
    bytes: ByteArray,
    uri: Uri,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
    }.getOrDefault(false)
}

internal suspend fun savePngToGallery(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "${Environment.DIRECTORY_PICTURES}/河马工具箱",
        )
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    var uri: Uri? = null
    try {
        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        val written = resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: false
        if (!written) {
            resolver.delete(uri, null, null)
            return@withContext false
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: Throwable) {
        uri?.let { runCatching { resolver.delete(it, null, null) } }
        false
    }
}

internal suspend fun savePngToUri(
    context: Context,
    bitmap: Bitmap,
    uri: Uri,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: false
    }.getOrDefault(false)
}
