package com.docsphere.ai.offline

import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * World-Class On-Device Document Reasoning & Synthesis Engine.
 * Modeled 100% after Google Drive Gemini AI (Gemini in Drive).
 *
 * Provides:
 * - Executive Summarization with Key Takeaways, Figures & Action Items
 * - Targeted Fact-Finding & Question Answering with Direct Answers First
 * - Full Professional Drafting (Emails, Memos, Executive Briefs, Cover Letters)
 * - Multi-Document Comparative Analysis (Markdown Comparison Tables)
 * - Deep Entity & Financial Extraction (Currencies, Dates, Contacts, Clauses)
 * - Verifiable Document Citations & Contextual Interactive Follow-up Prompts
 */
@Singleton
class DocumentReasoningEngine @Inject constructor() {

    private val datePattern = Pattern.compile(
        "\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2}(?:st|nd|rd|th)?,?\\s+\\d{4}|(?:Q[1-4]|Quarter\\s+[1-4])\\s*\\d{4}?)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val moneyPattern = Pattern.compile(
        "(?:[\\$€£₹]|USD|INR|EUR|GBP)\\s*[\\d,]+(?:\\.\\d{1,2})?|\\b[\\d,]+(?:\\.\\d{1,2})?\\s*(?:dollars|rupees|cents|euros|lakhs|crores)\\b|\\b\\d+(?:\\.\\d+)?%\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val emailPattern = Pattern.compile(
        "[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+",
        Pattern.CASE_INSENSITIVE
    )

    private val phonePattern = Pattern.compile(
        "(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parsed document structure containing metadata and extracted elements.
     */
    private data class ParsedDoc(
        val filename: String,
        val extension: String,
        val fullText: String,
        val paragraphs: List<String>,
        val sentences: List<String>,
        val dates: List<String>,
        val amounts: List<String>,
        val emails: List<String>,
        val phones: List<String>,
        val docType: String
    )

    /**
     * Main reasoning entry point: classifies user intent and routes to Drive AI synthesis pipeline.
     * Returns null if documents do not contain an answer to the query, allowing general AI fallback.
     */
    fun reason(
        query: String,
        contextChunks: List<String>,
        isExpandedModel: Boolean = true
    ): String? {
        val lowerQuery = query.trim().lowercase(Locale.ROOT)
        val parsedDocs = parseChunks(contextChunks)

        if (parsedDocs.isEmpty()) {
            return null
        }

        // 1. Document Inventory / Catalog Intent
        if (isCatalogIntent(lowerQuery)) {
            return synthesizeCatalog(parsedDocs)
        }

        // 2. Drafting Intent (Emails, Memos, Letters, Briefs)
        if (isDraftIntent(lowerQuery)) {
            return synthesizeDraft(query, lowerQuery, parsedDocs, isExpandedModel)
        }

        // 3. Comparison Intent (Compare 2 or more files)
        if (isCompareIntent(lowerQuery) && parsedDocs.size >= 2) {
            return synthesizeComparison(query, parsedDocs, isExpandedModel)
        }

        // 4. Entity Extraction Intent (Amounts, Dates, Contacts)
        if (isEntityExtractionIntent(lowerQuery)) {
            return synthesizeEntityAudit(lowerQuery, parsedDocs)
        }

        // 4.5. Specific Field Query (Aadhaar, PAN, Resume, Invoice, ID cards)
        val fieldExtraction = synthesizeSpecificFieldQuery(query, lowerQuery, parsedDocs)
        if (fieldExtraction != null) {
            return fieldExtraction
        }

        // 5. Summarization Intent
        if (isSummaryIntent(lowerQuery)) {
            return synthesizeExecutiveSummary(query, parsedDocs, isExpandedModel)
        }

        // 6. Targeted Question Answering & Fact Retrieval
        return synthesizeTargetedQA(query, lowerQuery, parsedDocs, isExpandedModel)
    }

    // ── Intent Detectors ──────────────────────────────────────────────────────

    private fun isCatalogIntent(q: String): Boolean = listOf(
        "what file", "what doc", "list file", "list doc", "show file", "show doc",
        "my file", "my doc", "which file", "which doc", "how many file", "how many doc",
        "library", "inventory", "what do i have", "all files", "indexed files"
    ).any { q.contains(it) }

    private fun isDraftIntent(q: String): Boolean = listOf(
        "draft", "write email", "write an email", "send email", "compose", "write letter",
        "write memo", "cover letter", "craft an email", "create memo", "draft reply",
        "prepare email", "draft message"
    ).any { q.contains(it) }

    private fun isCompareIntent(q: String): Boolean = listOf(
        "compare", "difference", "differences", "vs", "versus", "comparison", "distinct"
    ).any { q.contains(it) }

    private fun isEntityExtractionIntent(q: String): Boolean = listOf(
        "extract", "list all dates", "find dates", "list money", "amounts", "financial figures",
        "contacts", "find email", "phone numbers", "audit", "all numbers", "figures"
    ).any { q.contains(it) }

    private fun isSummaryIntent(q: String): Boolean = listOf(
        "summarize", "summary", "brief", "overview", "highlight", "highlights", "breakdown",
        "recap", "explain this file", "what is this document", "what does this document say",
        "tell me about this file", "about this doc", "main points", "key points"
    ).any { q.contains(it) }

    // ── Parsing & Entity Extraction ──────────────────────────────────────────

    private fun parseChunks(chunks: List<String>): List<ParsedDoc> {
        return chunks.mapIndexed { idx, chunk ->
            val lines = chunk.lines()
            val filename = lines.firstOrNull()?.replace("[", "")?.replace("]", "")?.trim()
                ?: "Document ${idx + 1}"
            val rawBody = lines.drop(1).joinToString("\n").trim()
            val body = sanitizeText(rawBody)
            val ext = filename.substringAfterLast('.', "").uppercase(Locale.ROOT)

            val paragraphs = body.split(Regex("\n{2,}"))
                .map { it.trim().replace(Regex("\\s+"), " ") }
                .filter { it.length > 20 }
                .ifEmpty {
                    if (body.isNotBlank()) listOf(body.take(300))
                    else listOf("Document '${filename}' (${ext.ifEmpty { "FILE" }}) indexed in Docsphere.")
                }

            val sentences = body.split(Regex("(?<=[.!?])\\s+|\n+"))
                .map { it.trim().replace(Regex("\\s+"), " ") }
                .filter { it.length in 3..350 && !it.startsWith("http") }
                .ifEmpty {
                    if (body.isNotBlank()) listOf(body.take(200))
                    else listOf("Document '${filename}' is cataloged in Docsphere library.")
                }

            val dates = extractMatches(body, datePattern)
            val amounts = extractMatches(body, moneyPattern)
            val emails = extractMatches(body, emailPattern)
            val phones = extractMatches(body, phonePattern)

            val lowerBody = body.lowercase(Locale.ROOT)
            val lowerName = filename.lowercase(Locale.ROOT)
            val docType = when {
                lowerName.contains("aadhaar") || lowerName.contains("aadhar") || lowerBody.contains("aadhaar") || lowerBody.contains("aadhar") || lowerBody.contains("uidai") || lowerBody.contains("unique identification") -> "Aadhaar Card"
                lowerName.contains("pan") || lowerBody.contains("permanent account number") || lowerBody.contains("income tax department") -> "PAN Card"
                lowerName.contains("resume") || lowerName.contains("cv") || lowerBody.contains("curriculum vitae") || (lowerBody.contains("education") && lowerBody.contains("experience")) -> "Resume / CV"
                lowerBody.contains("invoice") || lowerBody.contains("bill to") || lowerBody.contains("tax invoice") -> "Invoice / Bill"
                lowerBody.contains("agreement") || lowerBody.contains("contract") || lowerBody.contains("nda") || lowerBody.contains("terms and conditions") -> "Legal Agreement"
                lowerBody.contains("receipt") || lowerBody.contains("payment receipt") -> "Payment Receipt"
                lowerBody.contains("report") || lowerBody.contains("executive summary") || lowerBody.contains("findings") -> "Executive Report"
                lowerBody.contains("specification") || lowerBody.contains("architecture") || lowerBody.contains("api") -> "Technical Spec"
                ext in listOf("XLSX", "XLS", "CSV") -> "Spreadsheet Data"
                else -> "Document ($ext)"
            }

            ParsedDoc(
                filename = filename,
                extension = ext,
                fullText = body,
                paragraphs = paragraphs,
                sentences = sentences,
                dates = dates,
                amounts = amounts,
                emails = emails,
                phones = phones,
                docType = docType
            )
        }
    }

    private fun extractMatches(text: String, pattern: Pattern): List<String> {
        val matches = mutableListOf<String>()
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val item = matcher.group().trim()
            if (item.isNotEmpty() && !matches.contains(item)) {
                matches.add(item)
            }
        }
        return matches
    }

