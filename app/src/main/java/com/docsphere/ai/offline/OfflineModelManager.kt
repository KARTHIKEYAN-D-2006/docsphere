package com.docsphere.ai.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import com.docsphere.data.preferences.DocSpherePreferences
import com.docsphere.util.DeviceCapabilityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ── Exception with exact failure details for download cards ─────────────────
class ModelDownloadException(
    val statusCode: Int?,
    val host: String,
    val exceptionClass: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    fun formatUserMessage(): String {
        val statusPart = if (statusCode != null) "HTTP $statusCode " else ""
        return "${statusPart}(host: $host) - $exceptionClass: $message"
    }
}

fun cleanHfToken(token: String): String {
    var t = token.trim()
    while (t.startsWith("Bearer ", ignoreCase = true)) {
        t = t.substring(7).trim()
    }
    return t
}

// ── Per-model download state ─────────────────────────────────────────────────

data class ModelDownloadState(
    val model: LlmModel,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val statusText: String = "",
    val isRunnable: Boolean = true,
    val incompatibilityReason: String? = null
)

// ── Legacy single-model state (kept for backward compatibility) ───────────────

data class OfflineModelState(
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val modelName: String = "DOCSPHERE INSTANT ENGINE",
    val modelVersion: String = "BASE ENGINE",
    val modelSizeDisplay: String = "BUILT-IN (0 MB)",
    val statusText: String = "READY (100% OFFLINE)"
)

/**
 * Manages LiteRT-LM models on Android following Google AI Edge Gallery conventions.
 *
 * - Enforces device RAM compatibility rules.
 * - Handles resilient downloads with Wi-Fi checks, storage checks, resume, and verification.
 * - Handles Hugging Face authorization headers without hardcoding credentials.
 * - Migrates users away from obsolete .bin and .gguf formats, deleting obsolete files.
 */
