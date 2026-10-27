package com.docsphere.data.local

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "documents_fts")
@Fts4(contentEntity = DocumentRoomEntity::class)
data class DocumentFtsEntity(
    val displayName: String,
    val extractedText: String
)
