package com.example.toolkit.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.caverock.androidsvg.SVG
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ------------------------------------------------------------------ */
/* Model                                                              */
/* ------------------------------------------------------------------ */

internal enum class ConvertKind(val label: String) {
    Image("图片"),
    Pdf("文档"),
    Text("文本"),
    Data("数据"),
    Office("办公文档"),
    Archive("压缩包"),
    Audio("音频"),
    Video("视频"),
    Ebook("电子书"),
    Vector("矢量图"),
    Font("字体"),
    Subtitle("字幕"),
    Other("文件"),
}

internal data class ConvertFormat(
    val id: String,
    val label: String,
    val mime: String,
    val kind: ConvertKind,
    val extensions: List<String>,
    val note: String,
)

internal data class PickedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String,
    val format: ConvertFormat,
)

internal data class ConvertedFile(
    val name: String,
    val mime: String,
    val bytes: ByteArray,
)

internal data class ConvertOutcome(
    val files: List<ConvertedFile>,
    val notice: String? = null,
)

/* ------------------------------------------------------------------ */
/* Catalogue                                                          */
/* ------------------------------------------------------------------ */

internal object ConvertFormats {

    private fun fmt(
        id: String,
        label: String,
        mime: String,
        kind: ConvertKind,
        extensions: List<String>,
        note: String,
    ) = ConvertFormat(id, label, mime, kind, extensions, note)

    val JPG = fmt("jpg", "JPG", "image/jpeg", ConvertKind.Image, listOf("jpg", "jpeg", "jpe", "jfif"), "有损压缩")
    val PNG = fmt("png", "PNG", "image/png", ConvertKind.Image, listOf("png"), "无损，保留透明")
    val WEBP = fmt("webp", "WEBP", "image/webp", ConvertKind.Image, listOf("webp"), "体积更小")
    val BMP = fmt("bmp", "BMP", "image/bmp", ConvertKind.Image, listOf("bmp", "dib"), "24 位位图")
    val GIF = fmt("gif", "GIF", "image/gif", ConvertKind.Image, listOf("gif"), "动图仅取首帧")
    val HEIC = fmt("heic", "HEIC", "image/heic", ConvertKind.Image, listOf("heic", "heif"), "iPhone 照片")
    val AVIF = fmt("avif", "AVIF", "image/avif", ConvertKind.Image, listOf("avif"), "Android 12+")
    val JXL = fmt("jxl", "JXL", "image/jxl", ConvertKind.Image, listOf("jxl"), "JPEG XL")
    val JP2 = fmt(
        "jp2", "JP2", "image/jp2", ConvertKind.Image,
        listOf("jp2", "j2k", "j2c", "jpf", "jpx"),
        "JPEG 2000",
    )

    val PDF = fmt("pdf", "PDF", "application/pdf", ConvertKind.Pdf, listOf("pdf"), "文档")
    val PDF_MERGE = fmt("merge_pdf", "合并 PDF", "application/pdf", ConvertKind.Pdf, emptyList(), "合并多个 PDF")
    val PDF_SPLIT = fmt("split_pdf", "拆分 PDF", "application/pdf", ConvertKind.Pdf, emptyList(), "每页一个 PDF")
    val PDF_COMPRESS = fmt("compress_pdf", "压缩 PDF", "application/pdf", ConvertKind.Pdf, emptyList(), "重新压缩图片")
    val PDF_ROTATE = fmt("rotate_pdf", "旋转 PDF", "application/pdf", ConvertKind.Pdf, emptyList(), "顺时针旋转")
    val PDF_REORDER = fmt("reorder_pdf", "页面排序", "application/pdf", ConvertKind.Pdf, emptyList(), "调整页面顺序")
    val PDF_INSERT = fmt("insert_pdf", "插入 PDF", "application/pdf", ConvertKind.Pdf, emptyList(), "插入另一个 PDF")
    val PDF_EXTRACT = fmt("extract_pdf", "提取页", "application/pdf", ConvertKind.Pdf, emptyList(), "按页码范围")
    val PDF_DELETE = fmt("delete_pdf", "删除页", "application/pdf", ConvertKind.Pdf, emptyList(), "移除页码范围")

    val TXT = fmt("txt", "TXT", "text/plain", ConvertKind.Text, listOf("txt", "text"), "纯文本")
    val LOG = fmt("log", "LOG", "text/plain", ConvertKind.Text, listOf("log"), "日志文本")
    val MD = fmt("md", "MD", "text/markdown", ConvertKind.Text, listOf("md", "markdown", "mdown"), "Markdown")
    val HTML = fmt("html", "HTML", "text/html", ConvertKind.Text, listOf("html", "htm", "xhtml"), "网页")
    val XML = fmt("xml", "XML", "application/xml", ConvertKind.Text, listOf("xml"), "标记文本")
    val RTF = fmt("rtf", "RTF", "application/rtf", ConvertKind.Text, listOf("rtf"), "富文本")

    val JSON = fmt("json", "JSON", "application/json", ConvertKind.Data, listOf("json"), "结构化数据")
    val JSONL = fmt("jsonl", "JSONL", "application/x-ndjson", ConvertKind.Data, listOf("jsonl", "ndjson"), "JSON Lines")
    val CSV = fmt("csv", "CSV", "text/csv", ConvertKind.Data, listOf("csv"), "表格数据")
    val TSV = fmt("tsv", "TSV", "text/tab-separated-values", ConvertKind.Data, listOf("tsv"), "制表符表格")
    val YAML = fmt("yaml", "YAML", "application/yaml", ConvertKind.Data, listOf("yaml", "yml"), "结构化数据")
    val TOML = fmt("toml", "TOML", "application/toml", ConvertKind.Data, listOf("toml"), "配置文件")

    val SVG = fmt("svg", "SVG", "image/svg+xml", ConvertKind.Vector, listOf("svg", "svgz"), "矢量图")
    val AI = fmt("ai", "AI", "application/postscript", ConvertKind.Vector, listOf("ai", "eps", "ps"), "矢量图")
    val EMF = fmt("emf", "EMF", "image/emf", ConvertKind.Vector, listOf("emf", "wmf"), "矢量图")

    val TIFF = fmt("tiff", "TIFF", "image/tiff", ConvertKind.Image, listOf("tif", "tiff"), "TIFF")
    val ICO = fmt("ico", "ICO", "image/x-icon", ConvertKind.Image, listOf("ico", "cur"), "Icon")
    val PSD = fmt("psd", "PSD", "image/vnd.adobe.photoshop", ConvertKind.Image, listOf("psd"), "Photoshop")
    val QOI = fmt("qoi", "QOI", "image/qoi", ConvertKind.Image, listOf("qoi"), "Quite OK Image")
    val TGA = fmt("tga", "TGA", "image/x-tga", ConvertKind.Image, listOf("tga", "icb", "vda", "vst"), "Targa")
    val PNM = fmt("pnm", "PNM", "image/x-portable-anymap", ConvertKind.Image, listOf("pnm", "ppm", "pgm", "pbm"), "Portable Anymap")
    val HDR = fmt("hdr", "HDR", "image/vnd.radiance", ConvertKind.Image, listOf("hdr"), "Radiance HDR")
    val DDS = fmt("dds", "DDS", "image/vnd-ms.dds", ConvertKind.Image, listOf("dds"), "DirectDraw Surface")
    val PCX = fmt("pcx", "PCX", "image/x-pcx", ConvertKind.Image, listOf("pcx"), "PCX")
    val RAW = fmt(
        "raw", "RAW", "image/x-raw", ConvertKind.Image,
        listOf(
            "raw", "dng", "cr2", "cr3", "nef", "arw", "orf", "rw2", "pef", "raf",
            "srw", "kdc", "dcr", "crw", "3fr", "erf", "mef", "mos", "mrw", "nrw",
            "rwl", "sr2", "srf", "x3f",
        ),
        "相机原始格式",
    )

