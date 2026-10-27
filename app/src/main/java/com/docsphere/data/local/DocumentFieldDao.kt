package com.docsphere.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DocumentFieldDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(fields: List<DocumentFieldRoomEntity>)

    @Query("DELETE FROM document_fields WHERE documentId = :documentId")
    suspend fun deleteByDocumentId(documentId: String)

    @Query("DELETE FROM document_fields")
    suspend fun clearAll()

    @Query("SELECT * FROM document_fields WHERE documentId = :documentId")
    suspend fun getFieldsByDocumentId(documentId: String): List<DocumentFieldRoomEntity>

    @Query("""
        SELECT document_fields.id, document_fields.documentId, document_fields.fieldName,
               document_fields.fieldValue, document_fields.sourceChunkIndex, documents.displayName AS documentName,
               document_fields.confidence
        FROM document_fields
        JOIN documents ON document_fields.documentId = documents.id
        WHERE document_fields.fieldName = :fieldName
        ORDER BY CASE WHEN document_fields.confidence = 'VERIFIED' THEN 0 ELSE 1 END, documents.displayName ASC
    """)
    suspend fun getFieldsByName(fieldName: String): List<DocumentFieldWithDocName>

    @Query("""
        SELECT document_fields.id, document_fields.documentId, document_fields.fieldName,
               document_fields.fieldValue, document_fields.sourceChunkIndex, documents.displayName AS documentName,
               document_fields.confidence
        FROM document_fields
        JOIN documents ON document_fields.documentId = documents.id
        WHERE document_fields.fieldName IN (:fieldNames)
        ORDER BY CASE WHEN document_fields.confidence = 'VERIFIED' THEN 0 ELSE 1 END, documents.displayName ASC
    """)
    suspend fun getFieldsByNames(fieldNames: List<String>): List<DocumentFieldWithDocName>

    @Query("SELECT fieldValue FROM document_fields WHERE documentId = :documentId AND fieldName = :fieldName ORDER BY CASE WHEN confidence = 'VERIFIED' THEN 0 ELSE 1 END LIMIT 1")
    suspend fun getFieldValue(documentId: String, fieldName: String): String?

    @Query("SELECT COUNT(*) FROM document_fields")
    suspend fun count(): Int
}

data class DocumentFieldWithDocName(
    val id: String,
    val documentId: String,
    val fieldName: String,
    val fieldValue: String,
    val sourceChunkIndex: Int?,
    val documentName: String,
    val confidence: String = "VERIFIED"
)