    // ── Drive AI Synthesizers ────────────────────────────────────────────────

    /**
     * 1. Google Drive Gemini Executive Summary (Single & Multi-Document Portfolio)
     */
    private fun synthesizeExecutiveSummary(
        query: String,
        docs: List<ParsedDoc>,
        isExpandedModel: Boolean
    ): String = buildString {
        if (docs.size == 1) {
            synthesizeSingleDocSummary(this, docs.first(), isExpandedModel)
        } else {
            synthesizeMultiDocSummary(this, docs, isExpandedModel)
        }
    }

    private fun synthesizeMultiDocSummary(
        builder: StringBuilder,
        docs: List<ParsedDoc>,
        isExpandedModel: Boolean
    ) = with(builder) {
        appendLine("### 📋 Executive Summary — Multi-Document Portfolio")
        appendLine("Synthesized across **${docs.size} referenced documents**:")
        appendLine()

        // 1. High-level portfolio overview
        val docTypes = docs.map { it.docType }.distinct().joinToString(", ")
        appendLine("Your document collection contains **${docs.size} files** spanning $docTypes. Below is the executive synthesis of key findings and figures across your library.")
        appendLine()

        // 2. Individual Document Analysis
        appendLine("#### 📁 File Analysis")
        docs.forEachIndexed { index, doc ->
            val icon = when (doc.extension) {
                "PDF" -> "📄"
                "DOCX", "DOC" -> "📝"
                "XLSX", "XLS", "CSV" -> "📊"
                "PPTX", "PPT" -> "📽️"
                "JPG", "JPEG", "PNG" -> "🖼️"
                else -> "📁"
            }
            val brief = doc.paragraphs.firstOrNull()?.take(180)?.trim()
                ?: doc.sentences.firstOrNull()?.take(180)?.trim()
                ?: "Indexed document ready for deep semantic query and text synthesis."
            appendLine("**${index + 1}. $icon `${doc.filename}`** · *${doc.docType}*")
            appendLine("> $brief")

            val docMetrics = mutableListOf<String>()
            if (doc.amounts.isNotEmpty()) docMetrics.add("Amounts: ${doc.amounts.take(2).joinToString(", ")}")
            if (doc.dates.isNotEmpty()) docMetrics.add("Dates: ${doc.dates.take(2).joinToString(", ")}")
            if (docMetrics.isNotEmpty()) {
                appendLine("  *Key Points: ${docMetrics.joinToString(" · ")}*")
            }
            appendLine()
        }

        // 3. Consolidated Cross-Document Key Takeaways
        appendLine("### 🔑 Key Takeaways & Core Findings")
        val crossTakeaways = docs.flatMap { doc ->
            selectHighValueSentences(doc, limit = 2).map { s -> doc.filename to s }
        }.take(if (isExpandedModel) 6 else 4)

        if (crossTakeaways.isNotEmpty()) {
            crossTakeaways.forEach { (filename, sentence) ->
                val boldPrefix = sentence.take(35).substringBeforeLast(" ", "")
                val rest = sentence.removePrefix(boldPrefix).trim()
                if (boldPrefix.isNotBlank()) {
                    appendLine("• **$boldPrefix**: $rest *(from `${filename}`)*")
                } else {
                    appendLine("• $sentence *(from `${filename}`)*")
                }
            }
        } else {
            appendLine("• Textual records successfully cataloged across all files.")
        }
        appendLine()

        // 4. Critical Metrics & Financial Figures
        val allAmounts = docs.flatMap { it.amounts }.distinct().take(4)
        val allDates = docs.flatMap { it.dates }.distinct().take(4)
        if (allAmounts.isNotEmpty() || allDates.isNotEmpty()) {
            appendLine("### 📊 Extracted Figures & Timelines")
            if (allAmounts.isNotEmpty()) {
                appendLine("• **Key Amounts**: ${allAmounts.joinToString(", ") { "**$it**" }}")
            }
            if (allDates.isNotEmpty()) {
                appendLine("• **Critical Dates**: ${allDates.joinToString(", ") { "**$it**" }}")
            }
            appendLine()
        }

        // 5. Source Citations
        appendLine("📄 **Sources:** " + docs.joinToString(", ") { "`${it.filename}`" })
    }

