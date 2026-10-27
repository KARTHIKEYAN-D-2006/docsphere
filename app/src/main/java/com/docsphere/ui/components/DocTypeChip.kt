package com.docsphere.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.docsphere.ui.theme.*

@Composable
fun DocTypeChip(
    mimeType: String,
    modifier: Modifier = Modifier
) {
    val (label, color) = mimeTypeToChip(mimeType)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

fun mimeTypeToChip(mimeType: String): Pair<String, Color> = when {
    mimeType == "application/pdf" -> "PDF" to PdfColor
    mimeType.startsWith("image/") -> mimeType.substringAfter("image/").uppercase().take(4) to ImageColor
    mimeType.startsWith("audio/") -> "AUDIO" to AudioColor
    mimeType.startsWith("video/") -> "VIDEO" to VideoColor
    mimeType.contains("word") -> "DOCX" to DocColor
    mimeType.contains("spreadsheet") || mimeType.contains("excel") -> "XLSX" to ExcelColor
    mimeType.contains("presentation") || mimeType.contains("powerpoint") -> "PPTX" to PptColor
    mimeType == "text/plain" -> "TXT" to TextColor
    mimeType == "text/csv" -> "CSV" to TealEmerald
    else -> "FILE" to TextMuted
}
