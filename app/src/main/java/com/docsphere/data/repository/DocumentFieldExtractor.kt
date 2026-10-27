package com.docsphere.data.repository

import android.util.Log
import com.docsphere.data.local.DocumentFieldRoomEntity
import java.util.Locale

object DocumentFieldExtractor {

    private const val TAG = "DocSphereDebug"

    const val FIELD_AADHAAR = "AADHAAR"
    const val FIELD_PAN = "PAN"
    const val FIELD_INVOICE_TOTAL = "INVOICE_TOTAL"
    const val FIELD_DATE_OF_BIRTH = "DATE_OF_BIRTH"
    const val FIELD_INVOICE_DATE = "INVOICE_DATE"
    const val FIELD_DUE_DATE = "DUE_DATE"
    const val FIELD_PHONE = "PHONE"
    const val FIELD_EMAIL = "EMAIL"
    const val FIELD_VOTER_ID = "VOTER_ID"
    const val FIELD_DRIVING_LICENCE = "DRIVING_LICENCE"
    const val FIELD_PASSPORT = "PASSPORT"
    const val FIELD_FULL_NAME = "FULL_NAME"
    const val FIELD_ADDRESS = "ADDRESS"
    const val FIELD_BANK_ACCOUNT = "BANK_ACCOUNT"
    const val FIELD_IFSC = "IFSC"

    /**
     * Extracts structured fields from document text and associates each with
     * the matching source chunk index if chunks are provided.
     *
     * For spreadsheets (e.g. .xlsx, .xls, .csv), numeric-only sheet cells/lines are skipped
     * to avoid false positives and high CPU load on numerical tables.
     */
    fun extractFields(
        documentId: String,
        documentName: String,
        text: String,
        chunks: List<DocumentChunk> = emptyList()
    ): List<DocumentFieldRoomEntity> {
        if (text.isBlank()) return emptyList()
        val startMs = System.currentTimeMillis()

        // For spreadsheets, skip cells and lines that are purely numeric
        val effectiveText = if (DocumentChunker.isSpreadsheet(documentName)) {
            text.lineSequence()
                .map { line ->
                    line.split(Regex("[,;\t]"))
                        .filterNot { cell ->
                            val trimmedCell = cell.trim()
                            trimmedCell.isNotEmpty() && trimmedCell.matches(Regex("^[0-9\\s.,\\-+/%()#$€₹]+$"))
                        }
                        .joinToString(", ")
                }
                .filter { it.isNotBlank() }
                .joinToString("\n")
        } else {
            text
        }

        if (effectiveText.isBlank()) return emptyList()

        val results = mutableListOf<DocumentFieldRoomEntity>()
        var fieldIdCounter = 0

        val addFieldWithConfidence: (String, String, String) -> Unit = { fieldName, value, confidence ->
            val trimmedValue = value.trim()
            if (trimmedValue.isNotBlank()) {
                // Deduplicate same field type with identical value in same document
                if (!results.any { it.fieldName == fieldName && it.fieldValue.equals(trimmedValue, ignoreCase = true) }) {
                    val sourceChunkIndex = if (chunks.isNotEmpty()) {
                        val foundIdx = chunks.indexOfFirst { chunk ->
                            chunk.text.contains(trimmedValue, ignoreCase = true) ||
                            chunk.text.replace("\\s+".toRegex(), "").contains(trimmedValue.replace("\\s+".toRegex(), ""), ignoreCase = true)
                        }
                        if (foundIdx >= 0) foundIdx else 0
                    } else null

                    val entity = DocumentFieldRoomEntity(
                        id = "${documentId}_field_${fieldName}_${fieldIdCounter++}",
                        documentId = documentId,
                        fieldName = fieldName,
                        fieldValue = trimmedValue,
                        sourceChunkIndex = sourceChunkIndex,
                        confidence = confidence
                    )
                    results.add(entity)
                }
            }
        }

        val addVerifiedField: (String, String) -> Unit = { fieldName, value ->
            addFieldWithConfidence(fieldName, value, "VERIFIED")
        }

        // 1. Aadhaar Extraction (Strict: 12 digits, non-spreadsheet, marker present; VERIFIED if Verhoeff passes, UNVERIFIED if checksum fails)
        extractAadhaarWithConfidence(effectiveText, documentName).forEach { (number, confidence) ->
            addFieldWithConfidence(FIELD_AADHAAR, number, confidence)
        }

        // 2. PAN Extraction
        extractPan(effectiveText).forEach { addVerifiedField(FIELD_PAN, it) }

        // 3. Invoice Total Extraction
        extractInvoiceTotal(effectiveText).forEach { addVerifiedField(FIELD_INVOICE_TOTAL, it) }

        // 4. Dates (DOB, Invoice Date, Due Date)
        extractDates(effectiveText).forEach { (field, date) -> addVerifiedField(field, date) }

        // 5. Phone & Email
        extractPhoneNumbers(effectiveText).forEach { addVerifiedField(FIELD_PHONE, it) }
        extractEmails(effectiveText).forEach { addVerifiedField(FIELD_EMAIL, it) }

        // 6. Voter ID
        extractVoterId(effectiveText).forEach { addVerifiedField(FIELD_VOTER_ID, it) }

        // 7. Driving Licence
        extractDrivingLicence(effectiveText).forEach { addVerifiedField(FIELD_DRIVING_LICENCE, it) }

        // 8. Passport
        extractPassport(effectiveText).forEach { addVerifiedField(FIELD_PASSPORT, it) }

        // 9. Full Name
        extractFullName(effectiveText).forEach { addVerifiedField(FIELD_FULL_NAME, it) }

        // 10. Address
        extractAddress(effectiveText).forEach { addVerifiedField(FIELD_ADDRESS, it) }

        // 11. Bank Account & IFSC
        extractBankAccount(effectiveText).forEach { addVerifiedField(FIELD_BANK_ACCOUNT, it) }
        extractIfsc(effectiveText).forEach { addVerifiedField(FIELD_IFSC, it) }

        val durationMs = System.currentTimeMillis() - startMs
        val fieldSummary = results.groupBy { it.fieldName }
            .map { "${it.key}: ${it.value.size}" }
            .joinToString(", ")
        Log.d(TAG, "[FieldExtractor] Finished extraction for '$documentName' in ${durationMs}ms. Total fields: ${results.size}${if (fieldSummary.isNotEmpty()) " ($fieldSummary)" else ""}")
        return results
    }

