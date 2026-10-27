package com.docsphere.data.local

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "chunks_fts")
@Fts4(contentEntity = ChunkRoomEntity::class)
data class ChunkFtsEntity(
    val text: String
)
