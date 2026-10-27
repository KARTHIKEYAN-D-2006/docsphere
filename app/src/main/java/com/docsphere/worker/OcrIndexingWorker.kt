package com.docsphere.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.docsphere.data.extractor.OcrResult
import com.docsphere.data.extractor.TextExtractor
import com.docsphere.data.local.ChunkDao
import com.docsphere.data.local.ChunkRoomEntity
import com.docsphere.data.local.DocumentDao
import com.docsphere.data.local.DocumentFieldDao
import com.docsphere.data.preferences.DocSpherePreferences
import com.docsphere.data.repository.DocumentChunk
import com.docsphere.data.repository.DocumentChunker
import com.docsphere.data.repository.DocumentFieldExtractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import com.docsphere.ai.InferenceCoordinator

/**
 * One-time background WorkManager job that processes all images that were previously
 * skipped or stored with an empty OCR result, and all images whose ocrStatus is NULL
 * (never attempted) or "failed:..." (previous attempt failed, retry now).
 *
 * Processing pipeline per image:
 *   1. OCR via ML Kit at up to 2048px with 15s timeout (via TextExtractor)
 *   2. Save extractedText + ocrStatus to Room
 *   3. Chunk the text (DocumentChunker, ~1200 chars / ~200 overlap)
 *   4. Extract structured fields (Aadhaar, PAN, invoice total, dates, phone, email)
 *   5. Update FTS indexes (via chunkDao.upsertAll and chunkFts triggers)
 *
 * Concurrency: limited to 2 parallel OCR tasks to protect battery and RAM.
 * Progress: shows a notification with current / total image count.
 * Skip: images whose ocrStatus is "ok" or "skipped" are not re-processed.
 * Already-indexed images that have not changed are also skipped (checked by ocrStatus != null).
 */
