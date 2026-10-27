package com.docsphere.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.docsphere.data.preferences.DocSpherePreferences
import com.docsphere.data.repository.DocumentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@HiltWorker
class IndexingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val documentRepository: DocumentRepository,
    private val preferences: DocSpherePreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "docsphere_indexing"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "docsphere_indexing_channel"
    }

    override suspend fun doWork(): Result {
        createNotificationChannel()
        showProgressNotification("Starting scan…", 0, 0)

        val forceRescan = inputData.getBoolean("force_rescan", false)
        var lastNotifTime = 0L

        return com.docsphere.ai.InferenceCoordinator.withWorkerLock {
            try {
                documentRepository.scanAndIndex(forceFullRescan = forceRescan) { current, total, fileName ->
                    val now = System.currentTimeMillis()
                    if (now - lastNotifTime > 500 || current == total) {
                        lastNotifTime = now
                        showProgressNotification(
                            "Indexing: $fileName",
                            current,
                            total
                        )
                    }
                }

                val now = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
                )
                preferences.setLastScanTime(now)
                showCompleteNotification()
                Result.success()
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DOCSPHERE INDEXING",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "File indexing progress"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(message: String, current: Int, total: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.docsphere.R.drawable.ic_docsphere_emblem)
            .setContentTitle("DOCSPHERE — INDEXING FILES")
            .setContentText(message)
            .apply {
                if (total > 0) setProgress(total, current, false)
                else setProgress(0, 0, true)
            }
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompleteNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.docsphere.R.drawable.ic_docsphere_emblem)
            .setContentTitle("DOCSPHERE — INDEXING COMPLETE")
            .setContentText("ALL FILES HAVE BEEN INDEXED AND ARE READY FOR SEARCH.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
