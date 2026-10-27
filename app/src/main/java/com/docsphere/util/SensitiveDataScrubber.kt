package com.docsphere.util

/**
 * Utility to scrub sensitive personal data (Aadhaar, PAN) from logs,
 * exceptions, and persisted message content.
 */
object SensitiveDataScrubber {

    // 12-digit Aadhaar (continuous or grouped in 4s with spaces or hyphens)
    private val AADHAAR_REGEX = Regex("""(?<!\d)(\d{4})[\s-]?(\d{4})[\s-]?(\d{4})(?!\d)""")

    // PAN: 5 uppercase letters, 4 digits, 1 uppercase letter
    private val PAN_REGEX = Regex("""\b([A-Z]{5})(\d{4})([A-Z])\b""")

    // Sensitive tag pattern: [[SENSITIVE:arg1:arg2:arg3]]
    private val SENSITIVE_TAG_REGEX = Regex("""\[\[SENSITIVE:([^:]+):([^:]+):([^\]]+)]]""")

    /**
     * Scrubs full Aadhaar and PAN numbers by replacing them with masked representations.
     * Example: "1234 5678 9012" -> "•••• •••• 9012"
     * Example: "ABCDE1234F" -> "••••••234F"
     * Also strips full values from legacy 4-part sensitive tags.
     */
    fun scrub(text: String?): String {
        if (text.isNullOrBlank()) return text ?: ""
        var result = text.replace(SENSITIVE_TAG_REGEX) { m ->
            val p3 = m.groupValues[3].trim()
            if (AADHAAR_REGEX.matches(p3) || PAN_REGEX.matches(p3)) {
                // Legacy tag containing full value as last argument: strip full value and keep masked
                "[[SENSITIVE:unknown:${m.groupValues[1]}:${m.groupValues[2]}]]"
            } else {
                m.value
            }
        }
        result = result.replace(AADHAAR_REGEX) { m ->
            "•••• •••• ${m.groupValues[3]}"
        }
        result = result.replace(PAN_REGEX) { m ->
            "••••••${m.value.takeLast(4)}"
        }
        return result
    }

    /**
     * Scrubs exception messages and throwables before logging.
     */
    fun scrubThrowable(t: Throwable?): String {
        if (t == null) return ""
        val msg = t.message ?: t.toString()
        return scrub(msg)
    }

    /**
     * Checks if a string contains any full/unmasked Aadhaar, PAN, or legacy sensitive tag
     * that holds full sensitive data.
     */
    fun containsFullSensitiveData(text: String): Boolean {
        // 1. Check for legacy tags containing full sensitive values in the 3rd parameter
        val tagMatches = SENSITIVE_TAG_REGEX.findAll(text)
        for (m in tagMatches) {
            val p3 = m.groupValues[3].trim()
            if (AADHAAR_REGEX.matches(p3) || PAN_REGEX.matches(p3)) {
                return true
            }
        }

        // 2. Check remaining text outside valid reference tags for unmasked Aadhaar or PAN
        val textWithoutTags = text.replace(SENSITIVE_TAG_REGEX, "")
        return AADHAAR_REGEX.containsMatchIn(textWithoutTags) || PAN_REGEX.containsMatchIn(textWithoutTags)
    }
}
