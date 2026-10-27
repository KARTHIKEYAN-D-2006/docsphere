package com.docsphere.util

import java.util.Locale

object DocSphereScreenshotFilter {
    private val UI_MARKERS = listOf(
        "Docsphere AI can make mistakes",
        "Enter a prompt here",
        "Ask Docsphere AI",
        "Sources:"
    )

    fun isDocSphereScreenshot(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase(Locale.ROOT)
        return UI_MARKERS.any { lower.contains(it.lowercase(Locale.ROOT)) }
    }
}
