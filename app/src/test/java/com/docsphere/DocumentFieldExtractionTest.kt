package com.docsphere

import com.docsphere.data.repository.DocumentChunk
import com.docsphere.data.repository.DocumentFieldExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentFieldExtractionTest {

    @Test
    fun testExtractAadhaar_standardFormat() {
        val text1 = "GOVERNMENT OF INDIA UIDAI Aadhaar No: 2123 4567 8901 Name: Karthikeyan D"
        val extracted1 = DocumentFieldExtractor.extractAadhaar(text1)
        assertTrue("Should extract 2123 4567 8901", extracted1.contains("2123 4567 8901"))

        val text2 = "Your Aadhaar Card Number is 9876-5432-1096 issued by UIDAI"
        val extracted2 = DocumentFieldExtractor.extractAadhaar(text2)
        assertTrue("Should extract 9876 5432 1096", extracted2.contains("9876 5432 1096"))

        val text3 = "Unique Identification Authority Ref 887766554436 (12 contiguous digits)"
        val extracted3 = DocumentFieldExtractor.extractAadhaar(text3)
        assertTrue("Should extract 8877 6655 4436", extracted3.contains("8877 6655 4436"))
    }

    @Test
    fun testExtractAadhaar_maskedFormat() {
        // Requirement 5: Store an Aadhaar value only if it has 12 digits, starts with 2-9, passes Verhoeff checksum
        // Masked Aadhaar (e.g. XXXX XXXX 5678) has only 4 digits, so it is never stored as an Aadhaar number
        val text1 = "Aadhaar: XXXX XXXX 5678"
        val extracted1 = DocumentFieldExtractor.extractAadhaar(text1)
        assertTrue("Masked Aadhaar without 12 digits must not be extracted", extracted1.isEmpty())

        val text2 = "Masked UID: xxxx-xxxx-9012"
        val extracted2 = DocumentFieldExtractor.extractAadhaar(text2)
        assertTrue("Masked UID without 12 digits must not be extracted", extracted2.isEmpty())
    }

    @Test
    fun testExtractAadhaar_messyOcr_oForZero_and_extraSpaces() {
        // OCR read capital 'O' instead of digit '0' (2123 4567 89O1 -> 2123 4567 8901)
        val textOcrLetterO = "Aadhaar Number: 2123 4567 89O1"
        val extractedO = DocumentFieldExtractor.extractAadhaar(textOcrLetterO)
        assertTrue("Should normalize 'O' to '0' and extract 2123 4567 8901", extractedO.contains("2123 4567 8901"))

        // OCR with extra spaces between blocks
        val textExtraSpaces = "UIDAI: 2123   4567   8901"
        val extractedSpaces = DocumentFieldExtractor.extractAadhaar(textExtraSpaces)
        assertTrue("Should handle extra spaces between 4-digit blocks", extractedSpaces.contains("2123 4567 8901"))

        // OCR with scattered spaces between individual digits
        val textScatteredDigits = "Aadhaar: 2 1 2 3   4 5 6 7   8 9 0 1"
        val extractedScattered = DocumentFieldExtractor.extractAadhaar(textScatteredDigits)
        assertTrue("Should handle scattered spaces between every digit", extractedScattered.contains("2123 4567 8901"))
    }

    @Test
    fun testExtractPan_standardAndMessyOcr() {
        // Standard PAN
        val textStandard = "INCOME TAX DEPARTMENT Permanent Account Number: ABCDE1234F"
        val extracted1 = DocumentFieldExtractor.extractPan(textStandard)
        assertTrue("Should extract ABCDE1234F", extracted1.contains("ABCDE1234F"))

        // Messy OCR: letter 'O' in place of '0' in the 4 digits
        val textLetterO = "PAN Card No: ABCDE12O4F"
        val extracted2 = DocumentFieldExtractor.extractPan(textLetterO)
        assertTrue("Should normalize 'O' to '0' and extract ABCDE1204F", extracted2.contains("ABCDE1204F"))

        // Messy OCR: extra spaces between characters
        val textSpaced = "PAN: A B C D E 1 2 3 4 F"
        val extracted3 = DocumentFieldExtractor.extractPan(textSpaced)
        assertTrue("Should handle spaced characters and extract ABCDE1234F", extracted3.contains("ABCDE1234F"))
    }

    @Test
    fun testExtractInvoiceTotal_labelsAndCurrencies() {
        val text1 = "Services rendered. Total Amount Due: $1,250.00. Payment due in 30 days."
        val extracted1 = DocumentFieldExtractor.extractInvoiceTotal(text1)
        assertTrue("Should extract $1,250.00", extracted1.any { it.contains("1,250.00") && it.contains("$") })

        val text2 = "TAX INVOICE. Grand Total: ₹ 14,999.50. Thank you for your business!"
        val extracted2 = DocumentFieldExtractor.extractInvoiceTotal(text2)
        assertTrue("Should extract ₹ 14,999.50", extracted2.any { it.contains("14,999.50") && it.contains("₹") })

        val text3 = "INVOICE #982. Amount Due: INR 5,400. Balance due: Rs. 850.00"
        val extracted3 = DocumentFieldExtractor.extractInvoiceTotal(text3)
        assertTrue("Should extract INR 5,400", extracted3.any { it.contains("INR 5,400") || it.contains("5,400") })
        assertTrue("Should extract Rs. 850.00", extracted3.any { it.contains("850.00") })
    }

    @Test
    fun testExtractDates_dobInvoiceDateDueDate() {
        val text = """
            EMPLOYEE DETAILS:
            Date of Birth: 15/08/1995
            D.O.B: 15-Aug-1995
            Invoice Date: 2024-03-25
            Payment Due Date: 15/04/2024
        """.trimIndent()

        val dates = DocumentFieldExtractor.extractDates(text)
        val dob = dates.filter { it.first == DocumentFieldExtractor.FIELD_DATE_OF_BIRTH }.map { it.second }
        val invoiceDate = dates.filter { it.first == DocumentFieldExtractor.FIELD_INVOICE_DATE }.map { it.second }
        val dueDate = dates.filter { it.first == DocumentFieldExtractor.FIELD_DUE_DATE }.map { it.second }

        assertTrue("Should extract DOB 15/08/1995", dob.any { it.contains("15/08/1995") || it.contains("15-Aug-1995") })
        assertTrue("Should extract Invoice Date 2024-03-25", invoiceDate.any { it.contains("2024-03-25") })
        assertTrue("Should extract Due Date 15/04/2024", dueDate.any { it.contains("15/04/2024") })
    }

    @Test
    fun testExtractPhoneAndEmail() {
        val text = "Please call our helpline at +91 98765 43210 or 9876543210, or email support@docsphere.com"
        val phones = DocumentFieldExtractor.extractPhoneNumbers(text)
        val emails = DocumentFieldExtractor.extractEmails(text)

        assertTrue("Should extract Indian mobile", phones.any { it.contains("98765 43210") })
        assertTrue("Should extract email", emails.contains("support@docsphere.com"))
    }

    @Test
    fun testSensitiveMasking_doesNotLeakInLogs() {
        val maskedAadhaar = DocumentFieldExtractor.maskValue(DocumentFieldExtractor.FIELD_AADHAAR, "1234 5678 9012")
        assertEquals("Should mask Aadhaar showing only last 4 digits", "•••• •••• 9012", maskedAadhaar)
        assertTrue("Must not contain first 8 digits", !maskedAadhaar.contains("1234") && !maskedAadhaar.contains("5678"))

        val maskedPan = DocumentFieldExtractor.maskValue(DocumentFieldExtractor.FIELD_PAN, "ABCDE1234F")
        assertEquals("Should mask PAN showing only last 4 chars", "••••••234F", maskedPan)
        assertTrue("Must not contain prefix", !maskedPan.contains("ABCDE"))
    }

    @Test
    fun testFullDocumentExtraction_attributesSourceChunkIndex() {
        val chunk0 = DocumentChunk("c0", "d1", "TestDoc.pdf", 0, "Introduction and preliminary legal disclosures.")
        val chunk1 = DocumentChunk("c1", "d1", "TestDoc.pdf", 1, "Financial records. Total Amount Due: $4,500.00. Payment method: Card.")
        val chunk2 = DocumentChunk("c2", "d1", "TestDoc.pdf", 2, "Identity details: UIDAI Aadhaar No: 9182 3847 1924, PAN: BNZPK1234A.")

        val fullText = "${chunk0.text}\n\n${chunk1.text}\n\n${chunk2.text}"
        val fields = DocumentFieldExtractor.extractFields("d1", "TestDoc.pdf", fullText, listOf(chunk0, chunk1, chunk2))

        val aadhaarField = fields.find { it.fieldName == DocumentFieldExtractor.FIELD_AADHAAR }
        assertNotNull("Aadhaar field must be found", aadhaarField)
        assertEquals("Aadhaar should be found in chunk index 2", 2, aadhaarField?.sourceChunkIndex)

        val invoiceTotalField = fields.find { it.fieldName == DocumentFieldExtractor.FIELD_INVOICE_TOTAL }
        assertNotNull("Invoice total must be found", invoiceTotalField)
        assertEquals("Invoice total should be found in chunk index 1", 1, invoiceTotalField?.sourceChunkIndex)
    }

    @Test
    fun testExtractVoterId_standardAndOcrNoise() {
        val text1 = "ELECTION COMMISSION OF INDIA Voter ID Card EPIC No: ABC1234567"
        val extracted1 = DocumentFieldExtractor.extractVoterId(text1)
        assertTrue("Should extract standard Voter ID ABC1234567", extracted1.contains("ABC1234567"))

        val text2 = "EPIC: XYZ-9876543 Name: Citizen"
        val extracted2 = DocumentFieldExtractor.extractVoterId(text2)
        assertTrue("Should extract hyphenated Voter ID XYZ9876543", extracted2.contains("XYZ9876543"))

        val text3 = "Voter Card: DEF 12O4567 (letter O for zero)"
        val extracted3 = DocumentFieldExtractor.extractVoterId(text3)
        assertTrue("Should handle OCR letter O and extract DEF1204567", extracted3.contains("DEF1204567"))
    }

    @Test
    fun testExtractDrivingLicence_standardAndOcrNoise() {
        val text1 = "UNION OF INDIA DRIVING LICENCE DL No: DL-0420110149646"
        val extracted1 = DocumentFieldExtractor.extractDrivingLicence(text1)
        assertTrue("Should extract DL-0420110149646", extracted1.any { it.contains("DL-0420110149646") || it.contains("0420110149646") })

        val text2 = "Licence No: TN01 20180001234 Valid till 2038"
        val extracted2 = DocumentFieldExtractor.extractDrivingLicence(text2)
        assertTrue("Should extract TN01 20180001234", extracted2.any { it.contains("TN01") && it.contains("20180001234") })
    }

    @Test
    fun testExtractPassport_standardAndOcrNoise() {
        val text1 = "REPUBLIC OF INDIA PASSPORT Passport No: J1234567"
        val extracted1 = DocumentFieldExtractor.extractPassport(text1)
        assertTrue("Should extract J1234567", extracted1.contains("J1234567"))

        val text2 = "PASSPORT Passport Number: A 9876543"
        val extracted2 = DocumentFieldExtractor.extractPassport(text2)
        assertTrue("Should extract spaced A9876543", extracted2.contains("A9876543"))

        val text3 = "Passport No: Z12O4567 (O for 0)"
        val extracted3 = DocumentFieldExtractor.extractPassport(text3)
        assertTrue("Should extract Z1204567 with OCR normalized zero", extracted3.contains("Z1204567"))
    }

    @Test
    fun testExtractFullName_andAddress() {
        val text = """
            GOVERNMENT OF INDIA
            Full Name: Karthikeyan D
            Date of Birth: 15/08/1995
            Permanent Address: 42 Palm Avenue, Indiranagar, Bangalore 560038
        """.trimIndent()

        val names = DocumentFieldExtractor.extractFullName(text)
        val addresses = DocumentFieldExtractor.extractAddress(text)

        assertTrue("Should extract full name", names.contains("Karthikeyan D"))
        assertTrue("Should not extract institutional name", !names.contains("Government of India"))
        assertTrue("Should extract address", addresses.any { it.contains("Indiranagar") && it.contains("560038") })
    }

    @Test
    fun testExtractBankAccountAndIfsc() {
        val text = "Bank Account Number: 12345678901234, RTGS/NEFT IFSC Code: HDFC0001234"
        val accounts = DocumentFieldExtractor.extractBankAccount(text)
        val ifscs = DocumentFieldExtractor.extractIfsc(text)

        assertTrue("Should extract bank account", accounts.contains("12345678901234"))
        assertTrue("Should extract IFSC", ifscs.contains("HDFC0001234"))

        val textOcrIfsc = "IFSC: SBIN0O01234"
        val ocrIfscs = DocumentFieldExtractor.extractIfsc(textOcrIfsc)
        assertTrue("Should normalize OCR 'O' in IFSC to zero", ocrIfscs.contains("SBIN0001234"))
    }

    @Test
    fun testSensitiveDataScrubber_preventsFullSensitiveValues() {
        val textWithRawAadhaar = "The user's aadhaar is 9876 5432 1098 and should be protected"
        val scrubbedAadhaar = com.docsphere.util.SensitiveDataScrubber.scrub(textWithRawAadhaar)
        assertTrue("Scrubbed text must not have full Aadhaar", !scrubbedAadhaar.contains("9876 5432 1098"))
        assertTrue("Scrubbed text must have masked representation", scrubbedAadhaar.contains("•••• •••• 1098"))

        val textWithRawPan = "The PAN number is BKZPD9876Q"
        val scrubbedPan = com.docsphere.util.SensitiveDataScrubber.scrub(textWithRawPan)
        assertTrue("Scrubbed text must not have full PAN", !scrubbedPan.contains("BKZPD9876Q"))
        assertTrue("Scrubbed text must have masked representation", scrubbedPan.contains("••••••876Q"))

        // Verify SensitiveDataScrubber detects messages containing raw data
        val legacyMessage = "[[SENSITIVE:Aadhaar Number:•••• •••• 9012:1234 5678 9012]]"
        assertTrue("Legacy message must be detected for deletion", com.docsphere.util.SensitiveDataScrubber.containsFullSensitiveData(legacyMessage))
        assertTrue("Raw Aadhaar must be detected for deletion", com.docsphere.util.SensitiveDataScrubber.containsFullSensitiveData(textWithRawAadhaar))
        assertTrue("Raw PAN must be detected for deletion", com.docsphere.util.SensitiveDataScrubber.containsFullSensitiveData(textWithRawPan))

        // Verify exception messages are scrubbed
        val sensitiveException = IllegalArgumentException("Invalid operation on Aadhaar 1234 5678 9012 and PAN ABCDE1234F")
        val scrubbedEx = com.docsphere.util.SensitiveDataScrubber.scrubThrowable(sensitiveException)
        assertTrue("Scrubbed exception must not contain full Aadhaar", !scrubbedEx.contains("1234 5678 9012"))
        assertTrue("Scrubbed exception must not contain full PAN", !scrubbedEx.contains("ABCDE1234F"))
    }

    @Test
    fun testSpreadsheetChunkingCap() {
        val largeTextBuilder = StringBuilder()
        for (i in 1..3000) {
            largeTextBuilder.append("Row $i: Student $i has scored 95 marks in Mathematics and 88 marks in Physics.\n\n")
        }
        val text = largeTextBuilder.toString()

        val spreadsheetChunks = com.docsphere.data.repository.DocumentChunker.splitIntoChunks(
            documentId = "sheet_1",
            documentName = "M.KUMARASAMY GROUP.xlsx",
            text = text
        )

        assertEquals(com.docsphere.data.repository.DocumentChunker.MAX_CHUNKS_PER_SPREADSHEET, spreadsheetChunks.size)

        // Non-spreadsheet should not cap at 200
        val pdfChunks = com.docsphere.data.repository.DocumentChunker.splitIntoChunks(
            documentId = "pdf_1",
            documentName = "LargeDocument.pdf",
            text = text
        )
        assertTrue("Non-spreadsheet with large text can exceed 200 chunks", pdfChunks.size > 200)
    }

    @Test
    fun testSpreadsheetNumericCellSkipping() {
        val spreadsheetContent = """
            101, 48204.00, 2024, 9876543210
            102, 123456789012, 55.4, 0.00
            Customer: John Doe, Email: john@example.com, Phone: +91 98765 43210, PAN: ABCDE1234F
        """.trimIndent()

        val fields = DocumentFieldExtractor.extractFields(
            documentId = "sheet_test",
            documentName = "Accounts.xlsx",
            text = spreadsheetContent
        )

        // The first two rows are purely numeric columns and should be skipped for false-positive extraction
        // The last row contains valid labels and fields
        val email = fields.find { it.fieldName == DocumentFieldExtractor.FIELD_EMAIL }
        assertNotNull("Should extract valid email from labeled row", email)
        assertEquals("john@example.com", email?.fieldValue)

        val pan = fields.find { it.fieldName == DocumentFieldExtractor.FIELD_PAN }
        assertNotNull("Should extract valid PAN from labeled row", pan)
        assertEquals("ABCDE1234F", pan?.fieldValue)
    }

    @Test
    fun testInferenceCoordinator_chatActiveState() {
        com.docsphere.ai.InferenceCoordinator.setChatActive(false)
        com.docsphere.ai.InferenceCoordinator.setInferring(false)
        assertEquals(false, com.docsphere.ai.InferenceCoordinator.isChatActive)
        assertEquals(false, com.docsphere.ai.InferenceCoordinator.isInferring)

        com.docsphere.ai.InferenceCoordinator.setChatActive(true)
        assertEquals(true, com.docsphere.ai.InferenceCoordinator.isChatActive)

        com.docsphere.ai.InferenceCoordinator.setChatActive(false)
        assertEquals(false, com.docsphere.ai.InferenceCoordinator.isChatActive)
    }
}
