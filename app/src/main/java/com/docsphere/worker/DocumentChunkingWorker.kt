package com.docsphere.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.docsphere.data.local.ChunkDao
import com.docsphere.data.local.ChunkRoomEntity
import com.docsphere.data.local.DocumentDao
import com.docsphere.data.preferences.DocSpherePreferences
import com.docsphere.data.repository.DocumentChunker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

import com.docsphere.ai.InferenceCoordinator
import kotlinx.coroutines.yield

@HiltWorker
class DocumentChunkingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val documentDao: DocumentDao,
    private val chunkDao: ChunkDao,
    private val preferences: DocSpherePreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "docsphere_document_chunking"
        private const val TAG = "DocSphereDebug"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val alreadyCompleted = preferences.hasCompletedChunkingV4.first()
        if (alreadyCompleted && chunkDao.count() > 0) {
            Log.d(TAG, "[DocumentChunkingWorker] Initial chunking already completed. Skipping.")
            return@withContext Result.success()
        }

        Log.d(TAG, "[DocumentChunkingWorker] Starting one-time background chunking of existing documents...")

        InferenceCoordinator.withWorkerLock {
            try {
                val pageSize = 50
                var offset = 0
                var totalChunksCreated = 0
                var totalProcessedDocs = 0

                while (true) {
                    InferenceCoordinator.pauseIfChatActive()

                    val page = documentDao.getDocumentsWithTextPaged(limit = pageSize, offset = offset)
                    if (page.isEmpty()) break

                    for (doc in page) {
                        InferenceCoordinator.pauseIfChatActive()

                        if (doc.extractedText.isNotBlank()) {
                            val chunks = DocumentChunker.splitIntoChunks(
                                documentId = doc.id,
                                documentName = doc.displayName,
                                text = doc.extractedText
                            )

                            val roomEntities = chunks.map {
                                ChunkRoomEntity(
                                    id = it.id,
                                    documentId = it.documentId,
                                    chunkIndex = it.chunkIndex,
                                    text = it.text
                                )
                            }

                            if (roomEntities.isNotEmpty()) {
                                // Batch in groups of 20 with yield() and pause check per batch to avoid long write locks
                                roomEntities.chunked(20).forEach { batch ->
                                    InferenceCoordinator.pauseIfChatActive()
                                    chunkDao.upsertAll(batch)
                                    yield()
                                }
                                totalChunksCreated += roomEntities.size
                            }
                        }
                        totalProcessedDocs++
                        yield()
                    }

                    offset += pageSize
                    Log.d(
                        TAG,
                        "[DocumentChunkingWorker] Progress: $totalProcessedDocs documents processed (Total chunks created: $totalChunksCreated)"
                    )
                }

                preferences.setChunkingV4Completed(true)
                Log.d(TAG, "[DocumentChunkingWorker] Finished! Total documents chunked: $totalProcessedDocs, Total chunks: $totalChunksCreated")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "[DocumentChunkingWorker] Failed to chunk documents: ${e.message}", e)
                Result.retry()
            }
        }
    }
}
