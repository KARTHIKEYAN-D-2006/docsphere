package com.docsphere.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChunkEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(embedding: ChunkEmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<ChunkEmbeddingEntity>)

    @Query("SELECT * FROM chunk_embeddings WHERE chunkId = :chunkId LIMIT 1")
    suspend fun getByChunkId(chunkId: String): ChunkEmbeddingEntity?

    @Query("SELECT * FROM chunk_embeddings WHERE chunkId IN (:chunkIds)")
    suspend fun getByChunkIds(chunkIds: List<String>): List<ChunkEmbeddingEntity>

    @Query("DELETE FROM chunk_embeddings WHERE chunkId = :chunkId")
    suspend fun deleteByChunkId(chunkId: String)

    @Query("DELETE FROM chunk_embeddings WHERE chunkId IN (SELECT id FROM chunks WHERE documentId = :documentId)")
    suspend fun deleteByDocumentId(documentId: String)

    @Query("DELETE FROM chunk_embeddings")
    suspend fun clearAll()
}
