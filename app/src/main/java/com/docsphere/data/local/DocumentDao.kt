package com.docsphere.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentRoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(documents: List<DocumentRoomEntity>)

    @Query("""
        SELECT id, displayName, sourceType, sensitivityTag, '' AS extractedText,
               pageCount, mimeType, fileSizeBytes, lastModifiedEpochMillis,
               localUri, cloudRef, thumbnailUri, isIndexed, ocrStatus
        FROM documents 
        ORDER BY lastModifiedEpochMillis DESC
    """)
    fun observeAll(): Flow<List<DocumentRoomEntity>>

    @Query("""
        SELECT id, displayName, sourceType, sensitivityTag, '' AS extractedText,
               pageCount, mimeType, fileSizeBytes, lastModifiedEpochMillis,
               localUri, cloudRef, thumbnailUri, isIndexed, ocrStatus
        FROM documents 
        WHERE sourceType = :sourceType 
        ORDER BY lastModifiedEpochMillis DESC
    """)
    fun observeBySource(sourceType: String): Flow<List<DocumentRoomEntity>>

    @Query("""
        SELECT id, displayName, sourceType, sensitivityTag, '' AS extractedText,
               pageCount, mimeType, fileSizeBytes, lastModifiedEpochMillis,
               localUri, cloudRef, thumbnailUri, isIndexed, ocrStatus
        FROM documents 
        WHERE mimeType LIKE :mimePattern 
        ORDER BY lastModifiedEpochMillis DESC
    """)
    fun observeByMimeType(mimePattern: String): Flow<List<DocumentRoomEntity>>

    @Query("""
        SELECT id, displayName, sourceType, sensitivityTag, '' AS extractedText,
               pageCount, mimeType, fileSizeBytes, lastModifiedEpochMillis,
               localUri, cloudRef, thumbnailUri, isIndexed, ocrStatus
        FROM documents 
        WHERE displayName LIKE '%' || :query || '%' 
           OR extractedText LIKE '%' || :query || '%'
        ORDER BY lastModifiedEpochMillis DESC
        LIMIT 60
    """)
    suspend fun searchDocuments(query: String): List<DocumentRoomEntity>

    @Query("""
        SELECT id, displayName, sourceType, sensitivityTag,
               substr(extractedText, 1, 1000) AS extractedText,
               pageCount, mimeType, fileSizeBytes, lastModifiedEpochMillis,
               localUri, cloudRef, thumbnailUri, isIndexed, ocrStatus
        FROM documents 
        WHERE displayName LIKE '%' || :query || '%' 
           OR extractedText LIKE '%' || :query || '%'
        ORDER BY lastModifiedEpochMillis DESC
        LIMIT :limit
    """)
    suspend fun searchDocumentsForAi(query: String, limit: Int = 8): List<DocumentRoomEntity>

    @Query("""
        SELECT documents.id, documents.displayName, documents.sourceType, documents.sensitivityTag,
               substr(documents.extractedText, 1, 1000) AS extractedText,
               documents.pageCount, documents.mimeType, documents.fileSizeBytes,
               documents.lastModifiedEpochMillis, documents.localUri, documents.cloudRef,
               documents.thumbnailUri, documents.isIndexed, documents.ocrStatus
        FROM documents 
        JOIN documents_fts ON documents.rowid = documents_fts.docid 
        WHERE documents_fts MATCH :ftsQuery
        LIMIT :limit
    """)
    suspend fun searchDocumentsFts(ftsQuery: String, limit: Int = 30): List<DocumentRoomEntity>

    @Query("SELECT COUNT(*) FROM documents")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE mimeType LIKE 'image/%'")
    fun observeImageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE mimeType LIKE 'video/%'")
    fun observeVideoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE mimeType LIKE 'audio/%'")
    fun observeAudioCount(): Flow<Int>

    @Query("""
        SELECT id, displayName, sourceType, sensitivityTag, '' AS extractedText,
               pageCount, mimeType, fileSizeBytes, lastModifiedEpochMillis,
               localUri, cloudRef, thumbnailUri, isIndexed, ocrStatus
        FROM documents 
        ORDER BY lastModifiedEpochMillis DESC 
        LIMIT :limit
    """)
    fun observeRecent(limit: Int = 10): Flow<List<DocumentRoomEntity>>

    @Query("SELECT * FROM documents WHERE extractedText != '' ORDER BY lastModifiedEpochMillis DESC LIMIT :limit")
    suspend fun getRecentWithText(limit: Int = 10): List<DocumentRoomEntity>

    @Query("SELECT * FROM documents WHERE extractedText != '' ORDER BY lastModifiedEpochMillis DESC LIMIT :limit OFFSET :offset")
    suspend fun getDocumentsWithTextPaged(limit: Int = 50, offset: Int = 0): List<DocumentRoomEntity>

    @Query("SELECT * FROM documents ORDER BY lastModifiedEpochMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<DocumentRoomEntity>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentRoomEntity?

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM documents")
    suspend fun clearAll()

    @Query("SELECT id, lastModifiedEpochMillis, fileSizeBytes FROM documents")
    suspend fun getAllMetadata(): List<DocumentMetadata>

    /**
     * Returns all image documents that have never been OCR-attempted (ocrStatus IS NULL)
     * or previously failed (ocrStatus LIKE 'failed:%'), so they can be re-processed
     * by OcrIndexingWorker in the background.
     */
    @Query("""
        SELECT * FROM documents 
        WHERE (mimeType LIKE 'image/%')
          AND (ocrStatus IS NULL OR (ocrStatus LIKE 'failed:%' AND ocrStatus NOT LIKE 'failed_retry:%'))
        ORDER BY lastModifiedEpochMillis DESC
        LIMIT :limit
    """)
    suspend fun getImagesNeedingOcr(limit: Int = 5000): List<DocumentRoomEntity>

    /**
     * Update ocrStatus for a document after OCR completes or fails.
     */
    @Query("UPDATE documents SET ocrStatus = :status, extractedText = :text, isIndexed = :indexed WHERE id = :id")
    suspend fun updateOcrResult(id: String, status: String, text: String, indexed: Boolean)
}

data class DocumentMetadata(
    val id: String,
    val lastModifiedEpochMillis: Long,
    val fileSizeBytes: Long
)