    val DOCX = fmt(
        "docx", "DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ConvertKind.Office,
        listOf("docx", "docm", "dotx"),
        "Word 文档",
    )
    val DOC = fmt(
        "doc", "DOC", "application/msword", ConvertKind.Office,
        listOf("doc", "dot", "pages", "wps"),
        "需要 LibreOffice 引擎",
    )
    val ODT = fmt(
        "odt", "ODT", "application/vnd.oasis.opendocument.text", ConvertKind.Office,
        listOf("odt"),
        "OpenDocument 文本",
    )

    val XLSX = fmt(
        "xlsx", "XLSX", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ConvertKind.Office,
        listOf("xlsx", "xlsm", "xltx"),
        "Excel 工作簿",
    )
    val XLS = fmt(
        "xls", "XLS", "application/vnd.ms-excel", ConvertKind.Office,
        listOf("xls", "xlt", "numbers", "et"),
        "需要 LibreOffice 引擎",
    )
    val ODS = fmt(
        "ods", "ODS", "application/vnd.oasis.opendocument.spreadsheet", ConvertKind.Office,
        listOf("ods"),
        "OpenDocument 表格",
    )

    val PPTX = fmt(
        "pptx", "PPTX", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        ConvertKind.Office,
        listOf("pptx", "pptm", "potx", "ppsx"),
        "PowerPoint 演示文稿",
    )
    val PPT = fmt(
        "ppt", "PPT", "application/vnd.ms-powerpoint", ConvertKind.Office,
        listOf("ppt", "pps", "pot", "key"),
        "需要 LibreOffice 引擎",
    )
    val ODP = fmt(
        "odp", "ODP", "application/vnd.oasis.opendocument.presentation", ConvertKind.Office,
        listOf("odp"),
        "OpenDocument 演示文稿",
    )

    val HWPX = fmt("hwpx", "HWPX", "application/x-hwpx", ConvertKind.Office, listOf("hwpx"), "HWPX 文档")
    val HWP = fmt("hwp", "HWP", "application/x-hwp", ConvertKind.Office, listOf("hwp"), "需要 rhwp 引擎")

    val ZIP = fmt("zip", "ZIP", "application/zip", ConvertKind.Archive, listOf("zip"), "ZIP 压缩包")
    val ZIP_ENC = fmt(
        "zip_enc", "加密 ZIP", "application/zip", ConvertKind.Archive,
        emptyList(), "AES-256 密码",
    )
    val ZIP_SPLIT = fmt(
        "zip_split", "分卷 ZIP", "application/zip", ConvertKind.Archive,
        emptyList(), "按大小拆分",
    )
    val SEVEN_Z = fmt("7z", "7Z", "application/x-7z-compressed", ConvertKind.Archive, listOf("7z"), "7-Zip 压缩包")
    val RAR = fmt("rar", "RAR", "application/vnd.rar", ConvertKind.Archive, listOf("rar"), "RAR 压缩包")
    val TAR = fmt("tar", "TAR", "application/x-tar", ConvertKind.Archive, listOf("tar"), "TAR 归档")
    val TAR_GZ = fmt("tgz", "TAR.GZ", "application/gzip", ConvertKind.Archive, listOf("tgz"), "TAR.GZ 归档")
    val TAR_BZ2 = fmt("tbz2", "TAR.BZ2", "application/x-bzip2", ConvertKind.Archive, listOf("tbz2", "tbz"), "TAR.BZ2 归档")
    val TAR_XZ = fmt("txz", "TAR.XZ", "application/x-xz", ConvertKind.Archive, listOf("txz"), "TAR.XZ 归档")
    val GZ = fmt("gz", "GZ", "application/gzip", ConvertKind.Archive, listOf("gz"), "GZIP")
    val BZ2 = fmt("bz2", "BZ2", "application/x-bzip2", ConvertKind.Archive, listOf("bz2"), "BZIP2")
    val XZ = fmt("xz", "XZ", "application/x-xz", ConvertKind.Archive, listOf("xz"), "XZ")
    val ARCHIVE_OTHER = fmt(
        "archive", "压缩包", "application/octet-stream", ConvertKind.Archive,
        listOf("iso", "cab", "alz", "egg", "arj", "lzh"),
        "需要额外引擎",
    )
    val EXTRACT = fmt("extract", "解压", "inode/directory", ConvertKind.Archive, emptyList(), "选择文件夹")
    val EXTRACT_SELECTED = fmt(
        "extract_selected", "选择解压", "inode/directory", ConvertKind.Archive,
        emptyList(), "只解压部分文件",
    )
    val MP3 = fmt("mp3", "MP3", "audio/mpeg", ConvertKind.Audio, listOf("mp3"), "MP3 音频")
    val M4A = fmt("m4a", "M4A", "audio/mp4", ConvertKind.Audio, listOf("m4a"), "AAC 音频")
    val AAC = fmt("aac", "AAC", "audio/aac", ConvertKind.Audio, listOf("aac"), "AAC 音频")
    val WAV = fmt("wav", "WAV", "audio/wav", ConvertKind.Audio, listOf("wav"), "PCM 音频")
    val FLAC = fmt("flac", "FLAC", "audio/flac", ConvertKind.Audio, listOf("flac"), "无损音频")
    val OGG = fmt("ogg", "OGG", "audio/ogg", ConvertKind.Audio, listOf("ogg", "oga"), "Ogg Vorbis")
    val OPUS = fmt("opus", "OPUS", "audio/opus", ConvertKind.Audio, listOf("opus"), "Opus 音频")
    val WMA = fmt("wma", "WMA", "audio/x-ms-wma", ConvertKind.Audio, listOf("wma"), "Windows Media")
    val AIFF = fmt("aiff", "AIFF", "audio/aiff", ConvertKind.Audio, listOf("aiff", "aif"), "AIFF")
    val AMR = fmt("amr", "AMR", "audio/amr", ConvertKind.Audio, listOf("amr"), "AMR")
    val APE = fmt("ape", "APE", "audio/x-ape", ConvertKind.Audio, listOf("ape"), "APE")
    val DSD = fmt("dsf", "DSF", "audio/x-dsf", ConvertKind.Audio, listOf("dsf", "dff"), "DSD")
    val AC3 = fmt("ac3", "AC3", "audio/ac3", ConvertKind.Audio, listOf("ac3", "eac3"), "AC3")
    val WV = fmt("wv", "WV", "audio/x-wavpack", ConvertKind.Audio, listOf("wv", "tta"), "WavPack/TTA")
    val AUDIO = fmt("audio", "音频", "audio/*", ConvertKind.Audio, emptyList(), "音频")

