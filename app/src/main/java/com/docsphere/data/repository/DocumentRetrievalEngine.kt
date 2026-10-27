package com.docsphere.data.repository

import android.util.Log
import com.docsphere.data.local.DocumentDao
import com.docsphere.data.local.DocumentRoomEntity
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRetrievalEngine @Inject constructor(
    private val embedder: com.docsphere.ai.embedding.OnDeviceEmbedder = com.docsphere.ai.embedding.OnDeviceEmbedder(),
    private val chunkEmbeddingDao: com.docsphere.data.local.ChunkEmbeddingDao? = null
) {

    companion object {
        const val MIN_CHUNK_RELEVANCE_SCORE = 15.0
        private const val TAG = "DocSphereDebug"

        val STOPWORDS: Set<String> = setOf(
            "retrieve", "my", "the", "show", "find", "get", "what", "is", "of", "a", "an",
            "me", "please", "can", "you", "tell", "give", "in", "on", "at", "to", "for", "from",
            "with", "by", "about", "into", "through", "during", "before", "after", "above", "below",
            "up", "down", "out", "off", "over", "under", "again", "further", "then", "once", "here",
            "there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so",
            "than", "too", "very", "will", "just", "should", "now", "are", "was", "were", "be",
            "been", "being", "have", "has", "had", "do", "does", "did", "doing", "would", "could",
            "i", "he", "she", "it", "we", "they", "them", "their", "this", "that", "these", "those",
            "number"
        )

        // Synonym expansion clusters
        val AADHAAR_SYNONYMS = setOf("aadhar", "aadhaar", "uid", "uidai", "adhar")
        val PAN_SYNONYMS = setOf("pan", "permanent account")
        val INVOICE_SYNONYMS = setOf("invoice", "bill", "receipt")
        val RESUME_SYNONYMS = setOf("resume", "cv")

        val SYNONYM_CLUSTERS = listOf(
            AADHAAR_SYNONYMS,
            PAN_SYNONYMS,
            INVOICE_SYNONYMS,
            RESUME_SYNONYMS
        )
        val FTS_OPERATORS_REGEX = Regex("[^a-zA-Z0-9\\s]")
        val FTS_RESERVED_WORDS = setOf("and", "or", "not", "near")

        val AADHAAR_REGEX = Regex("""(?<!\d)(?:[0-9]{4}[\s-]?[0-9]{4}[\s-]?[0-9]{4}|[0-9]{12})(?!\d)""")
        val PAN_REGEX = Regex("""(?<![A-Za-z0-9])[A-Za-z]{5}[0-9]{4}[A-Za-z](?![A-Za-z0-9])""")
        val VOTER_REGEX = Regex("""(?<![A-Za-z0-9])[A-Za-z]{3}[0-9]{7}(?![A-Za-z0-9])""")
        val DL_REGEX = Regex("""(?<![A-Za-z0-9])[A-Za-z]{2}[0-9]{2}[\s-]?[0-9]{11}(?![A-Za-z0-9])""")
        val PASSPORT_REGEX = Regex("""(?<![A-Za-z0-9])[A-Za-z][0-9]{7}(?![A-Za-z0-9])""")
        val AMOUNT_REGEX = Regex("""(?:₹|Rs\.?|INR|\$|EUR|€)\s*[0-9]+(?:,[0-9]+)*(?:\.[0-9]{1,2})?|\b[0-9]+(?:,[0-9]+)*\.[0-9]{2}\b""")

        // Blank label regex, e.g. "Aadhaar No.: ____" or "Aadhaar Number: .........."
        val BLANK_LABEL_REGEX = Regex("""(?i)\b(aadhaar|aadhar|pan|voter|licence|license|account|total)[\s_.:-]+([_\s-]{3,}|blank|\.{3,}|_{2,})""")

        fun isNumberOrIdQuery(query: String): Boolean {
            val lower = query.lowercase(Locale.ROOT)
            return lower.contains("number") || lower.contains("no.") || lower.contains("no ") ||
                   lower.contains(" id") || lower.contains("id ") || lower.contains("code") ||
                   lower.contains("total") || lower.contains("amount") || lower.contains("digits") ||
                   lower.contains("card number") || lower.contains("account") || lower.contains("dob") ||
                   lower.contains("date of birth") || lower.contains("date") || lower.contains("due")
        }

        fun hasGenericNumberIntent(query: String): Boolean {
            val lower = query.lowercase(Locale.ROOT)
            return isNumberOrIdQuery(lower)
        }

        fun hasDigitSequences(text: String): Boolean {
            return Regex("""(?:\d{4}[\s-]?\d{4}[\s-]?\d{4})|\d{4,}|\b\d{2,}[\s/-]\d{2,}\b""").containsMatchIn(text)
        }

        fun isLabelOnlyNoDigits(text: String): Boolean {
            val hasDigits = text.any { it.isDigit() }
            val isBlankPattern = BLANK_LABEL_REGEX.containsMatchIn(text)
            val hasLabel = Regex("""(?i)\b(aadhaar|aadhar|pan|voter|licence|license|account|total|date|dob|code|id)[\s_.:-]+""").containsMatchIn(text)
            return isBlankPattern || (hasLabel && !hasDigits)
        }

        fun getFieldRegexForQuery(query: String): Regex? {
            val lower = query.lowercase(Locale.ROOT)
            return when {
                lower.contains("aadhaar") || lower.contains("aadhar") || lower.contains("uid") || lower.contains("adhar") -> AADHAAR_REGEX
                lower.contains("pan") -> PAN_REGEX
                lower.contains("voter") || lower.contains("epic") -> VOTER_REGEX
                lower.contains("driving") || lower.contains("license") || lower.contains("licence") || lower.contains("dl") -> DL_REGEX
                lower.contains("passport") -> PASSPORT_REGEX
                lower.contains("total") || lower.contains("amount") || lower.contains("due") || lower.contains("bill") || lower.contains("invoice") -> AMOUNT_REGEX
                else -> null
            }
        }

        val CHAT_WORDS: Set<String> = setOf(
            "hi", "hii", "hello", "hey", "ok", "okay", "yes", "yeah", "yep", "no", "nope",
            "thanks", "thank", "thankyou", "please", "pls", "give", "my", "me", "show", "tell",
            "good", "morning", "afternoon", "evening", "night", "bye", "goodbye"
        )

        fun extractMeaningfulKeywords(query: String): List<String> {
            val lower = query.trim().lowercase(Locale.ROOT)
            val tokens = lower.split(Regex("[^a-zA-Z0-9]+"))
                .map { it.replace(FTS_OPERATORS_REGEX, "").trim() }
                .filter { it.isNotBlank() && it.length >= 2 }
                .filter { it !in FTS_RESERVED_WORDS }

            return tokens.filter { it !in STOPWORDS && it !in CHAT_WORDS }.distinct()
        }
    }

    /**
     * Extracts keywords by lowercasing the question, splitting into words,
     * removing stopwords and FTS reserved words.
     */
    fun extractKeywords(query: String): List<String> {
        val lower = query.trim().lowercase(Locale.ROOT)
        val tokens = lower.split(Regex("[^a-zA-Z0-9]+"))
            .map { it.replace(FTS_OPERATORS_REGEX, "").trim() }
            .filter { it.isNotBlank() && it.length >= 2 }
            .filter { it !in FTS_RESERVED_WORDS }

        return tokens.filter { it !in STOPWORDS }.distinct()
    }

    /**
     * Expands keywords with synonyms:
     * - aadhar/aadhaar/uid/adhar
     * - pan/permanent account
     * - invoice/bill/receipt
     * - resume/cv
     */
    fun expandSynonyms(keywords: List<String>): List<String> {
        val expanded = LinkedHashSet<String>()
        for (kw in keywords) {
            val lowerKw = kw.lowercase(Locale.ROOT)
            var matchedCluster = false
            for (cluster in SYNONYM_CLUSTERS) {
                if (cluster.any { it.equals(lowerKw, ignoreCase = true) || it.split(" ").contains(lowerKw) }) {
                    expanded.addAll(cluster)
                    matchedCluster = true
                }
            }
            if (!matchedCluster) {
                expanded.add(kw)
            }
        }
        return expanded.toList()
    }

    /**
     * Builds a safe FTS search query:
     * - Strips and escapes quotes, hyphens, parentheses, asterisks, colons and other FTS operators.
     * - Drops empty or single-character tokens and FTS reserved operators.
     * - If no valid keyword remains, returns an empty string to skip FTS.
     */
    fun buildFtsQuery(query: String): String {
        val keywords = extractKeywords(query)
        if (keywords.isEmpty()) return ""

        val expanded = expandSynonyms(keywords)
        val validTerms = mutableListOf<String>()

        for (term in expanded) {
            val cleanWords = term.split(Regex("\\s+"))
                .map { it.replace(FTS_OPERATORS_REGEX, "").trim() }
                .filter { it.isNotBlank() && it.lowercase(Locale.ROOT) !in FTS_RESERVED_WORDS && it.length >= 2 }

            if (cleanWords.isEmpty()) continue

            if (cleanWords.size == 1) {
                validTerms.add(cleanWords[0])
            } else {
                validTerms.add("\"${cleanWords.joinToString(" ")}\"")
            }
        }

        if (validTerms.isEmpty()) return ""
        return validTerms.distinct().joinToString(" OR ")
    }

    /**
     * Relevance scoring (BM25-style frequency saturation & keyword match count):
     * 1. Display name matches (high weight)
     * 2. Extracted text term frequency with BM25 saturation
     * 3. Distinct query concept coverage
     * 4. Exact phrase matching
     */
    fun rankDocuments(
        query: String,
        candidates: List<DocumentRoomEntity>,
        limit: Int = 5,
        docIdsWithMatchingField: Set<String> = emptySet()
    ): List<DocumentRoomEntity> {
        if (candidates.isEmpty()) return emptyList()

        val keywords = extractKeywords(query)
        val expanded = expandSynonyms(keywords)
        val cleanQuery = query.trim().lowercase(Locale.ROOT)
        val isNumberQuery = isNumberOrIdQuery(cleanQuery)
        val fieldRegex = getFieldRegexForQuery(cleanQuery)

        val scored = candidates.map { doc ->
            var score = calculateRelevanceScore(doc, keywords, expanded, cleanQuery)
            // Requirement 1: Boost documents that have a stored field of that type
            if (docIdsWithMatchingField.contains(doc.id)) {
                score += 80.0
            }
            // Boost if document text matches the field's regex
            if (fieldRegex != null && fieldRegex.containsMatchIn(doc.extractedText)) {
                score += 40.0
            }
            // If question asks for a number, down-rank documents with no digits
            if (isNumberQuery && !doc.extractedText.any { it.isDigit() }) {
                score -= 30.0
            }
            doc to score
        }

        return scored
            .sortedWith(
                compareByDescending<Pair<DocumentRoomEntity, Double>> { it.second }
                    .thenByDescending { it.first.lastModifiedEpochMillis }
            )
            .map { it.first }
            .take(limit)
    }

    fun calculateRelevanceScore(
        doc: DocumentRoomEntity,
        keywords: List<String>,
        expanded: List<String>,
        cleanQuery: String
    ): Double {
        val nameLower = doc.displayName.lowercase(Locale.ROOT)
        val textLower = doc.extractedText.lowercase(Locale.ROOT)

        var score = 0.0
        var matchedKeywordGroups = 0

        // Check coverage per original keyword concept
        for (kw in keywords) {
            val synonymsForKw = SYNONYM_CLUSTERS.firstOrNull { cluster ->
                cluster.any { it.equals(kw, ignoreCase = true) || it.split(" ").contains(kw) }
            } ?: setOf(kw)

            val matchedInName = synonymsForKw.any { nameLower.contains(it) }
            val matchedInText = synonymsForKw.any { textLower.contains(it) }

            if (matchedInName || matchedInText) {
                matchedKeywordGroups++
            }
        }

        // 1. Concept Coverage Bonus (matching multiple distinct query concepts)
        score += matchedKeywordGroups * 25.0

        // 2. Display Name Matches
        for (term in expanded) {
            if (nameLower.contains(term)) {
                score += if (term in keywords) 20.0 else 12.0
            }
        }

        // 3. Term frequency in extractedText with BM25 saturation
        for (term in expanded) {
            val count = countOccurrences(textLower, term)
            if (count > 0) {
                val tfComponent = (count.toDouble() * 2.0) / (count.toDouble() + 1.2)
                val weight = if (term in keywords) 5.0 else 3.0
                score += tfComponent * weight
            }
        }

        // 4. Exact Phrase Bonus
        val significantWords = keywords.filter { it !in STOPWORDS }
        if (significantWords.size >= 2) {
            val phrase = significantWords.joinToString(" ")
            if (nameLower.contains(phrase) || textLower.contains(phrase)) {
                score += 30.0
            }
        }

        return score
    }

    private fun countOccurrences(text: String, term: String): Int {
        if (text.isEmpty() || term.isEmpty()) return 0
        var count = 0
        var index = 0
        while (index < text.length) {
            val found = text.indexOf(term, index)
            if (found == -1) break
            count++
            index = found + term.length
            if (count >= 20) break
        }
        return count
    }

    /**
     * Executes the full retrieval pipeline on Dispatchers.IO:
     * 1. Extract keywords & expand synonyms
     * 2. Build safe FTS query (skips if empty/invalid)
     * 3. Fetch candidates from FTS (catching SQLiteException) with LIKE fallback
     * 4. Rank by relevance
     * 5. Log stages and timings under DocSphereDebug without logging text or IDs
     * 6. Return top documents
     */
    suspend fun retrieve(
        query: String,
        documentDao: DocumentDao,
        limit: Int = 5,
        docIdsWithMatchingField: Set<String> = emptySet()
    ): List<DocumentRoomEntity> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val queryStartTime = System.currentTimeMillis()
        Log.d(TAG, "[Timing] Query received (length: ${query.length} chars)")

        val kwStart = System.currentTimeMillis()
        val keywords = extractKeywords(query)
        val expanded = expandSynonyms(keywords)
        val kwDuration = System.currentTimeMillis() - kwStart
        Log.d(TAG, "[Timing] Keywords extracted: ${keywords.size} terms (took ${kwDuration}ms)")

        val docsStart = System.currentTimeMillis()
        val ftsQuery = buildFtsQuery(query)

        val candidates = if (ftsQuery.isNotBlank()) {
            try {
                documentDao.searchDocumentsFts(ftsQuery, limit = 20)
            } catch (e: android.database.sqlite.SQLiteException) {
                Log.w(TAG, "[Timing] FTS MATCH SQLiteException caught, falling back safely: ${e.message}")
                emptyList()
            } catch (e: Throwable) {
                Log.w(TAG, "[Timing] FTS search failed, falling back to LIKE search: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }

        val allCandidates = LinkedHashSet(candidates)
        if (allCandidates.size < limit) {
            for (kw in (keywords + expanded).distinct().take(3)) {
                try {
                    val fallbackMatches = documentDao.searchDocumentsForAi(kw, limit = 5)
                    allCandidates.addAll(fallbackMatches)
                } catch (e: Throwable) {
                    Log.w(TAG, "[Timing] Fallback LIKE search error: ${e.message}")
                }
            }
        }

        val rankedDocs = rankDocuments(query, allCandidates.toList(), limit = limit, docIdsWithMatchingField = docIdsWithMatchingField)
        val docsDuration = System.currentTimeMillis() - docsStart
        Log.d(TAG, "[Timing] Documents found: ${rankedDocs.size} candidates (took ${docsDuration}ms)")

        return@withContext rankedDocs
    }

    /**
     * Chunk-level relevance scoring reusing BM25 term frequency saturation,
     * document name matching, concept coverage, and exact phrase matching.
     */
    fun calculateChunkRelevanceScore(
        chunk: DocumentChunk,
        keywords: List<String>,
        expanded: List<String>,
        cleanQuery: String
    ): Double {
        val nameLower = chunk.documentName.lowercase(Locale.ROOT)
        val textLower = chunk.text.lowercase(Locale.ROOT)

        var score = 0.0
        var matchedKeywordGroups = 0

        // Check coverage per original keyword concept
        for (kw in keywords) {
            val synonymsForKw = SYNONYM_CLUSTERS.firstOrNull { cluster ->
                cluster.any { it.equals(kw, ignoreCase = true) || it.split(" ").contains(kw) }
            } ?: setOf(kw)

            val matchedInName = synonymsForKw.any { nameLower.contains(it) }
            val matchedInText = synonymsForKw.any { textLower.contains(it) }

            if (matchedInName || matchedInText) {
                matchedKeywordGroups++
            }
        }

        // 1. Concept Coverage Bonus (matching multiple distinct query concepts in this chunk)
        score += matchedKeywordGroups * 30.0

        // 2. Display Name Matches
        for (term in expanded) {
            if (nameLower.contains(term)) {
                score += if (term in keywords) 15.0 else 10.0
            }
        }

        // 3. Term frequency in chunk text with BM25 saturation
        for (term in expanded) {
            val count = countOccurrences(textLower, term)
            if (count > 0) {
                val tfComponent = (count.toDouble() * 2.0) / (count.toDouble() + 1.2)
                val weight = if (term in keywords) 8.0 else 5.0
                score += tfComponent * weight
            }
        }

        // 4. Exact Phrase Bonus inside this chunk
        val significantWords = keywords.filter { it !in STOPWORDS }
        if (significantWords.size >= 2) {
            val phrase = significantWords.joinToString(" ")
            if (textLower.contains(phrase) || nameLower.contains(phrase)) {
                score += 35.0
            }
        }

        return score
    }

    fun rankChunks(
        query: String,
        candidateChunks: List<DocumentChunk>,
        limit: Int = 3,
        docIdsWithMatchingField: Set<String> = emptySet(),
        chunkEmbeddingMap: Map<String, FloatArray> = emptyMap()
    ): List<DocumentChunk> {
        if (candidateChunks.isEmpty()) return emptyList()

        val cleanQuery = query.trim().lowercase(Locale.ROOT)
        val meaningfulKeywords = extractMeaningfulKeywords(cleanQuery)
        // Item 3: Relevance Gate - if query has no meaningful keywords, greetings/short general queries return no context
        if (meaningfulKeywords.isEmpty()) {
            Log.d(TAG, "[RelevanceGate] Query '$cleanQuery' has no meaningful keywords. Returning empty context.")
            return emptyList()
        }

        val expandedSynonyms = expandSynonyms(meaningfulKeywords)

        val isNumberQuery = isNumberOrIdQuery(cleanQuery) || hasGenericNumberIntent(cleanQuery)
        val fieldRegex = getFieldRegexForQuery(cleanQuery)

        // Item 1: If a question asks for a number, discard chunks without digits
        val viableChunks = if (isNumberQuery) {
            candidateChunks.filter { chunk -> chunk.text.any { it.isDigit() } }
        } else {
            candidateChunks
        }

        if (viableChunks.isEmpty()) return emptyList()

        val keywords = extractKeywords(query)
        val expanded = expandSynonyms(keywords)

        // 1. Keyword / BM25 Scoring
        val keywordScores = viableChunks.map { chunk ->
            var score = calculateChunkRelevanceScore(chunk, keywords, expanded, cleanQuery)
            if (docIdsWithMatchingField.contains(chunk.documentId)) score += 50.0
            if (fieldRegex != null && fieldRegex.containsMatchIn(chunk.text)) score += 60.0
            if (isLabelOnlyNoDigits(chunk.text)) score -= 50.0
            chunk to score
        }
        val sortedByKeyword = keywordScores.sortedByDescending { it.second }
        val keywordRankMap = sortedByKeyword.mapIndexed { idx, pair -> pair.first.id to (idx + 1) }.toMap()

        // 2. Semantic Embedding Scoring (on-device)
        val queryVector = embedder.embed(query)
        val semanticScores = viableChunks.map { chunk ->
            val chunkVector = chunkEmbeddingMap[chunk.id] ?: embedder.embed(chunk.text)
            val similarity = embedder.cosineSimilarity(queryVector, chunkVector)
            chunk to similarity
        }
        val sortedBySemantic = semanticScores.sortedByDescending { it.second }
        val semanticRankMap = sortedBySemantic.mapIndexed { idx, pair -> pair.first.id to (idx + 1) }.toMap()

        // 3. Reciprocal Rank Fusion (RRF) + Generic Number Intent + Length Normalization
        val scored = viableChunks.map { chunk ->
            val kRank = keywordRankMap[chunk.id] ?: viableChunks.size
            val sRank = semanticRankMap[chunk.id] ?: viableChunks.size

            // RRF: 1 / (60 + kRank) + 1 / (60 + sRank)
            var rrfScore = (1.0 / (60.0 + kRank)) + (1.0 / (60.0 + sRank))

            // Generic number intent: boost digit sequences, down-rank label-only chunks
            if (isNumberQuery) {
                if (hasDigitSequences(chunk.text)) {
                    rrfScore += 0.04
                }
                if (isLabelOnlyNoDigits(chunk.text)) {
                    rrfScore -= 0.05
                }
            }

            // Length Normalization: down-rank very large spreadsheet chunks so a big sheet does not beat a small, exact document
            val isSheet = DocumentChunker.isSpreadsheet(chunk.documentName)
            val lengthFactor = if (isSheet) {
                1.0 + (chunk.text.length / 400.0)
            } else {
                1.0 + (chunk.text.length / 1500.0)
            }
            val normalizedRrf = rrfScore / lengthFactor

            val rawKeywordScore = keywordScores.firstOrNull { it.first.id == chunk.id }?.second ?: 0.0
            val combinedScore = normalizedRrf * 1000.0 + (rawKeywordScore / lengthFactor)

            Triple(chunk, combinedScore, rawKeywordScore)
        }

        val keywordSynonymMap = meaningfulKeywords.associateWith { kw ->
            val set = mutableSetOf(kw)
            for (cluster in SYNONYM_CLUSTERS) {
                if (cluster.contains(kw)) {
                    set.addAll(cluster)
                }
            }
            set
        }

        // 4. Rank files by their best chunk with Item 3 Relevance Gate
        fun getMeaningfulKeywordOverlap(chunkText: String, docName: String): Double {
            if (meaningfulKeywords.isEmpty()) return 0.0
            val textLower = chunkText.lowercase(Locale.ROOT)
            val nameLower = docName.lowercase(Locale.ROOT)
            val matches = meaningfulKeywords.count { kw ->
                val syns = keywordSynonymMap[kw] ?: setOf(kw)
                syns.any { syn -> textLower.contains(syn) || nameLower.contains(syn) }
            }
            return matches.toDouble() / meaningfulKeywords.size.toDouble()
        }

        val byDocument = scored.groupBy { it.first.documentId }
        val rankedDocuments = byDocument.entries.sortedByDescending { entry ->
            entry.value.maxOf { it.second }
        }

        val topChunks = mutableListOf<DocumentChunk>()
        for (docEntry in rankedDocuments) {
            val chunksInDoc = docEntry.value.sortedByDescending { it.second }
            for (chunkTriple in chunksInDoc) {
                val chunk = chunkTriple.first
                val rawScore = chunkTriple.third
                val combinedScore = chunkTriple.second

                val overlap = getMeaningfulKeywordOverlap(chunk.text, chunk.documentName)
                val semanticSim = semanticScores.firstOrNull { it.first.id == chunk.id }?.second ?: 0.0f
                val passesGate = overlap >= 0.60 || semanticSim >= 0.70f

                if (passesGate && (rawScore >= MIN_CHUNK_RELEVANCE_SCORE || combinedScore >= 15.0)) {
                    topChunks.add(chunk)
                    if (topChunks.size >= limit) break
                }
            }
            if (topChunks.size >= limit) break
        }

        return topChunks.take(limit)
    }

    /**
     * Executes chunk-level retrieval on Dispatchers.IO:
     * 1. Searches chunks directly across chunks_fts table.
     * 2. Retrieves candidate document chunks for broad coverage.
     * 3. Combines FTS keyword ranking and on-device semantic vector ranking via RRF.
     * 4. Normalizes length and boosts generic number intent.
     * 5. Returns top chunks ranked by best chunk per file.
     */
    suspend fun retrieveTopChunks(
        query: String,
        documentDao: DocumentDao,
        chunkDao: com.docsphere.data.local.ChunkDao,
        limit: Int = 3,
        docIdsWithMatchingField: Set<String> = emptySet()
    ): List<DocumentChunk> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cleanQuery = query.trim()
        val meaningfulKeywords = extractMeaningfulKeywords(cleanQuery)
        // Item 3: Relevance Gate fast exit
        if (meaningfulKeywords.isEmpty()) {
            Log.d(TAG, "[RelevanceGate] Fast exit: no meaningful keywords for query '$cleanQuery'")
            return@withContext emptyList()
        }

        val chunksStart = System.currentTimeMillis()
        val ftsQuery = buildFtsQuery(cleanQuery)

        val directChunks = if (ftsQuery.isNotBlank()) {
            try {
                chunkDao.searchAllChunksFts(ftsQuery, limit = 50)
            } catch (_: Throwable) {
                emptyList()
            }
        } else emptyList()

        val candidateDocs = retrieve(cleanQuery, documentDao, limit = 5, docIdsWithMatchingField = docIdsWithMatchingField)
        val docCandidateChunks = if (candidateDocs.isNotEmpty()) {
            val candidateIds = candidateDocs.map { it.id }.take(500)
            try {
                if (ftsQuery.isNotBlank()) {
                    chunkDao.searchChunksFts(candidateIds, ftsQuery, limit = 30)
                } else {
                    chunkDao.getChunksForDocuments(candidateIds, limit = 30)
                }
            } catch (_: Throwable) {
                emptyList()
            }
        } else emptyList()

        val allChunkEntities = (directChunks + docCandidateChunks).distinctBy { it.id }
        if (allChunkEntities.isEmpty()) {
            Log.d(TAG, "[Timing] Chunks found: 0 chunks (took 0ms)")
            return@withContext emptyList()
        }

        val docNameMap = candidateDocs.associate { it.id to it.displayName }
        val chunksToRank = allChunkEntities.map { entity ->
            DocumentChunk(
                id = entity.id,
                documentId = entity.documentId,
                documentName = entity.documentName.ifBlank { docNameMap[entity.documentId] ?: "Document" },
                chunkIndex = entity.chunkIndex,
                text = entity.text
            )
        }

        val chunkIds = chunksToRank.map { it.id }
        val storedEmbeddings: Map<String, FloatArray> = try {
            chunkEmbeddingDao?.getByChunkIds(chunkIds)?.associate {
                it.chunkId to com.docsphere.ai.embedding.OnDeviceEmbedder.deserialize(it.vector)
            } ?: emptyMap()
        } catch (_: Throwable) {
            emptyMap()
        }

        val topChunks = rankChunks(
            query = cleanQuery,
            candidateChunks = chunksToRank,
            limit = limit,
            docIdsWithMatchingField = docIdsWithMatchingField,
            chunkEmbeddingMap = storedEmbeddings
        )
        val chunksDuration = System.currentTimeMillis() - chunksStart
        Log.d(TAG, "[Timing] Chunks found: ${topChunks.size} chunks (took ${chunksDuration}ms)")

        return@withContext topChunks
    }
}

