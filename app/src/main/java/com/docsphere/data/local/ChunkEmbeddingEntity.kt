package com.docsphere.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chunk_embeddings",
    indices = [Index(value = ["chunkId"])]
)
data class ChunkEmbeddingEntity(
    @PrimaryKey val chunkId: String,
    val vector: String
)