    val MP4 = fmt("mp4", "MP4", "video/mp4", ConvertKind.Video, listOf("mp4", "m4v"), "MP4 视频")
    val MOV = fmt("mov", "MOV", "video/quicktime", ConvertKind.Video, listOf("mov"), "QuickTime")
    val MKV = fmt("mkv", "MKV", "video/x-matroska", ConvertKind.Video, listOf("mkv"), "Matroska")
    val WEBM = fmt("webm", "WEBM", "video/webm", ConvertKind.Video, listOf("webm"), "WebM")
    val AVI = fmt("avi", "AVI", "video/x-msvideo", ConvertKind.Video, listOf("avi"), "AVI")
    val WMV = fmt("wmv", "WMV", "video/x-ms-wmv", ConvertKind.Video, listOf("wmv"), "Windows Media")
    val FLV = fmt("flv", "FLV", "video/x-flv", ConvertKind.Video, listOf("flv"), "Flash Video")
    val MPEG = fmt("mpeg", "MPEG", "video/mpeg", ConvertKind.Video, listOf("mpeg", "mpg"), "MPEG")
    val TS = fmt("ts", "TS", "video/mp2t", ConvertKind.Video, listOf("ts", "m2ts"), "MPEG-TS")
    val THREE_GP = fmt("3gp", "3GP", "video/3gpp", ConvertKind.Video, listOf("3gp"), "3GPP")
    val OGV = fmt("ogv", "OGV", "video/ogg", ConvertKind.Video, listOf("ogv"), "Ogg Video")
    val VOB = fmt("vob", "VOB", "video/dvd", ConvertKind.Video, listOf("vob"), "DVD Video")
    val VIDEO = fmt("video", "视频", "video/*", ConvertKind.Video, emptyList(), "视频")
    val VIDEO_GIF = fmt("gif", "GIF", "image/gif", ConvertKind.Video, emptyList(), "视频转 GIF")
    val EPUB = fmt("epub", "EPUB", "application/epub+zip", ConvertKind.Ebook, listOf("epub", "kepub"), "电子书")
    val EBOOK = fmt(
        "mobi", "MOBI", "application/x-mobipocket-ebook", ConvertKind.Ebook,
        listOf("mobi", "azw", "azw3", "azw4", "fb2", "cbz", "cbr", "cb7", "lit", "lrf", "chm", "pdb"),
        "需要 calibre 引擎",
    )
    val FONT = fmt(
        "font", "字体", "font/ttf", ConvertKind.Font,
        listOf("ttf", "otf", "woff", "woff2", "eot", "fon"),
        "字体转换",
    )
    val SRT = fmt("srt", "SRT", "application/x-subrip", ConvertKind.Subtitle, listOf("srt"), "SubRip 字幕")
    val VTT = fmt("vtt", "VTT", "text/vtt", ConvertKind.Subtitle, listOf("vtt", "webvtt"), "WebVTT 字幕")
    val ASS = fmt("ass", "ASS", "text/x-ass", ConvertKind.Subtitle, listOf("ass", "ssa"), "ASS/SSA 字幕")
    val LRC = fmt("lrc", "LRC", "text/x-lrc", ConvertKind.Subtitle, listOf("lrc"), "LRC 歌词")
    val SMI = fmt("smi", "SMI", "application/x-sami", ConvertKind.Subtitle, listOf("smi", "sami", "ttml", "scc"), "需要额外引擎")

    val UNKNOWN = fmt("unknown", "文件", "application/octet-stream", ConvertKind.Other, emptyList(), "未知格式")

    private val all = listOf(
        JPG, PNG, WEBP, BMP, GIF, HEIC, AVIF, JXL, JP2,
        PDF,
        TXT, LOG, MD, HTML, XML, RTF,
        JSON, JSONL, CSV, TSV, YAML, TOML,
        SVG, AI, EMF,
        TIFF, ICO, PSD, QOI, TGA, PNM, HDR, DDS, PCX, RAW,
        DOCX, DOC, ODT, XLSX, XLS, ODS, PPTX, PPT, ODP, HWPX, HWP,
        ZIP, SEVEN_Z, RAR, TAR, TAR_GZ, TAR_BZ2, TAR_XZ, GZ, BZ2, XZ, ARCHIVE_OTHER,
        MP3, M4A, AAC, WAV, FLAC, OGG, OPUS, WMA, AIFF, AMR, APE, DSD, AC3, WV, AUDIO,
        MP4, MOV, MKV, WEBM, AVI, WMV, FLV, MPEG, TS, THREE_GP, OGV, VOB, VIDEO,
        EPUB, EBOOK, FONT,
        SRT, VTT, ASS, LRC, SMI,
    )

    private val byExtension: Map<String, ConvertFormat> =
        all.flatMap { format -> format.extensions.map { it to format } }.toMap()

    private val wordIds = setOf("docx", "odt", "rtf", "hwpx")
    private val sheetIds = setOf("xlsx", "ods")
    private val slideIds = setOf("pptx", "odp")
    private val subtitleIds = setOf("srt", "vtt", "ass", "lrc")
    private val audioTargets = listOf(MP3, M4A, WAV, FLAC, OGG, OPUS)
    private val videoTargets = listOf(MP4, WEBM, MKV, MOV, AVI, VIDEO_GIF)

    fun detect(name: String, mime: String?): ConvertFormat {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
        val lowerName = name.lowercase(Locale.US)
        when {
            lowerName.endsWith(".tar.gz") -> return TAR_GZ
            lowerName.endsWith(".tar.bz2") -> return TAR_BZ2
            lowerName.endsWith(".tar.xz") -> return TAR_XZ
        }
        byExtension[extension]?.let { return it }

        return when {
            mime == null -> UNKNOWN
            mime == "application/pdf" -> PDF
            mime == "image/svg+xml" -> SVG
            mime == "application/json" -> JSON
            mime == "application/x-ndjson" || mime == "application/jsonl" -> JSONL
            mime == "text/csv" -> CSV
            mime == "text/tab-separated-values" -> TSV
            mime == "text/html" -> HTML
            mime == "text/markdown" -> MD
            mime == "text/vtt" -> VTT
            mime == "application/rtf" || mime == "text/rtf" -> RTF
            mime == "application/zip" -> ZIP
            mime == "application/x-7z-compressed" -> SEVEN_Z
            mime == "application/vnd.rar" || mime == "application/x-rar-compressed" -> RAR
            mime == "application/x-tar" -> TAR
            mime == "application/gzip" -> GZ
            mime == "application/x-bzip2" -> BZ2
            mime == "application/x-xz" -> XZ
            mime == "application/epub+zip" -> EPUB
            mime == "application/msword" -> DOC
            mime == "application/vnd.ms-excel" -> XLS
            mime == "application/vnd.ms-powerpoint" -> PPT
            mime == "application/x-hwp" -> HWP
            mime == "application/x-hwpx" -> HWPX
            mime.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml") -> DOCX
            mime.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml") -> XLSX
            mime.startsWith("application/vnd.openxmlformats-officedocument.presentationml") -> PPTX
            mime.startsWith("application/vnd.oasis.opendocument.text") -> ODT
            mime.startsWith("application/vnd.oasis.opendocument.spreadsheet") -> ODS
            mime.startsWith("application/vnd.oasis.opendocument.presentation") -> ODP
            mime == "image/tiff" -> TIFF
            mime == "image/vnd.adobe.photoshop" -> PSD
            mime == "image/x-icon" -> ICO
            mime == "image/qoi" -> QOI
            mime == "image/x-tga" -> TGA
            mime == "image/x-portable-anymap" ||
                mime == "image/x-portable-pixmap" ||
                mime == "image/x-portable-graymap" ||
                mime == "image/x-portable-bitmap" -> PNM

            mime == "image/vnd.radiance" -> HDR
            mime == "image/vnd-ms.dds" -> DDS
            mime == "image/x-pcx" -> PCX
            mime.startsWith("image/") -> ConvertFormat(
                id = "image",
                label = extension.uppercase(Locale.US).ifBlank { "IMAGE" },
                mime = mime,
                kind = ConvertKind.Image,
                extensions = emptyList(),
                note = "图片",
            )

            mime.startsWith("text/") -> TXT
            mime.startsWith("audio/") -> AUDIO
            mime.startsWith("video/") -> VIDEO
            else -> UNKNOWN
        }
    }

    /** Formats Filee would offer for this source, limited to what Android can do offline. */
    fun targets(source: ConvertFormat): List<ConvertFormat> = when {
        source.kind == ConvertKind.Audio -> audioTargets.filter { it.id != source.id }
        source.kind == ConvertKind.Video -> videoTargets.filter { it.id != source.id } + audioTargets
        source.id == "archive" -> emptyList()
        source.kind == ConvertKind.Archive ->
            listOf(EXTRACT, EXTRACT_SELECTED, ZIP, ZIP_ENC, ZIP_SPLIT, SEVEN_Z, TAR, TAR_GZ)
                .filter { it.id != source.id }

        source.id == "svg" -> listOf(JPG, PNG, WEBP, BMP, TIFF, ICO, JXL, PDF)
        source.kind == ConvertKind.Image -> listOf(JPG, PNG, WEBP, BMP, TIFF, ICO, JXL, PDF)
        source.id == "pdf" -> listOf(
            TXT, PNG, JPG, PDF_COMPRESS, PDF_SPLIT, PDF_ROTATE, PDF_REORDER, PDF_INSERT,
            PDF_EXTRACT, PDF_DELETE, PDF_MERGE,
        )
        source.id == "txt" || source.id == "log" -> listOf(PDF, HTML)
        source.id == "md" -> listOf(HTML, TXT, PDF)
        source.id == "html" -> listOf(TXT, PDF)
        source.id == "xml" -> listOf(TXT, JSON)
        source.id == "json" -> listOf(CSV, JSONL, XML, XLSX, YAML, TOML)
        source.id == "jsonl" -> listOf(JSON, CSV)
        source.id == "csv" -> listOf(JSON, TSV, XLSX)
        source.id == "tsv" -> listOf(JSON, CSV, XLSX)
        source.id == "yaml" -> listOf(JSON, TOML)
        source.id == "toml" -> listOf(JSON, YAML)
        source.id in wordIds -> listOf(TXT, HTML, PDF)
        source.id in sheetIds -> listOf(CSV, TSV, JSON)
        source.id in slideIds -> listOf(TXT, HTML, PDF)
        source.id == "epub" -> listOf(TXT, HTML, PDF)
        source.id in subtitleIds -> listOf(SRT, VTT, ASS, LRC).filter { it.id != source.id }
        else -> emptyList()
    }

