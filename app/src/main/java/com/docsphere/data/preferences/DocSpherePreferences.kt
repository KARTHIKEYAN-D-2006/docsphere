package com.docsphere.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.docsphere.ai.offline.LlmModel
import com.docsphere.ai.offline.ModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "docsphere_prefs")

@Singleton
class DocSpherePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_FORCE_OFFLINE = booleanPreferencesKey("force_offline")
        val KEY_AUTO_SCAN_ON_LAUNCH = booleanPreferencesKey("auto_scan_on_launch")
        val KEY_LAST_SCAN_TIME = stringPreferencesKey("last_scan_time")
        val KEY_PRIVACY_DEFAULT_TAG = stringPreferencesKey("default_sensitivity_tag")
        val KEY_OFFLINE_MODEL_DOWNLOADED = booleanPreferencesKey("offline_model_downloaded")
        val KEY_OFFLINE_MODEL_VERSION = stringPreferencesKey("offline_model_version")
        val KEY_USER_NAME = stringPreferencesKey("user_profile_name")
        val KEY_SELECTED_LLM_ID = stringPreferencesKey("selected_llm_id")
        val KEY_CHUNKING_V4_COMPLETED = booleanPreferencesKey("chunking_v4_completed")
        val KEY_FIELD_EXTRACTION_V5_COMPLETED = booleanPreferencesKey("field_extraction_v5_completed")
        val KEY_FIELD_EXTRACTION_V7_COMPLETED = booleanPreferencesKey("field_extraction_v7_completed")
        val KEY_OCR_INDEXING_V6_COMPLETED = booleanPreferencesKey("ocr_indexing_v6_completed")
        val KEY_OCR_RETRY_PASS_COMPLETED = booleanPreferencesKey("ocr_retry_pass_completed")
        val KEY_SCAN_ALL_IMAGES = booleanPreferencesKey("scan_all_images")
        val KEY_SCAN_CAMERA_ROLL = booleanPreferencesKey("scan_camera_roll")
        val KEY_SHOW_FULL_SENSITIVE_VALUES = booleanPreferencesKey("show_full_sensitive_values")
        val KEY_REQUIRE_WIFI_FOR_DOWNLOAD = booleanPreferencesKey("require_wifi_for_download")
        val KEY_HUGGING_FACE_TOKEN = stringPreferencesKey("hugging_face_token")
        val KEY_MODEL_MIGRATION_MESSAGE = stringPreferencesKey("model_migration_message")
        val KEY_USE_GPU_ACCELERATION = booleanPreferencesKey("use_gpu_acceleration")
        val KEY_GPU_FAILED_ON_DEVICE = booleanPreferencesKey("gpu_failed_on_device")
        val KEY_CHAT_GRACE_PERIOD = stringPreferencesKey("chat_grace_period")
        val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        val KEY_PERSONAL_INTELLIGENCE = booleanPreferencesKey("personal_intelligence")
    }

    val personalIntelligence: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_PERSONAL_INTELLIGENCE] ?: true }

    val maxTokens: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_MAX_TOKENS] ?: 2048 }

    val useGpuAcceleration: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_USE_GPU_ACCELERATION] ?: true }

    val gpuFailedOnDevice: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_GPU_FAILED_ON_DEVICE] ?: false }

    val chatGracePeriod: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_CHAT_GRACE_PERIOD] ?: "2 minutes" }

    val requireWifiForDownload: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_REQUIRE_WIFI_FOR_DOWNLOAD] ?: true }

    val huggingFaceToken: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_HUGGING_FACE_TOKEN] ?: "" }

    val modelMigrationMessage: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_MODEL_MIGRATION_MESSAGE] }

    val showFullSensitiveValues: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_SHOW_FULL_SENSITIVE_VALUES] ?: true }

    val scanAllImages: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_SCAN_ALL_IMAGES] ?: true }

    val scanCameraRoll: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_SCAN_CAMERA_ROLL] ?: false }

    val hasCompletedChunkingV4: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_CHUNKING_V4_COMPLETED] ?: false }

    val hasCompletedFieldExtractionV5: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_FIELD_EXTRACTION_V5_COMPLETED] ?: false }

    val hasCompletedFieldExtractionV7: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_FIELD_EXTRACTION_V7_COMPLETED] ?: false }

    val hasCompletedOcrIndexingV6: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_OCR_INDEXING_V6_COMPLETED] ?: false }

    val hasCompletedOcrRetryPass: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_OCR_RETRY_PASS_COMPLETED] ?: false }

    val userName: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_USER_NAME] }

    val geminiApiKey: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_GEMINI_API_KEY] ?: "" }

    val forceOffline: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_FORCE_OFFLINE] ?: false }

    val autoScanOnLaunch: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_AUTO_SCAN_ON_LAUNCH] ?: true }

    val lastScanTime: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_LAST_SCAN_TIME] ?: "Never" }

    val offlineModelDownloaded: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_OFFLINE_MODEL_DOWNLOADED] ?: false }

    val offlineModelVersion: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_OFFLINE_MODEL_VERSION] ?: "BUILT-IN NEURAL BASE" }

    val selectedLlmId: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_SELECTED_LLM_ID] ?: "built_in" }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { prefs -> prefs[KEY_USER_NAME] = name }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[KEY_GEMINI_API_KEY] = key }
    }

    suspend fun setForceOffline(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_FORCE_OFFLINE] = value }
    }

    suspend fun setAutoScanOnLaunch(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_SCAN_ON_LAUNCH] = value }
    }

    suspend fun setLastScanTime(time: String) {
        context.dataStore.edit { prefs -> prefs[KEY_LAST_SCAN_TIME] = time }
    }

    suspend fun setOfflineModelDownloaded(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_OFFLINE_MODEL_DOWNLOADED] = value }
    }

    suspend fun setOfflineModelVersion(version: String) {
        context.dataStore.edit { prefs -> prefs[KEY_OFFLINE_MODEL_VERSION] = version }
    }

    suspend fun setSelectedLlmId(id: String) {
        context.dataStore.edit { prefs -> prefs[KEY_SELECTED_LLM_ID] = id }
    }

    suspend fun setChunkingV4Completed(completed: Boolean = true) {
        context.dataStore.edit { prefs -> prefs[KEY_CHUNKING_V4_COMPLETED] = completed }
    }

    suspend fun setFieldExtractionV5Completed(completed: Boolean = true) {
        context.dataStore.edit { prefs -> prefs[KEY_FIELD_EXTRACTION_V5_COMPLETED] = completed }
    }

    suspend fun setFieldExtractionV7Completed(completed: Boolean = true) {
        context.dataStore.edit { prefs -> prefs[KEY_FIELD_EXTRACTION_V7_COMPLETED] = completed }
    }

    suspend fun setOcrIndexingV6Completed(completed: Boolean = true) {
        context.dataStore.edit { prefs -> prefs[KEY_OCR_INDEXING_V6_COMPLETED] = completed }
    }

    suspend fun setOcrRetryPassCompleted(completed: Boolean = true) {
        context.dataStore.edit { prefs -> prefs[KEY_OCR_RETRY_PASS_COMPLETED] = completed }
    }

    suspend fun setScanAllImages(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SCAN_ALL_IMAGES] = value }
    }

    suspend fun setScanCameraRoll(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SCAN_CAMERA_ROLL] = value }
    }

    suspend fun setShowFullSensitiveValues(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SHOW_FULL_SENSITIVE_VALUES] = value }
    }

    suspend fun setRequireWifiForDownload(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_REQUIRE_WIFI_FOR_DOWNLOAD] = value }
    }

    suspend fun setHuggingFaceToken(token: String) {
        context.dataStore.edit { prefs -> prefs[KEY_HUGGING_FACE_TOKEN] = token.trim() }
    }

    suspend fun setModelMigrationMessage(message: String?) {
        context.dataStore.edit { prefs ->
            if (message != null) prefs[KEY_MODEL_MIGRATION_MESSAGE] = message
            else prefs.remove(KEY_MODEL_MIGRATION_MESSAGE)
        }
    }

    suspend fun setUseGpuAcceleration(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_USE_GPU_ACCELERATION] = value }
    }

    suspend fun setGpuFailedOnDevice(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_GPU_FAILED_ON_DEVICE] = value }
    }

    suspend fun setChatGracePeriod(period: String) {
        context.dataStore.edit { prefs -> prefs[KEY_CHAT_GRACE_PERIOD] = period }
    }

    suspend fun setMaxTokens(value: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_MAX_TOKENS] = value }
    }

    suspend fun setPersonalIntelligence(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_PERSONAL_INTELLIGENCE] = value }
    }

    fun getModelConfig(model: LlmModel): Flow<ModelConfig> {
        val default = ModelConfig.defaultFor(model)
        val accKey = stringPreferencesKey("model_acc_${model.id}")
        val tokKey = intPreferencesKey("model_max_tok_${model.id}")
        val tempKey = floatPreferencesKey("model_temp_${model.id}")
        val topKKey = intPreferencesKey("model_top_k_${model.id}")
        val topPKey = floatPreferencesKey("model_top_p_${model.id}")
        val gpuFailedKey = booleanPreferencesKey("model_gpu_failed_${model.id}")

        return context.dataStore.data.map { prefs ->
            ModelConfig(
                accelerator = prefs[accKey] ?: default.accelerator,
                maxTokens = prefs[tokKey] ?: default.maxTokens,
                temperature = prefs[tempKey] ?: default.temperature,
                topK = prefs[topKKey] ?: default.topK,
                topP = prefs[topPKey] ?: default.topP,
                gpuFailed = prefs[gpuFailedKey] ?: default.gpuFailed
            )
        }
    }

    suspend fun saveModelConfig(model: LlmModel, config: ModelConfig) {
        val accKey = stringPreferencesKey("model_acc_${model.id}")
        val tokKey = intPreferencesKey("model_max_tok_${model.id}")
        val tempKey = floatPreferencesKey("model_temp_${model.id}")
        val topKKey = intPreferencesKey("model_top_k_${model.id}")
        val topPKey = floatPreferencesKey("model_top_p_${model.id}")
        val gpuFailedKey = booleanPreferencesKey("model_gpu_failed_${model.id}")

        context.dataStore.edit { prefs ->
            prefs[accKey] = config.accelerator
            prefs[tokKey] = config.maxTokens
            prefs[tempKey] = config.temperature
            prefs[topKKey] = config.topK
            prefs[topPKey] = config.topP
            prefs[gpuFailedKey] = config.gpuFailed
        }
    }

    suspend fun resetModelConfig(model: LlmModel) {
        saveModelConfig(model, ModelConfig.defaultFor(model))
    }

    suspend fun markModelGpuFailed(model: LlmModel) {
        val accKey = stringPreferencesKey("model_acc_${model.id}")
        val gpuFailedKey = booleanPreferencesKey("model_gpu_failed_${model.id}")
        context.dataStore.edit { prefs ->
            prefs[accKey] = "CPU"
            prefs[gpuFailedKey] = true
        }
    }
}

