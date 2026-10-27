package com.example.toolkit.ui.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat

/**
 * A tiny bridge between the Activity intent and the file conversion tool.
 * MainActivity feeds shared files in; FileConvertTool consumes them when shown.
 */
object IncomingShare {
    data class Batch(
        val uris: List<Uri>,
        val mime: String?,
    )

    var version by mutableIntStateOf(0)
        private set

    var batch by mutableStateOf<Batch?>(null)
        private set

    val uris: List<Uri>
        get() = batch?.uris.orEmpty()

    fun accept(context: Context, intent: Intent?) {
        if (intent == null) return
        val incoming = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
                intent.data,
            )

            Intent.ACTION_SEND_MULTIPLE -> {
                val list = IntentCompat.getParcelableArrayListExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java,
                ).orEmpty()
                list
            }

            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }.toMutableList()

        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let { incoming += it }
            }
        }

        val unique = incoming.distinct().take(20)
        if (unique.isEmpty()) return
        unique.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        batch = Batch(unique, intent.type?.takeIf { it.isNotBlank() && it != "*/*" })
        version += 1
    }

    fun consume(): Batch? {
        val result = batch
        batch = null
        return result
    }
}
