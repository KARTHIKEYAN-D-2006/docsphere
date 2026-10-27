package com.docsphere.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.docsphere.data.model.DocumentEntity
import com.docsphere.data.model.FileCategory
import com.docsphere.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FileCard(
    document: DocumentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAskAi: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onRetryOcr: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = GoogleSurfaceDark
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // File type icon / thumbnail (Google Files styled)
            FileIconOrThumbnail(document = document, size = 48.dp)

            // File info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = document.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = document.readableSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text("•", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = formatFileDate(document.lastModified),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )

                    if (document.isIndexed) {
                        Spacer(Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GoogleGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "AI",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = GoogleGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (com.docsphere.data.repository.DocumentChunker.isSpreadsheet(document.displayName)) {
                            Spacer(Modifier.width(2.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GoogleBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "200 cap",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = GoogleBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (document.fileCategory == FileCategory.IMAGE) {
                        Spacer(Modifier.width(2.dp))
                        OcrStatusBadge(document = document, onRetryOcr = onRetryOcr)
                    }
                }
            }

            // Kebab 3-dot menu button
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        leadingIcon = { Icon(Icons.Default.Visibility, null) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    if (onAskAi != null) {
                        DropdownMenuItem(
                            text = { Text("Ask AI") },
                            leadingIcon = { Image(painter = painterResource(id = com.docsphere.R.drawable.ic_agent_robot), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onAskAi()
                            }
                        )
                    }
                    if (onRetryOcr != null && (document.ocrFailed || document.fileCategory == FileCategory.IMAGE)) {
                        DropdownMenuItem(
                            text = { Text(if (document.ocrFailed) "Retry OCR" else "Re-run OCR") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null, tint = GoogleBlue) },
                            onClick = {
                                showMenu = false
                                onRetryOcr()
                            }
                        )
                    }
                    if (onShare != null) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                    }
                    if (onInfo != null) {
                        DropdownMenuItem(
                            text = { Text("File info") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = {
                                showMenu = false
                                onInfo()
                            }
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = GoogleRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = GoogleRed) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GridFileCard(
    document: DocumentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    onRetryOcr: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Large Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .background(GoogleSurfaceVariantDark),
                contentAlignment = Alignment.Center
            ) {
                if (document.fileCategory == FileCategory.IMAGE && document.localUri != null) {
                    val context = LocalContext.current
                    val imageRequest = remember(document.localUri) {
                        ImageRequest.Builder(context)
                            .data(document.localUri)
                            .size(360, 360)
                            .crossfade(true)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = document.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val drawableRes = fileDrawableRes(document.mimeType)
                    val (icon, color) = fileIconAndColor(document.mimeType)
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (drawableRes != null) {
                            Image(
                                painter = painterResource(id = drawableRes),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
                        }
                    }
                }

                if (document.fileCategory == FileCategory.IMAGE) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        OcrStatusBadge(document = document, onRetryOcr = onRetryOcr)
                    }
                }
            }

            // Info row
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = document.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = document.readableSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    if (document.ocrFailed && onRetryOcr != null) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = GoogleBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onRetryOcr() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OcrStatusBadge(
    document: DocumentEntity,
    onRetryOcr: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (document.fileCategory != FileCategory.IMAGE) return

    val status = document.ocrStatus
    when {
        status == "ok" -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(GoogleGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    "OCR ✓",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = GoogleGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        status == "skipped" -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(TextMuted.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    "OCR Skipped",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        status?.startsWith("failed") == true -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(GoogleRed.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .clickable(enabled = onRetryOcr != null) { onRetryOcr?.invoke() }
            ) {
                Text(
                    "OCR Failed ↺",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = GoogleRed,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(GoogleYellow.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    "OCR Pending",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = GoogleYellow,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FileIconOrThumbnail(
    document: DocumentEntity,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    val drawableRes = fileDrawableRes(document.mimeType)
    val (icon, color) = fileIconAndColor(document.mimeType)

    if (document.fileCategory == FileCategory.IMAGE && document.localUri != null) {
        val context = LocalContext.current
        val imageRequest = remember(document.localUri) {
            ImageRequest.Builder(context)
                .data(document.localUri)
                .size(128, 128)
                .crossfade(true)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = document.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
        )
    } else if (drawableRes != null) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = null,
                modifier = Modifier.size(size * 0.9f)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

fun fileDrawableRes(mimeType: String): Int? = when {
    mimeType == "application/pdf" -> com.docsphere.R.drawable.ic_file_pdf
    mimeType.contains("word") -> com.docsphere.R.drawable.ic_file_doc
    mimeType.contains("spreadsheet") || mimeType.contains("excel") || mimeType == "text/csv" -> com.docsphere.R.drawable.ic_file_csv
    mimeType.startsWith("audio/") -> com.docsphere.R.drawable.ic_cat_audio
    mimeType.startsWith("video/") -> com.docsphere.R.drawable.ic_cat_videos
    mimeType.startsWith("image/") -> com.docsphere.R.drawable.ic_cat_images
    else -> null
}

fun fileIconAndColor(mimeType: String): Pair<ImageVector, Color> = when {
    mimeType == "application/pdf" -> Icons.Default.PictureAsPdf to GoogleRed
    mimeType.startsWith("image/") -> Icons.Default.Image to GooglePurple
    mimeType.startsWith("audio/") -> Icons.Default.AudioFile to Color(0xFFFF6D00)
    mimeType.startsWith("video/") -> Icons.Default.VideoFile to Color(0xFF00B0FF)
    mimeType.contains("word") -> Icons.Default.Description to GoogleBlue
    mimeType.contains("spreadsheet") || mimeType.contains("excel") -> Icons.Default.TableChart to GoogleGreen
    mimeType.contains("presentation") || mimeType.contains("powerpoint") -> Icons.Default.Slideshow to GoogleYellow
    mimeType == "text/plain" || mimeType == "text/csv" -> Icons.Default.TextSnippet to Color(0xFF00BFA5)
    else -> Icons.Default.InsertDriveFile to TextSecondary
}

private fun formatFileDate(instant: java.time.Instant): String {
    return try {
        val now = java.time.LocalDate.now()
        val fileDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        when {
            fileDate.isEqual(now) -> "Today"
            fileDate.isEqual(now.minusDays(1)) -> "Yesterday"
            else -> fileDate.format(DateTimeFormatter.ofPattern("MMM d"))
        }
    } catch (_: Exception) {
        "Recent"
    }
}
