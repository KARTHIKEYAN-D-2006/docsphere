package com.docsphere.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug watchdog that monitors the Android main (UI) thread.
 * If the main thread looper is unresponsive or blocked for more than 2 seconds,
 * it captures and logs the full main thread stack trace to "DocSphereDebug".
 */
object MainThreadWatchdog {

    private const val TAG = "DocSphereDebug"
    private const val CHECK_INTERVAL_MS = 500L
    private const val BLOCK_THRESHOLD_MS = 2000L

    private val isRunning = AtomicBoolean(false)
    private val lastTickTime = AtomicLong(System.currentTimeMillis())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var watchdogThread: Thread? = null

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return

        lastTickTime.set(System.currentTimeMillis())

        watchdogThread = Thread({
            Log.d(TAG, "[MainThreadWatchdog] Started main thread watchdog (threshold: ${BLOCK_THRESHOLD_MS}ms)")

            while (isRunning.get()) {
                val tickScheduledTime = System.currentTimeMillis()
                val tickDone = AtomicBoolean(false)

                // Post a heartbeat probe to the main thread
                mainHandler.post {
                    lastTickTime.set(System.currentTimeMillis())
                    tickDone.set(true)
                }

                try {
                    Thread.sleep(CHECK_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }

                val now = System.currentTimeMillis()
                val timeSinceLastTick = now - lastTickTime.get()

                if (timeSinceLastTick > BLOCK_THRESHOLD_MS && !tickDone.get()) {
                    val mainThread = Looper.getMainLooper().thread
                    val stackTrace = mainThread.stackTrace

                    val sb = StringBuilder()
                    sb.appendLine("=================================================================")
                    sb.appendLine("[MainThreadWatchdog] ⚠️ MAIN THREAD BLOCKED FOR ${timeSinceLastTick}ms!")
                    sb.appendLine("Thread State: ${mainThread.state}")
                    sb.appendLine("Stack Trace:")
                    for (element in stackTrace) {
                        sb.appendLine("    at $element")
                    }
                    sb.appendLine("=================================================================")

                    Log.e(TAG, sb.toString())
                }
            }
            Log.d(TAG, "[MainThreadWatchdog] Stopped main thread watchdog")
        }, "DocSphere-MainWatchdog").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        watchdogThread?.interrupt()
        watchdogThread = null
    }
}
