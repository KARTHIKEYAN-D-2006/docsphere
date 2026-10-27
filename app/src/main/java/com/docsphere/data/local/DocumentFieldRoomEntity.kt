package com.docsphere.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_fields",
    foreignKeys = [
        ForeignKey(
            entity = DocumentRoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["fieldName"])
    ]
)
data class DocumentFieldRoomEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val fieldName: String,
    val fieldValue: String,
    val sourceChunkIndex: Int? = null,
    val confidence: String = "VERIFIED"
)