@Singleton
class OfflineModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: DocSpherePreferences
) {
    private val tag = "OfflineModelManager"

    // ── Per-model state ───────────────────────────────────────────────────────

    private val _allModelStates = MutableStateFlow(
        LlmModel.entries.associateWith { model ->
            ModelDownloadState(model = model, isDownloaded = model.isBuiltIn)
        }
    )
    val allModelStates: StateFlow<Map<LlmModel, ModelDownloadState>> = _allModelStates.asStateFlow()

    private val _selectedModel = MutableStateFlow(LlmModel.BUILT_IN)
    val selectedModel: StateFlow<LlmModel> = _selectedModel.asStateFlow()

    private val _modelState = MutableStateFlow(OfflineModelState())
    val modelState: StateFlow<OfflineModelState> = _modelState.asStateFlow()

    private val _migrationNotice = MutableStateFlow<String?>(null)
    val migrationNotice: StateFlow<String?> = _migrationNotice.asStateFlow()

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = ConcurrentHashMap<LlmModel, kotlinx.coroutines.Job>()

    init {
        // Automatically and safely run startup model discovery on background thread
        downloadScope.launch {
            try {
                initialize()
            } catch (t: Throwable) {
                Log.e(tag, "Initial discovery error: ${t.message}", t)
                _selectedModel.value = LlmModel.BUILT_IN
            }
        }
    }

    // ── File helpers ──────────────────────────────────────────────────────────

    fun modelFile(model: LlmModel): File = File(context.filesDir, model.fileName)

    private fun tempFile(model: LlmModel): File = File(context.filesDir, "${model.fileName}.tmp")

    fun isModelInstalled(model: LlmModel): Boolean {
        if (model.isBuiltIn) return true
        return try {
            val file = modelFile(model)
            file.exists() && file.isFile && (file.length() == model.sizeInBytes || (file.length() >= model.sizeInBytes * 99 / 100 && file.length() <= model.sizeInBytes * 101 / 100))
        } catch (_: Throwable) {
            false
        }
    }

    /** The file path the inference engine should load, based on the active selection. */
    val selectedModelFile: File?
        get() = try {
            val model = _selectedModel.value
            if (model.isBuiltIn) null
            else {
                val file = modelFile(model)
                if (file.exists() && file.isFile && (file.length() == model.sizeInBytes || (file.length() >= model.sizeInBytes * 99 / 100 && file.length() <= model.sizeInBytes * 101 / 100))) file
                else null
            }
        } catch (_: Throwable) {
            null
        }

    // ── Initialisation & Migration ────────────────────────────────────────────

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val totalRam = DeviceCapabilityHelper.getTotalRamGb(context)
            val savedId = try { preferences.selectedLlmId.first() } catch (_: Throwable) { "built_in" }

            // 1. Model Discovery & Migration:
            // Check if stored selected-model id is unknown, obsolete, or missing its file on device
            val matchedModel = LlmModel.entries.find { it.id == savedId }
            val isKnown = matchedModel != null && !LlmModel.REMOVED_MODEL_IDS.contains(savedId)
            val recommended = DeviceCapabilityHelper.getRecommendedModel(totalRam)
            val recommendedInstalled = isModelInstalled(recommended)

            val effectiveModel: LlmModel
            if (!isKnown) {
                // If the stored selected-model id is unknown/obsolete, fall back to recommended model if installed, or none (BUILT_IN)
                effectiveModel = if (recommendedInstalled) recommended else LlmModel.BUILT_IN
                try { preferences.setSelectedLlmId(effectiveModel.id) } catch (_: Throwable) {}
                val notice = "Upgraded on-device AI runtime to Google LiteRT-LM. Active model set to ${effectiveModel.displayName}."
                _migrationNotice.value = notice
                try { preferences.setModelMigrationMessage(notice) } catch (_: Throwable) {}
                Log.i(tag, "Unknown/obsolete stored model id '$savedId'. Fell back safely to ${effectiveModel.displayName}")
            } else if (!matchedModel.isBuiltIn && !isModelInstalled(matchedModel)) {
                // If its file is missing, fall back to recommended model if installed, or none (BUILT_IN) instead of throwing
                effectiveModel = if (recommendedInstalled) recommended else LlmModel.BUILT_IN
                try { preferences.setSelectedLlmId(effectiveModel.id) } catch (_: Throwable) {}
                Log.i(tag, "Stored model '${matchedModel.displayName}' file is missing on device. Fell back safely to ${effectiveModel.displayName}")
            } else {
                effectiveModel = matchedModel
                _migrationNotice.value = try { preferences.modelMigrationMessage.first() } catch (_: Throwable) { null }
            }

            // 2. CLEANUP: Delete old model files (.bin, .gguf, and obsolete names) to free up storage
            cleanOldModelFiles()

            // 3. Scan disk and hardware capabilities for each supported model
            val updated = LlmModel.entries.associateWith { model ->
                val isRunnable = DeviceCapabilityHelper.isModelRunnable(model, totalRam)
                val reason = DeviceCapabilityHelper.getCompatibilityReason(model, totalRam)
                val installed = isModelInstalled(model)
                ModelDownloadState(
                    model = model,
                    isDownloaded = installed,
                    isRunnable = isRunnable,
                    incompatibilityReason = reason,
                    statusText = when {
                        installed -> "INSTALLED"
                        !isRunnable -> reason ?: "INCOMPATIBLE"
                        else -> ""
                    }
                )
            }.toMutableMap()

            _allModelStates.value = updated
            _selectedModel.value = effectiveModel
            refreshLegacyState()
            Log.i(tag, "Startup model discovery completed successfully. Active model: ${effectiveModel.displayName}")
        } catch (t: Throwable) {
            Log.e(tag, "Startup model discovery failed safely: ${t.message}", t)
            _selectedModel.value = LlmModel.BUILT_IN
            refreshLegacyState()
        }
    }

    private fun cleanOldModelFiles() {
        try {
            val obsoleteExtensions = listOf(".bin", ".gguf")
            val obsoleteNames = setOf(
                "gemma-2b-it-cpu-int4.bin",
                "Phi-4-mini-instruct-Q4_K_M.gguf",
                "Qwen3-8B-Q4_K_M.gguf",
                "gemma-2-9b-it-Q4_K_M.gguf",
                "Mistral-Small-24B-Instruct-2501-Q4_K_M.gguf"
            )

            val dir = context.filesDir
            dir.listFiles()?.forEach { file ->
                val name = file.name
                val shouldDelete = obsoleteNames.contains(name) ||
                        obsoleteExtensions.any { ext -> name.endsWith(ext, ignoreCase = true) }
                if (shouldDelete) {
                    val deleted = file.delete()
                    Log.i(tag, "Cleaned up old model file $name (deleted: $deleted)")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error cleaning old model files: ${e.message}")
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    suspend fun selectModel(model: LlmModel) {
        val totalRam = DeviceCapabilityHelper.getTotalRamGb(context)
        if (!DeviceCapabilityHelper.isModelRunnable(model, totalRam)) {
            val reason = DeviceCapabilityHelper.getCompatibilityReason(model, totalRam)
            Log.w(tag, "Notice for ${model.displayName}: $reason. Selecting anyway per user request.")
        }
        _selectedModel.value = model
        preferences.setSelectedLlmId(model.id)
        refreshLegacyState()
    }

    fun dismissMigrationNotice() {
        _migrationNotice.value = null
        downloadScope.launch {
            preferences.setModelMigrationMessage(null)
        }
    }

    // ── Token Verification (Requirement A.2) ───────────────────────────────────

    suspend fun verifyHuggingFaceToken(token: String): String = withContext(Dispatchers.IO) {
        val clean = cleanHfToken(token)
        if (clean.isBlank()) return@withContext ""
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url("https://huggingface.co/api/whoami-v2")
                .header("Authorization", "Bearer $clean")
                .header("User-Agent", "DocSphere-LiteRTLM/1.0 (Android; Linux)")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val name = json.optString("name").ifBlank { json.optString("username", "verified") }
                    "Token OK (account: $name)"
                } else if (response.code == 401 || response.code == 403) {
                    "Token invalid"
                } else {
                    "Token invalid (HTTP ${response.code})"
                }
            }
        } catch (e: Exception) {
            "Token verification failed: ${e.message ?: "Network unreachable"}"
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    fun cancelDownload(model: LlmModel) {
        val job = downloadJobs.remove(model)
        job?.cancel()
        downloadScope.launch {
            tempFile(model).delete()
            updateModelState(model) {
                it.copy(isDownloading = false, downloadProgress = 0f, statusText = "CANCELLED")
            }
            refreshLegacyState()
        }
    }

    fun downloadModel(model: LlmModel, scope: CoroutineScope? = null) {
        if (model.isBuiltIn) return
        val current = _allModelStates.value[model] ?: return
        if (current.isDownloading) return

        val totalRam = DeviceCapabilityHelper.getTotalRamGb(context)
        if (!DeviceCapabilityHelper.isModelRunnable(model, totalRam)) {
            val reason = DeviceCapabilityHelper.getCompatibilityReason(model, totalRam)
            Log.w(tag, "Downloading ${model.displayName} on device with low RAM: $reason")
        }

        val job = downloadScope.launch {
            updateModelState(model) { it.copy(isDownloading = true, downloadProgress = 0.01f, statusText = "CONNECTING…") }
            refreshLegacyState()

            try {
                // 1. Wi-Fi Check
                val requireWifi = preferences.requireWifiForDownload.first()
                if (requireWifi && !isWifiOrEthernetConnected()) {
                    throw ModelDownloadException(
                        statusCode = null,
                        host = "localhost",
                        exceptionClass = "NetworkException",
                        message = "Wi-Fi connection required. Turn off 'Download Models on Wi-Fi Only' to use mobile data."
                    )
                }

                // 2. Storage Check
                val stat = StatFs(context.filesDir.path)
                val freeBytes = stat.availableBytes
                val requiredBytes = model.sizeInBytes + 50_000_000L
                if (freeBytes < requiredBytes) {
                    val freeGb = String.format(Locale.US, "%.1f", freeBytes.toDouble() / 1_000_000_000.0)
                    throw ModelDownloadException(
                        statusCode = null,
                        host = "localhost",
                        exceptionClass = "InsufficientStorageException",
                        message = "Insufficient storage (${freeGb} GB free, need ${model.sizeLabel})"
                    )
                }

                val rawToken = preferences.huggingFaceToken.first()
                val cleanToken = cleanHfToken(rawToken)
                val hfUrl = model.huggingFaceUrl
                val uriHost = try { URI(hfUrl).host ?: "huggingface.co" } catch (_: Throwable) { "huggingface.co" }

                // Requirement A.4: Send Authorization ONLY to huggingface.co hosts, stripped on CDN redirects
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addNetworkInterceptor { chain ->
                        val orig = chain.request()
                        val host = orig.url.host.lowercase(Locale.US)
                        val isHf = host == "huggingface.co" || host == "www.huggingface.co"
                        val req = if (!isHf && orig.header("Authorization") != null) {
                            orig.newBuilder().removeHeader("Authorization").build()
                        } else {
                            orig
                        }
                        chain.proceed(req)
                    }
                    .build()

                // Requirement A.5: Verify download URL with a HEAD request before downloading and show real status code
                val headRequestBuilder = Request.Builder()
                    .url(hfUrl)
                    .head()
                    .header("User-Agent", "DocSphere-LiteRTLM/1.0 (Android; Linux)")

                if (cleanToken.isNotBlank()) {
                    headRequestBuilder.header("Authorization", "Bearer $cleanToken")
                }

                val headCall = client.newCall(headRequestBuilder.build())
                val headResponse = try {
                    headCall.execute()
                } catch (e: Exception) {
                    throw ModelDownloadException(
                        statusCode = null,
                        host = uriHost,
                        exceptionClass = e.javaClass.simpleName,
                        message = e.localizedMessage ?: "Pre-download connection failed"
                    )
                }

                headResponse.use { hResp ->
                    val code = hResp.code
                    val respHost = hResp.request.url.host
                    if (code == 401) {
                        throw ModelDownloadException(
                            statusCode = 401,
                            host = respHost,
                            exceptionClass = "HttpUnauthorized",
                            message = "Your token is missing or invalid"
                        )
                    } else if (code == 403) {
                        throw ModelDownloadException(
                            statusCode = 403,
                            host = respHost,
                            exceptionClass = "HttpForbidden",
                            message = "Your token works, but you have not accepted the license for ${model.displayName}. Open the model page, accept it with the same account, then retry."
                        )
                    } else if (code == 404) {
                        throw ModelDownloadException(
                            statusCode = 404,
                            host = respHost,
                            exceptionClass = "HttpNotFound",
                            message = "Model file not found on server"
                        )
                    } else if (!hResp.isSuccessful && code !in 200..399) {
                        throw ModelDownloadException(
                            statusCode = code,
                            host = respHost,
                            exceptionClass = "HttpException",
                            message = "Server returned HTTP $code"
                        )
                    }
                }

                // Requirement B.3: Manage partial/corrupt files cleanly and resume cleanly
                val tmp = tempFile(model)
                var existingBytes = if (tmp.exists()) tmp.length() else 0L
                if (existingBytes >= model.sizeInBytes || existingBytes < 0L) {
                    tmp.delete()
                    existingBytes = 0L
                }

                val requestBuilder = Request.Builder()
                    .url(hfUrl)
                    .header("User-Agent", "DocSphere-LiteRTLM/1.0 (Android; Linux)")

                if (existingBytes > 0L) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                    Log.i(tag, "Resuming download for ${model.displayName} from byte $existingBytes")
                }

                if (cleanToken.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $cleanToken")
                }

                var request = requestBuilder.build()
                var response = client.newCall(request).execute()

                // If 416 Range Not Satisfiable, delete tmp and restart from 0
                if (response.code == 416) {
                    response.close()
                    tmp.delete()
                    existingBytes = 0L
                    request = Request.Builder()
                        .url(hfUrl)
                        .header("User-Agent", "DocSphere-LiteRTLM/1.0 (Android; Linux)")
                        .apply {
                            if (cleanToken.isNotBlank()) header("Authorization", "Bearer $cleanToken")
                        }
                        .build()
                    response = client.newCall(request).execute()
                }

                val getHost = response.request.url.host
                if (response.code == 401) {
                    response.close()
                    tmp.delete()
                    throw ModelDownloadException(
                        statusCode = 401,
                        host = getHost,
                        exceptionClass = "HttpUnauthorized",
                        message = "Your token is missing or invalid"
                    )
                }
                if (response.code == 403) {
                    response.close()
                    tmp.delete()
                    throw ModelDownloadException(
                        statusCode = 403,
                        host = getHost,
                        exceptionClass = "HttpForbidden",
                        message = "Your token works, but you have not accepted the license for ${model.displayName}. Open the model page, accept it with the same account, then retry."
                    )
                }
                if (!response.isSuccessful && response.code != 206) {
                    val code = response.code
                    response.close()
                    tmp.delete()
                    throw ModelDownloadException(
                        statusCode = code,
                        host = getHost,
                        exceptionClass = "HttpException",
                        message = "Download failed (HTTP $code)"
                    )
                }

                val body = response.body ?: throw ModelDownloadException(
                    statusCode = response.code,
                    host = getHost,
                    exceptionClass = "EmptyBodyException",
                    message = "Empty response body from model server"
                )

                val isResumed = response.code == 206
                val totalBytes = if (isResumed) {
                    existingBytes + body.contentLength()
                } else {
                    body.contentLength().takeIf { it > 0 } ?: model.sizeInBytes
                }

                val fileOut = FileOutputStream(tmp, isResumed)
                fileOut.use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(65_536)
                        var downloaded = if (isResumed) existingBytes else 0L
                        var read: Int
                        var lastUiUpdate = System.currentTimeMillis()

                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            out.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 300) {
                                val progress = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 0.99f)
                                updateModelState(model) {
                                    it.copy(
                                        downloadProgress = progress,
                                        statusText = "DOWNLOADING ${(progress * 100).toInt()}%…"
                                    )
                                }
                                if (model == _selectedModel.value) refreshLegacyState()
                                lastUiUpdate = now
                            }
                        }
                    }
                }

                // Verify file integrity and install
                verifyAndInstallModel(model, tmp)

                // Requirement B.4: Load model once as a self-test and show "Model ready" or the real load error
                updateModelState(model) {
                    it.copy(
                        isDownloading = true,
                        downloadProgress = 1f,
                        statusText = "VERIFYING MODEL (SELF-TEST)…"
                    )
                }

                val installedFile = modelFile(model)
                var selfTestSuccess = false
                var selfTestError: String? = null

                try {
                    val testConfig = com.google.ai.edge.litertlm.EngineConfig(
                        modelPath = installedFile.absolutePath,
                        backend = com.google.ai.edge.litertlm.Backend.CPU(),
                        visionBackend = null,
                        audioBackend = null,
                        maxNumTokens = 256,
                        cacheDir = context.cacheDir.absolutePath
                    )
                    com.google.ai.edge.litertlm.Engine(testConfig).use { testEngine ->
                        testEngine.initialize()
                    }
                    selfTestSuccess = true
                } catch (t: Throwable) {
                    selfTestError = t.localizedMessage ?: t.javaClass.simpleName
                    Log.e(tag, "Model self-test failed for ${model.displayName}: $selfTestError", t)
                    installedFile.delete() // Do not leave corrupt partial file that blocks retry
                }

                if (selfTestSuccess) {
                    updateModelState(model) {
                        it.copy(
                            isDownloaded = true,
                            isDownloading = false,
                            downloadProgress = 1f,
                            statusText = "Model ready"
                        )
                    }
                    Log.i(tag, "Model ${model.displayName} successfully installed and verified: Model ready")
                } else {
                    updateModelState(model) {
                        it.copy(
                            isDownloaded = false,
                            isDownloading = false,
                            downloadProgress = 0f,
                            statusText = "FAILED: Model load self-test failed - $selfTestError"
                        )
                    }
                }

            } catch (ce: kotlinx.coroutines.CancellationException) {
                Log.i(tag, "Download cancelled for ${model.id}")
                updateModelState(model) {
                    it.copy(isDownloading = false, downloadProgress = 0f, statusText = "CANCELLED")
                }
            } catch (mde: ModelDownloadException) {
                Log.e(tag, "Download failed for ${model.id}: ${mde.formatUserMessage()}")
                updateModelState(model) {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 0f,
                        statusText = "FAILED: ${mde.formatUserMessage()}"
                    )
                }
            } catch (uhe: java.net.UnknownHostException) {
                val host = uhe.message ?: "unknown"
                val msg = "FAILED: (host: $host) - UnknownHostException: Unable to resolve host. Check internet connection."
                Log.e(tag, msg)
                updateModelState(model) {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 0f,
                        statusText = msg
                    )
                }
            } catch (e: Exception) {
                val host = try { URI(model.huggingFaceUrl).host ?: "huggingface.co" } catch (_: Throwable) { "huggingface.co" }
                val msg = "FAILED: (host: $host) - ${e.javaClass.simpleName}: ${e.localizedMessage ?: "Download failed"}"
                Log.e(tag, msg, e)
                updateModelState(model) {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 0f,
                        statusText = msg
                    )
                }
            } finally {
                downloadJobs.remove(model)
                refreshLegacyState()
            }
        }
        downloadJobs[model] = job
    }

    private fun verifyAndInstallModel(model: LlmModel, tmp: File) {
        if (!tmp.exists()) {
            throw Exception("Downloaded file missing")
        }

        val downloadedLength = tmp.length()
        val minAcceptableBytes = (model.sizeInBytes * 95 / 100).coerceAtLeast(100_000_000L)
        if (downloadedLength < minAcceptableBytes) {
            tmp.delete()
            throw Exception("Downloaded file is incomplete ($downloadedLength bytes, expected ~${model.sizeInBytes})")
        }

        // Verify header / file readability
        try {
            RandomAccessFile(tmp, "r").use { raf ->
                val header = ByteArray(8)
                val read = raf.read(header)
                if (read < 4) {
                    throw Exception("File is corrupt or truncated")
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            throw Exception("File verification failed: ${e.message}")
        }

        val dest = modelFile(model)
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun isWifiOrEthernetConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    fun deleteModel(model: LlmModel, scope: CoroutineScope? = null) {
        if (model.isBuiltIn) return
        downloadScope.launch {
            modelFile(model).delete()
            tempFile(model).delete()
            updateModelState(model) {
                it.copy(isDownloaded = false, isDownloading = false, downloadProgress = 0f, statusText = "")
            }
            if (_selectedModel.value == model) {
                _selectedModel.value = LlmModel.BUILT_IN
                preferences.setSelectedLlmId(LlmModel.BUILT_IN.id)
            }
            refreshLegacyState()
        }
    }

    fun isExpandedModelActive(): Boolean = _selectedModel.value != LlmModel.BUILT_IN

    // ── Private ───────────────────────────────────────────────────────────────

    private fun updateModelState(model: LlmModel, transform: (ModelDownloadState) -> ModelDownloadState) {
        _allModelStates.update { current ->
            val existing = current[model] ?: ModelDownloadState(model)
            current + (model to transform(existing))
        }
    }

    private fun refreshLegacyState() {
        val active = _selectedModel.value
        val activeState = _allModelStates.value[active]
        if (active.isBuiltIn) {
            _modelState.value = OfflineModelState(
                isDownloaded = false,
                isDownloading = false,
                downloadProgress = 0f,
                modelName = "DOCSPHERE INSTANT ENGINE",
                modelVersion = "BUILT-IN",
                modelSizeDisplay = "BUILT-IN (0 MB)",
                statusText = "INSTANT ON-DEVICE ACTIVE"
            )
        } else {
            _modelState.value = OfflineModelState(
                isDownloaded = activeState?.isDownloaded == true,
                isDownloading = activeState?.isDownloading == true,
                downloadProgress = activeState?.downloadProgress ?: 0f,
                modelName = active.displayName.uppercase(),
                modelVersion = active.parameters,
                modelSizeDisplay = active.sizeLabel,
                statusText = activeState?.statusText ?: ""
            )
        }
    }
}
