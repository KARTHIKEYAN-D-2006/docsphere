package com.docsphere.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.docsphere.data.local.ChunkDao
import com.docsphere.data.local.DocumentDao
import com.docsphere.data.local.DocumentFieldDao
import com.docsphere.data.preferences.DocSpherePreferences
import com.docsphere.data.repository.DocumentChunk
import com.docsphere.data.repository.DocumentFieldExtractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

import com.docsphere.ai.InferenceCoordinator
import kotlinx.coroutines.yield

@HiltWorker
class FieldExtractionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val documentDao: DocumentDao,
    private val chunkDao: ChunkDao,
    private val documentFieldDao: DocumentFieldDao,
    private val preferences: DocSpherePreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "docsphere_field_extraction"
        private const val TAG = "DocSphereDebug"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val alreadyCompleted = preferences.hasCompletedFieldExtractionV7.first()
        if (alreadyCompleted && documentFieldDao.count() > 0) {
            Log.d(TAG, "[FieldExtractionWorker] Field extraction V7 already completed. Skipping.")
            return@withContext Result.success()
        }

        Log.d(TAG, "[FieldExtractionWorker] Re-running field extraction to purge legacy invalid fields and apply strict Verhoeff Aadhaar validation...")
        // Requirement 5: "remove old wrong values (the 'Found 4381 matching documents' bug)"
        documentFieldDao.clearAll()

        InferenceCoordinator.withWorkerLock {
            try {
                val pageSize = 50
                var offset = 0
                var totalFieldsCreated = 0
                var totalProcessedDocs = 0

                while (true) {
                    InferenceCoordinator.pauseIfChatActive()

                    val page = documentDao.getDocumentsWithTextPaged(limit = pageSize, offset = offset)
                    if (page.isEmpty()) break

                    for (doc in page) {
                        InferenceCoordinator.pauseIfChatActive()

                        if (doc.extractedText.isNotBlank()) {
                            // Fetch existing chunks for this document to accurately attribute sourceChunkIndex
                            val existingChunks = chunkDao.getChunksByDocumentId(doc.id).map { chunkEntity ->
                                DocumentChunk(
                                    id = chunkEntity.id,
                                    documentId = chunkEntity.documentId,
                                    documentName = doc.displayName,
                                    chunkIndex = chunkEntity.chunkIndex,
                                    text = chunkEntity.text
                                )
                            }

                            val fields = DocumentFieldExtractor.extractFields(
                                documentId = doc.id,
                                documentName = doc.displayName,
                                text = doc.extractedText,
                                chunks = existingChunks
                            )

                            if (fields.isNotEmpty()) {
                                // Batch in groups of 20 with yield() and pause check per batch
                                fields.chunked(20).forEach { batch ->
                                    InferenceCoordinator.pauseIfChatActive()
                                    documentFieldDao.upsertAll(batch)
                                    yield()
                                }
                                totalFieldsCreated += fields.size
                            }
                        }
                        totalProcessedDocs++
                        yield()
                    }

                    offset += pageSize
                    Log.d(
                        TAG,
                        "[FieldExtractionWorker] Progress: $totalProcessedDocs documents processed (Total fields extracted: $totalFieldsCreated)"
                    )
                }

                preferences.setFieldExtractionV7Completed(true)
                preferences.setFieldExtractionV5Completed(true)
                Log.d(TAG, "[FieldExtractionWorker] Finished! Total documents processed: $totalProcessedDocs, Total fields: $totalFieldsCreated")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "[FieldExtractionWorker] Failed to extract fields: ${e.message}", e)
                Result.retry()
            }
        }
    }
}
