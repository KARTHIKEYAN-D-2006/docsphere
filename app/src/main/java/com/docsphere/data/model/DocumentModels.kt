package com.docsphere.data.model

import java.time.Instant

enum class SourceType {
    LOCAL_STORAGE,
    GALLERY_OCR,
    GOOGLE_DRIVE,
    EMAIL_ATTACHMENT
}

enum class SensitivityTag {
    NONE,
    SOFT_PRIVATE,
    HARD_LOCKED;

    val syncEligible: Boolean
        get() = this != HARD_LOCKED
}

enum class FileCategory {
    ALL, DOCUMENT, IMAGE, AUDIO, VIDEO, OTHER;

    val label: String get() = when (this) {
        ALL -> "ALL"
        DOCUMENT -> "DOCUMENTS"
        IMAGE -> "IMAGES"
        AUDIO -> "AUDIO"
        VIDEO -> "VIDEOS"
        OTHER -> "OTHER"
    }
}

data class DocumentEntity(
    val id: String,
    val displayName: String,
    val sourceType: SourceType,
    val sensitivityTag: SensitivityTag,
    val extractedText: String,
    val pageCount: Int = 1,
    val mimeType: String,
    val fileSizeBytes: Long = 0L,
    val lastModified: Instant,
    val localUri: String?,
    val cloudRef: String? = null,
    val thumbnailUri: String? = null,
    val isIndexed: Boolean = false,
    /** Null = not an image or not yet attempted. "ok", "skipped", "failed:<reason>" */
    val ocrStatus: String? = null
) {
    val fileCategory: FileCategory get() = when {
        mimeType.startsWith("image/") -> FileCategory.IMAGE
        mimeType.startsWith("audio/") -> FileCategory.AUDIO
        mimeType.startsWith("video/") -> FileCategory.VIDEO
        mimeType == "application/pdf" ||
        mimeType.contains("word") ||
        mimeType.contains("spreadsheet") ||
        mimeType.contains("presentation") ||
        mimeType == "text/plain" -> FileCategory.DOCUMENT
        else -> FileCategory.OTHER
    }

    val fileExtension: String get() = displayName.substringAfterLast('.', "").lowercase()

    val readableSize: String get() = when {
        fileSizeBytes < 1024 -> "$fileSizeBytes B"
        fileSizeBytes < 1024 * 1024 -> "${fileSizeBytes / 1024} KB"
        fileSizeBytes < 1024 * 1024 * 1024 -> "${fileSizeBytes / (1024 * 1024)} MB"
        else -> "${fileSizeBytes / (1024 * 1024 * 1024)} GB"
    }

    /** True if this image was attempted but OCR failed and can be retried. */
    val ocrFailed: Boolean get() = ocrStatus?.startsWith("failed:") == true
}

data class RetrievedChunk(
    val documentId: String,
    val documentName: String,
    val pageNumber: Int?,
    val snippet: String,
    val relevanceScore: Float,
    val sourceType: SourceType
)

enum class RoutingDecision {
    LOCAL_ONLY,
    CLOUD_BOOSTED,
    FUSED
}

data class QueryResult(
    val answerMarkdown: String,
    val citations: List<RetrievedChunk>,
    val routingDecision: RoutingDecision,
    val routingReason: String
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Instant = Instant.now(),
    val routingDecision: RoutingDecision? = null,
    val citations: List<RetrievedChunk> = emptyList(),
    val isStreaming: Boolean = false,
    val responseTimeMs: Long? = null
)

data class AiSession(
    val sessionId: String,
    val title: String,
    val createdAt: Instant,
    val lastMessageAt: Instant,
    val messageCount: Int
)

sealed class ScanProgress {
    data object Idle : ScanProgress()
    data class Scanning(val current: Int, val total: Int, val currentFile: String) : ScanProgress()
    data class Indexing(val current: Int, val total: Int, val currentFile: String) : ScanProgress()
    data class Done(val totalIndexed: Int) : ScanProgress()
    data class Error(val message: String) : ScanProgress()
}