@HiltWorker
class OcrIndexingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val documentDao: DocumentDao,
    private val chunkDao: ChunkDao,
    private val chunkEmbeddingDao: com.docsphere.data.local.ChunkEmbeddingDao,
    private val embedder: com.docsphere.ai.embedding.OnDeviceEmbedder,
    private val documentFieldDao: DocumentFieldDao,
    private val extractor: TextExtractor,
    private val preferences: DocSpherePreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "docsphere_ocr_indexing_v6"
        private const val TAG = "DocSphereDebug"
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "docsphere_ocr_channel"
        /** Max parallel OCR tasks — 2 keeps the ML Kit executor threads under control */
        private const val MAX_CONCURRENT_OCR = 2
    }

    private suspend fun purgeExistingDocSphereScreenshots() {
        try {
            var offset = 0
            while (true) {
                val batch = documentDao.getDocumentsWithTextPaged(limit = 100, offset = offset)
                if (batch.isEmpty()) break
                for (doc in batch) {
                    if (com.docsphere.util.DocSphereScreenshotFilter.isDocSphereScreenshot(doc.extractedText)) {
                        Log.d(TAG, "[OcrIndexingWorker] Purging indexed DocSphere screenshot: ${doc.displayName} (${doc.id})")
                        chunkEmbeddingDao.deleteByDocumentId(doc.id)
                        chunkDao.deleteByDocumentId(doc.id)
                        documentFieldDao.deleteByDocumentId(doc.id)
                        documentDao.updateOcrResult(
                            id = doc.id,
                            status = "skipped_screenshot",
                            text = "",
                            indexed = false
                        )
                    }
                }
                offset += batch.size
            }
        } catch (e: Throwable) {
            Log.e(TAG, "[OcrIndexingWorker] Error purging screenshots: ${e.message}")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        purgeExistingDocSphereScreenshots()

        val alreadyCompleted = preferences.hasCompletedOcrIndexingV6.first() && preferences.hasCompletedOcrRetryPass.first()
        if (alreadyCompleted) {
            Log.d(TAG, "[OcrIndexingWorker] Backfill and retry pass already completed. Skipping.")
            return@withContext Result.success()
        }

        createNotificationChannel()
        Log.d(TAG, "[OcrIndexingWorker] Starting OCR backfill for images without OCR results...")

        InferenceCoordinator.withWorkerLock {
            try {
                val images = documentDao.getImagesNeedingOcr(limit = 5000)
                val totalImages = images.size

                if (totalImages == 0) {
                    Log.d(TAG, "[OcrIndexingWorker] No images need OCR. Done.")
                    preferences.setOcrIndexingV6Completed(true)
                    preferences.setOcrRetryPassCompleted(true)
                    return@withWorkerLock Result.success()
                }

                Log.d(TAG, "[OcrIndexingWorker] Found $totalImages images to OCR.")
                showProgressNotification("Starting OCR…", 0, totalImages)

                val semaphore = Semaphore(MAX_CONCURRENT_OCR)
                var processed = 0
                var succeeded = 0
                var failed = 0
                var skipped = 0

                for (doc in images) {
                    InferenceCoordinator.pauseIfChatActive()
                    semaphore.withPermit {
                        InferenceCoordinator.pauseIfChatActive()
                        val uri = Uri.parse(doc.localUri ?: doc.id)
                        val startMs = System.currentTimeMillis()

                        val ocrResult = extractor.extractImageOcr(
                            uri = uri,
                            displayName = doc.displayName,
                            sizeBytes = doc.fileSizeBytes
                        )

                        val elapsedMs = System.currentTimeMillis() - startMs

                        when (ocrResult) {
                            is OcrResult.Success -> {
                                val text = ocrResult.text
                                if (com.docsphere.util.DocSphereScreenshotFilter.isDocSphereScreenshot(text)) {
                                    Log.d(TAG, "[OcrIndexingWorker] SKIPPED '${doc.displayName}' — DocSphere screenshot detected")
                                    InferenceCoordinator.pauseIfChatActive()
                                    documentDao.updateOcrResult(
                                        id = doc.id,
                                        status = "skipped_screenshot",
                                        text = "",
                                        indexed = false
                                    )
                                    chunkEmbeddingDao.deleteByDocumentId(doc.id)
                                    chunkDao.deleteByDocumentId(doc.id)
                                    documentFieldDao.deleteByDocumentId(doc.id)
                                    skipped++
                                } else {
                                    Log.d(TAG, "[OcrIndexingWorker] OK '${doc.displayName}' — ${text.length} chars (${elapsedMs}ms)")

                                    InferenceCoordinator.pauseIfChatActive()
                                    // Save OCR text + status
                                    documentDao.updateOcrResult(
                                        id = doc.id,
                                        status = "ok",
                                        text = text,
                                        indexed = true
                                    )

                                    // Chunk the text
                                    val chunks = DocumentChunker.splitIntoChunks(
                                        documentId = doc.id,
                                        documentName = doc.displayName,
                                        text = text
                                    )
                                    if (chunks.isNotEmpty()) {
                                        val roomChunks = chunks.map {
                                            ChunkRoomEntity(
                                                id = it.id,
                                                documentId = it.documentId,
                                                chunkIndex = it.chunkIndex,
                                                text = it.text
                                            )
                                        }
                                        roomChunks.chunked(20).forEach { batch ->
                                            InferenceCoordinator.pauseIfChatActive()
                                            chunkDao.upsertAll(batch)
                                            yield()
                                        }

                                        // Generate on-device semantic embeddings for all chunks
                                        val embeddingEntities = chunks.map { ch ->
                                            com.docsphere.data.local.ChunkEmbeddingEntity(
                                                chunkId = ch.id,
                                                vector = com.docsphere.ai.embedding.OnDeviceEmbedder.serialize(embedder.embed(ch.text))
                                            )
                                        }
                                        embeddingEntities.chunked(20).forEach { batch ->
                                            InferenceCoordinator.pauseIfChatActive()
                                            chunkEmbeddingDao.insertAll(batch)
                                            yield()
                                        }
                                    }

                                    // Extract structured fields
                                    val fields = DocumentFieldExtractor.extractFields(
                                        documentId = doc.id,
                                        documentName = doc.displayName,
                                        text = text,
                                        chunks = chunks
                                    )
                                    if (fields.isNotEmpty()) {
                                        fields.chunked(20).forEach { batch ->
                                            InferenceCoordinator.pauseIfChatActive()
                                            documentFieldDao.upsertAll(batch)
                                            yield()
                                        }
                                    }

                                    succeeded++
                                }
                            }

                            is OcrResult.Failed -> {
                                val reason = ocrResult.reason
                                Log.d(TAG, "[OcrIndexingWorker] FAILED '${doc.displayName}' — $reason (${elapsedMs}ms)")

                                InferenceCoordinator.pauseIfChatActive()
                                // Store failure status so the UI can show a "Retry" option.
                                // If it was already a failed retry, mark failed_retry so background worker doesn't loop infinitely.
                                val isRetry = doc.ocrStatus?.startsWith("failed:") == true
                                val newStatus = if (isRetry) "failed_retry:$reason" else "failed:$reason"
                                documentDao.updateOcrResult(
                                    id = doc.id,
                                    status = newStatus,
                                    text = doc.extractedText, // preserve existing (may be empty)
                                    indexed = doc.isIndexed
                                )
                                failed++
                            }

                            OcrResult.Skipped -> {
                                Log.d(TAG, "[OcrIndexingWorker] SKIPPED '${doc.displayName}' — too small/large (${elapsedMs}ms)")
                                documentDao.updateOcrResult(
                                    id = doc.id,
                                    status = "skipped",
                                    text = doc.extractedText,
                                    indexed = doc.isIndexed
                                )
                                skipped++
                            }
                        }

                        processed++
                        yield()
                        if (processed % 5 == 0 || processed == totalImages) {
                            showProgressNotification(
                                message = "OCR: $processed/$totalImages — ✓$succeeded ✗$failed skip$skipped",
                                current = processed,
                                total = totalImages
                            )
                        }
                    }
                }

                preferences.setOcrIndexingV6Completed(true)
                preferences.setOcrRetryPassCompleted(true)
                Log.d(
                    TAG,
                    "[OcrIndexingWorker] Done. Total=$totalImages Succeeded=$succeeded Failed=$failed Skipped=$skipped"
                )
                showCompleteNotification(succeeded, failed, skipped)
                Result.success()

            } catch (e: Exception) {
                Log.e(TAG, "[OcrIndexingWorker] Fatal error during OCR backfill: ${e.message}", e)
                Result.retry()
            }
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DocSphere OCR",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Image text extraction (OCR) progress"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(message: String, current: Int, total: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.docsphere.R.drawable.ic_docsphere_emblem)
            .setContentTitle("DOCSPHERE — SCANNING IMAGES")
            .setContentText(message)
            .apply {
                if (total > 0) setProgress(total, current, false)
                else setProgress(0, 0, true)
            }
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompleteNotification(succeeded: Int, failed: Int, skipped: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val summary = "Indexed $succeeded images" +
            (if (failed > 0) " • $failed failed (tap to retry)" else "") +
            (if (skipped > 0) " • $skipped skipped" else "")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.docsphere.R.drawable.ic_docsphere_emblem)
            .setContentTitle("DOCSPHERE — IMAGE SCAN COMPLETE")
            .setContentText(summary)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
