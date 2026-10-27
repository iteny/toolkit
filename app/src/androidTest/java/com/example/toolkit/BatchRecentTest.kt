package com.example.toolkit

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.toolkit.ui.tools.ConvertFormats
import com.example.toolkit.ui.tools.ConvertedFile
import com.example.toolkit.ui.tools.IncomingShare
import com.example.toolkit.ui.tools.PickedFile
import com.example.toolkit.ui.tools.clearRecentConversions
import com.example.toolkit.ui.tools.fileConvertCommonTargets
import com.example.toolkit.ui.tools.fileConvertReadPicked
import com.example.toolkit.ui.tools.loadRecentConversions
import com.example.toolkit.ui.tools.recordRecentConversion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BatchRecentTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearRecentConversions(context)
    }

    @After
    fun tearDown() {
        clearRecentConversions(context)
    }

    private fun picked(name: String, mime: String): PickedFile {
        val file = File(context.cacheDir, name)
        file.writeBytes(byteArrayOf(1))
        return PickedFile(
            uri = Uri.fromFile(file),
            name = name,
            size = 1,
            mime = mime,
            format = ConvertFormats.detect(name, mime),
        )
    }

    @Test
    fun commonTargetsForMixedInputs() {
        val images = listOf(
            picked("one.jpg", "image/jpeg"),
            picked("two.png", "image/png"),
        )
        val imageTargets = fileConvertCommonTargets(images).map { it.id }
        assertTrue("jpg" in imageTargets)
        assertTrue("png" in imageTargets)
        assertTrue("pdf" in imageTargets)

        val archives = listOf(
            picked("one.zip", "application/zip"),
            picked("two.7z", "application/x-7z-compressed"),
        )
        assertEquals(
            listOf("extract", "extract_selected", "zip_enc", "zip_split", "tar", "tgz"),
            fileConvertCommonTargets(archives).map { it.id },
        )

        val incompatible = listOf(
            picked("three.txt", "text/plain"),
            picked("four.pdf", "application/pdf"),
        )
        assertTrue(fileConvertCommonTargets(incompatible).isEmpty())
    }

    @Test
    fun incomingShareExtractsSingleAndMultipleUris() {
        val first = File(context.cacheDir, "incoming-a.txt").apply { writeText("a") }
        val second = File(context.cacheDir, "incoming-b.txt").apply { writeText("b") }
        val single = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(first))
        }
        IncomingShare.accept(context, single)
        val singleBatch = IncomingShare.consume()
        assertEquals(listOf(Uri.fromFile(first)), singleBatch?.uris)
        assertEquals("text/plain", singleBatch?.mime)

        val multiple = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(Uri.fromFile(first), Uri.fromFile(second)),
            )
        }
        IncomingShare.accept(context, multiple)
        assertEquals(
            listOf(Uri.fromFile(first), Uri.fromFile(second)),
            IncomingShare.consume()?.uris,
        )
    }

    @Test
    fun incomingShareAcceptsUriInDataAndMimeHintCoversMissingMetadata() {
        val file = File(context.cacheDir, "incoming-data.png").apply { writeText("x") }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            data = Uri.fromFile(file)
        }
        IncomingShare.accept(context, intent)
        assertEquals(listOf(Uri.fromFile(file)), IncomingShare.consume()?.uris)

        // A provider that cannot answer metadata queries still yields a usable
        // format thanks to the intent MIME hint and the synthesized extension.
        val picked = fileConvertReadPicked(
            context,
            Uri.parse("content://com.example.missing/document/1"),
            "image/png",
        )
        assertEquals("image/png", picked.mime)
        assertEquals(ConvertFormats.PNG, picked.format)
    }

    @Test
    fun recentConversionPersistsAndCreatesShareUri() {
        val outputs = listOf(
            ConvertedFile("hello.txt", "text/plain", "hello".toByteArray()),
            ConvertedFile("notes.md", "text/markdown", "world".toByteArray()),
        )
        val entry = recordRecentConversion(context, outputs)
        assertNotNull(entry)
        assertEquals(2, entry!!.files.size)
        assertTrue(entry.files.all { File(it.path).exists() })

        val loaded = loadRecentConversions(context)
        assertEquals(1, loaded.size)
        assertEquals("hello.txt 等 2 个文件", loaded.first().title)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(entry.files.first().path),
        )
        assertEquals("content", uri.scheme)

        clearRecentConversions(context)
        assertTrue(loadRecentConversions(context).isEmpty())
        assertFalse(File(entry.files.first().path).exists())
    }
}