    private fun synthesizeSingleDocSummary(
        builder: StringBuilder,
        mainDoc: ParsedDoc,
        isExpandedModel: Boolean
    ) = with(builder) {
        appendLine("### 📋 Executive Summary")
        appendLine("**Document:** `${mainDoc.filename}` · *${mainDoc.docType}*")
        appendLine()

        // Synthesis Overview Paragraph
        val overview = buildOverviewParagraph(mainDoc)
        appendLine(overview)
        appendLine()

        // Key Findings / Highlights
        appendLine("### 🔑 Key Takeaways")
        val takeaways = selectHighValueSentences(mainDoc, limit = if (isExpandedModel) 5 else 3)
        if (takeaways.isNotEmpty()) {
            takeaways.forEach { sentence ->
                val boldPrefix = sentence.take(35).substringBeforeLast(" ", "")
                val rest = sentence.removePrefix(boldPrefix).trim()
                if (boldPrefix.isNotBlank()) {
                    appendLine("• **$boldPrefix**: $rest")
                } else {
                    appendLine("• $sentence")
                }
            }
        } else {
            appendLine("• Document contains verified textual records ready for querying.")
        }
        appendLine()

        // Critical Metrics & Financial Figures
        if (mainDoc.amounts.isNotEmpty() || mainDoc.dates.isNotEmpty() || mainDoc.emails.isNotEmpty()) {
            appendLine("### 📊 Extracted Metrics")
            if (mainDoc.amounts.isNotEmpty()) {
                appendLine("• **Amounts**: ${mainDoc.amounts.distinct().take(3).joinToString(", ") { "**$it**" }}")
            }
            if (mainDoc.dates.isNotEmpty()) {
                appendLine("• **Dates**: ${mainDoc.dates.distinct().take(3).joinToString(", ") { "**$it**" }}")
            }
            if (mainDoc.emails.isNotEmpty() || mainDoc.phones.isNotEmpty()) {
                val contacts = (mainDoc.emails + mainDoc.phones).distinct().take(2)
                appendLine("• **Contacts**: ${contacts.joinToString(", ") { "`$it`" }}")
            }
            appendLine()
        }

        // Citations
        appendLine("📄 **Source:** `${mainDoc.filename}`")
    }