    fun unsupportedReason(source: ConvertFormat): String = when (source.kind) {
        ConvertKind.Office -> "该 Office 格式需要 LibreOffice 引擎，当前版本暂未内置。"
        ConvertKind.Audio, ConvertKind.Video -> "该音视频格式暂不支持，转换失败时请尝试其他编码或容器。"
        ConvertKind.Archive -> "该压缩包格式暂未支持。当前支持 ZIP / 7Z / RAR / TAR / TAR.GZ / TAR.BZ2 / TAR.XZ / GZ / BZ2 / XZ。"
        ConvertKind.Ebook -> "该电子书格式需要 calibre 引擎，当前版本暂未内置。"
        ConvertKind.Vector -> "该矢量图格式暂未内置；SVG 已支持转图片和 PDF。"
        ConvertKind.Font -> "字体格式转换暂未内置。"
        ConvertKind.Subtitle -> "该字幕格式暂未内置；支持 SRT / VTT / ASS / LRC 互转。"
        ConvertKind.Data -> "该数据格式暂未内置。当前支持 JSON / JSONL / CSV / TSV / XML。"
        ConvertKind.Other -> when (source.id) {
            "unknown" -> "无法识别该文件格式。当前支持图片、PDF、Office、EPUB、文本、数据和字幕转换。"
            else -> "${source.label} 需要额外解码器，当前版本暂未内置。"
        }

        else -> "当前版本暂不支持该文件格式。"
    }
}

/* ------------------------------------------------------------------ */
/* Reading / writing                                                  */
/* ------------------------------------------------------------------ */

internal fun fileConvertReadPicked(context: Context, uri: Uri, mimeHint: String? = null): PickedFile {
    val resolver = context.contentResolver
    var name: String? = null
    var size = -1L

    runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
    }

    if (size <= 0) {
        size = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
    }

    val mime = runCatching { resolver.getType(uri) }.getOrNull()
        ?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
        ?: mimeHint?.takeIf { it.isNotBlank() }
        ?: "application/octet-stream"
    val displayName = name?.takeIf { it.isNotBlank() } ?: run {
        val extension = runCatching {
            android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        }.getOrNull()
        val suffix = if (extension.isNullOrBlank()) "" else ".$extension"
        "文件_${System.currentTimeMillis()}$suffix"
    }
    return PickedFile(
        uri = uri,
        name = displayName,
        size = size,
        mime = mime,
        format = ConvertFormats.detect(displayName, mime),
    )
}

internal suspend fun fileConvertWriteToUri(
    context: Context,
    bytes: ByteArray,
    uri: Uri,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
    }.getOrDefault(false)
}

internal fun fileConvertZip(files: List<ConvertedFile>, baseName: String): ConvertedFile {
    val buffer = ByteArrayOutputStream()
    ZipOutputStream(buffer).use { zip ->
        files.forEach { file ->
            zip.putNextEntry(ZipEntry(file.name))
            zip.write(file.bytes)
            zip.closeEntry()
        }
    }
    return ConvertedFile(
        name = "${fileConvertBaseName(baseName)}_converted.zip",
        mime = "application/zip",
        bytes = buffer.toByteArray(),
    )
}

internal fun fileConvertFormatSize(bytes: Long): String = when {
    bytes < 0 -> "大小未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}

internal fun fileConvertBaseName(name: String): String =
    name.substringBeforeLast('.', name)
        .ifBlank { "file" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(80)

internal fun fileConvertDecodePreview(bytes: ByteArray, maxDimension: Int = 1200): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return if (JxlCodec.looksLikeJxl(bytes)) JxlCodec.decode(bytes, maxDimension) else null
    }

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxDimension) {
        sample *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()
}

/* ------------------------------------------------------------------ */
/* Conversion                                                         */
/* ------------------------------------------------------------------ */

private const val MAX_PDF_PAGES = 24
private const val PDF_PAGE_MAX = 1500f
private const val MAX_PDF_OUTPUT_BYTES = 48L * 1024 * 1024
private const val MAX_TEXT_BYTES = 8L * 1024 * 1024

internal suspend fun fileConvertRun(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
    quality: Int,
    password: String? = null,
    pageRange: String? = null,
    rotation: Int = 90,
    splitSizeBytes: Long? = null,
    pageOrder: String? = null,
): ConvertOutcome = withContext(Dispatchers.IO) {
    when {
        source.format.id == "svg" ->
            convertSvgToImage(context, source, target, quality)

        source.format.kind == ConvertKind.Archive ->
            convertArchive(context, source, target, password, splitSizeBytes)

        source.format.kind == ConvertKind.Audio || source.format.kind == ConvertKind.Video ->
            convertMediaWithFfmpeg(context, source, target, quality)

        source.format.kind == ConvertKind.Image && target.id == "pdf" ->
            convertImageToPdf(context, source, target)

        source.format.kind == ConvertKind.Image ->
            convertImageToImage(context, source, target, quality)

        source.format.id == "pdf" && target.id == "split_pdf" ->
            splitPdf(context, source)

        source.format.id == "pdf" && target.id == "compress_pdf" ->
            compressPdf(context, source, quality)

        source.format.id == "pdf" && target.id == "rotate_pdf" ->
            rotatePdf(context, source, rotation)

        source.format.id == "pdf" && target.id == "reorder_pdf" ->
            reorderPdfPages(context, source, pageOrder.orEmpty())

        source.format.id == "pdf" && target.id == "extract_pdf" ->
            editPdfPages(context, source, pageRange.orEmpty(), keep = true)

        source.format.id == "pdf" && target.id == "delete_pdf" ->
            editPdfPages(context, source, pageRange.orEmpty(), keep = false)

        source.format.id == "pdf" && target.id == "txt" ->
            convertPdfToText(context, source, target)

        source.format.id == "pdf" ->
            convertPdfToImages(context, source, target, quality)

        source.format.id in setOf("docx", "odt", "rtf", "hwpx", "pptx", "odp", "epub") ->
            convertDocumentFile(context, source, target)

        source.format.id in setOf("xlsx", "ods") ->
            convertSpreadsheetFile(context, source, target)

        source.format.id in setOf("srt", "vtt", "ass", "lrc") ->
            convertSubtitleFile(context, source, target)

        source.format.id in setOf(
            "txt", "log", "md", "html", "xml", "json", "jsonl", "csv", "tsv", "yaml", "toml",
        ) ->
            convertTextFile(context, source, target)

        else -> error(ConvertFormats.unsupportedReason(source.format))
    }
}

