package com.docsphere.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.docsphere.ai.offline.LlmModel
import com.docsphere.ai.offline.ModelDownloadState
import com.docsphere.ai.offline.OfflineModelManager
import com.docsphere.ai.offline.OfflineModelState
import com.docsphere.data.preferences.DocSpherePreferences
import com.docsphere.data.repository.AiRepository
import com.docsphere.data.repository.DocumentRepository
import com.docsphere.worker.IndexingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val geminiApiKey: String = "",
    val maskedApiKey: String = "",
    val forceOffline: Boolean = false,
    val autoScanOnLaunch: Boolean = true,
    val lastScanTime: String = "Never",
    val showApiKey: Boolean = false,
    val modelState: OfflineModelState = OfflineModelState(),
    val allModelStates: Map<LlmModel, ModelDownloadState> = emptyMap(),
    val selectedModel: LlmModel = LlmModel.BUILT_IN,
    val totalIndexedDocs: Int = 0,
    val scanAllImages: Boolean = true,
    val scanCameraRoll: Boolean = false,
    val showFullSensitiveValues: Boolean = true,
    val requireWifiForDownload: Boolean = true,
    val huggingFaceToken: String = "",
    val migrationNotice: String? = null,
    val useGpuAcceleration: Boolean = true,
    val gpuFailedOnDevice: Boolean = false,
    val chatGracePeriod: String = "2 minutes",
    val maxTokens: Int = 2048,
    val hfTokenStatus: String? = null,
    val isHfTokenValidating: Boolean = false,
    val personalIntelligence: Boolean = true
)

