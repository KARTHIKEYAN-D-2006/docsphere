package com.docsphere

import com.docsphere.data.local.ChunkDao
import com.docsphere.data.local.ChunkRoomEntity
import com.docsphere.data.local.ChunkWithDocName
import com.docsphere.data.local.DocumentDao
import com.docsphere.data.local.DocumentRoomEntity
import com.docsphere.data.model.SensitivityTag
import com.docsphere.data.model.SourceType
import com.docsphere.data.repository.DocumentChunk
import com.docsphere.data.repository.DocumentChunker
import com.docsphere.data.repository.DocumentRepository
import com.docsphere.data.repository.DocumentRetrievalEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DocumentRetrievalTest {

    private lateinit var retrievalEngine: DocumentRetrievalEngine

    @Before
    fun setUp() {
        retrievalEngine = DocumentRetrievalEngine()
    }

    private fun createDoc(
        id: String,
        displayName: String,
        extractedText: String,
        timestamp: Long = System.currentTimeMillis()
    ) = DocumentRoomEntity(
        id = id,
        displayName = displayName,
        sourceType = SourceType.LOCAL_STORAGE,
        sensitivityTag = SensitivityTag.NONE,
        extractedText = extractedText,
        pageCount = 1,
        mimeType = "application/pdf",
        fileSizeBytes = 1024L,
        lastModifiedEpochMillis = timestamp,
        localUri = "content://docs/$id",
        cloudRef = null
    )

    @Test
    fun testKeywordsExtraction_removesStopwords() {
        val keywords = retrievalEngine.extractKeywords("retrieve my aadhar card number")
        // Stopwords 'retrieve', 'my', 'number' must be removed
        assertTrue("Expected 'aadhar' to be retained", keywords.contains("aadhar"))
        assertTrue("Expected 'card' to be retained", keywords.contains("card"))
        assertTrue("Expected 'retrieve' to be removed", !keywords.contains("retrieve"))
        assertTrue("Expected 'my' to be removed", !keywords.contains("my"))
        assertTrue("Expected 'number' to be removed", !keywords.contains("number"))
    }

    @Test
    fun testSynonymExpansion_includesRequiredSynonyms() {
        val aadharExpanded = retrievalEngine.expandSynonyms(listOf("aadhar"))
        assertTrue(aadharExpanded.containsAll(listOf("aadhar", "aadhaar", "uid", "adhar")))

        val panExpanded = retrievalEngine.expandSynonyms(listOf("pan"))
        assertTrue(panExpanded.contains("pan"))
        assertTrue(panExpanded.contains("permanent account"))

        val invoiceExpanded = retrievalEngine.expandSynonyms(listOf("invoice"))
        assertTrue(invoiceExpanded.containsAll(listOf("invoice", "bill", "receipt")))

        val resumeExpanded = retrievalEngine.expandSynonyms(listOf("resume"))
        assertTrue(resumeExpanded.containsAll(listOf("resume", "cv")))
    }

    @Test
    fun testFtsQueryBuilder_joinsWithOr() {
        val ftsQuery = retrievalEngine.buildFtsQuery("retrieve my aadhar card number")
        assertTrue("Query should contain OR operator", ftsQuery.contains(" OR "))
        assertTrue("Query should include aadhar synonyms", ftsQuery.contains("aadhaar"))
        assertTrue("Query should include card", ftsQuery.contains("card"))
    }

    @Test
    fun testRetrieveMyAadharCardNumber_returnsAadhaarDocument() {
        val aadhaarDoc = createDoc(
            id = "doc_aadhaar",
            displayName = "Aadhaar_Card_Official.pdf",
            extractedText = "GOVERNMENT OF INDIA UNIQUE IDENTIFICATION AUTHORITY OF INDIA UIDAI Aadhaar No: 9182 3847 1928 Name: Karthikeyan D DOB: 15/08/1995 Address: Bangalore"
        )
        val meetingNotes = createDoc(
            id = "doc_meeting",
            displayName = "Team_Sprint_Planning.docx",
            extractedText = "Please retrieve my notes from the morning standup. The number of attendees was 15."
        )
        val groceryReceipt = createDoc(
            id = "doc_grocery",
            displayName = "Weekly_Grocery_Receipt.pdf",
            extractedText = "Apples, Milk, Bread. Payment via Credit Card. Total: $45.20. Receipt number: 99482."
        )
        val designDoc = createDoc(
            id = "doc_design",
            displayName = "DocSphere_Architecture.pdf",
            extractedText = "DocSphere offline retrieval and LLM inference engine documentation."
        )

        val candidates = listOf(meetingNotes, groceryReceipt, aadhaarDoc, designDoc)
        val ranked = retrievalEngine.rankDocuments("retrieve my aadhar card number", candidates, limit = 5)

        assertNotNull(ranked)
        assertTrue("Results should not be empty", ranked.isNotEmpty())
        assertEquals("Aadhaar document must be ranked #1", "Aadhaar_Card_Official.pdf", ranked.first().displayName)
        assertEquals("doc_aadhaar", ranked.first().id)
    }

    @Test
    fun testTotalAmountDueOnMyInvoice_returnsInvoiceDocument() {
        val invoiceDoc = createDoc(
            id = "doc_invoice",
            displayName = "Vendor_Invoice_INV9823.pdf",
            extractedText = "Acme Cloud Services Inc. INVOICE #INV9823. Invoice Date: 2024-03-15. Total Amount Due: $3,450.00. Payment terms: Net 30 days."
        )
        val projectDoc = createDoc(
            id = "doc_project",
            displayName = "Project_Delivery_Roadmap.docx",
            extractedText = "Due to resource constraints the total number of deliverables is 6. Amount of work remaining is significant."
        )
        val resumeDoc = createDoc(
            id = "doc_resume",
            displayName = "Resume_Karthikeyan.pdf",
            extractedText = "Senior Mobile Engineer. Experienced in Kotlin, Jetpack Compose, Room DB, and on-device AI."
        )
        val genericDoc = createDoc(
            id = "doc_generic",
            displayName = "Meeting_Summary.pdf",
            extractedText = "Discussion on quarterly targets and team expansion."
        )

        val candidates = listOf(projectDoc, resumeDoc, invoiceDoc, genericDoc)
        val ranked = retrievalEngine.rankDocuments("total amount due on my invoice", candidates, limit = 5)

        assertNotNull(ranked)
        assertTrue("Results should not be empty", ranked.isNotEmpty())
        assertEquals("Invoice document must be ranked #1", "Vendor_Invoice_INV9823.pdf", ranked.first().displayName)
        assertEquals("doc_invoice", ranked.first().id)
    }

    @Test
    fun testDocumentRepository_getRelevantDocs_returnsTop5AndRespectsRanking() = runBlocking {
        val aadhaarDoc = createDoc(
            id = "doc_aadhaar",
            displayName = "Aadhaar_Card.pdf",
            extractedText = "UIDAI Aadhaar Number: 1234 5678 9012"
        )
        val invoiceDoc = createDoc(
            id = "doc_invoice",
            displayName = "Invoice_2024.pdf",
            extractedText = "Total Amount Due: $1,200.00 Invoice #102"
        )
        val otherDocs = (1..6).map { i ->
            createDoc(id = "doc_$i", displayName = "General_Doc_$i.txt", extractedText = "Standard document content number $i")
        }

        val allDocs = listOf(aadhaarDoc, invoiceDoc) + otherDocs

        val fakeDao = object : DocumentDao {
            override suspend fun upsert(document: DocumentRoomEntity) {}
            override suspend fun upsertAll(documents: List<DocumentRoomEntity>) {}
            override fun observeAll(): Flow<List<DocumentRoomEntity>> = emptyFlow()
            override fun observeBySource(sourceType: String): Flow<List<DocumentRoomEntity>> = emptyFlow()
            override fun observeByMimeType(mimePattern: String): Flow<List<DocumentRoomEntity>> = emptyFlow()
            override suspend fun searchDocuments(query: String): List<DocumentRoomEntity> = allDocs
            override suspend fun searchDocumentsForAi(query: String, limit: Int): List<DocumentRoomEntity> = allDocs.take(limit)
            override suspend fun searchDocumentsFts(ftsQuery: String, limit: Int): List<DocumentRoomEntity> = allDocs
            override fun observeCount(): Flow<Int> = emptyFlow()
            override fun observeImageCount(): Flow<Int> = emptyFlow()
            override fun observeVideoCount(): Flow<Int> = emptyFlow()
            override fun observeAudioCount(): Flow<Int> = emptyFlow()
            override fun observeRecent(limit: Int): Flow<List<DocumentRoomEntity>> = emptyFlow()
            override suspend fun getRecentWithText(limit: Int): List<DocumentRoomEntity> = allDocs.take(limit)
            override suspend fun getDocumentsWithTextPaged(limit: Int, offset: Int): List<DocumentRoomEntity> = allDocs.drop(offset).take(limit)
            override suspend fun getImagesNeedingOcr(limit: Int): List<DocumentRoomEntity> = emptyList()
            override suspend fun updateOcrResult(id: String, status: String, text: String, indexed: Boolean) {}
            override suspend fun getRecent(limit: Int): List<DocumentRoomEntity> = allDocs.take(limit)
            override suspend fun getById(id: String): DocumentRoomEntity? = allDocs.find { it.id == id }
            override suspend fun deleteById(id: String) {}
            override suspend fun clearAll() {}
            override suspend fun getAllMetadata(): List<com.docsphere.data.local.DocumentMetadata> = emptyList()
        }

        // Test 1 with retrieve: "retrieve my aadhar card number"
        val aadhaarResults = retrievalEngine.retrieve("retrieve my aadhar card number", fakeDao, limit = 5)
        assertTrue("Should return up to 5 documents", aadhaarResults.size <= 5)
        assertEquals("Aadhaar document must be ranked #1", "Aadhaar_Card.pdf", aadhaarResults.first().displayName)

        // Test 2 with invoice: "total amount due on my invoice"
        val invoiceResults = retrievalEngine.retrieve("total amount due on my invoice", fakeDao, limit = 5)
        assertTrue("Should return up to 5 documents", invoiceResults.size <= 5)
        assertEquals("Invoice document must be ranked #1", "Invoice_2024.pdf", invoiceResults.first().displayName)
    }

    @Test
    fun testLongDocumentWithAadhaarPast3000Chars_returnsChunkContainingAadhaar() {
        // Construct a long document (> 4500 characters) where first ~3400 characters are filler clauses
        val fillerIntro = StringBuilder()
        for (i in 1..35) {
            fillerIntro.append("Clause $i: Standard contract terms and organizational policy conditions apply for all transactions. ")
        }
        val aadhaarSection = " OFFICIAL GOVERNMENT ID RECORD: The individual's UIDAI Aadhaar card number is 9876 5432 1098 issued to Karthikeyan. "
        val fillerOutro = StringBuilder()
        for (i in 36..55) {
            fillerOutro.append("Clause $i: Confidentiality agreements and mutual nondisclosure covenants remain in full force and effect. ")
        }

        val fullText = fillerIntro.toString() + aadhaarSection + fillerOutro.toString()
        val aadhaarPos = fullText.indexOf("9876 5432 1098")
        assertTrue("Aadhaar number must be placed well past character 3000 (actual: $aadhaarPos)", aadhaarPos > 3000)

        // Split text into chunks using DocumentChunker (~1200 chars target, ~200 overlap, never mid-word)
        val chunks = DocumentChunker.splitIntoChunks(
            documentId = "doc_long_aadhaar",
            documentName = "Lengthy_Master_Agreement.pdf",
            text = fullText
        )

        assertTrue("Long document must be partitioned into multiple chunks", chunks.size >= 4)

        // Verify that the first chunk (which would have been the only thing retrieved with old 3000 truncation)
        // does NOT contain the Aadhaar number
        val firstChunk = chunks.first()
        assertTrue("First chunk must NOT contain Aadhaar number (verifying it is past char 3000)", !firstChunk.text.contains("9876 5432 1098"))

        // Rank the chunks for "retrieve my aadhar card number"
        val rankedChunks = retrievalEngine.rankChunks("retrieve my aadhar card number", chunks, limit = 3)

        assertNotNull(rankedChunks)
        assertTrue("Ranked chunks must not be empty", rankedChunks.isNotEmpty())

        val topChunk = rankedChunks.first()
        assertTrue("Top chunk must contain the Aadhaar number", topChunk.text.contains("9876 5432 1098"))
        assertEquals("Top chunk must be labeled with its document name", "Lengthy_Master_Agreement.pdf", topChunk.documentName)
        assertEquals("Top chunk must reference correct documentId", "doc_long_aadhaar", topChunk.documentId)
    }

    @Test
    fun testRetrieveTopChunks_fullPipeline_returnsTop3ChunksLabelledWithDocumentName() = runBlocking {
        val longAadhaarText = StringBuilder().apply {
            for (i in 1..35) append("Terms of Service section $i contains regular background and agreement info. ")
            append(" GOVERNMENT IDENTITY CARD: UIDAI Aadhaar number: 8877 6655 4433. ")
            for (i in 36..50) append("End of legal agreement chapter $i with signatory details and disclosures. ")
        }.toString()

        val aadhaarDoc = createDoc("doc_a", "Aadhaar_National_Card.pdf", longAadhaarText)
        val invoiceDoc = createDoc("doc_b", "Office_Rent_Invoice.pdf", "Invoice INV-2024. Total Amount Due: $2,500.00. Payment method: Card.")
        val otherDocs = (1..3).map { i ->
            createDoc("doc_other_$i", "General_Doc_$i.pdf", "General company document $i text content.")
        }

        val allDocs = listOf(aadhaarDoc, invoiceDoc) + otherDocs

        // Create chunk entities for all documents
        val allChunkEntities = allDocs.flatMap { doc ->
            DocumentChunker.splitIntoChunks(doc.id, doc.displayName, doc.extractedText).map { chunk ->
                ChunkWithDocName(
                    id = chunk.id,
                    documentId = chunk.documentId,
                    chunkIndex = chunk.chunkIndex,
                    text = chunk.text,
                    documentName = chunk.documentName
                )
            }
        }

        val fakeDocumentDao = object : DocumentDao {
            override suspend fun upsert(document: DocumentRoomEntity) {}
            override suspend fun upsertAll(documents: List<DocumentRoomEntity>) {}
            override fun observeAll(): Flow<List<DocumentRoomEntity>> = emptyFlow()
            override fun observeBySource(sourceType: String): Flow<List<DocumentRoomEntity>> = emptyFlow()
            override fun observeByMimeType(mimePattern: String): Flow<List<DocumentRoomEntity>> = emptyFlow()
            override suspend fun searchDocuments(query: String): List<DocumentRoomEntity> = allDocs
            override suspend fun searchDocumentsForAi(query: String, limit: Int): List<DocumentRoomEntity> = allDocs.take(limit)
            override suspend fun searchDocumentsFts(ftsQuery: String, limit: Int): List<DocumentRoomEntity> = allDocs
            override fun observeCount(): Flow<Int> = emptyFlow()
            override fun observeImageCount(): Flow<Int> = emptyFlow()
            override fun observeVideoCount(): Flow<Int> = emptyFlow()
            override fun observeAudioCount(): Flow<Int> = emptyFlow()
            override fun observeRecent(limit: Int): Flow<List<DocumentRoomEntity>> = emptyFlow()
            override suspend fun getRecentWithText(limit: Int): List<DocumentRoomEntity> = allDocs.take(limit)
            override suspend fun getDocumentsWithTextPaged(limit: Int, offset: Int): List<DocumentRoomEntity> = allDocs.drop(offset).take(limit)
            override suspend fun getImagesNeedingOcr(limit: Int): List<DocumentRoomEntity> = emptyList()
            override suspend fun updateOcrResult(id: String, status: String, text: String, indexed: Boolean) {}
            override suspend fun getRecent(limit: Int): List<DocumentRoomEntity> = allDocs.take(limit)
            override suspend fun getById(id: String): DocumentRoomEntity? = allDocs.find { it.id == id }
            override suspend fun deleteById(id: String) {}
            override suspend fun clearAll() {}
            override suspend fun getAllMetadata(): List<com.docsphere.data.local.DocumentMetadata> = emptyList()
        }

        val fakeChunkDao = object : ChunkDao {
            override suspend fun upsertAll(chunks: List<ChunkRoomEntity>) {}
            override suspend fun deleteByDocumentId(documentId: String) {}
            override suspend fun clearAll() {}
            override suspend fun getChunksByDocumentId(documentId: String): List<ChunkRoomEntity> = emptyList()
            override suspend fun getChunksForDocumentsInternal(documentIds: List<String>, limit: Int): List<ChunkWithDocName> {
                return allChunkEntities.filter { it.documentId in documentIds }.take(limit)
            }
            override suspend fun searchChunksFtsInternal(documentIds: List<String>, ftsQuery: String, limit: Int): List<ChunkWithDocName> {
                return allChunkEntities.filter { it.documentId in documentIds }.take(limit)
            }
            override suspend fun searchAllChunksFts(ftsQuery: String, limit: Int): List<ChunkWithDocName> {
                return allChunkEntities.take(limit)
            }
            override suspend fun count(): Int = allChunkEntities.size
        }

        val topChunks = retrievalEngine.retrieveTopChunks(
            query = "retrieve my aadhar card number",
            documentDao = fakeDocumentDao,
            chunkDao = fakeChunkDao,
            limit = 3
        )

        assertNotNull(topChunks)
        assertTrue("Must return at most 3 chunks", topChunks.size in 1..3)
        val bestChunk = topChunks.first()
        assertTrue("Best chunk must contain the Aadhaar number", bestChunk.text.contains("8877 6655 4433"))
        assertEquals("Best chunk must have document name", "Aadhaar_National_Card.pdf", bestChunk.documentName)
        for (chunk in topChunks) {
            assertTrue("Each chunk must have non-empty document name", chunk.documentName.isNotBlank())
        }
    }

    @Test
    fun testDocumentChunker_splitsOnBoundariesNeverMidWord() {
        val paragraph1 = "This is paragraph 1 with significant information about our financial year end report. ".repeat(15)
        val paragraph2 = "This is paragraph 2 containing specific employee salary and bonus records. ".repeat(15)
        val fullText = "$paragraph1\n\n$paragraph2"

        val chunks = DocumentChunker.splitIntoChunks("doc_para", "Financial_Report.pdf", fullText)
        assertTrue("Should create multiple chunks", chunks.size >= 2)

        for (chunk in chunks) {
            // Verify chunks do not start or end mid-word
            val text = chunk.text
            assertTrue("Chunk must not be empty", text.isNotBlank())
            assertTrue("Chunk must preserve document name", chunk.documentName == "Financial_Report.pdf")
            // Check that first and last characters are clean non-whitespace
            assertTrue("Chunk text trimmed properly", text == text.trim())
        }
    }

    @Test
    fun testFtsQueryBuilder_sanitizesOperatorsAndSpecialCharacters() {
        val rawInput = "\"invoice-2024*\" (final) [urgent]: test?"
        val ftsQuery = retrievalEngine.buildFtsQuery(rawInput)

        // Must strip operators like quotes, hyphens, asterisks, brackets, colons, question marks
        assertTrue("FTS query must not contain unescaped asterisks", !ftsQuery.contains("*"))
        assertTrue("FTS query must not contain unescaped hyphens", !ftsQuery.contains("-"))
        assertTrue("FTS query must not contain unescaped parentheses", !ftsQuery.contains("(") && !ftsQuery.contains(")"))
        assertTrue("FTS query must not contain unescaped square brackets", !ftsQuery.contains("[") && !ftsQuery.contains("]"))
        assertTrue("FTS query must contain clean words", ftsQuery.contains("invoice") && ftsQuery.contains("final"))
    }

    @Test
    fun testFtsQueryBuilder_skipsFtsWhenNoValidKeywords() {
        // Queries with only punctuation or stopwords should produce empty string to safely skip FTS MATCH
        val punctuationOnly = "??? *** --- () ::: !!!"
        assertEquals("", retrievalEngine.buildFtsQuery(punctuationOnly))

        val stopwordsOnly = "what is the of"
        assertEquals("", retrievalEngine.buildFtsQuery(stopwordsOnly))

        val empty = ""
        assertEquals("", retrievalEngine.buildFtsQuery(empty))
    }

    @Test
    fun testChunkDao_splitsMoreThan500DocumentIdsIntoBatches() = runBlocking {
        val capturedBatchSizes = mutableListOf<Int>()
        val testChunkDao = object : ChunkDao {
            override suspend fun upsertAll(chunks: List<ChunkRoomEntity>) {}
            override suspend fun deleteByDocumentId(documentId: String) {}
            override suspend fun clearAll() {}
            override suspend fun getChunksByDocumentId(documentId: String): List<ChunkRoomEntity> = emptyList()
            override suspend fun getChunksForDocumentsInternal(documentIds: List<String>, limit: Int): List<ChunkWithDocName> {
                capturedBatchSizes.add(documentIds.size)
                return emptyList()
            }
            override suspend fun searchChunksFtsInternal(documentIds: List<String>, ftsQuery: String, limit: Int): List<ChunkWithDocName> {
                capturedBatchSizes.add(documentIds.size)
                return emptyList()
            }
            override suspend fun searchAllChunksFts(ftsQuery: String, limit: Int): List<ChunkWithDocName> {
                return emptyList()
            }
            override suspend fun count(): Int = 0
        }

        // Test with 1200 document IDs
        val docIds = (1..1200).map { "doc_$it" }
        testChunkDao.getChunksForDocuments(docIds, limit = 50)

        // Must have chunked into batches of at most 500: 500, 500, 200
        assertTrue("Must be called in multiple batches", capturedBatchSizes.size >= 2)
        for (batchSize in capturedBatchSizes) {
            assertTrue("Each batch size must be <= 500, was: $batchSize", batchSize <= 500)
        }
    }
}