private suspend fun decodeAnyImage(context: Context, source: PickedFile): Bitmap? {
    if (source.format.id == "raw") {
        val bytes = runCatching {
            fileConvertReadBytes(context, source.uri, 200L * 1024 * 1024)
        }.getOrNull() ?: return null
        return RawDecoder.decode(bytes, 4096)
    }
    if (source.format.id == "jp2") {
        return decodeImageWithFfmpeg(context, source)
    }
    if (source.format.id == "jxl") {
        val bytes = runCatching {
            fileConvertReadBytes(context, source.uri, 160L * 1024 * 1024)
        }.getOrNull() ?: return null
        return JxlCodec.decode(bytes, 4096)
    }
    decodeImage(context, source.uri, maxDimension = 4096)?.let { return it }
    val bytes = runCatching {
        fileConvertReadBytes(context, source.uri, 160L * 1024 * 1024)
    }.getOrNull() ?: return null
    return runCatching {
        decodeCustomImage(bytes, source.format.id, 4096)
    }.getOrNull()
}

private suspend fun convertImageToImage(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
    quality: Int,
): ConvertOutcome {
    val bitmap = decodeAnyImage(context, source)
        ?: error(
            if (source.format.id == "raw") {
                "RAW 解码失败，可能是该相机型号暂不受支持"
            } else {
                "图片解码失败，设备可能不支持 ${source.format.label} 格式"
            },
        )
    return try {
        val bytes = encodeBitmap(bitmap, target, quality)
        ConvertOutcome(
            listOf(
                ConvertedFile(
                    name = fileConvertOutputName(source.name, target),
                    mime = target.mime,
                    bytes = bytes,
                ),
            ),
        )
    } finally {
        bitmap.recycle()
    }
}

private suspend fun convertImageToPdf(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
): ConvertOutcome {
    val bitmap = decodeAnyImage(context, source)
        ?: error(
            if (source.format.id == "raw") {
                "RAW 解码失败，可能是该相机型号暂不受支持"
            } else {
                "图片解码失败，设备可能不支持 ${source.format.label} 格式"
            },
        )
    return try {
        ConvertOutcome(
            listOf(
                ConvertedFile(
                    name = fileConvertOutputName(source.name, target),
                    mime = target.mime,
                    bytes = bitmapToPdf(bitmap),
                ),
            ),
        )
    } finally {
        bitmap.recycle()
    }
}

private suspend fun convertSvgToImage(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
    quality: Int,
): ConvertOutcome {
    val svg = context.contentResolver.openInputStream(source.uri)?.use { input ->
        runCatching { SVG.getFromInputStream(input) }.getOrNull()
    } ?: error("SVG 读取失败")

    val width = svg.documentWidth.takeIf { it > 0f } ?: 1024f
    val height = svg.documentHeight.takeIf { it > 0f } ?: 1024f
    val scale = (4096f / max(width, height)).coerceAtMost(8f)
    val outputWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val outputHeight = (height * scale).roundToInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        val picture = svg.renderToPicture(outputWidth, outputHeight)
        canvas.drawPicture(picture)
        val bytes = if (target.id == "pdf") {
            bitmapToPdf(bitmap)
        } else {
            encodeBitmap(bitmap, target, quality)
        }
        return ConvertOutcome(
            listOf(
                ConvertedFile(
                    name = fileConvertOutputName(source.name, target),
                    mime = target.mime,
                    bytes = bytes,
                ),
            ),
        )
    } finally {
        bitmap.recycle()
    }
}

private val pdfBoxLock = Any()

@Volatile
private var pdfBoxInitialized = false

internal fun ensurePdfBox(context: Context) {
    if (pdfBoxInitialized) return
    synchronized(pdfBoxLock) {
        if (!pdfBoxInitialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxInitialized = true
        }
    }
}

private fun convertPdfToText(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
): ConvertOutcome {
    ensurePdfBox(context)
    val temp = File.createTempFile("pdfbox_", ".pdf", context.cacheDir)
    try {
        context.contentResolver.openInputStream(source.uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取 PDF 文件")

        val document = try {
            PDDocument.load(temp)
        } catch (throwable: Throwable) {
            error("PDF 解析失败：${throwable.message ?: "文件可能已加密"}")
        }

        try {
            val text = PDFTextStripper().getText(document)
                .replace('\u000C', '\n')
                .trim()
            if (text.isBlank()) error("PDF 中没有可提取的文本，可能是扫描版")
            return ConvertOutcome(
                listOf(
                    ConvertedFile(
                        name = fileConvertOutputName(source.name, target),
                        mime = target.mime,
                        bytes = text.toByteArray(Charsets.UTF_8),
                    ),
                ),
            )
        } finally {
            runCatching { document.close() }
        }
    } finally {
        temp.delete()
    }
}

private fun convertPdfToImages(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
    quality: Int,
): ConvertOutcome {
    val resolver = context.contentResolver
    var descriptor: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    var tempFile: File? = null

    try {
        descriptor = runCatching { resolver.openFileDescriptor(source.uri, "r") }.getOrNull()
        if (descriptor != null) {
            renderer = runCatching { PdfRenderer(descriptor) }.getOrNull()
            if (renderer == null) {
                descriptor.close()
                descriptor = null
            }
        }

        // Some document providers hand out a non-seekable pipe; PdfRenderer
        // needs a real file, so fall back to a temporary copy in the cache.
        if (renderer == null) {
            val temp = File.createTempFile("fileconvert_", ".pdf", context.cacheDir)
            tempFile = temp
            resolver.openInputStream(source.uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取 PDF 文件")

            val copied = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor = copied
            renderer = try {
                PdfRenderer(copied)
            } catch (throwable: Throwable) {
                error("PDF 解析失败：${throwable.message ?: "文件可能已加密"}")
            }
        }

        val activeRenderer = renderer ?: error("PDF 解析失败")
        val pageCount = activeRenderer.pageCount
        if (pageCount <= 0) error("PDF 没有可转换的页面")

        val base = fileConvertBaseName(source.name)
        val files = ArrayList<ConvertedFile>()
        var totalBytes = 0L
        var stoppedEarly = false
        val limit = min(pageCount, MAX_PDF_PAGES)

        for (index in 0 until limit) {
            val page = activeRenderer.openPage(index)
            val bitmap = try {
                val scale = (PDF_PAGE_MAX / max(page.width, page.height).toFloat()).coerceIn(1f, 4f)
                val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } catch (throwable: Throwable) {
                page.close()
                throw throwable
            }

            val bytes = try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                encodeBitmap(bitmap, target, quality)
            } catch (throwable: Throwable) {
                bitmap.recycle()
                throw throwable
            } finally {
                page.close()
            }
            bitmap.recycle()

            totalBytes += bytes.size
            files += ConvertedFile(
                name = "${base}_p${index + 1}.${target.id}",
                mime = target.mime,
                bytes = bytes,
            )
            if (totalBytes >= MAX_PDF_OUTPUT_BYTES) {
                stoppedEarly = true
                break
            }
        }

        val notice = when {
            pageCount > MAX_PDF_PAGES -> "PDF 共 $pageCount 页，已导出前 ${files.size} 页"
            stoppedEarly -> "已达到 48 MB 导出上限，共导出 ${files.size} 页"
            else -> null
        }
        return ConvertOutcome(files, notice)
    } finally {
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        tempFile?.delete()
    }
}

private fun convertTextFile(
    context: Context,
    source: PickedFile,
    target: ConvertFormat,
): ConvertOutcome {
    val text = fileConvertDecodeText(fileConvertReadBytes(context, source.uri, MAX_TEXT_BYTES))

    val output: ByteArray = when (source.format.id) {
        "txt", "log" -> when (target.id) {
            "pdf" -> textToPdf(text)
            "html" -> plainTextToHtml(text).toByteArray(Charsets.UTF_8)
            else -> error("暂不支持 TXT 转 ${target.label}")
        }

        "md" -> when (target.id) {
            "html" -> markdownToHtml(text).toByteArray(Charsets.UTF_8)
            "txt" -> markdownToPlainText(text).toByteArray(Charsets.UTF_8)
            "pdf" -> textToPdf(markdownToPlainText(text))
            else -> error("暂不支持 Markdown 转 ${target.label}")
        }

        "html" -> when (target.id) {
            "txt" -> htmlToPlainText(text).toByteArray(Charsets.UTF_8)
            "pdf" -> textToPdf(htmlToPlainText(text))
            else -> error("暂不支持 HTML 转 ${target.label}")
        }

        "xml" -> when (target.id) {
            "txt" -> htmlToPlainText(text).toByteArray(Charsets.UTF_8)
            "json" -> xmlToJson(text).toByteArray(Charsets.UTF_8)
            else -> error("暂不支持 XML 转 ${target.label}")
        }

        "json" -> when (target.id) {
            "csv" -> jsonToCsv(text).toByteArray(Charsets.UTF_8)
            "jsonl" -> jsonToJsonLines(text).toByteArray(Charsets.UTF_8)
            "xml" -> jsonToXml(text).toByteArray(Charsets.UTF_8)
            "xlsx" -> buildXlsx(
                parseDelimited(jsonToCsv(text), ','),
                fileConvertBaseName(source.name),
            )

            "yaml" -> jsonToYaml(text).toByteArray(Charsets.UTF_8)
            "toml" -> jsonToToml(text).toByteArray(Charsets.UTF_8)
            else -> error("暂不支持 JSON 转 ${target.label}")
        }

        "yaml" -> when (target.id) {
            "json" -> yamlToJson(text).toByteArray(Charsets.UTF_8)
            "toml" -> jsonToToml(yamlToJson(text)).toByteArray(Charsets.UTF_8)
            else -> error("暂不支持 YAML 转 ${target.label}")
        }

        "toml" -> when (target.id) {
            "json" -> tomlToJson(text).toByteArray(Charsets.UTF_8)
            "yaml" -> jsonToYaml(tomlToJson(text)).toByteArray(Charsets.UTF_8)
            else -> error("暂不支持 TOML 转 ${target.label}")
        }

        "jsonl" -> when (target.id) {
            "json" -> jsonLinesToJson(text).toByteArray(Charsets.UTF_8)
            "csv" -> jsonToCsv(jsonLinesToJson(text)).toByteArray(Charsets.UTF_8)
            else -> error("暂不支持 JSONL 转 ${target.label}")
        }

        "csv" -> when (target.id) {
            "json" -> csvToJson(text, ',').toByteArray(Charsets.UTF_8)
            "tsv" -> delimitedToDelimited(text, ',', '\t').toByteArray(Charsets.UTF_8)
            "xlsx" -> buildXlsx(
                parseDelimited(text, ','),
                fileConvertBaseName(source.name),
            )

            else -> error("暂不支持 CSV 转 ${target.label}")
        }

        "tsv" -> when (target.id) {
            "json" -> csvToJson(text, '\t').toByteArray(Charsets.UTF_8)
            "csv" -> delimitedToDelimited(text, '\t', ',').toByteArray(Charsets.UTF_8)
            "xlsx" -> buildXlsx(
                parseDelimited(text, '\t'),
                fileConvertBaseName(source.name),
            )

            else -> error("暂不支持 TSV 转 ${target.label}")
        }

        else -> error(ConvertFormats.unsupportedReason(source.format))
    }

    return ConvertOutcome(
        listOf(
            ConvertedFile(
                name = fileConvertOutputName(source.name, target),
                mime = target.mime,
                bytes = output,
            ),
        ),
    )
}

/* ------------------------------------------------------------------ */
/* Image encoding                                                     */
/* ------------------------------------------------------------------ */

private fun encodeBitmap(bitmap: Bitmap, target: ConvertFormat, quality: Int): ByteArray {
    val safeQuality = quality.coerceIn(60, 100)
    return when (target.id) {
        "jpg" -> {
            val flattened = flattenOnWhite(bitmap)
            try {
                ByteArrayOutputStream().also { output ->
                    if (!flattened.compress(Bitmap.CompressFormat.JPEG, safeQuality, output)) {
                        error("JPEG 编码失败")
                    }
                }.toByteArray()
            } finally {
                if (flattened !== bitmap) flattened.recycle()
            }
        }

        "png" -> ByteArrayOutputStream().also { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                error("PNG 编码失败")
            }
        }.toByteArray()

        "webp" -> ByteArrayOutputStream().also { output ->
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, safeQuality, output)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, safeQuality, output)
            }
            if (!ok) error("WebP 编码失败")
        }.toByteArray()

        "bmp" -> encodeBmp(bitmap)

        "tiff" -> encodeTiff(bitmap)

        "ico" -> encodeIco(bitmap)

        "jxl" -> JxlCodec.encode(bitmap, safeQuality)

        else -> error("暂不支持输出 ${target.label}")
    }
}

