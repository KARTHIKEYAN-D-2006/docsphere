package com.docsphere.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.docsphere.data.extractor.TextExtractor
import com.docsphere.data.local.ChunkDao
import com.docsphere.data.local.ChunkRoomEntity
import com.docsphere.data.local.DocumentDao
import com.docsphere.data.local.DocumentFieldDao
import com.docsphere.data.local.DocumentRoomEntity
import com.docsphere.data.model.DocumentEntity
import com.docsphere.data.model.SensitivityTag
import com.docsphere.data.model.SourceType
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTime: Long,
    val webViewLink: String?
)

@Singleton
class DriveRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao,
    private val chunkDao: ChunkDao,
    private val documentFieldDao: DocumentFieldDao,
    private val extractor: TextExtractor
) {

    companion object {
        const val CLIENT_ID = "120384741593-ipklo9f5pap3v0use6m9lmm0o9lq4avv.apps.googleusercontent.com"
        const val PROJECT_ID = "docsphere-508217"
        private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
    }

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DRIVE_SCOPE))
        .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    fun getSignedInEmail(): String? = GoogleSignIn.getLastSignedInAccount(context)?.email

    suspend fun signOut() {
        signInClient.signOut().await()
    }

    /**
     * Observes all documents that have been imported from Google Drive into Room.
     */
    fun observeDriveDocuments(): Flow<List<DocumentEntity>> =
        documentDao.observeBySource(SourceType.GOOGLE_DRIVE.name).map { list ->
            list.map { e ->
                DocumentEntity(
                    id = e.id,
                    displayName = e.displayName,
                    sourceType = e.sourceType,
                    sensitivityTag = e.sensitivityTag,
                    extractedText = e.extractedText,
                    pageCount = e.pageCount,
                    mimeType = e.mimeType,
                    fileSizeBytes = e.fileSizeBytes,
                    lastModified = Instant.ofEpochMilli(e.lastModifiedEpochMillis),
                    localUri = e.localUri,
                    cloudRef = e.cloudRef,
                    thumbnailUri = e.thumbnailUri,
                    isIndexed = e.isIndexed
                )
            }
        }

    /**
     * Imports and indexes a file chosen from Google Drive via Android's Document Provider.
     */
    suspend fun importDriveDocument(uri: Uri): DocumentEntity? {
        var name = "DriveDocument_${System.currentTimeMillis()}"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}

        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = context.contentResolver.getType(uri) ?: when (ext) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "md" -> "text/markdown"
            else -> "application/octet-stream"
        }

        val text = extractor.extract(uri, mime, size, name)
        val entity = DocumentRoomEntity(
            id = uri.toString(),
            displayName = name,
            sourceType = SourceType.GOOGLE_DRIVE,
            sensitivityTag = SensitivityTag.NONE,
            extractedText = text,
            pageCount = 1,
            mimeType = mime,
            fileSizeBytes = size,
            lastModifiedEpochMillis = System.currentTimeMillis(),
            localUri = uri.toString(),
            cloudRef = "gdrive",
            thumbnailUri = null,
            isIndexed = text.isNotBlank()
        )
        documentDao.upsert(entity)
        if (entity.extractedText.isNotBlank()) {
            val chunks = DocumentChunker.splitIntoChunks(
                documentId = entity.id,
                documentName = entity.displayName,
                text = entity.extractedText
            )
            if (chunks.isNotEmpty()) {
                val roomChunks = chunks.map {
                    ChunkRoomEntity(
                        id = it.id,
                        documentId = it.documentId,
                        chunkIndex = it.chunkIndex,
                        text = it.text
                    )
                }
                roomChunks.chunked(20).forEach { batch ->
                    com.docsphere.ai.InferenceCoordinator.pauseIfChatActive()
                    chunkDao.upsertAll(batch)
                    kotlinx.coroutines.yield()
                }
            }

            val fields = DocumentFieldExtractor.extractFields(
                documentId = entity.id,
                documentName = entity.displayName,
                text = entity.extractedText,
                chunks = chunks
            )
            if (fields.isNotEmpty()) {
                fields.chunked(20).forEach { batch ->
                    com.docsphere.ai.InferenceCoordinator.pauseIfChatActive()
                    documentFieldDao.upsertAll(batch)
                    kotlinx.coroutines.yield()
                }
            }
        }
        return DocumentEntity(
            id = entity.id,
            displayName = entity.displayName,
            sourceType = entity.sourceType,
            sensitivityTag = entity.sensitivityTag,
            extractedText = entity.extractedText,
            pageCount = entity.pageCount,
            mimeType = entity.mimeType,
            fileSizeBytes = entity.fileSizeBytes,
            lastModified = Instant.ofEpochMilli(entity.lastModifiedEpochMillis),
            localUri = entity.localUri,
            cloudRef = entity.cloudRef,
            thumbnailUri = entity.thumbnailUri,
            isIndexed = entity.isIndexed
        )
    }

    suspend fun removeDriveDocument(id: String) {
        documentFieldDao.deleteByDocumentId(id)
        chunkDao.deleteByDocumentId(id)
        documentDao.deleteById(id)
    }
}