    /**
     * 2. Google Drive Gemini Drafting (Email, Memo, Cover Letter)
     */
    private fun synthesizeDraft(
        query: String,
        lowerQuery: String,
        docs: List<ParsedDoc>,
        isExpandedModel: Boolean
    ): String = buildString {
        val doc = docs.first()
        val isEmail = lowerQuery.contains("email")
        val isMemo = lowerQuery.contains("memo")
        val isCoverLetter = lowerQuery.contains("cover letter")

        val title = when {
            isCoverLetter -> "Professional Cover Letter"
            isMemo -> "Executive Memorandum"
            else -> "Professional Email Draft"
        }

        appendLine("### ✉️ $title")
        appendLine("Crafted directly from facts in **`${doc.filename}`**:")
        appendLine()
        appendLine("---")

        if (isEmail) {
            val subjectTopic = doc.filename.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
            appendLine("**Subject:** Update & Summary Regarding $subjectTopic")
            appendLine()
            appendLine("Dear Team / Client,")
            appendLine()
            appendLine("I hope this message finds you well.")
            appendLine()
            appendLine("I am writing to share the key details synthesized from **${doc.filename}**:")
            appendLine()
            val bullets = selectHighValueSentences(doc, limit = 3)
            bullets.forEach { appendLine("• $it") }
            if (doc.amounts.isNotEmpty()) {
                appendLine("• **Financial Scope**: ${doc.amounts.take(2).joinToString(", ")}")
            }
            if (doc.dates.isNotEmpty()) {
                appendLine("• **Timeline / Deadline**: ${doc.dates.first()}")
            }
            appendLine()
            appendLine("Please review and let me know if you have any questions.")
            appendLine()
            appendLine("Best regards,  ")
            appendLine("[Your Name]")
        } else if (isCoverLetter) {
            appendLine("**Subject:** Application for [Target Role] — [Your Name]")
            appendLine()
            appendLine("Dear Hiring Manager,")
            appendLine()
            appendLine("I am writing to express my strong enthusiasm for the [Target Role] position. Demonstrating a track record in **${doc.filename}**, I bring proven expertise and hands-on execution skills.")
            appendLine()
            val bullets = selectHighValueSentences(doc, limit = 3)
            appendLine("**Key Qualifications:**")
            bullets.forEach { appendLine("• $it") }
            appendLine()
            appendLine("I would welcome the opportunity to discuss how my background aligns with your upcoming goals.")
            appendLine()
            appendLine("Sincerely,  ")
            appendLine("[Your Name]")
            if (doc.emails.isNotEmpty()) appendLine("${doc.emails.first()}")
        } else {
            // Executive Memo
            appendLine("**MEMORANDUM**")
            appendLine("**TO:** Executive Leadership")
            appendLine("**DATE:** ${doc.dates.firstOrNull() ?: "Current"}")
            appendLine("**SUBJECT:** Executive Brief: ${doc.filename.substringBeforeLast('.')}")
            appendLine()
            appendLine("### 1. Objective")
            appendLine("This memorandum summarizes findings extracted from **${doc.filename}**.")
            appendLine()
            appendLine("### 2. Key Findings")
            selectHighValueSentences(doc, limit = 3).forEach { appendLine("• $it") }
            appendLine()
            appendLine("### 3. Recommendation")
            appendLine("Proceed with scheduled milestones as specified.")
        }

        appendLine("---")
        appendLine()
        appendLine("📄 **Source:** `${doc.filename}`")
    }

    /**
     * 3. Google Drive Gemini Targeted Question Answering & Fact Retrieval
     * Delivers ONLY the direct answer requested without padding or unwanted sections.
     */
    private fun synthesizeTargetedQA(
        query: String,
        lowerQuery: String,
        docs: List<ParsedDoc>,
        isExpandedModel: Boolean
    ): String? {
        val queryKeywords = lowerQuery.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && it !in STOP_WORDS }

        if (queryKeywords.isEmpty()) return null

        data class ScoredMatch(val doc: ParsedDoc, val excerpt: String, val score: Int)
        val scoredList = mutableListOf<ScoredMatch>()

        for (doc in docs) {
            val docBonus = if (doc.extension in listOf("PDF", "DOCX", "DOC", "XLSX", "TXT")) 3 else 0

            // 1. Score full paragraphs for rich contextual answers
            for (p in doc.paragraphs) {
                val pLower = p.lowercase(Locale.ROOT)
                var score = 0
                for (kw in queryKeywords) {
                    if (pLower.contains(kw)) score += 3
                }
                if (score >= 6) {
                    scoredList.add(ScoredMatch(doc, p, score + docBonus + 2))
                }
            }

            // 2. Score individual sentences for high precision
            for (s in doc.sentences) {
                val sLower = s.lowercase(Locale.ROOT)
                var score = 0
                for (kw in queryKeywords) {
                    if (sLower.contains(kw)) score += 3
                }
                if ((lowerQuery.contains("amount") || lowerQuery.contains("price") || lowerQuery.contains("cost") || lowerQuery.contains("fee") || lowerQuery.contains("salary") || lowerQuery.contains("total")) && doc.amounts.any { s.contains(it) }) {
                    score += 6
                }
                if ((lowerQuery.contains("date") || lowerQuery.contains("when") || lowerQuery.contains("deadline") || lowerQuery.contains("time") || lowerQuery.contains("expire")) && doc.dates.any { s.contains(it) }) {
                    score += 6
                }
                if ((lowerQuery.contains("who") || lowerQuery.contains("contact") || lowerQuery.contains("email") || lowerQuery.contains("phone") || lowerQuery.contains("person")) && (doc.emails.any { s.contains(it) } || doc.phones.any { s.contains(it) })) {
                    score += 6
                }

                if (score >= 6) {
                    scoredList.add(ScoredMatch(doc, s, score + docBonus))
                }
            }
        }

        val highConfidenceMatches = scoredList
            .sortedByDescending { it.score }
            .distinctBy { it.excerpt.take(60) }
            .take(2)

        if (highConfidenceMatches.isNotEmpty()) {
            val best = highConfidenceMatches.first()
            val cleanExcerpt = cleanSentenceForAnswer(best.excerpt)
            return buildString {
                appendLine("Based on **`${best.doc.filename}`**:")
                appendLine()
                appendLine("> \"$cleanExcerpt\"")
                appendLine()
                if (highConfidenceMatches.size > 1) {
                    val second = highConfidenceMatches[1]
                    val secondClean = cleanSentenceForAnswer(second.excerpt)
                    if (secondClean != cleanExcerpt) {
                        appendLine("Additionally, in **`${second.doc.filename}`**:")
                        appendLine("> \"$secondClean\"")
                        appendLine()
                    }
                }
                val matchedDocs = highConfidenceMatches.map { it.doc.filename }.distinct()
                appendLine("📄 **Source:** " + matchedDocs.joinToString(", ") { "`$it`" })
            }
        }

