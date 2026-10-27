package com.docsphere.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(chunks: List<ChunkRoomEntity>)

    @Query("DELETE FROM chunks WHERE documentId = :documentId")
    suspend fun deleteByDocumentId(documentId: String)

    @Query("DELETE FROM chunks")
    suspend fun clearAll()

    @Query("SELECT * FROM chunks WHERE documentId = :documentId ORDER BY chunkIndex ASC")
    suspend fun getChunksByDocumentId(documentId: String): List<ChunkRoomEntity>

    @Query("""
        SELECT chunks.id, chunks.documentId, chunks.chunkIndex, chunks.text, documents.displayName AS documentName
        FROM chunks
        JOIN documents ON chunks.documentId = documents.id
        WHERE chunks.documentId IN (:documentIds)
        ORDER BY chunks.documentId, chunks.chunkIndex ASC
        LIMIT :limit
    """)
    suspend fun getChunksForDocumentsInternal(documentIds: List<String>, limit: Int): List<ChunkWithDocName>

    @Query("""
        SELECT chunks.id, chunks.documentId, chunks.chunkIndex, chunks.text, documents.displayName AS documentName
        FROM chunks
        JOIN documents ON chunks.documentId = documents.id
        JOIN chunks_fts ON chunks.rowid = chunks_fts.docid
        WHERE chunks.documentId IN (:documentIds)
          AND chunks_fts MATCH :ftsQuery
        LIMIT :limit
    """)
    suspend fun searchChunksFtsInternal(documentIds: List<String>, ftsQuery: String, limit: Int): List<ChunkWithDocName>

    @Query("""
        SELECT chunks.id, chunks.documentId, chunks.chunkIndex, chunks.text, documents.displayName AS documentName
        FROM chunks
        JOIN documents ON chunks.documentId = documents.id
        JOIN chunks_fts ON chunks.rowid = chunks_fts.docid
        WHERE chunks_fts MATCH :ftsQuery
        LIMIT :limit
    """)
    suspend fun searchAllChunksFts(ftsQuery: String, limit: Int = 50): List<ChunkWithDocName>

    /**
     * Splits documentIds into batches of at most 500 (avoiding SQLite variable limits)
     * and bounds total results to [limit].
     */
    suspend fun getChunksForDocuments(documentIds: List<String>, limit: Int = 50): List<ChunkWithDocName> {
        if (documentIds.isEmpty() || limit <= 0) return emptyList()
        val results = mutableListOf<ChunkWithDocName>()
        for (batch in documentIds.chunked(500)) {
            val needed = limit - results.size
            if (needed <= 0) break
            results.addAll(getChunksForDocumentsInternal(batch, needed))
        }
        return results
    }

    /**
     * Splits documentIds into batches of at most 500 (avoiding SQLite variable limits)
     * and bounds total results to [limit].
     */
    suspend fun searchChunksFts(documentIds: List<String>, ftsQuery: String, limit: Int = 50): List<ChunkWithDocName> {
        if (documentIds.isEmpty() || ftsQuery.isBlank() || limit <= 0) return emptyList()
        val results = mutableListOf<ChunkWithDocName>()
        for (batch in documentIds.chunked(500)) {
            val needed = limit - results.size
            if (needed <= 0) break
            results.addAll(searchChunksFtsInternal(batch, ftsQuery, needed))
        }
        return results
    }

    @Query("SELECT COUNT(*) FROM chunks")
    suspend fun count(): Int
}

data class ChunkWithDocName(
    val id: String,
    val documentId: String,
    val chunkIndex: Int,
    val text: String,
    val documentName: String
)