@HiltViewModel
@Suppress("PropertyName")
class SettingsViewModel @Inject constructor(
    private val preferences: DocSpherePreferences,
    private val documentRepository: DocumentRepository,
    private val aiRepository: AiRepository,
    private val offlineModelManager: OfflineModelManager,
    private val workManager: WorkManager,
    private val nanoEngine: com.docsphere.ai.offline.GeminiNanoEngine
) : ViewModel() {

    private val _showApiKey = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            offlineModelManager.initialize()
        }
    }

    private val _imageAndDownloadPrefs = combine(
        preferences.scanAllImages,
        preferences.scanCameraRoll,
        preferences.showFullSensitiveValues,
        preferences.requireWifiForDownload,
        preferences.huggingFaceToken
    ) { scanAll, cameraRoll, showFullValues, reqWifi, hfToken ->
        object {
            val scanAll = scanAll
            val cameraRoll = cameraRoll
            val showFullValues = showFullValues
            val reqWifi = reqWifi
            val hfToken = hfToken
        }
    }

    private val _gpuPrefs = combine(
        preferences.useGpuAcceleration,
        preferences.gpuFailedOnDevice,
        preferences.chatGracePeriod,
        preferences.maxTokens,
        preferences.personalIntelligence
    ) { useGpu, gpuFailed, gracePeriod, maxTok, personalInt ->
        object {
            val useGpu = useGpu
            val gpuFailed = gpuFailed
            val gracePeriod = gracePeriod
            val maxTokens = maxTok
            val personalIntelligence = personalInt
        }
    }

    private val _extendedPrefs = combine(
        _imageAndDownloadPrefs,
        _gpuPrefs
    ) { imgPrefs, gpuPrefs ->
        Pair(imgPrefs, gpuPrefs)
    }

    private val _baseSettings = combine(
        preferences.forceOffline,
        preferences.autoScanOnLaunch,
        preferences.lastScanTime,
        documentRepository.observeTotalCount(),
        _extendedPrefs
    ) { forceOffline, autoScan, lastScan, totalDocs, (imgPrefs, gpuPrefs) ->
        SettingsUiState(
            forceOffline = forceOffline,
            autoScanOnLaunch = autoScan,
            lastScanTime = lastScan,
            totalIndexedDocs = totalDocs,
            scanAllImages = imgPrefs.scanAll,
            scanCameraRoll = imgPrefs.cameraRoll,
            showFullSensitiveValues = imgPrefs.showFullValues,
            requireWifiForDownload = imgPrefs.reqWifi,
            huggingFaceToken = imgPrefs.hfToken,
            useGpuAcceleration = gpuPrefs.useGpu,
            gpuFailedOnDevice = gpuPrefs.gpuFailed,
            chatGracePeriod = gpuPrefs.gracePeriod,
            maxTokens = gpuPrefs.maxTokens,
            personalIntelligence = gpuPrefs.personalIntelligence
        )
    }

    private val _hfTokenStatus = MutableStateFlow<String?>(null)
    private val _isHfTokenValidating = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        _baseSettings,
        offlineModelManager.modelState,
        offlineModelManager.allModelStates,
        offlineModelManager.selectedModel,
        combine(
            offlineModelManager.migrationNotice,
            _hfTokenStatus,
            _isHfTokenValidating
        ) { migration, tokenStatus, isValidating ->
            Triple(migration, tokenStatus, isValidating)
        }
    ) { base, model, allModels, selected, (migration, tokenStatus, isValidating) ->
        base.copy(
            modelState = model,
            allModelStates = allModels,
            selectedModel = selected,
            migrationNotice = migration,
            hfTokenStatus = tokenStatus,
            isHfTokenValidating = isValidating
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun saveApiKey(key: String) {
        viewModelScope.launch { preferences.setGeminiApiKey(key) }
    }

    fun setForceOffline(value: Boolean) {
        viewModelScope.launch { preferences.setForceOffline(value) }
    }

    fun setAutoScan(value: Boolean) {
        viewModelScope.launch { preferences.setAutoScanOnLaunch(value) }
    }

    fun setScanAllImages(value: Boolean) {
        viewModelScope.launch { preferences.setScanAllImages(value) }
    }

    fun setScanCameraRoll(value: Boolean) {
        viewModelScope.launch { preferences.setScanCameraRoll(value) }
    }

    fun setShowFullSensitiveValues(value: Boolean) {
        viewModelScope.launch { preferences.setShowFullSensitiveValues(value) }
    }

    fun setRequireWifi(value: Boolean) {
        viewModelScope.launch { preferences.setRequireWifiForDownload(value) }
    }

    fun saveHuggingFaceToken(token: String) {
        val clean = com.docsphere.ai.offline.cleanHfToken(token)
        viewModelScope.launch {
            preferences.setHuggingFaceToken(clean)
            if (clean.isBlank()) {
                _hfTokenStatus.value = null
                return@launch
            }
            _isHfTokenValidating.value = true
            _hfTokenStatus.value = "Checking token..."
            val result = offlineModelManager.verifyHuggingFaceToken(clean)
            _hfTokenStatus.value = result
            _isHfTokenValidating.value = false
        }
    }

    fun dismissMigrationNotice() {
        offlineModelManager.dismissMigrationNotice()
    }

    fun toggleApiKeyVisibility() {
        _showApiKey.value = !_showApiKey.value
    }

    // ── Per-model actions ──────────────────────────────────────────────────

    fun downloadModel(model: LlmModel) {
        offlineModelManager.downloadModel(model, viewModelScope)
    }

    fun downloadWithCellular(model: LlmModel) {
        viewModelScope.launch {
            preferences.setRequireWifiForDownload(false)
            offlineModelManager.downloadModel(model, viewModelScope)
        }
    }

    fun cancelDownload(model: LlmModel) {
        offlineModelManager.cancelDownload(model)
    }

    fun deleteModel(model: LlmModel) {
        offlineModelManager.deleteModel(model, viewModelScope)
    }

    fun selectModel(model: LlmModel) {
        viewModelScope.launch {
            offlineModelManager.selectModel(model)
            nanoEngine.warmup(forceReload = true)
        }
    }

    fun setUseGpuAcceleration(value: Boolean) {
        viewModelScope.launch {
            preferences.setUseGpuAcceleration(value)
            if (value) {
                preferences.setGpuFailedOnDevice(false)
            }
            nanoEngine.warmup(forceReload = true)
        }
    }

    // ── Other ──────────────────────────────────────────────────────────────

    fun triggerRescan() {
        val request = OneTimeWorkRequestBuilder<IndexingWorker>()
            .setInputData(androidx.work.workDataOf("force_rescan" to true))
            .build()
        workManager.enqueueUniqueWork(
            IndexingWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun clearIndex() {
        viewModelScope.launch { documentRepository.clearAll() }
    }

    fun clearChatHistory() {
        viewModelScope.launch { aiRepository.clearAllHistory() }
    }

    fun setChatGracePeriod(period: String) {
        viewModelScope.launch { preferences.setChatGracePeriod(period) }
    }

    fun setMaxTokens(tokens: Int) {
        viewModelScope.launch {
            preferences.setMaxTokens(tokens)
            nanoEngine.warmup(forceReload = true)
        }
    }

    fun setPersonalIntelligence(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setPersonalIntelligence(enabled)
        }
    }
}
