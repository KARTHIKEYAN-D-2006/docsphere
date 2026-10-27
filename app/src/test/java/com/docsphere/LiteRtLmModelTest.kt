package com.docsphere

import com.docsphere.ai.offline.LlmModel
import com.docsphere.util.DeviceCapabilityHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmModelTest {

    @Test
    fun testAllGalleryModelsInList() {
        val models = LlmModel.entries
        // Exactly 8 models: BUILT_IN + 7 Gallery models
        assertEquals(8, models.size)

        val ids = models.map { it.id }.toSet()
        val expectedIds = setOf(
            "built_in",
            "gemma_4_e2b",
            "gemma_4_e4b",
            "gemma_3n_e2b",
            "gemma_3n_e4b",
            "gemma3_1b",
            "qwen2_5_1_5b",
            "deepseek_r1_1_5b"
        )
        assertEquals(expectedIds, ids)

        // Ensure legacy removed models are not in the active entries
        assertFalse(ids.contains("gemma_2b"))
        assertFalse(ids.contains("phi4_mini"))
        assertFalse(ids.contains("qwen3_8b"))
        assertFalse(ids.contains("gemma4_12b"))
        assertFalse(ids.contains("mistral_24b"))
    }

    @Test
    fun testRemovedModelIdsSet() {
        val removed = LlmModel.REMOVED_MODEL_IDS
        assertTrue(removed.contains("gemma_2b"))
        assertTrue(removed.contains("phi4_mini"))
        assertTrue(removed.contains("qwen3_8b"))
        assertTrue(removed.contains("gemma4_12b"))
        assertTrue(removed.contains("mistral_24b"))
    }

    @Test
    fun testModelFileNamesAndExtensions() {
        val neuralModels = LlmModel.entries.filter { !it.isBuiltIn }
        for (model in neuralModels) {
            assertTrue(
                "Model ${model.id} file must end with .litertlm",
                model.fileName.endsWith(".litertlm")
            )
            assertTrue(
                "Model ${model.id} URL must come from huggingface.co",
                model.huggingFaceUrl.contains("huggingface.co")
            )
            assertTrue(
                "Model ${model.id} size in bytes must be > 500 MB",
                model.sizeInBytes > 500_000_000L
            )
        }
    }

    @Test
    fun testGatedModelsFlags() {
        assertTrue(LlmModel.GEMMA3_1B.isGated)
        assertTrue(LlmModel.GEMMA_3N_E2B.isGated)
        assertTrue(LlmModel.GEMMA_3N_E4B.isGated)
        assertFalse(LlmModel.GEMMA_4_E2B.isGated)
        assertFalse(LlmModel.GEMMA_4_E4B.isGated)
        assertFalse(LlmModel.QWEN2_5_1_5B.isGated)
        assertFalse(LlmModel.DEEPSEEK_R1_1_5B.isGated)
    }

    @Test
    fun testReasoningModelFlag() {
        assertTrue(LlmModel.DEEPSEEK_R1_1_5B.isReasoning)
        assertFalse(LlmModel.GEMMA_4_E2B.isReasoning)
        assertFalse(LlmModel.GEMMA_4_E4B.isReasoning)
        assertFalse(LlmModel.GEMMA3_1B.isReasoning)
    }

    @Test
    fun testDeviceCapability_NeverBlocksDownloadingOrRunning() {
        // Requirement A: RAM rules show warning, not a block (all 7 are runnable)
        val ramLevels = listOf(3.5, 4.0, 5.1, 6.0, 8.0, 12.0, 16.0)
        for (ram in ramLevels) {
            for (model in LlmModel.entries) {
                assertTrue(
                    "Model ${model.id} must be runnable on $ram GB RAM",
                    DeviceCapabilityHelper.isModelRunnable(model, ram)
                )
            }
        }
    }

    @Test
    fun testDeviceCapability_12GbHasZeroWarnings() {
        val ram = 12.0
        for (model in LlmModel.entries) {
            assertNull(
                "Model ${model.id} should have 0 warnings on 12 GB RAM",
                DeviceCapabilityHelper.getCompatibilityReason(model, ram)
            )
        }
    }

    @Test
    fun testDeviceCapability_WarningsOnLowerRam() {
        val ram4Gb = 4.0
        // E4B models recommend 12 GB, E2B recommend 8 GB -> should show warning on 4 GB
        assertNotNull(DeviceCapabilityHelper.getCompatibilityReason(LlmModel.GEMMA_4_E4B, ram4Gb))
        assertNotNull(DeviceCapabilityHelper.getCompatibilityReason(LlmModel.GEMMA_3N_E4B, ram4Gb))
        assertNotNull(DeviceCapabilityHelper.getCompatibilityReason(LlmModel.GEMMA_4_E2B, ram4Gb))

        // Gemma3-1B requires 6 GB -> no warning on 6 GB+ RAM
        val ram6Gb = 6.0
        assertNull(DeviceCapabilityHelper.getCompatibilityReason(LlmModel.GEMMA3_1B, ram6Gb))

        // On 8 GB RAM, E2B has no warning, but E4B still shows warning
        val ram8Gb = 8.0
        assertNull(DeviceCapabilityHelper.getCompatibilityReason(LlmModel.GEMMA_4_E2B, ram8Gb))
        assertNotNull(DeviceCapabilityHelper.getCompatibilityReason(LlmModel.GEMMA_4_E4B, ram8Gb))
    }

    @Test
    fun testGemma4E2BIsRecommendedDefault() {
        assertTrue(LlmModel.GEMMA_4_E2B.isRecommended)
        assertFalse(LlmModel.GEMMA3_1B.isRecommended)
        assertFalse(LlmModel.GEMMA_4_E4B.isRecommended)
        assertFalse(LlmModel.QWEN2_5_1_5B.isRecommended)
    }

    @Test
    fun testFromId_safeHandlingOfNullAndUnknownAndRemovedIds() {
        assertEquals(LlmModel.BUILT_IN, LlmModel.fromId(null))
        assertEquals(LlmModel.BUILT_IN, LlmModel.fromId("unknown_model_xyz"))
        assertEquals(LlmModel.BUILT_IN, LlmModel.fromId("phi4_mini"))
        assertEquals(LlmModel.BUILT_IN, LlmModel.fromId("gemma_2b"))
        assertEquals(LlmModel.GEMMA_4_E2B, LlmModel.fromId("gemma_4_e2b"))
        assertEquals(LlmModel.GEMMA_4_E4B, LlmModel.fromId("gemma_4_e4b"))
        assertEquals(LlmModel.GEMMA_3N_E2B, LlmModel.fromId("gemma_3n_e2b"))
        assertEquals(LlmModel.GEMMA_3N_E4B, LlmModel.fromId("gemma_3n_e4b"))
        assertEquals(LlmModel.GEMMA3_1B, LlmModel.fromId("gemma3_1b"))
        assertEquals(LlmModel.QWEN2_5_1_5B, LlmModel.fromId("qwen2_5_1_5b"))
        assertEquals(LlmModel.DEEPSEEK_R1_1_5B, LlmModel.fromId("deepseek_r1_1_5b"))
    }
}
