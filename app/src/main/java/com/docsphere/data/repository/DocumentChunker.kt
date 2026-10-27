package com.docsphere.data.repository

import android.util.Log

data class DocumentChunk(
    val id: String,
    val documentId: String,
    val documentName: String,
    val chunkIndex: Int,
    val text: String
)

object DocumentChunker {

    private const val TAG = "DocSphereDebug"
    const val TARGET_CHUNK_SIZE = 1200
    const val TARGET_OVERLAP = 200
    private const val MIN_CHUNK_SIZE = 200
    const val MAX_CHUNKS_PER_SPREADSHEET = 200

    fun isSpreadsheet(documentName: String): Boolean {
        val ext = documentName.substringAfterLast('.', "").lowercase()
        return ext in listOf("xlsx", "xls", "csv")
    }

    /**
     * Splits extracted text into chunks of about 1200 characters with about 200 characters of overlap.
     * Splits on paragraph or sentence boundaries where possible, and never mid-word.
     * Keeps the document name with each chunk.
     *
     * For spreadsheets (e.g. .xlsx, .xls, .csv), chunks are capped at [MAX_CHUNKS_PER_SPREADSHEET] (200 chunks)
     * to prevent massive database locking, memory bloat, and ANRs.
     */
    fun splitIntoChunks(
        documentId: String,
        documentName: String,
        text: String,
        maxChunks: Int = if (isSpreadsheet(documentName)) MAX_CHUNKS_PER_SPREADSHEET else Int.MAX_VALUE
    ): List<DocumentChunk> {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return emptyList()

        if (cleanText.length <= TARGET_CHUNK_SIZE) {
            val single = DocumentChunk(
                id = "${documentId}_chunk_0",
                documentId = documentId,
                documentName = documentName,
                chunkIndex = 0,
                text = cleanText
            )
            Log.d(TAG, "[Chunking] Document '$documentName' (len: ${cleanText.length}) split into 1 chunk")
            return listOf(single)
        }

        val chunks = mutableListOf<DocumentChunk>()
        var startIndex = 0
        var chunkIndex = 0

        while (startIndex < cleanText.length) {
            if (chunks.size >= maxChunks) {
                Log.d(TAG, "[Chunking] Document '$documentName' reached chunk cap of $maxChunks. Stopping chunking.")
                break
            }

            val remaining = cleanText.length - startIndex
            if (remaining <= TARGET_CHUNK_SIZE) {
                val chunkText = cleanText.substring(startIndex).trim()
                if (chunkText.isNotBlank()) {
                    chunks.add(
                        DocumentChunk(
                            id = "${documentId}_chunk_$chunkIndex",
                            documentId = documentId,
                            documentName = documentName,
                            chunkIndex = chunkIndex,
                            text = chunkText
                        )
                    )
                }
                break
            }

            // Ideal end is startIndex + 1200
            val idealEnd = startIndex + TARGET_CHUNK_SIZE
            val searchStart = maxOf(startIndex + TARGET_CHUNK_SIZE - 300, startIndex + 100)
            val window = cleanText.substring(searchStart, minOf(idealEnd + 100, cleanText.length))

            // 1. Try paragraph boundary (\n\n)
            var splitPos = -1
            val paraIdx = window.lastIndexOf("\n\n")
            if (paraIdx != -1) {
                splitPos = searchStart + paraIdx + 2
            } else {
                // 2. Try sentence boundary (. , ! , ? , or \n)
                val sentenceBreak = findLastSentenceBoundary(window)
                if (sentenceBreak != -1) {
                    splitPos = searchStart + sentenceBreak
                } else {
                    // 3. Try word boundary (space) - never mid-word
                    val spaceIdx = window.lastIndexOf(' ')
                    if (spaceIdx != -1) {
                        splitPos = searchStart + spaceIdx + 1
                    } else {
                        splitPos = idealEnd
                    }
                }
            }

            val endIndex = splitPos.coerceIn(startIndex + MIN_CHUNK_SIZE, cleanText.length)
            val chunkContent = cleanText.substring(startIndex, endIndex).trim()

            if (chunkContent.isNotBlank()) {
                chunks.add(
                    DocumentChunk(
                        id = "${documentId}_chunk_$chunkIndex",
                        documentId = documentId,
                        documentName = documentName,
                        chunkIndex = chunkIndex,
                        text = chunkContent
                    )
                )
                chunkIndex++
            }

            if (chunks.size >= maxChunks) {
                Log.d(TAG, "[Chunking] Document '$documentName' reached chunk cap of $maxChunks. Stopping chunking.")
                break
            }

            if (endIndex >= cleanText.length) break

            // Calculate next start with ~200 overlap
            var nextStart = maxOf(startIndex + 1, endIndex - TARGET_OVERLAP)
            // Adjust nextStart to a word boundary (don't start mid-word)
            if (nextStart < cleanText.length && !cleanText[nextStart].isWhitespace()) {
                val nextSpace = cleanText.indexOf(' ', nextStart)
                if (nextSpace != -1 && nextSpace - nextStart < 50) {
                    nextStart = nextSpace + 1
                }
            }

            startIndex = nextStart
        }

        Log.d(TAG, "[Chunking] Document '$documentName' (len: ${cleanText.length}) split into ${chunks.size} chunks")
        return chunks
    }

    private fun findLastSentenceBoundary(text: String): Int {
        val delimiters = listOf(". ", ".\n", "! ", "? ", "\n")
        var bestIdx = -1
        for (delim in delimiters) {
            val idx = text.lastIndexOf(delim)
            if (idx != -1 && idx + delim.length > bestIdx) {
                bestIdx = idx + delim.length
            }
        }
        return bestIdx
    }
}
