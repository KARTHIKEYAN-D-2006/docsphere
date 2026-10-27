package com.docsphere.data.scanner

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.docsphere.data.model.ScanProgress
import com.docsphere.data.model.SensitivityTag
import com.docsphere.data.model.SourceType
import com.docsphere.data.local.DocumentRoomEntity
import com.docsphere.data.preferences.DocSpherePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RawFileEntry(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val sourceType: SourceType
)

@Singleton
class LocalStorageScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: DocSpherePreferences
) {

    private val supportedDocExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "json"
    )

    fun scan(): Flow<ScanProgress> = flow {
        emit(ScanProgress.Scanning(0, 0, "Starting scan…"))
        val files = scanAll()
        emit(ScanProgress.Scanning(files.size, files.size, "Found ${files.size} files"))
    }.flowOn(Dispatchers.IO)

    /**
     * Scans all local storage sources:
     * 1. MediaStore Documents (via MIME types + file extensions)
     * 2. MediaStore Downloads (API 29+)
     * 3. Direct Public Directories (Downloads & Documents folders)
     * 4. MediaStore Images (if scanAllImages is enabled, camera roll optional)
     * All results are deduplicated.
     */
    suspend fun scanAll(): List<RawFileEntry> {
        val allEntries = mutableListOf<RawFileEntry>()

        // 1. Documents from MediaStore.Files
        allEntries += queryMediaStoreFiles()

        // 2. Downloads from MediaStore.Downloads (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            allEntries += queryMediaStoreDownloads()
        }

        // 3. Direct filesystem search in Downloads & Documents
        allEntries += scanDirectDirectories()

        // 4. Images from MediaStore.Images (if enabled)
        val shouldScanImages = try { preferences.scanAllImages.first() } catch (_: Exception) { true }
        if (shouldScanImages) {
            val includeCameraRoll = try { preferences.scanCameraRoll.first() } catch (_: Exception) { false }
            allEntries += queryMediaStoreImages(includeCameraRoll = includeCameraRoll)
        }

        // Deduplicate by displayName + sizeBytes or Uri
        val seen = mutableSetOf<String>()
        val deduplicated = mutableListOf<RawFileEntry>()
        for (entry in allEntries) {
            val key = "${entry.displayName.lowercase()}_${entry.sizeBytes}"
            if (seen.add(key)) {
                deduplicated.add(entry)
            }
        }

        return deduplicated
    }

    private fun queryMediaStoreFiles(): List<RawFileEntry> {
        val results = mutableListOf<RawFileEntry>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val nameConditions = supportedDocExtensions.joinToString(" OR ") {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.$it'"
        }
        val mimeConditions = listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv"
        ).joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = '$it'" }

        val selection = "($nameConditions) OR ($mimeConditions)"

        val cursor: Cursor? = try {
            context.contentResolver.query(
                uri, projection, selection, null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )
        } catch (_: Exception) { null }

        cursor?.use {
            val idCol = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val nameCol = it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = it.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val dateCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (it.moveToNext()) {
                val id = if (idCol != -1) it.getLong(idCol) else continue
                val name = if (nameCol != -1) it.getString(nameCol) ?: "Document" else "Document"
                val rawMime = if (mimeCol != -1) it.getString(mimeCol) else null
                val mime = resolveMimeType(name, rawMime)
                val size = if (sizeCol != -1) it.getLong(sizeCol) else 0L
                val date = if (dateCol != -1) it.getLong(dateCol) * 1000L else System.currentTimeMillis()

                results += RawFileEntry(
                    uri = Uri.withAppendedPath(uri, id.toString()),
                    displayName = name,
                    mimeType = mime,
                    sizeBytes = size,
                    lastModifiedMillis = date,
                    sourceType = SourceType.LOCAL_STORAGE
                )
            }
        }
        return results
    }

    private fun queryMediaStoreDownloads(): List<RawFileEntry> {
        val results = mutableListOf<RawFileEntry>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return results

        val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED
        )

        val cursor: Cursor? = try {
            context.contentResolver.query(
                uri, projection, null, null,
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )
        } catch (_: Exception) { null }

        cursor?.use {
            val idCol = it.getColumnIndex(MediaStore.Downloads._ID)
            val nameCol = it.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
            val mimeCol = it.getColumnIndex(MediaStore.Downloads.MIME_TYPE)
            val sizeCol = it.getColumnIndex(MediaStore.Downloads.SIZE)
            val dateCol = it.getColumnIndex(MediaStore.Downloads.DATE_MODIFIED)

            while (it.moveToNext()) {
                val id = if (idCol != -1) it.getLong(idCol) else continue
                val name = if (nameCol != -1) it.getString(nameCol) ?: "Download" else "Download"
                val rawMime = if (mimeCol != -1) it.getString(mimeCol) else null
                val mime = resolveMimeType(name, rawMime)
                val ext = name.substringAfterLast('.', "").lowercase()

                // Only include documents or relevant files
                if (ext in supportedDocExtensions || mime.startsWith("text/") || mime == "application/pdf") {
                    val size = if (sizeCol != -1) it.getLong(sizeCol) else 0L
                    val date = if (dateCol != -1) it.getLong(dateCol) * 1000L else System.currentTimeMillis()

                    results += RawFileEntry(
                        uri = Uri.withAppendedPath(uri, id.toString()),
                        displayName = name,
                        mimeType = mime,
                        sizeBytes = size,
                        lastModifiedMillis = date,
                        sourceType = SourceType.LOCAL_STORAGE
                    )
                }
            }
        }
        return results
    }

    private fun scanDirectDirectories(): List<RawFileEntry> {
        val results = mutableListOf<RawFileEntry>()
        val dirsToScan = mutableListOf<File>()

        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
                if (it.exists() && it.canRead()) dirsToScan.add(it)
            }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let {
                if (it.exists() && it.canRead()) dirsToScan.add(it)
            }

            // If All Files Access is granted on Android 11+, check storage root
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                Environment.getExternalStorageDirectory()?.listFiles()?.forEach { file ->
                    if (file.isDirectory && !file.name.equals("Android", ignoreCase = true) && !file.name.startsWith(".")) {
                        dirsToScan.add(file)
                    }
                }
            }
        } catch (_: Exception) {}

        for (dir in dirsToScan) {
            try {
                dir.walkTopDown()
                    .maxDepth(3)
                    .onEnter { folder ->
                        val name = folder.name.lowercase()
                        !name.startsWith(".") && name != "cache" && name != "android" && name != "obb" && name != "node_modules"
                    }
                    .filter { it.isFile && it.extension.lowercase() in supportedDocExtensions }
                    .take(200)
                    .forEach { file ->
                        results += RawFileEntry(
                            uri = Uri.fromFile(file),
                            displayName = file.name,
                            mimeType = resolveMimeType(file.name, null),
                            sizeBytes = file.length(),
                            lastModifiedMillis = file.lastModified(),
                            sourceType = SourceType.LOCAL_STORAGE
                        )
                    }
            } catch (_: Exception) {}
        }
        return results
    }

    private fun queryMediaStoreImages(includeCameraRoll: Boolean = false): List<RawFileEntry> {
        val results = mutableListOf<RawFileEntry>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATA
        )

        val cursor: Cursor? = try {
            context.contentResolver.query(
                uri, projection, null, null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )
        } catch (_: Exception) { null }

        cursor?.use {
            val idCol = it.getColumnIndex(MediaStore.Images.Media._ID)
            val nameCol = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = it.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
            val sizeCol = it.getColumnIndex(MediaStore.Images.Media.SIZE)
            val dateCol = it.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
            val dataCol = it.getColumnIndex(MediaStore.Images.Media.DATA)

            var count = 0
            while (it.moveToNext() && count < 250) { // Keep gallery scan fast & responsive
                val filePath = if (dataCol != -1) it.getString(dataCol)?.lowercase() ?: "" else ""
                val isCameraRoll = filePath.contains("/dcim/") || filePath.contains("/camera")
                if (isCameraRoll && !includeCameraRoll) {
                    continue
                }

                val id = if (idCol != -1) it.getLong(idCol) else continue
                val name = if (nameCol != -1) it.getString(nameCol) ?: "Image" else "Image"
                val mime = if (mimeCol != -1) it.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                val size = if (sizeCol != -1) it.getLong(sizeCol) else 0L
                val date = if (dateCol != -1) it.getLong(dateCol) * 1000L else System.currentTimeMillis()

                results += RawFileEntry(
                    uri = Uri.withAppendedPath(uri, id.toString()),
                    displayName = name,
                    mimeType = mime,
                    sizeBytes = size,
                    lastModifiedMillis = date,
                    sourceType = SourceType.GALLERY_OCR
                )
                count++
            }
        }
        return results
    }

    private fun resolveMimeType(displayName: String, existingMime: String?): String {
        if (!existingMime.isNullOrBlank() && existingMime != "application/octet-stream") {
            return existingMime
        }
        return when (displayName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "md" -> "text/markdown"
            "json" -> "application/json"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> existingMime ?: "application/octet-stream"
        }
    }

    fun RawFileEntry.toRoomEntity(extractedText: String = ""): DocumentRoomEntity {
        return DocumentRoomEntity(
            id = uri.toString(),
            displayName = displayName,
            sourceType = sourceType,
            sensitivityTag = SensitivityTag.NONE,
            extractedText = extractedText,
            pageCount = 1,
            mimeType = mimeType,
            fileSizeBytes = sizeBytes,
            lastModifiedEpochMillis = lastModifiedMillis,
            localUri = uri.toString(),
            cloudRef = null,
            thumbnailUri = null,
            isIndexed = extractedText.isNotBlank()
        )
    }
}
