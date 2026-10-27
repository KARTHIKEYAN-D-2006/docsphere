package com.docsphere.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.docsphere.data.model.SensitivityTag
import com.docsphere.data.model.SourceType

/**
 * Room's on-disk table representation. Kept separate from DocumentEntity (the
 * domain model used by the repository/UI) so persistence details don't leak
 * into the rest of the app — mapping happens at the DAO boundary.
 */
@Entity(tableName = "documents")
data class DocumentRoomEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val sourceType: SourceType,
    val sensitivityTag: SensitivityTag,
    val extractedText: String,
    val pageCount: Int,
    val mimeType: String,
    val fileSizeBytes: Long = 0L,
    val lastModifiedEpochMillis: Long,
    val localUri: String?,
    val cloudRef: String?,
    val thumbnailUri: String? = null,
    val isIndexed: Boolean = false,
    /**
     * OCR status for image documents. Null = not an image or not yet attempted.
     * "ok" = OCR succeeded. "failed:<reason>" = OCR failed with reason.
     * "skipped" = image was too small/large to attempt.
     */
    val ocrStatus: String? = null
)
