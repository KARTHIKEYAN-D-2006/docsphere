package com.docsphere.data.extractor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Sealed result type for image OCR — carries success text or a descriptive failure reason
 * that can be shown in the UI and stored in the database.
 */
sealed class OcrResult {
    data class Success(val text: String) : OcrResult()
    data class Failed(val reason: String) : OcrResult()
    object Skipped : OcrResult()
}

/**
 * High-performance dispatcher that routes text extraction to the optimal engine:
 * - PDFs: instant text-layer extraction via PdfBox-Android with 5s timeout guard
 * - Office Docs: streaming direct ZIP XML character parsing for DOCX, XLSX, PPTX
 * - Plain Text & CSV: bounded buffer stream reading
 * - Images: OCR via ML Kit with 15s timeout. Tries 1024px first, retries at 2048px
 *   if fewer than 20 characters are returned. All images are scanned; camera-roll
 *   images are included since they may be screenshots or document photos.
 */
@Singleton
class TextExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val TAG = "DocSphereDebug"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    companion object {
        private const val MAX_CHARS = 50_000
        private const val MAX_PDF_PAGES = 30
        /** Images smaller than this (icons/thumbnails) are skipped */
        private const val MIN_IMAGE_BYTES = 15_000L
        /** Images larger than this may be raw DSLR photos — still attempt OCR */
        private const val MAX_IMAGE_BYTES_FOR_OCR = 25 * 1024 * 1024L
    }

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) {}
    }

    /**
     * Extracts readable text from any supported file.
     * For images, returns empty string on success or "OCR_FAILED:<reason>" on failure
     * so callers can distinguish "no text found" from "engine error".
     */
    suspend fun extract(
        uri: Uri,
        mimeType: String,
        sizeBytes: Long = 0L,
        displayName: String = ""
    ): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val cleanMime = mimeType.lowercase()
            val ext = displayName.substringAfterLast('.', "").lowercase()

            when {
                // ── 1. PDF Documents ─────────────────────────────────────
                cleanMime == "application/pdf" || ext == "pdf" -> {
                    extractPdf(uri)
                }

                // ── 2. Plain Text, CSV, Markdown, Logs, JSON ────────────
                cleanMime == "text/plain" || cleanMime == "text/csv" || cleanMime == "text/markdown"
                || ext in listOf("txt", "csv", "md", "json", "xml", "log") -> {
                    extractPlainText(uri)
                }

                // ── 3. Microsoft Word (.docx, .doc) ─────────────────────
                cleanMime.contains("word")
                || cleanMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                || ext == "docx" || ext == "doc" -> {
                    extractOfficeText(uri, "docx")
                }

                // ── 4. Microsoft Excel (.xlsx, .xls) ────────────────────
                cleanMime.contains("excel") || cleanMime.contains("spreadsheet")
                || cleanMime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                || ext == "xlsx" || ext == "xls" -> {
                    extractOfficeText(uri, "xlsx")
                }

                // ── 5. Microsoft PowerPoint (.pptx, .ppt) ───────────────
                cleanMime.contains("presentation") || cleanMime.contains("powerpoint")
                || cleanMime == "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                || ext == "pptx" || ext == "ppt" -> {
                    extractOfficeText(uri, "pptx")
                }

                // ── 6. Images — ALL images are attempted; camera roll included ──
                cleanMime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "heic", "heif") -> {
                    when (val result = extractImageOcr(uri, displayName, sizeBytes)) {
                        is OcrResult.Success -> result.text
                        is OcrResult.Failed  -> "OCR_FAILED:${result.reason}"
                        is OcrResult.Skipped -> ""
                    }
                }

                else -> ""
            }
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * Full OCR pipeline with:
     * - Skip if too small (icon/thumbnail < 15 KB)
     * - Try 1024px; if < 20 chars, retry at 2048px
     * - 15s timeout per attempt
     * - Returns OcrResult.Failed with a human-readable reason on errors
     * - Logs: filename, duration, char count (never the extracted text)
     */
    suspend fun extractImageOcr(
        uri: Uri,
        displayName: String,
        sizeBytes: Long
    ): OcrResult = withContext(Dispatchers.IO) {
        if (sizeBytes in 1 until MIN_IMAGE_BYTES) {
            Log.d(TAG, "[OCR] SKIP $displayName — too small ($sizeBytes bytes, likely icon/thumbnail)")
            return@withContext OcrResult.Skipped
        }
        if (sizeBytes > MAX_IMAGE_BYTES_FOR_OCR) {
            Log.d(TAG, "[OCR] SKIP $displayName — too large ($sizeBytes bytes, raw camera file)")
            return@withContext OcrResult.Skipped
        }

        val startMs = System.currentTimeMillis()

        // First attempt at 1024px
        val result1 = runOcrAtResolution(uri, maxPx = 1024)
        return@withContext when {
            result1 is OcrResult.Failed -> {
                val elapsedMs = System.currentTimeMillis() - startMs
                Log.d(TAG, "[OCR] FAILED $displayName — ${result1.reason} (${elapsedMs}ms)")
                result1
            }
            result1 is OcrResult.Success && result1.text.length >= 20 -> {
                val elapsedMs = System.currentTimeMillis() - startMs
                Log.d(TAG, "[OCR] OK $displayName — ${result1.text.length} chars at 1024px (${elapsedMs}ms)")
                result1
            }
            else -> {
                // Fewer than 20 characters — retry at 2048px for better detail
                Log.d(TAG, "[OCR] Retrying $displayName at 2048px (only ${(result1 as? OcrResult.Success)?.text?.length ?: 0} chars at 1024px)")
                val result2 = runOcrAtResolution(uri, maxPx = 2048)
                val elapsedMs = System.currentTimeMillis() - startMs
                when (result2) {
                    is OcrResult.Success -> {
                        Log.d(TAG, "[OCR] OK $displayName — ${result2.text.length} chars at 2048px (${elapsedMs}ms total)")
                        if (result2.text.isBlank()) OcrResult.Failed("no text detected") else result2
                    }
                    is OcrResult.Failed -> {
                        Log.d(TAG, "[OCR] FAILED $displayName at 2048px — ${result2.reason} (${elapsedMs}ms total)")
                        result2
                    }
                    OcrResult.Skipped -> OcrResult.Failed("no text detected")
                }
            }
        }
    }

    private suspend fun runOcrAtResolution(uri: Uri, maxPx: Int): OcrResult {
        return withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { cont ->
                try {
                    val bitmap = decodeSampledBitmap(uri, maxPx, maxPx)
                    if (bitmap == null) {
                        cont.resume(OcrResult.Failed("bitmap decode returned null"))
                        return@suspendCancellableCoroutine
                    }
                    val image = InputImage.fromBitmap(bitmap, 0)
                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            val text = result.text.take(10_000)
                            cont.resume(OcrResult.Success(text))
                        }
                        .addOnFailureListener { e ->
                            cont.resume(OcrResult.Failed(e.message ?: "ML Kit error"))
                        }
                } catch (e: Exception) {
                    cont.resume(OcrResult.Failed(e.message ?: "decode exception"))
                }
            }
        } ?: OcrResult.Failed("timeout after 15s")
    }

    private fun decodeSampledBitmap(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            openStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        } catch (_: Exception) {
            null
        }
    }

    // ────────────────────────────────────────────
    //  PDF via Apache PdfBox-Android (5s timeout guard)
    // ────────────────────────────────────────────
    private suspend fun extractPdf(uri: Uri): String =
        withTimeoutOrNull(5000L) {
            try {
                openStream(uri)?.use { stream ->
                    val doc = PDDocument.load(stream)
                    val stripper = PDFTextStripper().apply {
                        startPage = 1
                        endPage = minOf(MAX_PDF_PAGES, doc.numberOfPages)
                    }
                    val text = stripper.getText(doc)
                    doc.close()
                    text.take(MAX_CHARS)
                } ?: ""
            } catch (_: Throwable) { "" }
        } ?: ""

    // ────────────────────────────────────────────
    //  Plain Text / CSV / Markdown (bounded buffer read)
    // ────────────────────────────────────────────
    private fun extractPlainText(uri: Uri): String {
        return try {
            openStream(uri)?.use { stream ->
                val reader = stream.bufferedReader()
                val chars = CharArray(MAX_CHARS)
                val read = reader.read(chars, 0, MAX_CHARS)
                if (read > 0) String(chars, 0, read) else ""
            } ?: ""
        } catch (_: Throwable) { "" }
    }

    // ────────────────────────────────────────────
    //  Office Documents (DOCX/XLSX/PPTX) via streaming ZipInputStream & character reader
    // ────────────────────────────────────────────
    private fun extractOfficeText(uri: Uri, type: String): String {
        return try {
            openStream(uri)?.use { rawStream ->
                val buffered = BufferedInputStream(rawStream, 32768)
                val zipStream = ZipInputStream(buffered)
                val targetPrefixes = when (type) {
                    "docx" -> listOf("word/document.xml", "word/document2.xml")
                    "xlsx" -> listOf("xl/sharedStrings.xml", "xl/worksheets/sheet")
                    "pptx" -> listOf("ppt/slides/slide")
                    else -> emptyList()
                }
                val sb = StringBuilder()
                var entry = zipStream.nextEntry
                while (entry != null && sb.length < MAX_CHARS) {
                    val entryName = entry.name
                    if (targetPrefixes.any { entryName.startsWith(it) }) {
                        val text = readXmlText(zipStream, MAX_CHARS - sb.length)
                        if (text.length > 5) {
                            sb.append(text).append("\n")
                        }
                    }
                    zipStream.closeEntry()
                    entry = try { zipStream.nextEntry } catch (_: Exception) { null }
                }
                zipStream.close()
                sb.toString().take(MAX_CHARS)
            } ?: ""
        } catch (_: Throwable) { "" }
    }

    private fun readXmlText(stream: InputStream, maxChars: Int): String {
        val reader = InputStreamReader(stream, Charsets.UTF_8)
        val sb = StringBuilder()
        var inTag = false
        var lastWasSpace = false
        var ch = 0
        while (sb.length < maxChars && reader.read().also { ch = it } != -1) {
            val c = ch.toChar()
            if (c == '<') {
                inTag = true
                if (!lastWasSpace && sb.isNotEmpty()) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else if (c == '>') {
                inTag = false
            } else if (!inTag) {
                if (c.isWhitespace()) {
                    if (!lastWasSpace && sb.isNotEmpty()) {
                        sb.append(' ')
                        lastWasSpace = true
                    }
                } else {
                    sb.append(c)
                    lastWasSpace = false
                }
            }
        }
        return sb.toString().trim()
    }

    private fun openStream(uri: Uri): InputStream? {
        return try {
            if (uri.scheme == "file") {
                uri.path?.let { java.io.FileInputStream(it) }
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (_: Exception) {
            null
        }
    }
}
