package com.docsphere.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.docsphere.data.model.DocumentEntity
import com.docsphere.data.model.ScanProgress
import com.docsphere.data.preferences.DocSpherePreferences
import com.docsphere.data.repository.DocumentRepository
import com.docsphere.worker.IndexingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.docsphere.util.FileOpener
import com.docsphere.util.StorageStats

data class HomeUiState(
    val totalDocs: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val recentFiles: List<DocumentEntity> = emptyList(),
    val scanProgress: ScanProgress = ScanProgress.Idle,
    val lastScanTime: String = "Never",
    val storageStats: StorageStats = StorageStats.EMPTY
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val preferences: DocSpherePreferences,
    private val workManager: WorkManager
) : ViewModel() {

    private val _storageStats = flow {
        emit(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            FileOpener.getStorageStats()
        })
    }

    init {
        viewModelScope.launch {
            if (preferences.autoScanOnLaunch.first()) {
                triggerScan()
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        documentRepository.observeTotalCount(),
        documentRepository.observeImageCount(),
        documentRepository.observeVideoCount(),
        documentRepository.observeAudioCount(),
        documentRepository.observeRecentDocuments(10)
    ) { total, images, videos, audios, recent ->
        HomeUiState(
            totalDocs = total,
            imageCount = images,
            videoCount = videos,
            audioCount = audios,
            recentFiles = recent
        )
    }.combine(_storageStats) { state, storage ->
        state.copy(storageStats = storage)
    }.combine(documentRepository.scanProgress) { state, progress ->
        state.copy(scanProgress = progress)
    }.combine(preferences.lastScanTime) { state, time ->
        state.copy(lastScanTime = time)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun triggerScan() {
        val request = OneTimeWorkRequestBuilder<IndexingWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            IndexingWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun importDocuments(uris: List<android.net.Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                documentRepository.importDocument(uri)
            }
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            documentRepository.deleteDocument(doc.id)
        }
    }

    fun retryOcr(doc: DocumentEntity) {
        viewModelScope.launch {
            documentRepository.retryOcr(doc.id)
        }
    }
}