private fun flattenOnWhite(source: Bitmap): Bitmap {
    if (!source.hasAlpha()) return source
    val flattened = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    Canvas(flattened).apply {
        drawColor(Color.WHITE)
        drawBitmap(source, 0f, 0f, null)
    }
    return flattened
}

/** 24-bit, bottom-up BMP without an alpha channel. */
private fun encodeBmp(source: Bitmap): ByteArray {
    val width = source.width
    val height = source.height
    val rowSize = (width * 3 + 3) and 3.inv()
    val pixelSize = rowSize * height
    val fileSize = 54 + pixelSize
    val output = ByteArrayOutputStream(fileSize)

    val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN)
    header.put('B'.code.toByte())
    header.put('M'.code.toByte())
    header.putInt(fileSize)
    header.putShort(0)
    header.putShort(0)
    header.putInt(54)
    header.putInt(40)
    header.putInt(width)
    header.putInt(height)
    header.putShort(1)
    header.putShort(24)
    header.putInt(0)
    header.putInt(pixelSize)
    header.putInt(2835)
    header.putInt(2835)
    header.putInt(0)
    header.putInt(0)
    output.write(header.array())

    val pixels = IntArray(width)
    val row = ByteArray(rowSize)
    for (y in height - 1 downTo 0) {
        source.getPixels(pixels, 0, width, 0, y, width, 1)
        var index = 0
        for (x in 0 until width) {
            val color = pixels[x]
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            val alpha = (color ushr 24) and 0xFF
            row[index++] = blendOnWhite(blue, alpha).toByte()
            row[index++] = blendOnWhite(green, alpha).toByte()
            row[index++] = blendOnWhite(red, alpha).toByte()
        }
        while (index < rowSize) row[index++] = 0
        output.write(row)
    }
    return output.toByteArray()
}

private fun blendOnWhite(channel: Int, alpha: Int): Int =
    (channel * alpha + 255 * (255 - alpha)) / 255

/* ------------------------------------------------------------------ */
/* PDF output                                                         */
/* ------------------------------------------------------------------ */

private fun bitmapToPdf(bitmap: Bitmap): ByteArray {
    val landscape = bitmap.width > bitmap.height
    val pageWidth = if (landscape) 842 else 595
    val pageHeight = if (landscape) 595 else 842
    val document = PdfDocument()
    try {
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create(),
        )
        try {
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val margin = 24f
            val availableWidth = pageWidth - margin * 2
            val availableHeight = pageHeight - margin * 2
            val scale = min(
                availableWidth / bitmap.width,
                availableHeight / bitmap.height,
            )
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val left = (pageWidth - drawWidth) / 2f
            val top = (pageHeight - drawHeight) / 2f
            canvas.drawBitmap(
                bitmap,
                Rect(0, 0, bitmap.width, bitmap.height),
                RectF(left, top, left + drawWidth, top + drawHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        } finally {
            document.finishPage(page)
        }
        return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
    } finally {
        document.close()
    }
}

internal fun textToPdf(text: String): ByteArray {
    val pageWidth = 595
    val pageHeight = 842
    val margin = 48f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 10.5f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    val lineHeight = 15.5f
    val maxWidth = pageWidth - margin * 2

    val document = PdfDocument()
    var pageNumber = 1
    var page = document.startPage(
        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create(),
    )
    var canvas = page.canvas
    var y = margin + paint.textSize
    var pageOpen = true

    fun nextPage() {
        document.finishPage(page)
        pageOpen = false
        pageNumber += 1
        page = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create(),
        )
        canvas = page.canvas
        y = margin + paint.textSize
        pageOpen = true
    }

    try {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        for (raw in lines) {
            val line = raw.replace("\t", "    ")
            val chunks = wrapLine(line, paint, maxWidth)
            for (chunk in chunks) {
                if (y > pageHeight - margin) nextPage()
                canvas.drawText(chunk, margin, y, paint)
                y += lineHeight
            }
        }
        document.finishPage(page)
        pageOpen = false
        return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
    } catch (throwable: Throwable) {
        if (pageOpen) runCatching { document.finishPage(page) }
        throw throwable
    } finally {
        document.close()
    }
}

