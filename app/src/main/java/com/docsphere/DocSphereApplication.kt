package com.docsphere

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Integrates Hilt + WorkManager with Hilt-Work
 * and configures optimized, hardware-accelerated Coil image loading with
 * bounded memory caching to avoid OOM errors on large photo collections.
 */
@HiltAndroidApp
class DocSphereApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build()
            )
        }

        // Start debug main-thread watchdog to detect ANRs and long thread blockages (>2000ms)
        com.docsphere.util.MainThreadWatchdog.start()

        // Enqueue background workers on Dispatchers.IO so WorkManager SQLite access does not block main thread or trigger StrictMode
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val chunkRequest = androidx.work.OneTimeWorkRequestBuilder<com.docsphere.worker.DocumentChunkingWorker>().build()
            androidx.work.WorkManager.getInstance(this@DocSphereApplication).enqueueUniqueWork(
                com.docsphere.worker.DocumentChunkingWorker.WORK_NAME,
                androidx.work.ExistingWorkPolicy.KEEP,
                chunkRequest
            )

            val fieldRequest = androidx.work.OneTimeWorkRequestBuilder<com.docsphere.worker.FieldExtractionWorker>().build()
            androidx.work.WorkManager.getInstance(this@DocSphereApplication).enqueueUniqueWork(
                com.docsphere.worker.FieldExtractionWorker.WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                fieldRequest
            )

            // Re-process images that were previously skipped or had empty OCR results.
            // Uses at most 2 parallel ML Kit tasks and shows a progress notification.
            val ocrRequest = androidx.work.OneTimeWorkRequestBuilder<com.docsphere.worker.OcrIndexingWorker>().build()
            androidx.work.WorkManager.getInstance(this@DocSphereApplication).enqueueUniqueWork(
                com.docsphere.worker.OcrIndexingWorker.WORK_NAME,
                androidx.work.ExistingWorkPolicy.KEEP,
                ocrRequest
            )
        }
    }


    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .allowHardware(true)
            .respectCacheHeaders(false)
            .build()
    }
}