package com.docsphere

import com.docsphere.data.local.DocumentRoomEntity
import com.docsphere.data.model.ChatMessage
import com.docsphere.data.model.MessageRole
import com.docsphere.data.model.RetrievedChunk
import com.docsphere.data.model.SensitivityTag
import com.docsphere.data.model.SourceType
import com.docsphere.data.repository.DocumentChunk
import com.docsphere.data.repository.DocumentFieldExtractor
import com.docsphere.data.repository.DocumentRetrievalEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DocumentKnowledgeFixTest {

    private val retrievalEngine = DocumentRetrievalEngine()

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

    // ── Item 1: Ranking for number/ID questions ───────────────────────────────

    @Test
    fun testItem1_rankingBoostsStoredFieldAndDiscardsDigitlessChunks() {
        val query = "give my aadhar card number"

        // Form has many mentions of "Aadhaar" but no ID number
        val formDoc = createDoc(
            id = "doc_form",
            displayName = "Application_form_HCLT.pdf",
            extractedText = "Aadhaar card is mandatory. Attach your Aadhaar photocopy. Aadhaar No.: ____"
        )
        // Card has fewer words but has the actual number and stored field
        val cardDoc = createDoc(
            id = "doc_card",
            displayName = "Aadhaar_Card.jpg",
            extractedText = "UIDAI Aadhaar 2123 4567 8901"
        )

        // 1. Boost document that has a stored field
        val rankedDocs = retrievalEngine.rankDocuments(
            query = query,
            candidates = listOf(formDoc, cardDoc),
            limit = 2,
            docIdsWithMatchingField = setOf("doc_card")
        )
        assertEquals("doc_card", rankedDocs.first().id)

        // 2. Chunks without digits must be discarded when question asks for a number
        val chunkNoDigits = DocumentChunk(
            id = "c1",
            documentId = "doc_form",
            documentName = "Application_form_HCLT.pdf",
            chunkIndex = 0,
            text = "Please attach your Aadhaar card photocopy here. Aadhaar card is required."
        )
        val chunkBlankLabel = DocumentChunk(
            id = "c2",
            documentId = "doc_form",
            documentName = "Application_form_HCLT.pdf",
            chunkIndex = 1,
            text = "Applicant Details - Aadhaar No.: ____"
        )
        val chunkWithDigits = DocumentChunk(
            id = "c3",
            documentId = "doc_card",
            documentName = "Aadhaar_Card.jpg",
            chunkIndex = 0,
            text = "GOVERNMENT OF INDIA UIDAI Aadhaar Number: 2123 4567 8901"
        )

        val rankedChunks = retrievalEngine.rankChunks(
            query = query,
            candidateChunks = listOf(chunkNoDigits, chunkBlankLabel, chunkWithDigits),
            limit = 3,
            docIdsWithMatchingField = setOf("doc_card")
        )

        // Digitless chunks discarded; only chunkWithDigits survives
        assertEquals(1, rankedChunks.size)
        assertEquals("c3", rankedChunks.first().id)
    }

    @Test
    fun testItem1_downRanksBlankLabelChunks() {
        val query = "what is my aadhaar card number"

        val blankChunk = DocumentChunk(
            id = "blank",
            documentId = "doc_form",
            documentName = "Form.pdf",
            chunkIndex = 0,
            text = "Aadhaar No.: ____ (Form 12)" // Has digit 12 so not completely discarded
        )
        val realChunk = DocumentChunk(
            id = "real",
            documentId = "doc_card",
            documentName = "Card.jpg",
            chunkIndex = 0,
            text = "UIDAI Aadhaar: 2123 4567 8901"
        )

        val ranked = retrievalEngine.rankChunks(
            query = query,
            candidateChunks = listOf(blankChunk, realChunk),
            limit = 2
        )
        assertEquals("real", ranked.first().id)
    }

    // ── Item 2: OCR-noisy IDs ────────────────────────────────────────────────

    @Test
    fun testItem2_ocrNoisyAadhaarStoredAsUnverifiedAndRankedSecond() {
        // Valid 12-digit Aadhaar passing Verhoeff checksum
        val validText = "UIDAI Aadhaar No: 2123 4567 8901"
        val validExtracted = DocumentFieldExtractor.extractAadhaarWithConfidence(validText, "Aadhaar.jpg")
        assertEquals(1, validExtracted.size)
        assertEquals("2123 4567 8901", validExtracted[0].first)
        assertEquals("VERIFIED", validExtracted[0].second)

        // Noisy 12-digit number failing Verhoeff checksum (e.g. OCR misread last digit: 8901 -> 8902)
        val noisyText = "UIDAI Aadhaar No: 2123 4567 8902"
        val noisyExtracted = DocumentFieldExtractor.extractAadhaarWithConfidence(noisyText, "Aadhaar.jpg")
        assertEquals(1, noisyExtracted.size)
        assertEquals("2123 4567 8902", noisyExtracted[0].first)
        assertEquals("UNVERIFIED", noisyExtracted[0].second)

        // Verified values must be ranked before unverified values
        val mixedText = "UIDAI Aadhaar candidate 1: 2123 4567 8902 and candidate 2: 2123 4567 8901"
        val mixedExtracted = DocumentFieldExtractor.extractAadhaarWithConfidence(mixedText, "Aadhaar.jpg")
        assertEquals(2, mixedExtracted.size)
        assertEquals("VERIFIED", mixedExtracted[0].second)
        assertEquals("UNVERIFIED", mixedExtracted[1].second)

        // Never extracted from spreadsheets
        val spreadsheetExtracted = DocumentFieldExtractor.extractAadhaarWithConfidence(noisyText, "data.xlsx")
        assertTrue("Spreadsheet must not extract Aadhaar", spreadsheetExtracted.isEmpty())

        // Extracted through extractFields
        val fieldEntities = DocumentFieldExtractor.extractFields(
            documentId = "doc_1",
            documentName = "Card.jpg",
            text = noisyText
        )
        val aadhaarField = fieldEntities.firstOrNull { it.fieldName == DocumentFieldExtractor.FIELD_AADHAAR }
        assertEquals("UNVERIFIED", aadhaarField?.confidence)
    }

}