private fun wrapLine(line: String, paint: Paint, maxWidth: Float): List<String> {
    if (line.isEmpty()) return listOf("")
    val chunks = ArrayList<String>()
    var remaining = line
    while (remaining.isNotEmpty()) {
        val count = paint.breakText(remaining, true, maxWidth, null)
        if (count <= 0) {
            chunks += remaining
            break
        }
        chunks += remaining.substring(0, count)
        remaining = remaining.substring(count)
    }
    return chunks
}

/* ------------------------------------------------------------------ */
/* Text / data conversions                                            */
/* ------------------------------------------------------------------ */

internal fun fileConvertOutputName(sourceName: String, target: ConvertFormat): String {
    val base = fileConvertBaseName(sourceName)
    val sourceExtension = sourceName.substringAfterLast('.', "").lowercase(Locale.US)
    return if (sourceExtension == target.id) {
        "${base}_converted.${target.id}"
    } else {
        "$base.${target.id}"
    }
}

internal fun fileConvertReadBytes(context: Context, uri: Uri, maxBytes: Long): ByteArray {
    val input = context.contentResolver.openInputStream(uri) ?: error("无法读取文件")
    input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                error("文件超过 ${maxBytes / 1024 / 1024} MB，暂不支持在设备上直接转换")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

internal fun fileConvertDecodeText(bytes: ByteArray): String {
    if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    ) {
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    }

    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        runCatching { String(bytes, Charset.forName("GBK")) }
            .getOrDefault(String(bytes, Charsets.UTF_8))
    }
}

private fun plainTextToHtml(text: String): String =
    htmlDocument("<pre class=\"plain\">${escapeHtml(text)}</pre>")

private fun markdownToHtml(markdown: String): String {
    val out = StringBuilder()
    var inCode = false
    var codeLanguage = ""
    var listType: String? = null
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            out.append("<p>").append(markdownInline(paragraph.toString())).append("</p>\n")
            paragraph.clear()
        }
    }

    fun closeList() {
        val type = listType ?: return
        out.append("</").append(type).append(">\n")
        listType = null
    }

    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    for (raw in lines) {
        val trimmed = raw.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            closeList()
            if (inCode) {
                out.append("</code></pre>\n")
                inCode = false
                codeLanguage = ""
            } else {
                inCode = true
                codeLanguage = trimmed.removePrefix("```")
                    .trim()
                    .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                val languageClass = if (codeLanguage.isBlank()) "" else " class=\"language-$codeLanguage\""
                out.append("<pre><code$languageClass>")
            }
            continue
        }

        if (inCode) {
            out.append(escapeHtml(raw)).append('\n')
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            closeList()
            continue
        }

        if (Regex("^([-*_])\\1{2,}$").matches(trimmed)) {
            flushParagraph()
            closeList()
            out.append("<hr/>\n")
            continue
        }

        val heading = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
        if (heading != null) {
            flushParagraph()
            closeList()
            val level = heading.groupValues[1].length
            out.append("<h$level>")
                .append(markdownInline(heading.groupValues[2]))
                .append("</h$level>\n")
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            closeList()
            out.append("<blockquote>")
                .append(markdownInline(trimmed.removePrefix(">").trim()))
                .append("</blockquote>\n")
            continue
        }

        val unordered = Regex("^[-*+]\\s+(.*)$").find(trimmed)
        val ordered = Regex("^\\d+\\.\\s+(.*)$").find(trimmed)
        if (unordered != null || ordered != null) {
            flushParagraph()
            val wanted = if (unordered != null) "ul" else "ol"
            if (listType != wanted) {
                closeList()
                out.append("<").append(wanted).append(">\n")
                listType = wanted
            }
            val item = unordered?.groupValues?.get(1) ?: ordered?.groupValues?.get(1).orEmpty()
            out.append("<li>").append(markdownInline(item)).append("</li>\n")
            continue
        }

        closeList()
        if (paragraph.isNotEmpty()) paragraph.append(' ')
        paragraph.append(trimmed)
    }

    if (inCode) out.append("</code></pre>\n")
    flushParagraph()
    closeList()
    return htmlDocument(out.toString().trim())
}

private fun markdownInline(text: String): String {
    var value = escapeHtml(text)
    value = Regex("`([^`]+)`").replace(value) { match ->
        "<code>${match.groupValues[1]}</code>"
    }
    value = Regex("!\\[([^]]*)]\\(([^)]+)\\)").replace(value) { match ->
        "<img alt=\"${match.groupValues[1]}\" src=\"${match.groupValues[2]}\"/>"
    }
    value = Regex("\\[([^]]+)]\\(([^)]+)\\)").replace(value) { match ->
        "<a href=\"${match.groupValues[2]}\">${match.groupValues[1]}</a>"
    }
    value = Regex("\\*\\*(.+?)\\*\\*").replace(value) { match ->
        "<strong>${match.groupValues[1]}</strong>"
    }
    value = Regex("__(.+?)__").replace(value) { match ->
        "<strong>${match.groupValues[1]}</strong>"
    }
    value = Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)").replace(value) { match ->
        "<em>${match.groupValues[1]}</em>"
    }
    value = Regex("~~(.+?)~~").replace(value) { match ->
        "<del>${match.groupValues[1]}</del>"
    }
    return value
}

private fun markdownToPlainText(markdown: String): String {
    var text = markdown.replace("\r\n", "\n").replace('\r', '\n')
    text = text.replace(Regex("(?m)^```.*$"), "")
    text = text.replace(Regex("(?m)^\\s*([-*_])\\1{2,}\\s*$"), "")
    text = text.replace(Regex("!\\[([^]]*)]\\([^)]+\\)")) { it.groupValues[1] }
    text = text.replace(Regex("\\[([^]]+)]\\([^)]+\\)")) { it.groupValues[1] }
    text = text.replace(Regex("(?m)^#{1,6}\\s+"), "")
    text = text.replace(Regex("(?m)^>\\s?"), "")
    text = text.replace(Regex("(?m)^\\s*[-*+]\\s+")) { "• " }
    text = text.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
    text = text.replace(Regex("\\*\\*(.+?)\\*\\*")) { it.groupValues[1] }
    text = text.replace(Regex("__(.+?)__")) { it.groupValues[1] }
    text = text.replace(Regex("~~(.+?)~~")) { it.groupValues[1] }
    text = text.replace(Regex("`([^`]+)`")) { it.groupValues[1] }
    text = text.replace(Regex("\\*([^*\\n]+)\\*")) { it.groupValues[1] }
    return text.trim()
}

private fun htmlToPlainText(html: String): String {
    var text = html.replace(Regex("(?is)<script.*?</script>"), " ")
    text = text.replace(Regex("(?is)<style.*?</style>"), " ")
    text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
    text = text.replace(Regex("(?i)</(p|div|li|tr|h[1-6]|section|article|blockquote|pre)>"), "\n")
    text = text.replace(Regex("(?s)<[^>]+>"), "")
    text = decodeHtmlEntities(text)
    text = text.replace("\r\n", "\n").replace('\r', '\n')
    text = text.split('\n').joinToString("\n") { it.trim() }
    text = text.replace(Regex("\n{3,}"), "\n\n")
    return text.trim()
}