    /**
     * Verhoeff checksum algorithm for Indian Aadhaar verification (UIDAI standard).
     */
    object Verhoeff {
        private val d = arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
            intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
            intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
            intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
            intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
            intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
            intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
            intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
            intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
        )

        private val p = arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
            intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
            intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
            intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
            intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
            intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
            intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
        )

        fun validate(num: String): Boolean {
            val digits = num.filter { it.isDigit() }
            if (digits.length != 12) return false
            if (digits[0] !in '2'..'9') return false
            var c = 0
            val reversed = digits.reversed()
            for (i in reversed.indices) {
                c = d[c][p[i % 8][reversed[i] - '0']]
            }
            return c == 0
        }
    }

    fun hasAadhaarMarker(text: String, docName: String = ""): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        val lowerName = docName.lowercase(Locale.ROOT)
        return lower.contains("aadhaar") || lower.contains("aadhar") || lower.contains("adhaar") ||
               lower.contains("uidai") || lower.contains("unique identification") ||
               lowerName.contains("aadhaar") || lowerName.contains("aadhar") || lowerName.contains("uidai")
    }

    /**
     * Extracts Aadhaar numbers with verification confidence:
     * - Stored only if 12 digits, starts with 2-9, and document contains an Aadhaar marker.
     * - If Verhoeff checksum passes, confidence is VERIFIED.
     * - If Verhoeff checksum fails (e.g. OCR misread a digit), confidence is UNVERIFIED.
     * - Never extracted from .xlsx, .xls, or .csv files.
     */
    fun extractAadhaarWithConfidence(text: String, documentName: String = ""): List<Pair<String, String>> {
        if (documentName.endsWith(".xlsx", ignoreCase = true) ||
            documentName.endsWith(".xls", ignoreCase = true) ||
            documentName.endsWith(".csv", ignoreCase = true) ||
            DocumentChunker.isSpreadsheet(documentName)
        ) {
            return emptyList()
        }

        if (!hasAadhaarMarker(text, documentName)) {
            return emptyList()
        }

        val rawCandidates = mutableListOf<String>()

        // 1. Grouped digits (4-4-4) with optional spaces or hyphens
        val groupedOcrRegex = Regex("(?<!\\d)([0-9Oo]{4})[\\s-]+([0-9Oo]{4})[\\s-]+([0-9Oo]{4})(?!\\d)")
        for (m in groupedOcrRegex.findAll(text)) {
            val part1 = m.groupValues[1].replace('O', '0').replace('o', '0')
            val part2 = m.groupValues[2].replace('O', '0').replace('o', '0')
            val part3 = m.groupValues[3].replace('O', '0').replace('o', '0')
            val raw = "$part1$part2$part3"
            if (raw.length == 12 && raw.all { it.isDigit() }) {
                rawCandidates.add(raw)
            }
        }

        // 2. 12 contiguous digits (not preceded or followed by other digits)
        val contiguousRegex = Regex("(?<!\\d)([0-9]{12})(?!\\d)")
        for (m in contiguousRegex.findAll(text)) {
            val raw = m.groupValues[1]
            if (raw.length == 12 && raw.all { it.isDigit() }) {
                rawCandidates.add(raw)
            }
        }

        // 3. Messy OCR with scattered spaces between every digit
        val scatteredRegex = Regex("(?<!\\d)(?:[0-9Oo]\\s*){12}(?!\\d)")
        for (m in scatteredRegex.findAll(text)) {
            val raw = m.value.replace("\\s+".toRegex(), "").replace('O', '0').replace('o', '0')
            if (raw.length == 12 && raw.all { it.isDigit() }) {
                rawCandidates.add(raw)
            }
        }

        val distinctRaws = rawCandidates.distinct()
        val results = mutableListOf<Pair<String, String>>()
        for (raw in distinctRaws) {
            val isVerified = Verhoeff.validate(raw)
            val formatted = "${raw.substring(0, 4)} ${raw.substring(4, 8)} ${raw.substring(8, 12)}"
            results.add(formatted to (if (isVerified) "VERIFIED" else "UNVERIFIED"))
        }

        // Sort so VERIFIED comes first
        return results.sortedBy { if (it.second == "VERIFIED") 0 else 1 }
    }

    fun extractAadhaar(text: String, documentName: String = ""): List<String> {
        return extractAadhaarWithConfidence(text, documentName).map { it.first }
    }

    /**
     * Extracts PAN card numbers:
     * - Pattern: [A-Z]{5}[0-9]{4}[A-Z]
     * - Resilient to messy OCR: handles "O" / "o" read as "0" in the 4 digits,
     *   and extra spaces between characters (e.g. "A B C D E 1 2 3 4 F").
     */
    fun extractPan(text: String): List<String> {
        val matches = mutableListOf<String>()

        // 1. Standard or OCR 'O'/'o' for '0' in the 4 digits: [A-Z]{5}[0-9Oo]{4}[A-Z]
        val panOcrRegex = Regex("(?<![A-Za-z0-9])([A-Za-z]{5})\\s*([0-9Oo]{4})\\s*([A-Za-z])(?![A-Za-z0-9])")
        for (m in panOcrRegex.findAll(text)) {
            val prefix = m.groupValues[1].uppercase(Locale.ROOT)
            val digits = m.groupValues[2].replace('O', '0').replace('o', '0')
            val suffix = m.groupValues[3].uppercase(Locale.ROOT)
            if (prefix.length == 5 && digits.length == 4 && digits.all { it.isDigit() } && suffix.length == 1) {
                matches.add("$prefix$digits$suffix")
            }
        }

        // 2. Extra spaces between letters/digits (e.g. "A B C D E 1 2 3 4 F")
        val spacedPanRegex = Regex("(?<![A-Za-z0-9])([A-Za-z]\\s+[A-Za-z]\\s+[A-Za-z]\\s+[A-Za-z]\\s+[A-Za-z]\\s+[0-9Oo]\\s+[0-9Oo]\\s+[0-9Oo]\\s+[0-9Oo]\\s+[A-Za-z])(?![A-Za-z0-9])")
        for (m in spacedPanRegex.findAll(text)) {
            val compact = m.groupValues[1].replace("\\s+".toRegex(), "").uppercase(Locale.ROOT)
            if (compact.length == 10) {
                val prefix = compact.substring(0, 5)
                val digits = compact.substring(5, 9).replace('O', '0')
                val suffix = compact.substring(9, 10)
                if (prefix.all { it.isLetter() } && digits.all { it.isDigit() } && suffix.all { it.isLetter() }) {
                    matches.add("$prefix$digits$suffix")
                }
            }
        }

        return matches.distinct()
    }

    /**
     * Extracts invoice total amounts:
     * - Labels: "total", "grand total", "amount due", "balance due", "invoice total", "total amount", "net amount"
     * - Currencies: ₹, Rs., Rs, INR, $, €, EUR
     */
    fun extractInvoiceTotal(text: String): List<String> {
        val matches = mutableListOf<String>()

        val labelPattern = "(?i)(?:grand\\s+total|total\\s+amount\\s+due|amount\\s+due|balance\\s+due|total\\s+due|invoice\\s+total|total\\s+amount|net\\s+amount|\\btotal\\b)"
        val currencyPattern = "(?:₹|Rs\\.?|INR|\\$|EUR|€)?"
        val amountPattern = "([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"

        val totalRegex = Regex("$labelPattern\\s*(?:[:=–\\-]|is)?\\s*($currencyPattern\\s*$amountPattern)")

        for (m in totalRegex.findAll(text)) {
            val rawAmount = m.groupValues[1].trim()
            // Clean up internal excessive whitespace
            val cleanAmount = rawAmount.replace("\\s+".toRegex(), " ")
            // Filter out bare numbers that look like years (e.g. 2024) or page numbers (< 2 chars without currency)
            val digitsOnly = cleanAmount.replace("[^0-9]".toRegex(), "")
            if (digitsOnly.length >= 2 && !(digitsOnly.length == 4 && digitsOnly.startsWith("20") && !cleanAmount.contains(Regex("[₹$€]")) && !cleanAmount.contains("."))) {
                matches.add(cleanAmount)
            }
        }

        return matches.distinct()
    }

    /**
     * Extracts dates near labels like DOB, "Date of Birth", "Invoice Date", "Due Date".
     */
    fun extractDates(text: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()

        // Date patterns
        // 1. DD/MM/YYYY or DD-MM-YYYY or YYYY-MM-DD or DD.MM.YYYY
        val standardDate = "\\b(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}|\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2})\\b"
        // 2. 15 Aug 1995, 15 August 1995, 15-Aug-1995
        val monthNameDate = "\\b(\\d{1,2}(?:st|nd|rd|th)?[\\s,-]+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s,-]+\\d{2,4})\\b"
        // 3. August 15, 1995
        val monthFirstDate = "\\b((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s,-]+\\d{1,2}(?:st|nd|rd|th)?[\\s,-]+\\d{2,4})\\b"

        val combinedDatePattern = "(?:$standardDate|$monthNameDate|$monthFirstDate)"

        // DOB
        val dobLabel = Regex("(?i)(?:date\\s+of\\s+birth|birth\\s+date|\\bdob\\b|d\\.o\\.b\\.)\\s*[:=–\\-]?\\s*([^\n\r]{0,35})")
        for (m in dobLabel.findAll(text)) {
            val window = m.groupValues[1]
            val dateMatch = Regex(combinedDatePattern, RegexOption.IGNORE_CASE).find(window)
            if (dateMatch != null) {
                results.add(FIELD_DATE_OF_BIRTH to dateMatch.value.trim())
            }
        }

        // Invoice Date
        val invoiceDateLabel = Regex("(?i)(?:invoice\\s+date|bill\\s+date|\\bdated\\b)\\s*[:=–\\-]?\\s*([^\n\r]{0,35})")
        for (m in invoiceDateLabel.findAll(text)) {
            val window = m.groupValues[1]
            val dateMatch = Regex(combinedDatePattern, RegexOption.IGNORE_CASE).find(window)
            if (dateMatch != null) {
                results.add(FIELD_INVOICE_DATE to dateMatch.value.trim())
            }
        }

        // Due Date
        val dueDateLabel = Regex("(?i)(?:due\\s+date|payment\\s+due(?:\\s+date)?)\\s*[:=–\\-]?\\s*([^\n\r]{0,35})")
        for (m in dueDateLabel.findAll(text)) {
            val window = m.groupValues[1]
            val dateMatch = Regex(combinedDatePattern, RegexOption.IGNORE_CASE).find(window)
            if (dateMatch != null) {
                results.add(FIELD_DUE_DATE to dateMatch.value.trim())
            }
        }

        return results.distinct()
    }

    /**
     * Extracts 10-digit Indian mobile numbers (optionally with +91 or 0 prefix).
     */
    fun extractPhoneNumbers(text: String): List<String> {
        val matches = mutableListOf<String>()
        val phoneRegex = Regex("(?<!\\d)(?:(?:\\+91|0)[\\s-]?)?([6-9]\\d{4}[\\s-]?\\d{5})(?!\\d)")

        for (m in phoneRegex.findAll(text)) {
            val digits = m.groupValues[1].replace("\\s+".toRegex(), "").replace("-", "")
            if (digits.length == 10 && digits[0] in '6'..'9') {
                val formatted = "+91 ${digits.substring(0, 5)} ${digits.substring(5, 10)}"
                matches.add(formatted)
            }
        }
        return matches.distinct()
    }

    /**
     * Extracts email addresses.
     */
    fun extractEmails(text: String): List<String> {
        val emailRegex = Regex("\\b([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b")
        return emailRegex.findAll(text)
            .map { it.groupValues[1].lowercase(Locale.ROOT) }
            .distinct()
            .toList()
    }

    /**
     * Extracts Voter ID / EPIC numbers:
     * - Pattern: 3 uppercase letters followed by 7 digits ([A-Z]{3}[0-9]{7}).
     * - OCR noise resilient: handles spaces or hyphens between letters and digits, and 'O'/'o' for '0'.
     */
    fun extractVoterId(text: String): List<String> {
        val matches = mutableListOf<String>()
        val epicRegex = Regex("(?<![A-Za-z0-9])([A-Za-z]{3})[\\s\\-/]?([0-9Oo]{7})(?![A-Za-z0-9])")
        for (m in epicRegex.findAll(text)) {
            val prefix = m.groupValues[1].uppercase(Locale.ROOT)
            val digits = m.groupValues[2].replace('O', '0').replace('o', '0')
            if (prefix.all { it.isLetter() } && digits.all { it.isDigit() }) {
                matches.add("$prefix$digits")
            }
        }
        return matches.distinct()
    }

    /**
     * Extracts Indian Driving Licence numbers:
     * - Patterns: [A-Z]{2}[0-9]{2}[0-9]{11} or [A-Z]{2}[-\s]?[0-9]{2}[-\s]?(?:19|20)?[0-9]{2}[-\s]?[0-9]{7}
     * - Also supports near labels like "DL No", "Driving Licence", "Licence No".
     * - OCR resilient: handles spaces, hyphens, slashes, 'O'/'o' for '0'.
     */
    fun extractDrivingLicence(text: String): List<String> {
        val matches = mutableListOf<String>()

        // 1. Label-based search: DL No: TN01 20180001234 or Driving Licence: DL-0420110149646
        val dlLabelRegex = Regex("(?i)(?:driving\\s+licen[cs]e(?:\\s+no|\\s+number)?|\\bdl\\s+no\\.?|\\bdl\\s+number|licen[cs]e\\s+no\\.?)\\s*[:=–\\-]?\\s*([A-Za-z0-9/\\-\\s]{9,22})")
        for (m in dlLabelRegex.findAll(text)) {
            val candidate = m.groupValues[1].trim().replace("\\s+".toRegex(), " ")
            val digits = candidate.replace("[^0-9]".toRegex(), "")
            if (digits.length in 9..15 && candidate.any { it.isLetter() }) {
                matches.add(candidate.replace("/", "-"))
            }
        }

        // 2. Standard format without label: State code (2 letters) + 2 digits RTO + 4-digit year + 7 digits
        val dlStandardRegex = Regex("(?<![A-Za-z0-9])([A-Za-z]{2})[-\\s]?([0-9Oo]{2})[-\\s]?((?:19|20)[0-9Oo]{2})[-\\s]?([0-9Oo]{7})(?![A-Za-z0-9])")
        for (m in dlStandardRegex.findAll(text)) {
            val state = m.groupValues[1].uppercase(Locale.ROOT)
            val rto = m.groupValues[2].replace('O', '0').replace('o', '0')
            val year = m.groupValues[3].replace('O', '0').replace('o', '0')
            val serial = m.groupValues[4].replace('O', '0').replace('o', '0')
            val formatted = "$state$rto $year$serial"
            if (matches.none { it.replace(" ", "").equals(formatted.replace(" ", ""), ignoreCase = true) }) {
                matches.add(formatted)
            }
        }

        return matches.distinct()
    }

    /**
     * Extracts Indian Passport numbers:
     * - Pattern: 1 uppercase letter followed by 7 digits ([A-Z][0-9]{7}).
     * - OCR resilient: handles spaces or hyphens after letter, and 'O'/'o' for '0'.
     */
    fun extractPassport(text: String): List<String> {
        val matches = mutableListOf<String>()
        val hasPassportContext = text.contains("passport", ignoreCase = true) ||
                                 text.contains("republic of india", ignoreCase = true)

        // 1. Label-based: Passport No: A1234567 or Passport Number: J 9876543
        val labelRegex = Regex("(?i)(?:passport(?:\\s+no\\.?|\\s+number)?)\\s*[:=–\\-]?\\s*([A-Za-z])[\\s\\-/]?([0-9Oo]{7})(?![A-Za-z0-9])")
        for (m in labelRegex.findAll(text)) {
            val letter = m.groupValues[1].uppercase(Locale.ROOT)
            val digits = m.groupValues[2].replace('O', '0').replace('o', '0')
            if (digits.all { it.isDigit() }) {
                matches.add("$letter$digits")
            }
        }

        // 2. Standalone pattern when passport context is present in the document
        if (hasPassportContext) {
            val standaloneRegex = Regex("(?<![A-Za-z0-9])([A-Za-z])[\\s\\-/]?([0-9Oo]{7})(?![A-Za-z0-9])")
            for (m in standaloneRegex.findAll(text)) {
                val letter = m.groupValues[1].uppercase(Locale.ROOT)
                val digits = m.groupValues[2].replace('O', '0').replace('o', '0')
                if (digits.all { it.isDigit() }) {
                    val code = "$letter$digits"
                    if (code !in matches) {
                        matches.add(code)
                    }
                }
            }
        }

        return matches.distinct()
    }

    /**
     * Extracts full name of the user/holder near common identification labels.
     */
    fun extractFullName(text: String): List<String> {
        val matches = mutableListOf<String>()
        val ignoredInstitutionalNames = setOf(
            "government of india", "income tax department", "election commission of india",
            "unique identification authority", "state bank of india", "reserve bank of india",
            "department", "authority", "signature", "elector", "cardholder", "applicant"
        )

        val nameLabelRegex = Regex("(?i)(?:full\\s+name|name\\s+of\\s+(?:holder|cardholder|applicant)|holder(?:'s)?\\s+name|cardholder\\s+name|applicant\\s+name|employee\\s+name|\\bname\\b)\\s*[:=–\\-]?\\s*([A-Za-z\\s.]{3,35})(?:[\r\n]|$)")
        for (m in nameLabelRegex.findAll(text)) {
            val candidate = m.groupValues[1].trim()
                .replace("\\s+".toRegex(), " ")
                .trimEnd('.', ',', ':', ';')

            val lower = candidate.lowercase(Locale.ROOT)
            val isIgnored = ignoredInstitutionalNames.any { lower.contains(it) } ||
                            lower.startsWith("of ") || lower.contains("date") ||
                            lower.contains("card") || lower.contains("number")

            if (!isIgnored && candidate.length >= 3 && candidate.any { it.isLetter() }) {
                // Ensure name consists of 1 to 4 capitalized/letter tokens
                val words = candidate.split(" ")
                if (words.size in 1..4 && words.all { it.isNotBlank() && it.first().isLetter() }) {
                    matches.add(candidate)
                }
            }
        }
        return matches.distinct()
    }

    /**
     * Extracts address near address labels.
     */
    fun extractAddress(text: String): List<String> {
        val matches = mutableListOf<String>()
        val addressRegex = Regex("(?i)(?:permanent\\s+address|residential\\s+address|communication\\s+address|present\\s+address|\\baddress\\b)\\s*[:=–\\-]?\\s*([^\r\n]{10,120})")
        for (m in addressRegex.findAll(text)) {
            val raw = m.groupValues[1].trim().replace("\\s+".toRegex(), " ")
            if (raw.length >= 12 && !raw.contains("http", ignoreCase = true) && !raw.contains("@")) {
                matches.add(raw)
            }
        }
        return matches.distinct()
    }

    /**
     * Extracts bank account numbers (9 to 18 digits near account labels).
     */
    fun extractBankAccount(text: String): List<String> {
        val matches = mutableListOf<String>()
        val accRegex = Regex("(?i)(?:bank\\s+account(?:\\s+number|\\s+no\\.?)?|account\\s+number|account\\s+no\\.?|a/c\\s+no\\.?|a/c\\s+number|bank\\s+a/c)\\s*[:=–\\-]?\\s*([0-9\\s-]{9,24})(?!\\d)")
        for (m in accRegex.findAll(text)) {
            val digits = m.groupValues[1].replace("[^0-9]".toRegex(), "")
            if (digits.length in 9..18 && !digits.startsWith("00000000")) {
                matches.add(digits)
            }
        }
        return matches.distinct()
    }

    /**
     * Extracts Indian Financial System Code (IFSC):
     * - Pattern: 4 uppercase letters, digit 0 (or 'O'), 6 alphanumeric characters ([A-Z]{4}0[A-Z0-9]{6}).
     * - OCR resilient: handles 'O' in place of '0'.
     */
    fun extractIfsc(text: String): List<String> {
        val matches = mutableListOf<String>()
        val ifscRegex = Regex("(?<![A-Za-z0-9])([A-Za-z]{4})[0Oo]([A-Za-z0-9]{6})(?![A-Za-z0-9])")
        for (m in ifscRegex.findAll(text)) {
            val prefix = m.groupValues[1].uppercase(Locale.ROOT)
            var suffix = m.groupValues[2].uppercase(Locale.ROOT)
            if (suffix.any { it.isDigit() }) {
                suffix = suffix.replace('O', '0')
            }
            matches.add("${prefix}0$suffix")
        }
        return matches.distinct()
    }

    /**
     * Masks sensitive values (Aadhaar, PAN, Voter ID, Passport, DL, Bank Account) showing ONLY the last few characters.
     * Never write full values to logs or unmasked direct outputs.
     */
    fun maskValue(fieldName: String, value: String): String {
        val clean = value.trim()
        return when (fieldName) {
            FIELD_AADHAAR -> {
                val digitsOnly = clean.replace("[^0-9]".toRegex(), "")
                val last4 = if (digitsOnly.length >= 4) digitsOnly.takeLast(4) else clean.takeLast(4)
                "•••• •••• $last4"
            }
            FIELD_PAN -> {
                val last4 = clean.takeLast(4)
                "••••••$last4"
            }
            FIELD_VOTER_ID -> {
                val last3 = clean.takeLast(3)
                "•••••••$last3"
            }
            FIELD_PASSPORT -> {
                val last3 = clean.takeLast(3)
                "•••••$last3"
            }
            FIELD_DRIVING_LICENCE -> {
                val last4 = clean.takeLast(4)
                "••••••••$last4"
            }
            FIELD_BANK_ACCOUNT -> {
                val last4 = clean.takeLast(4)
                "••••••••$last4"
            }
            else -> clean
        }
    }
}
