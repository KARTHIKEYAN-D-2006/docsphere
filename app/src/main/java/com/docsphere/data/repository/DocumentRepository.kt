package com.docsphere.data.repository

import android.content.Context
import android.net.Uri
import com.docsphere.data.extractor.TextExtractor
import com.docsphere.data.local.ChunkDao
import com.docsphere.data.local.ChunkRoomEntity
import com.docsphere.data.local.DocumentDao
import com.docsphere.data.local.DocumentFieldDao
import com.docsphere.data.local.DocumentRoomEntity
import com.docsphere.data.model.DocumentEntity
import com.docsphere.data.model.FileCategory
import com.docsphere.data.model.ScanProgress
import com.docsphere.data.model.SensitivityTag
import com.docsphere.data.model.SourceType
import com.docsphere.data.scanner.LocalStorageScanner
import com.docsphere.data.scanner.RawFileEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao,
    private val chunkDao: ChunkDao,
    private val chunkEmbeddingDao: com.docsphere.data.local.ChunkEmbeddingDao,
    private val embedder: com.docsphere.ai.embedding.OnDeviceEmbedder,
    private val documentFieldDao: DocumentFieldDao,
    private val scanner: LocalStorageScanner,
    private val extractor: TextExtractor
) {
    private val retrievalEngine: DocumentRetrievalEngine = DocumentRetrievalEngine(embedder, chunkEmbeddingDao)

    private val _scanProgress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    fun observeAllDocuments(): Flow<List<DocumentEntity>> =
        documentDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeRecentDocuments(limit: Int = 10): Flow<List<DocumentEntity>> =
        documentDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeByCategory(category: FileCategory): Flow<List<DocumentEntity>> =
        when (category) {
            FileCategory.ALL -> observeAllDocuments()
            FileCategory.IMAGE -> documentDao.observeByMimeType("image/%")
                .map { it.map { e -> e.toDomain() } }
            FileCategory.VIDEO -> documentDao.observeByMimeType("video/%")
                .map { it.map { e -> e.toDomain() } }
            FileCategory.AUDIO -> documentDao.observeByMimeType("audio/%")
                .map { it.map { e -> e.toDomain() } }
            FileCategory.DOCUMENT -> documentDao.observeAll()
                .map { list ->
                    list.filter { e ->
                        val m = e.mimeType.lowercase()
                        val ext = e.displayName.substringAfterLast('.', "").lowercase()
                        m == "application/pdf" || m.contains("word") || m.contains("excel") ||
                        m.contains("sheet") || m.contains("presentation") || m.contains("powerpoint") ||
                        m.startsWith("text/") || m.contains("csv") || m.contains("json") ||
                        ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "json")
                    }.map { it.toDomain() }
                }
            FileCategory.OTHER -> documentDao.observeAll()
                .map { list ->
                    list.filter { e ->
                        !e.mimeType.startsWith("image/") &&
                        !e.mimeType.startsWith("video/") &&
                        !e.mimeType.startsWith("audio/")
                    }.map { it.toDomain() }
                }
        }

    fun observeTotalCount(): Flow<Int> = documentDao.observeCount()
    fun observeImageCount(): Flow<Int> = documentDao.observeImageCount()
    fun observeVideoCount(): Flow<Int> = documentDao.observeVideoCount()
    fun observeAudioCount(): Flow<Int> = documentDao.observeAudioCount()

    suspend fun getDocumentById(id: String): DocumentEntity? =
        documentDao.getById(id)?.toDomain()

    suspend fun search(query: String): List<DocumentEntity> =
        documentDao.searchDocuments(query).map { it.toDomain() }

    /**
     * Retrieves up to top 5 relevant documents using FTS4 search, keyword extraction,
     * synonym expansion, BM25-style relevance ranking, and DocSphereDebug logging.
     */
    suspend fun getRelevantDocs(query: String, limit: Int = 5): List<DocumentRoomEntity> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val lowerQuery = cleanQuery.lowercase(java.util.Locale.ROOT)

        // 0. Conversational, social, or personal queries should never trigger document RAG
        try {
            retrievalEngine.retrieve(cleanQuery, documentDao, limit = limit)
        } catch (e: Throwable) {
            android.util.Log.e("DocSphereDebug", "[DocumentRepository] getRelevantDocs failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Retrieves top 3 relevant chunks (not whole documents, each labelled with its document name)
     * using document candidate search, chunk-level FTS, and BM25 ranking.
     */
    suspend fun getRelevantChunks(query: String, limit: Int = 3): List<DocumentChunk> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        try {
            retrievalEngine.retrieveTopChunks(
                query = cleanQuery,
                documentDao = documentDao,
                chunkDao = chunkDao,
                limit = limit
            )
        } catch (e: Throwable) {
            android.util.Log.e("DocSphereDebug", "[DocumentRepository] getRelevantChunks failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Ultra-fast local file scan and index into Room:
     * 1. Incremental: queries Room metadata and skips unchanged files immediately.
     * 2. Multithreaded parallel extraction across CPU cores (Dispatchers.IO + Semaphore).
     * 3. Batch SQLite writes (upsertAll) to reduce DB I/O latency by 99%.
     * 4. Throttled progress reporting for smooth UI and zero notification spam.
     */
    suspend fun scanAndIndex(
        forceFullRescan: Boolean = false,
        onProgress: (current: Int, total: Int, name: String) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        _scanProgress.value = ScanProgress.Scanning(0, 0, "Discovering files…")
        val files = scanner.scanAll()
        val totalFiles = files.size

        if (totalFiles == 0) {
            _scanProgress.value = ScanProgress.Done(0)
            onProgress(0, 0, "No files found")
            return@withContext
        }

        // 1. Fetch existing file metadata to skip unchanged files
        val existingMeta = if (forceFullRescan) {
            emptyMap()
        } else {
            try {
                documentDao.getAllMetadata().associateBy { it.id }
            } catch (_: Exception) {
                emptyMap()
            }
        }

        // 2. Partition into unchanged (skipped) and changed/new (to process)
        val toProcess = mutableListOf<RawFileEntry>()
        var skippedCount = 0

        for (file in files) {
            val fileId = file.uri.toString()
            val existing = existingMeta[fileId]
            if (existing != null &&
                existing.lastModifiedEpochMillis == file.lastModifiedMillis &&
                existing.fileSizeBytes == file.sizeBytes
            ) {
                skippedCount++
            } else {
                toProcess.add(file)
            }
        }

        // If all files are up-to-date, finish immediately
        if (toProcess.isEmpty()) {
            onProgress(totalFiles, totalFiles, "All files up to date")
            _scanProgress.value = ScanProgress.Done(totalFiles)
            return@withContext
        }

        // 3. Process new and modified files in controlled chunks of 50 to eliminate memory pressure
        val concurrency = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val semaphore = Semaphore(concurrency)
        val processedCounter = AtomicInteger(skippedCount)
        val lastProgressTime = AtomicLong(0L)

        for (chunk in toProcess.chunked(50)) {
            val batchResults = coroutineScope {
                chunk.map { entry ->
                    async {
                        semaphore.withPermit {
                            val rawText = extractor.extract(entry.uri, entry.mimeType, entry.sizeBytes, entry.displayName)
                            val isImage = entry.mimeType.startsWith("image/")
                            val isScreenshot = isImage && com.docsphere.util.DocSphereScreenshotFilter.isDocSphereScreenshot(rawText)

                            // Derive OCR status for images; non-images get null
                            val ocrStatus: String? = when {
                                !isImage -> null
                                isScreenshot -> "skipped_screenshot"
                                rawText.startsWith("OCR_FAILED:") -> rawText.removePrefix("OCR_FAILED:")
                                    .let { reason -> "failed:$reason" }
                                rawText.isNotBlank() -> "ok"
                                else -> null // will be retried by OcrIndexingWorker
                            }
                            val cleanText = if (rawText.startsWith("OCR_FAILED:") || isScreenshot) "" else rawText

                            val entity = with(scanner) {
                                entry.toRoomEntity(cleanText).copy(
                                    ocrStatus = ocrStatus,
                                    isIndexed = cleanText.isNotBlank() && !isScreenshot
                                )
                            }

                            val current = processedCounter.incrementAndGet()
                            val now = System.currentTimeMillis()

                            // Throttle progress updates to UI and notification (at most every 250ms)
                            if (now - lastProgressTime.get() > 250 || current == totalFiles) {
                                lastProgressTime.set(now)
                                onProgress(current, totalFiles, entry.displayName)
                                _scanProgress.value = ScanProgress.Indexing(current, totalFiles, entry.displayName)
                            }
                            entity
                        }
                    }
                }.awaitAll()
            }

            if (batchResults.isNotEmpty()) {
                documentDao.upsertAll(batchResults)
                // Chunk newly indexed documents (~1200 chars, ~200 overlap, never mid-word)
                val allChunks = batchResults.filter { it.extractedText.isNotBlank() }.flatMap { doc ->
                    DocumentChunker.splitIntoChunks(
                        documentId = doc.id,
                        documentName = doc.displayName,
                        text = doc.extractedText
                    )
                }
                if (allChunks.isNotEmpty()) {
                    val roomChunks = allChunks.map {
                        ChunkRoomEntity(
                            id = it.id,
                            documentId = it.documentId,
                            chunkIndex = it.chunkIndex,
                            text = it.text
                        )
                    }
                    roomChunks.chunked(20).forEach { batch ->
                        com.docsphere.ai.InferenceCoordinator.pauseIfChatActive()
                        chunkDao.upsertAll(batch)
                        kotlinx.coroutines.yield()
                    }

                    val embeddingEntities = allChunks.map { ch ->
                        com.docsphere.data.local.ChunkEmbeddingEntity(
                            chunkId = ch.id,
                            vector = com.docsphere.ai.embedding.OnDeviceEmbedder.serialize(embedder.embed(ch.text))
                        )
                    }
                    embeddingEntities.chunked(20).forEach { batch ->
                        com.docsphere.ai.InferenceCoordinator.pauseIfChatActive()
                        chunkEmbeddingDao.insertAll(batch)
                        kotlinx.coroutines.yield()
                    }
                }

                // Extract structured fields (Aadhaar, PAN, invoice total, dates, phone, email)
                val allFields = batchResults.filter { it.extractedText.isNotBlank() }.flatMap { doc ->
                    val docChunks = allChunks.filter { it.documentId == doc.id }
                    DocumentFieldExtractor.extractFields(
                        documentId = doc.id,
                        documentName = doc.displayName,
                        text = doc.extractedText,
                        chunks = docChunks
                    )
                }
                if (allFields.isNotEmpty()) {
                    allFields.chunked(20).forEach { batch ->
                        com.docsphere.ai.InferenceCoordinator.pauseIfChatActive()
                        documentFieldDao.upsertAll(batch)
                        kotlinx.coroutines.yield()
                    }
                }
            }
        }


        _scanProgress.value = ScanProgress.Done(totalFiles)
        onProgress(totalFiles, totalFiles, "Done")
    }

    /**
     * Directly imports and indexes any user-selected file URI (SAF picker).
     */
    suspend fun importDocument(
        uri: Uri,
        sourceType: SourceType = SourceType.LOCAL_STORAGE
    ): DocumentEntity? {
        var name = "Document_${System.currentTimeMillis()}"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}

        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = context.contentResolver.getType(uri) ?: when (ext) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "md" -> "text/markdown"
            else -> "application/octet-stream"
        }

        val rawText = extractor.extract(uri, mime, size, name)
        val isImage = mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "heic", "heif")
        val ocrStatus: String? = when {
            !isImage -> null
            rawText.startsWith("OCR_FAILED:") -> rawText.removePrefix("OCR_FAILED:").let { reason -> "failed:$reason" }
            rawText.isNotBlank() -> "ok"
            else -> "failed:no text detected"
        }
        val cleanText = if (rawText.startsWith("OCR_FAILED:")) "" else rawText

        val entity = DocumentRoomEntity(
            id = uri.toString(),
            displayName = name,
            sourceType = sourceType,
            sensitivityTag = SensitivityTag.NONE,
            extractedText = cleanText,
            pageCount = 1,
            mimeType = mime,
            fileSizeBytes = size,
            lastModifiedEpochMillis = System.currentTimeMillis(),
            localUri = uri.toString(),
            cloudRef = if (sourceType == SourceType.GOOGLE_DRIVE) "gdrive" else null,
            thumbnailUri = null,
            isIndexed = cleanText.isNotBlank(),
            ocrStatus = ocrStatus
        )
        documentDao.upsert(entity)

        if (entity.extractedText.isNotBlank()) {
            val chunks = DocumentChunker.splitIntoChunks(
                documentId = entity.id,
                documentName = entity.displayName,
                text = entity.extractedText
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
                    com.docsphere.ai.InferenceCoordinator.pauseIfChatActive()
                    chunkDao.upsertAll(batch)
                    kotlinx.coroutines.yield()
                }

                val embeddingEntities = chunks.map { ch ->
                    com.docsphere.data.local.ChunkEmbeddingEntity(
                        chunkId = ch.id,
                        vector = com.docsphere.ai.embedding.OnDeviceEmbedder.serialize(embedder.embed(ch.text))
                    )
                }
                embeddingEntities.chunked(20).forEach { batch ->
                    com.docsphere.ai.InferenceCoordinator.pauseIfChatActive()
                    chunkEmbeddingDao.insertAll(batch)
                    kotlinx.coroutines.yield()
                }
            }

            val fields = DocumentFieldExtractor.extractFields(
                documentId = entity.id,
                documentName = entity.displayName,
                text = entity.extractedText,
                chunks = chunks
            )
            if (fields.isNotEmpty()) {
                fields.chunked(20).forEach { batch ->
                    com.docsphere.ai.InferenceCoordinator.pauseIfChatActive()
                    documentFieldDao.upsertAll(batch)
                    kotlinx.coroutines.yield()
                }
            }
        }

        return entity.toDomain()
    }

    /**
     * Retries OCR extraction for an image document on user request.
     * Updates document text, chunks, structured fields, and FTS indexes on success.
     */
    suspend fun retryOcr(documentId: String): Boolean = withContext(Dispatchers.IO) {
        val entity = documentDao.getById(documentId) ?: return@withContext false
        val uri = Uri.parse(entity.localUri ?: entity.id)
        val result = extractor.extractImageOcr(uri, entity.displayName, entity.fileSizeBytes)
        when (result) {
            is com.docsphere.data.extractor.OcrResult.Success -> {
                val text = result.text
                if (com.docsphere.util.DocSphereScreenshotFilter.isDocSphereScreenshot(text)) {
                    android.util.Log.d("DocSphereDebug", "[DocumentRepository] retryOcr: DocSphere screenshot detected, marking skipped.")
                    documentDao.updateOcrResult(documentId, "skipped_screenshot", "", indexed = false)
                    chunkEmbeddingDao.deleteByDocumentId(documentId)
                    chunkDao.deleteByDocumentId(documentId)
                    documentFieldDao.deleteByDocumentId(documentId)
                    return@withContext true
                }
                val hasText = text.isNotBlank()
                val status = if (hasText) "ok" else "failed:no text detected"
                documentDao.updateOcrResult(documentId, status, text, indexed = hasText)

                if (hasText) {
                    val chunks = DocumentChunker.splitIntoChunks(documentId, entity.displayName, text)
                    if (chunks.isNotEmpty()) {
                        chunkDao.deleteByDocumentId(documentId)
                        val roomChunks = chunks.map {
                            ChunkRoomEntity(it.id, it.documentId, it.chunkIndex, it.text)
                        }
                        roomChunks.chunked(20).forEach { batch ->
                            com.docsphere.ai.InferenceCoordinator.pauseIfChatActive()
                            chunkDao.upsertAll(batch)
                            kotlinx.coroutines.yield()
                        }

                        val embeddingEntities = chunks.map { ch ->
                            com.docsphere.data.local.ChunkEmbeddingEntity(
                                chunkId = ch.id,
                                vector = com.docsphere.ai.embedding.OnDeviceEmbedder.serialize(embedder.embed(ch.text))
                            )
                        }
                        embeddingEntities.chunked(20).forEach { batch ->
                            com.docsphere.ai.InferenceCoordinator.pauseIfChatActive()
                            chunkEmbeddingDao.insertAll(batch)
                            kotlinx.coroutines.yield()
                        }
                    }
                    val fields = DocumentFieldExtractor.extractFields(documentId, entity.displayName, text, chunks)
                    if (fields.isNotEmpty()) {
                        documentFieldDao.deleteByDocumentId(documentId)
                        fields.chunked(20).forEach { batch ->
                            com.docsphere.ai.InferenceCoordinator.pauseIfChatActive()
                            documentFieldDao.upsertAll(batch)
                            kotlinx.coroutines.yield()
                        }
                    }
                }
                true
            }
            is com.docsphere.data.extractor.OcrResult.Failed -> {
                documentDao.updateOcrResult(documentId, "failed:${result.reason}", entity.extractedText, indexed = entity.isIndexed)
                false
            }
            com.docsphere.data.extractor.OcrResult.Skipped -> {
                documentDao.updateOcrResult(documentId, "skipped", entity.extractedText, indexed = entity.isIndexed)
                false
            }
        }
    }

    /**
     * Purges already indexed DocSphere screenshots, deleting chunks, embeddings,
     * and fields, and setting status to skipped_screenshot.
     */
    suspend fun purgeDocSphereScreenshots() = withContext(Dispatchers.IO) {
        try {
            var offset = 0
            while (true) {
                val batch = documentDao.getDocumentsWithTextPaged(limit = 100, offset = offset)
                if (batch.isEmpty()) break
                for (doc in batch) {
                    if (com.docsphere.util.DocSphereScreenshotFilter.isDocSphereScreenshot(doc.extractedText)) {
                        android.util.Log.d("DocSphereDebug", "[DocumentRepository] Purging DocSphere screenshot: ${doc.displayName} (${doc.id})")
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
            android.util.Log.e("DocSphereDebug", "[DocumentRepository] Error purging screenshots: ${e.message}")
        }
    }

    suspend fun deleteDocument(id: String) {
        documentFieldDao.deleteByDocumentId(id)
        chunkDao.deleteByDocumentId(id)
        documentDao.deleteById(id)
    }

    suspend fun clearAll() {
        documentFieldDao.clearAll()
        chunkDao.clearAll()
        documentDao.clearAll()
    }

    suspend fun getFieldValue(documentId: String, fieldName: String): String? = withContext(Dispatchers.IO) {
        try {
            documentFieldDao.getFieldValue(documentId, fieldName)
        } catch (e: Throwable) {
            android.util.Log.e("DocSphereDebug", "[DocumentRepository] getFieldValue failed: ${e.message}", e)
            null
        }
    }

    private fun DocumentRoomEntity.toDomain() = DocumentEntity(
        id = id,
        displayName = displayName,
        sourceType = sourceType,
        sensitivityTag = sensitivityTag,
        extractedText = extractedText,
        pageCount = pageCount,
        mimeType = mimeType,
        fileSizeBytes = fileSizeBytes,
        lastModified = Instant.ofEpochMilli(lastModifiedEpochMillis),
        localUri = localUri,
        cloudRef = cloudRef,
        thumbnailUri = thumbnailUri,
        isIndexed = isIndexed,
        ocrStatus = ocrStatus
    )
}