private fun htmlDocument(body: String): String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>文件格式转换</title>
<style>
:root { color-scheme: light; }
body { margin: 0; padding: 28px; background: #f7f8fb; color: #14161c; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; line-height: 1.65; }
main { max-width: 820px; margin: 0 auto; padding: 28px 32px; background: #fff; border-radius: 18px; box-shadow: 0 12px 40px rgba(50, 60, 110, .08); }
pre { white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 14px; }
code { background: #f1f3f8; padding: 1px 5px; border-radius: 5px; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
pre code { background: none; padding: 0; }
blockquote { margin: 12px 0; padding: 4px 16px; border-left: 3px solid #d9deea; color: #4c525e; }
img { max-width: 100%; }
a { color: #0071e3; }
hr { border: none; border-top: 1px solid #e5e8f0; margin: 22px 0; }
</style>
</head>
<body><main>
$body
</main></body>
</html>
""".trimIndent()

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private fun decodeHtmlEntities(text: String): String {
    var value = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

    value = Regex("&#([xX]?[0-9A-Fa-f]+);").replace(value) { match ->
        val raw = match.groupValues[1]
        val codePoint = runCatching {
            if (raw.startsWith("x", ignoreCase = true)) {
                raw.substring(1).toInt(16)
            } else {
                raw.toInt()
            }
        }.getOrDefault(-1)
        if (codePoint in 0..0x10FFFF) String(Character.toChars(codePoint)) else match.value
    }
    return value
}

/* ------------------------------------------------------------------ */
/* JSON / CSV / XML                                                   */
/* ------------------------------------------------------------------ */

private fun jsonToCsv(json: String): String {
    val root = JSONTokener(json).nextValue()
    val objects = ArrayList<JSONObject>()
    val keys = LinkedHashSet<String>()

    fun addObject(value: JSONObject) {
        objects += value
        val iterator = value.keys()
        while (iterator.hasNext()) keys += iterator.next()
    }

    when (root) {
        is JSONArray -> {
            for (index in 0 until root.length()) {
                when (val item = root.get(index)) {
                    is JSONObject -> addObject(item)
                    else -> addObject(JSONObject().put("value", item))
                }
            }
        }

        is JSONObject -> addObject(root)
        else -> addObject(JSONObject().put("value", root))
    }

    if (objects.isEmpty()) return ""

    val header = keys.toList()
    val out = StringBuilder()
    out.append(header.joinToString(",") { escapeDelimited(it, ',') }).append("\r\n")
    for (obj in objects) {
        out.append(
            header.joinToString(",") { key ->
                escapeDelimited(jsonCell(obj.opt(key)), ',')
            },
        ).append("\r\n")
    }
    return out.toString()
}

private fun jsonToJsonLines(json: String): String {
    val root = JSONTokener(json).nextValue()
    val out = StringBuilder()
    when (root) {
        is JSONArray -> {
            for (index in 0 until root.length()) {
                out.append(root.get(index).toString()).append('\n')
            }
        }

        else -> out.append(root.toString()).append('\n')
    }
    return out.toString().trimEnd()
}

private fun jsonLinesToJson(text: String): String {
    val array = JSONArray()
    text.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty()) array.put(JSONTokener(trimmed).nextValue())
    }
    return array.toString(2)
}

private fun xmlToJson(xml: String): String {
    val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = false
    factory.isExpandEntityReferences = false
    val document = factory.newDocumentBuilder()
        .parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
    val root = document.documentElement ?: error("XML 没有根节点")
    val result = JSONObject()
    result.put(root.tagName, xmlElementToJson(root))
    return result.toString(2)
}

private fun xmlElementToJson(element: org.w3c.dom.Element): Any {
    val obj = JSONObject()
    val attributes = element.attributes
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index)
        obj.put("@${attribute.nodeName}", attribute.nodeValue.orEmpty())
    }

    val children = element.childNodes
    var text = StringBuilder()
    for (index in 0 until children.length) {
        val child = children.item(index)
        when (child.nodeType) {
            org.w3c.dom.Node.ELEMENT_NODE -> {
                val childElement = child as org.w3c.dom.Element
                val value = xmlElementToJson(childElement)
                val existing = obj.opt(childElement.tagName)
                when {
                    existing == null || existing == JSONObject.NULL -> obj.put(childElement.tagName, value)
                    existing is JSONArray -> existing.put(value)
                    else -> obj.put(childElement.tagName, JSONArray().put(existing).put(value))
                }
            }

            org.w3c.dom.Node.TEXT_NODE, org.w3c.dom.Node.CDATA_SECTION_NODE ->
                text.append(child.nodeValue.orEmpty())
        }
    }

    val normalized = text.toString().trim()
    if (obj.length() == 0) return normalized
    if (normalized.isNotEmpty()) obj.put("#text", normalized)
    return obj
}

private fun jsonToXml(json: String): String {
    val root = JSONTokener(json).nextValue()
    val out = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>")
    fun writeValue(name: String, value: Any?) {
        when (value) {
            is JSONObject -> {
                out.append("<").append(name).append(">")
                val iterator = value.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    writeValue(key, value.opt(key))
                }
                out.append("</").append(name).append(">")
            }

            is JSONArray -> {
                for (index in 0 until value.length()) {
                    writeValue("item", value.get(index))
                }
            }

            null, JSONObject.NULL -> out.append("<").append(name).append("/>")
            else -> out.append("<").append(name).append(">")
                .append(escapeXml(value.toString()))
                .append("</").append(name).append(">")
        }
    }

    when (root) {
        is JSONObject -> {
            val iterator = root.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                writeValue(key, root.opt(key))
            }
        }

        is JSONArray -> {
            for (index in 0 until root.length()) writeValue("item", root.get(index))
        }

        else -> writeValue("value", root)
    }
    out.append("</root>")
    return out.toString()
}

private fun escapeXml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun jsonCell(value: Any?): String = when (value) {
    null, JSONObject.NULL -> ""
    is String -> value
    is JSONObject, is JSONArray -> value.toString()
    else -> value.toString()
}

private fun escapeDelimited(value: String, delimiter: Char): String =
    if (value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }

private fun delimitedToDelimited(text: String, from: Char, to: Char): String {
    val rows = parseDelimited(text, from)
    val lineEnding = if (to == '\t') "\n" else "\r\n"
    return rows.joinToString(lineEnding) { row ->
        row.joinToString(to.toString()) { cell -> escapeDelimited(cell, to) }
    }
}

private fun csvToJson(text: String, delimiter: Char): String {
    val rows = parseDelimited(text, delimiter)
    if (rows.isEmpty()) return "[]"

    val seen = HashMap<String, Int>()
    val headers = rows.first().mapIndexed { index, raw ->
        val base = raw.trim().ifBlank { "column_${index + 1}" }
        val count = (seen[base] ?: 0) + 1
        seen[base] = count
        if (count == 1) base else "${base}_$count"
    }

    val array = JSONArray()
    for (row in rows.drop(1)) {
        if (row.all { it.isBlank() }) continue
        val obj = JSONObject()
        headers.forEachIndexed { index, header ->
            obj.put(header, row.getOrNull(index) ?: "")
        }
        array.put(obj)
    }
    return array.toString(2)
}

private fun parseDelimited(text: String, delimiter: Char): List<List<String>> {
    val source = text.removePrefix("\uFEFF")
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var index = 0

    while (index < source.length) {
        val char = source[index]
        if (inQuotes) {
            if (char == '"') {
                if (index + 1 < source.length && source[index + 1] == '"') {
                    cell.append('"')
                    index += 1
                } else {
                    inQuotes = false
                }
            } else {
                cell.append(char)
            }
        } else {
            when (char) {
                '"' -> inQuotes = true
                delimiter -> {
                    row += cell.toString()
                    cell.clear()
                }

                '\n' -> {
                    row += cell.toString()
                    cell.clear()
                    rows += row
                    row = ArrayList()
                }

                '\r' -> Unit
                else -> cell.append(char)
            }
        }
        index += 1
    }

    if (cell.isNotEmpty() || row.isNotEmpty()) {
        row += cell.toString()
        rows += row
    }

    return rows.filterNot { it.size == 1 && it[0].isBlank() }
}
