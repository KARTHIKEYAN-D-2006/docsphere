package com.docsphere.util

import android.app.ActivityManager
import android.content.Context
import com.docsphere.ai.offline.LlmModel
import kotlin.math.roundToInt

/**
 * Reads device hardware metrics (such as total physical RAM) and determines
 * warnings and recommendations for on-device LiteRT-LM models.
 *
 * Rules:
 * - On 12 GB RAM, all 7 models are allowed with NO warning.
 * - RAM rules show a warning badge, NOT a block (user is free to download and run any model).
 */
object DeviceCapabilityHelper {

    /**
     * Reads the phone's total RAM in Gigabytes (GB).
     */
    fun getTotalRamGb(context: Context): Double {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return 12.0
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
        } catch (_: Exception) {
            12.0
        }
    }

    /**
     * Requirement A.3: Show a warning, not a block.
     * All models are runnable (never blocked by RAM check).
     */
    fun isModelRunnable(model: LlmModel, totalRamGb: Double): Boolean {
        return true
    }

    /**
     * Returns a warning message if the device RAM is below the model's recommended tier.
     * On 12 GB devices (totalRamGb >= 10.0), all 7 models return null (no warning).
     */
    fun getCompatibilityReason(model: LlmModel, totalRamGb: Double): String? {
        val ramDisplay = totalRamGb.roundToInt()
        // On 12 GB devices, all 7 models are fully capable without warnings
        if (totalRamGb >= 10.0) return null

        return when {
            model.isBuiltIn -> null
            model.minRamGb >= 12 && totalRamGb < 10.0 ->
                "⚠️ May be slow: requires 12 GB RAM (device has ${ramDisplay} GB)"
            model.minRamGb >= 8 && totalRamGb < 6.5 ->
                "⚠️ May be slow: requires 8 GB RAM (device has ${ramDisplay} GB)"
            model.minRamGb >= 6 && totalRamGb < 4.5 ->
                "⚠️ Low memory: requires 6 GB RAM (device has ${ramDisplay} GB)"
            else -> null
        }
    }

    /**
     * Preselects the optimal recommended model for this hardware:
     * - Default: Gemma-4-E2B-it (Gallery AI Chat default)
     * - For weaker phones (< 4.5 GB RAM): Gemma3-1B-IT
     */
    fun getRecommendedModel(totalRamGb: Double): LlmModel {
        return if (totalRamGb >= 4.5) {
            LlmModel.GEMMA_4_E2B
        } else {
            LlmModel.GEMMA3_1B
        }
    }
}
