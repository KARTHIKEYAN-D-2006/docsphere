package com.docsphere.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chunks",
    indices = [Index(value = ["documentId"])]
)
data class ChunkRoomEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val chunkIndex: Int,
    val text: String
)