        // If user specifically asked about their personal/local files, give a clear direct notification
        val isExplicitDocSearch = listOf(
            "in my", "in this", "from my", "my doc", "my file", "my pdf",
            "search document", "search file", "look in", "find in file"
        ).any { lowerQuery.contains(it) }

        if (isExplicitDocSearch) {
            return "I searched your referenced documents for **\"$query\"**, but no matching records were found."
        }

        return null
    }

    /**
     * 4. Multi-Document Comparison Table
     */
    private fun synthesizeComparison(
        query: String,
        docs: List<ParsedDoc>,
        isExpandedModel: Boolean
    ): String = buildString {
        val doc1 = docs[0]
        val doc2 = docs[1]

        appendLine("### 📊 Document Comparison")
        appendLine("Comparing **`${doc1.filename}`** vs **`${doc2.filename}`**:")
        appendLine()

        // Comparison Table
        appendLine("| Evaluation Attribute | `${doc1.filename}` | `${doc2.filename}` |")
        appendLine("| :--- | :--- | :--- |")
        appendLine("| **Document Category** | ${doc1.docType} | ${doc2.docType} |")
        appendLine("| **File Format** | ${doc1.extension} | ${doc2.extension} |")
        appendLine("| **Key Figures** | ${doc1.amounts.take(2).joinToString(", ").ifBlank { "N/A" }} | ${doc2.amounts.take(2).joinToString(", ").ifBlank { "N/A" }} |")
        appendLine("| **Key Dates** | ${doc1.dates.take(2).joinToString(", ").ifBlank { "N/A" }} | ${doc2.dates.take(2).joinToString(", ").ifBlank { "N/A" }} |")
        appendLine("| **Contact Info** | ${doc1.emails.firstOrNull() ?: "None"} | ${doc2.emails.firstOrNull() ?: "None"} |")
        appendLine()

        appendLine("### 💡 Comparative Summary")
        val sample1 = doc1.sentences.firstOrNull() ?: "Primary reference record."
        val sample2 = doc2.sentences.firstOrNull() ?: "Secondary reference record."
        appendLine("• **`${doc1.filename}`**: *\"$sample1\"*")
        appendLine("• **`${doc2.filename}`**: *\"$sample2\"*")
        appendLine()

        appendLine("📄 **Sources:** `${doc1.filename}`, `${doc2.filename}`")
    }

    /**
     * 5. Entity & Financial Audit
     */
    private fun synthesizeEntityAudit(lowerQuery: String, docs: List<ParsedDoc>): String = buildString {
        appendLine("### 🔍 Extracted Entities & Audit Report")
        appendLine("Synthesized across **${docs.size}** referenced file(s):")
        appendLine()

        val allAmounts = docs.flatMap { doc -> doc.amounts.map { doc.filename to it } }
        val allDates = docs.flatMap { doc -> doc.dates.map { doc.filename to it } }
        val allEmails = docs.flatMap { doc -> doc.emails.map { doc.filename to it } }
        val allPhones = docs.flatMap { doc -> doc.phones.map { doc.filename to it } }

        if (allAmounts.isNotEmpty()) {
            appendLine("#### 💰 Financial Figures & Currency Values")
            allAmounts.take(8).forEach { (doc, amt) ->
                appendLine("• **$amt** — *found in `$doc`*")
            }
            appendLine()
        }

        if (allDates.isNotEmpty()) {
            appendLine("#### 📅 Calendar Dates & Deadlines")
            allDates.take(8).forEach { (doc, dt) ->
                appendLine("• **$dt** — *found in `$doc`*")
            }
            appendLine()
        }

        if (allEmails.isNotEmpty() || allPhones.isNotEmpty()) {
            appendLine("#### 📞 Contact Channels & Identifiers")
            allEmails.take(4).forEach { (doc, email) ->
                appendLine("• Email: `$email` — *found in `$doc`*")
            }
            allPhones.take(4).forEach { (doc, phone) ->
                appendLine("• Phone: `$phone` — *found in `$doc`*")
            }
            appendLine()
        }

        appendLine("📄 **Sources:** " + docs.joinToString(", ") { "`${it.filename}`" })
    }

    /**
     * 6. Google Drive Style File Catalog
     */
    private fun synthesizeCatalog(docs: List<ParsedDoc>): String = buildString {
        appendLine("### 📚 Your Google Drive & Local Document Library")
        appendLine()
        appendLine("Indexed and ready for intelligent AI search: **${docs.size}** document(s)")
        appendLine()

        docs.forEachIndexed { index, doc ->
            val icon = when (doc.extension) {
                "PDF" -> "📄"
                "DOCX", "DOC" -> "📝"
                "XLSX", "XLS", "CSV" -> "📊"
                "PPTX", "PPT" -> "📽️"
                "JPG", "JPEG", "PNG" -> "🖼️"
                else -> "📁"
            }
            val preview = doc.paragraphs.firstOrNull()?.take(110)?.trim() ?: "Indexed file ready for semantic Q&A"
            appendLine("${index + 1}. $icon **`${doc.filename}`** — *${doc.docType}*")
            appendLine("   > *$preview…*")
            appendLine()
        }

        appendLine("💡 **You can ask me to:**")
        appendLine("• *\"Summarize [filename]\"*")
        appendLine("• *\"Draft an email based on [filename]\"*")
        appendLine("• *\"Compare [file1] and [file2]\"*")
    }

    // ── Linguistic Helpers ───────────────────────────────────────────────────

    private fun buildOverviewParagraph(doc: ParsedDoc): String {
        val firstPara = doc.paragraphs.firstOrNull { p ->
            p.length in 50..350 &&
            !p.lowercase().contains("copyright") &&
            !p.lowercase().contains("all rights reserved")
        }
        if (firstPara != null) {
            return firstPara
        }
        val topSentences = selectHighValueSentences(doc, limit = 2)
        if (topSentences.isNotEmpty()) {
            return topSentences.joinToString(" ")
        }
        return "This ${doc.docType} contains verified structural records and documentation indexed in Docsphere for executive synthesis and semantic inquiry."
    }

    private fun selectHighValueSentences(doc: ParsedDoc, limit: Int): List<String> {
        val highSignalKeywords = setOf(
            "agree", "agrees", "agreement", "require", "requires", "required", "shall", "must",
            "objective", "scope", "purpose", "obligation", "deliverable", "deliverables",
            "payment", "fee", "cost", "total", "rate", "budget", "price",
            "deadline", "schedule", "term", "terms", "milestone", "milestones",
            "responsible", "responsibility", "client", "contractor", "party", "parties",
            "conclude", "conclusion", "summary", "result", "results", "finding", "findings",
            "approved", "effective", "commence", "termination", "confidential"
        )

        return doc.sentences
            .filter { s ->
                s.length in 35..250 &&
                !s.startsWith("http") &&
                !s.lowercase().contains("copyright") &&
                (s.contains(" ") && s.count { it == ' ' } >= 5)
            }
            .distinct()
            .map { sentence ->
                val lower = sentence.lowercase(Locale.ROOT)
                var score = 0

                // Score by high-signal keywords
                for (kw in highSignalKeywords) {
                    if (lower.contains(kw)) score += 3
                }

                // Score by entity presence
                if (doc.amounts.any { sentence.contains(it) }) score += 4
                if (doc.dates.any { sentence.contains(it) }) score += 4
                if (doc.emails.any { sentence.contains(it) } || doc.phones.any { sentence.contains(it) }) score += 3

                // Ideal length bonus (60 to 180 chars is typical executive sentence)
                if (sentence.length in 60..180) score += 2

                sentence to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }

    private fun sanitizeText(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("[\\t\\r]+"), " ")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    private fun cleanSentenceForAnswer(sentence: String): String {
        var clean = sanitizeText(sentence)
        if (clean.startsWith("\"") && clean.endsWith("\"")) {
            clean = clean.substring(1, clean.length - 1).trim()
        }
        return clean
    }

    /**
     * Dedicated Field-Specific Extractor for Aadhaar, PAN, Resumes, and Invoices.
     * Extracts exact target values instead of dumping generic text.
     */
    private fun synthesizeSpecificFieldQuery(
        query: String,
        lowerQuery: String,
        docs: List<ParsedDoc>
    ): String? {
        val isAadhaarQuery = lowerQuery.contains("aadhar") || lowerQuery.contains("aadhaar") ||
                lowerQuery.contains("adhaar") || lowerQuery.contains("adhar") || lowerQuery.contains("uidai")

        val isPanQuery = lowerQuery.contains("pan card") || lowerQuery.contains("pan number") ||
                (lowerQuery.contains("pan") && (lowerQuery.contains("name") || lowerQuery.contains("no")))

        val isResumeQuery = lowerQuery.contains("resume") || lowerQuery.contains("cv") || lowerQuery.contains("curriculum vitae")

        val isInvoiceQuery = lowerQuery.contains("invoice") || lowerQuery.contains("bill")

        // ── 1. Aadhaar Card Field Inquiries ─────────────────────────────────
        if (isAadhaarQuery) {
            val aadhaarDoc = docs.firstOrNull { doc ->
                val dl = doc.filename.lowercase(Locale.ROOT)
                val tl = doc.fullText.lowercase(Locale.ROOT)
                dl.contains("aadhaar") || dl.contains("aadhar") || dl.contains("uidai") ||
                tl.contains("aadhaar") || tl.contains("aadhar") || tl.contains("uidai") ||
                tl.contains("unique identification") || tl.contains("government of india")
            }

            if (aadhaarDoc != null) {
                // Name on Aadhaar
                if (lowerQuery.contains("name") || lowerQuery.contains("who") || lowerQuery.contains("person") ||
                    lowerQuery.contains("cardholder") || lowerQuery.contains("holder")) {
                    val name = com.docsphere.data.repository.UserIdentityResolver.extractAadhaarName(aadhaarDoc.fullText)
                    return if (name != null) {
                        "The name on your Aadhaar card is **$name**."
                    } else {
                        "I found your Aadhaar document (**${aadhaarDoc.filename}**), but could not clearly identify the cardholder name. Please make sure the text is clearly legible."
                    }
                }
                // Aadhaar Number
                if (lowerQuery.contains("number") || lowerQuery.contains("no") || lowerQuery.contains("uid") || lowerQuery.contains("digit")) {
                    val num = extractAadhaarNumber(aadhaarDoc.fullText)
                    return if (num != null) {
                        "Your Aadhaar number is **$num**."
                    } else {
                        "I found your Aadhaar document (**${aadhaarDoc.filename}**), but could not detect the 12-digit Aadhaar number."
                    }
                }
                // Date of birth
                if (lowerQuery.contains("dob") || lowerQuery.contains("date of birth") || lowerQuery.contains("birth") ||
                    lowerQuery.contains("born") || lowerQuery.contains("year") || lowerQuery.contains("age")) {
                    val dob = extractAadhaarDob(aadhaarDoc.fullText)
                    return if (dob != null) {
                        "The date of birth on your Aadhaar card is **$dob**."
                    } else {
                        "I found your Aadhaar document (**${aadhaarDoc.filename}**), but could not detect the date of birth."
                    }
                }
                // Guardian / Father
                if (lowerQuery.contains("father") || lowerQuery.contains("care of") || lowerQuery.contains("s/o") ||
                    lowerQuery.contains("d/o") || lowerQuery.contains("w/o") || lowerQuery.contains("c/o") || lowerQuery.contains("guardian")) {
                    val father = extractAadhaarFather(aadhaarDoc.fullText)
                    return if (father != null) {
                        "The guardian/father name on your Aadhaar card is **$father**."
                    } else {
                        "I found your Aadhaar document (**${aadhaarDoc.filename}**), but could not detect the guardian/father name."
                    }
                }
                // Address
                if (lowerQuery.contains("address") || lowerQuery.contains("location") || lowerQuery.contains("live") || lowerQuery.contains("place")) {
                    val addr = extractAadhaarAddress(aadhaarDoc.fullText)
                    return if (addr != null) {
                        "The address on your Aadhaar card is **$addr**."
                    } else {
                        "I found your Aadhaar document (**${aadhaarDoc.filename}**), but could not detect the address on the card."
                    }
                }

                // General Aadhaar card overview
                val name = com.docsphere.data.repository.UserIdentityResolver.extractAadhaarName(aadhaarDoc.fullText)
                val num = extractAadhaarNumber(aadhaarDoc.fullText)
                val dob = extractAadhaarDob(aadhaarDoc.fullText)
                val father = extractAadhaarFather(aadhaarDoc.fullText)
                val addr = extractAadhaarAddress(aadhaarDoc.fullText)

                return buildString {
                    appendLine("📄 **Aadhaar Card Details:**")
                    appendLine()
                    if (name != null) appendLine("• **Name**: **$name**")
                    if (num != null) appendLine("• **Aadhaar Number**: **$num**")
                    if (dob != null) appendLine("• **Date of Birth**: **$dob**")
                    if (father != null) appendLine("• **Father/Guardian**: $father")
                    if (addr != null) appendLine("• **Address**: $addr")
                    if (name == null && num == null && dob == null) {
                        appendLine("I found your Aadhaar document (**${aadhaarDoc.filename}**), but some details are obscured or illegible.")
                    }
                }.trimEnd()
            } else {
                return "I couldn't find an Aadhaar card document in your indexed library. Please scan or import your Aadhaar PDF or image first."
            }
        }

        // ── 2. PAN Card Field Inquiries ────────────────────────────────────
        if (isPanQuery) {
            val panDoc = docs.firstOrNull { doc ->
                val dl = doc.filename.lowercase(Locale.ROOT)
                val tl = doc.fullText.lowercase(Locale.ROOT)
                dl.contains("pan") || tl.contains("income tax") || tl.contains("permanent account")
            } ?: docs.firstOrNull()

            if (panDoc != null) {
                if (lowerQuery.contains("name") || lowerQuery.contains("cardholder")) {
                    val name = com.docsphere.data.repository.UserIdentityResolver.extractPanName(panDoc.fullText)
                    if (name != null) return "The name on your PAN card is **$name**."
                }
                if (lowerQuery.contains("number") || lowerQuery.contains("no") || lowerQuery.contains("pan")) {
                    val panNum = extractPanNumber(panDoc.fullText)
                    if (panNum != null) return "Your PAN number is **$panNum**."
                }
            }
        }

        // ── 3. Resume / Candidate Queries ──────────────────────────────────
        if (isResumeQuery) {
            val resumeDoc = docs.firstOrNull { doc ->
                val dl = doc.filename.lowercase(Locale.ROOT)
                val tl = doc.fullText.lowercase(Locale.ROOT)
                dl.contains("resume") || dl.contains("cv") || tl.contains("curriculum vitae") || (tl.contains("education") && tl.contains("experience"))
            } ?: docs.firstOrNull()

            if (resumeDoc != null) {
                if (lowerQuery.contains("name") || lowerQuery.contains("candidate") || lowerQuery.contains("who")) {
                    val name = com.docsphere.data.repository.UserIdentityResolver.extractResumeName(resumeDoc.fullText)
                    if (name != null) return "The candidate name on the resume is **$name**."
                }
                if (lowerQuery.contains("email") || lowerQuery.contains("contact")) {
                    val email = resumeDoc.emails.firstOrNull()
                    if (email != null) return "The email address on the resume is **$email**."
                }
                if (lowerQuery.contains("phone") || lowerQuery.contains("mobile") || lowerQuery.contains("number")) {
                    val phone = resumeDoc.phones.firstOrNull()
                    if (phone != null) return "The phone number on the resume is **$phone**."
                }
            }
        }

        // ── 4. Invoice / Bill Queries ──────────────────────────────────────
        if (isInvoiceQuery) {
            val invoiceDoc = docs.firstOrNull { it.docType.contains("Invoice", true) || it.filename.contains("invoice", true) || it.filename.contains("bill", true) } ?: docs.firstOrNull()
            if (invoiceDoc != null) {
                if (lowerQuery.contains("amount") || lowerQuery.contains("total") || lowerQuery.contains("cost") || lowerQuery.contains("price") || lowerQuery.contains("fee") || lowerQuery.contains("due")) {
                    val total = extractInvoiceTotal(invoiceDoc.fullText) ?: invoiceDoc.amounts.firstOrNull()
                    if (total != null) return "The total invoice amount is **$total**."
                }
                if (lowerQuery.contains("number") || lowerQuery.contains("no") || lowerQuery.contains("id")) {
                    val invNo = extractInvoiceNumber(invoiceDoc.fullText)
                    if (invNo != null) return "The invoice number is **$invNo**."
                }
                if (lowerQuery.contains("date") || lowerQuery.contains("due date") || lowerQuery.contains("when")) {
                    val dt = invoiceDoc.dates.firstOrNull()
                    if (dt != null) return "The invoice date is **$dt**."
                }
            }
        }

        return null
    }

    private fun extractAadhaarNumber(text: String): String? {
        val regexGrouped = Pattern.compile("\\b(\\d{4}\\s+\\d{4}\\s+\\d{4})\\b")
        val mGrouped = regexGrouped.matcher(text)
        if (mGrouped.find()) return mGrouped.group(1)

        val regexMasked = Pattern.compile("\\b([X\\d]{4}\\s+[X\\d]{4}\\s+\\d{4})\\b", Pattern.CASE_INSENSITIVE)
        val mMasked = regexMasked.matcher(text)
        if (mMasked.find()) return mMasked.group(1)

        val regexContinuous = Pattern.compile("\\b([2-9]\\d{11})\\b")
        val mCont = regexContinuous.matcher(text)
        if (mCont.find()) {
            val raw = mCont.group(1) ?: return null
            return "${raw.substring(0, 4)} ${raw.substring(4, 8)} ${raw.substring(8, 12)}"
        }
        return null
    }

    private fun extractAadhaarDob(text: String): String? {
        val regex = Pattern.compile("(?:DOB|Date\\s*of\\s*Birth|जन्म\\s*तिथि|பிறந்த\\s*தேதி|పుట్టిన\\s*తేదీ)[^0-9]*([0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{4})", Pattern.CASE_INSENSITIVE)
        val m = regex.matcher(text)
        if (m.find()) return m.group(1)

        val regexYob = Pattern.compile("(?:Year\\s*of\\s*Birth|YOB)[^0-9]*([0-9]{4})", Pattern.CASE_INSENSITIVE)
        val mYob = regexYob.matcher(text)
        if (mYob.find()) return mYob.group(1)

        return null
    }

    private fun extractAadhaarFather(text: String): String? {
        val regex = Pattern.compile("(?:S/O|D/O|W/O|C/O|Son\\s+of|Daughter\\s+of|Wife\\s+of|Care\\s+of)[:\\s]+([A-Za-z\\s.]+)", Pattern.CASE_INSENSITIVE)
        val m = regex.matcher(text)
        if (m.find()) {
            val raw = m.group(1)?.lines()?.firstOrNull()?.trim() ?: ""
            val clean = com.docsphere.data.repository.UserIdentityResolver.extractCleanPersonName(raw)
            if (clean != null) return clean
        }
        return null
    }

    private fun extractAadhaarAddress(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        if (lower.contains("address:") || lower.contains("address :")) {
            val after = text.substring(lower.indexOf("address")).substringAfter(":").trim()
            val lines = after.lines().take(4).map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                return lines.joinToString(", ")
            }
        }
        return null
    }

    private fun extractPanNumber(text: String): String? {
        val m = Pattern.compile("\\b([A-Z]{5}[0-9]{4}[A-Z])\\b").matcher(text)
        return if (m.find()) m.group(1) else null
    }

    private fun extractInvoiceTotal(text: String): String? {
        val m = Pattern.compile("(?:Total|Grand Total|Amount Due|Net Amount|Total Due)[:\\s]*([₹$€£]?\\s*[\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE).matcher(text)
        return if (m.find()) m.group(1)?.trim() else null
    }

    private fun extractInvoiceNumber(text: String): String? {
        val m = Pattern.compile("(?:Invoice\\s*(?:No|Number|#)?|Bill\\s*No)[:\\s]*([A-Z0-9\\-_/]+)", Pattern.CASE_INSENSITIVE).matcher(text)
        return if (m.find()) m.group(1)?.trim() else null
    }

    private fun synthesizeEmptyFallback(query: String): String = buildString {
        appendLine("### 🔍 Google Drive AI Document Intelligence")
        appendLine()
        appendLine("No documents currently match your search query: *\"$query\"*.")
        appendLine()
        appendLine("### 💡 Recommended Next Steps:")
        appendLine("• **Tap 'Scan Files'** on the Home screen to index all device documents")
        appendLine("• **Connect Google Drive** on the Drive tab to import cloud docs")
        appendLine("• **Search document names**: *\"resume\"*, *\"report\"*, *\"invoice\"*, *\"project\"*")
    }

    companion object {
        private val STOP_WORDS = setOf(
            "a", "about", "all", "an", "and", "any", "are", "as", "at", "be", "because",
            "been", "before", "being", "but", "by", "can", "can't", "could", "did", "do",
            "does", "doing", "don't", "for", "from", "had", "has", "have", "he", "her",
            "here", "him", "his", "how", "i", "if", "in", "into", "is", "it", "its", "me",
            "my", "no", "not", "of", "on", "or", "our", "she", "should", "so", "some",
            "such", "than", "that", "the", "their", "them", "then", "there", "these",
            "they", "this", "those", "to", "too", "under", "up", "very", "was", "we",
            "were", "what", "when", "where", "which", "who", "whom", "why", "will", "with",
            "would", "you", "your", "yours", "file", "files", "document", "documents",
            "please", "tell", "show", "give", "find", "get", "name", "names", "called",
            "say", "said", "look", "see", "think", "know", "mean", "want", "like", "need"
        )
    }
}
