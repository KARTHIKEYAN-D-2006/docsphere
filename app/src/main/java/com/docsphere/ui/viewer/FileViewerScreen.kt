package com.docsphere.ui.viewer

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.docsphere.data.model.DocumentEntity
import com.docsphere.data.model.FileCategory
import com.docsphere.ui.components.fileIconAndColor
import com.docsphere.ui.theme.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsphere.util.FileOpener
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FileViewerRoute(
    onBack: () -> Unit,
    onAskAi: (DocumentEntity) -> Unit,
    viewModel: FileViewerViewModel = hiltViewModel()
) {
    val document by viewModel.document.collectAsStateWithLifecycle()
    FileViewerScreen(
        document = document,
        onBack = onBack,
        onAskAi = onAskAi,
        onDelete = { viewModel.deleteDocument(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    document: DocumentEntity?,
    onBack: () -> Unit,
    onAskAi: (DocumentEntity) -> Unit,
    onDelete: (DocumentEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (document == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GoogleBackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("File not found", color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Go back") }
            }
        }
        return
    }

    val isImage = document.fileCategory == FileCategory.IMAGE || document.mimeType.startsWith("image/")
    val (typeIcon, typeColor) = fileIconAndColor(document.mimeType)

    Scaffold(
        containerColor = if (isImage) Color.Black else GoogleBackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${document.readableSize} • ${document.fileCategory.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { FileOpener.shareFile(context, document) }) {
                        Icon(Icons.Default.Share, "Share", tint = TextPrimary)
                    }
                    IconButton(onClick = { FileOpener.openWithSystem(context, document) }) {
                        Icon(Icons.Default.OpenInNew, "Open with external app", tint = TextPrimary)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More", tint = TextPrimary)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("File info") },
                                leadingIcon = { Icon(Icons.Default.Info, null) },
                                onClick = {
                                    showMenu = false
                                    showDetailsSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open with…") },
                                leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                                onClick = {
                                    showMenu = false
                                    FileOpener.openWithSystem(context, document)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ask Docsphere AI") },
                                leadingIcon = { Image(painter = painterResource(id = com.docsphere.R.drawable.ic_agent_robot), contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMenu = false
                                    onAskAi(document)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = GoogleRed) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = GoogleRed) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isImage) Color.Black.copy(alpha = 0.8f) else GoogleSurfaceDark
                )
            )
        },
        bottomBar = {
            Surface(
                color = if (isImage) Color.Black.copy(alpha = 0.9f) else GoogleSurfaceDark,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ask AI Highlight Button (Google Files + Docsphere superpower)
                    Button(
                        onClick = { onAskAi(document) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoogleBlue
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Image(painter = painterResource(id = com.docsphere.R.drawable.ic_agent_robot), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ask AI about this file", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(Modifier.width(12.dp))

                    // Share button
                    IconButton(
                        onClick = { FileOpener.shareFile(context, document) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(GoogleSurfaceVariantDark)
                    ) {
                        Icon(Icons.Default.Share, "Share", tint = TextPrimary)
                    }

                    Spacer(Modifier.width(8.dp))

                    // Info button
                    IconButton(
                        onClick = { showDetailsSheet = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(GoogleSurfaceVariantDark)
                    ) {
                        Icon(Icons.Default.Info, "Details", tint = TextPrimary)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // ── 1. Image Viewer (Zoomable / Panable) ────────────────────
                isImage -> {
                    ZoomableImageViewer(imageUri = document.localUri ?: document.id)
                }

                // ── 2. PDF & Office Document Previewer ────────────────────
                document.fileCategory == FileCategory.DOCUMENT || document.mimeType == "application/pdf" -> {
                    DocumentReaderView(
                        document = document,
                        onOpenExternal = { FileOpener.openWithSystem(context, document) }
                    )
                }

                // ── 3. Audio / Video Preview ──────────────────────────────
                document.fileCategory == FileCategory.AUDIO || document.fileCategory == FileCategory.VIDEO -> {
                    MediaPreviewView(
                        document = document,
                        onPlay = { FileOpener.openWithSystem(context, document) }
                    )
                }

                // ── 4. Generic Text / File Viewer ─────────────────────────
                else -> {
                    DocumentReaderView(
                        document = document,
                        onOpenExternal = { FileOpener.openWithSystem(context, document) }
                    )
                }
            }
        }
    }

    // ── File Info Modal Bottom Sheet (Google Files style) ───────────────
    if (showDetailsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDetailsSheet = false },
            containerColor = GoogleSurfaceDark,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            FileInfoSheet(
                document = document,
                onCopyPath = {
                    clipboardManager.setText(AnnotatedString(document.localUri ?: document.id))
                    Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                onClose = { showDetailsSheet = false }
            )
        }
    }

    // ── Delete Confirmation Dialog ──────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete file?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove \"${document.displayName}\" from Docsphere.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(document)
                        onBack()
                    }
                ) {
                    Text("Delete", color = GoogleRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = GoogleSurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * High-performance zoomable and pannable image viewer like Google Files.
 */
@Composable
private fun ZoomableImageViewer(imageUri: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        val imageRequest = remember(imageUri) {
            coil.request.ImageRequest.Builder(context)
                .data(imageUri)
                .crossfade(true)
                .allowHardware(true)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = "Image preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

/**
 * Clean document reader previewing extracted text with page styling.
 */
@Composable
private fun DocumentReaderView(
    document: DocumentEntity,
    onOpenExternal: () -> Unit
) {
    val scrollState = rememberScrollState()
    val hasText = document.extractedText.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GoogleBackgroundDark)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top header banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val (icon, color) = fileIconAndColor(document.mimeType)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${document.readableSize} • ${if (hasText) "Text extracted (${document.extractedText.length} chars)" else "Ready for system viewer"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                OutlinedButton(
                    onClick = onOpenExternal,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GoogleBlue)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp), tint = GoogleBlue)
                    Spacer(Modifier.width(6.dp))
                    Text("Open", color = GoogleBlue)
                }
            }
        }

        // Spreadsheet cap notification banner
        if (com.docsphere.data.repository.DocumentChunker.isSpreadsheet(document.displayName)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GoogleGreen.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, GoogleGreen.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = GoogleGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Spreadsheet indexing cap: First 200 chunks indexed for AI retrieval to ensure smooth responsiveness.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }
        }

        // Extracted Document Text Preview
        if (hasText) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DOCUMENT PREVIEW",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoogleBlue,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${document.extractedText.split("\\s+".toRegex()).size} words",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    HorizontalDivider(color = GoogleSurfaceVariantDark)

                    Text(
                        text = document.extractedText,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = TextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Description, null, tint = GoogleBlue, modifier = Modifier.size(48.dp))
                    Text(
                        "Tap below to view full file in your default viewer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = onOpenExternal,
                        colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open in System App")
                    }
                }
            }
        }
    }
}

/**
 * Audio / Video media preview card.
 */
@Composable
private fun MediaPreviewView(
    document: DocumentEntity,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GoogleBackgroundDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                val (icon, color) = fileIconAndColor(document.mimeType)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(40.dp))
                }

                Text(
                    text = document.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = "${document.readableSize} • ${document.fileCategory.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play in Media Player")
                }
            }
        }
    }
}

/**
 * File Details Bottom Sheet (Google Files style).
 */
@Composable
private fun FileInfoSheet(
    document: DocumentEntity,
    onCopyPath: () -> Unit,
    onClose: () -> Unit
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "File details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, tint = TextSecondary)
            }
        }

        HorizontalDivider(color = GoogleSurfaceVariantDark)

        DetailRow(title = "Name", value = document.displayName)
        DetailRow(title = "Type", value = document.mimeType)
        DetailRow(title = "Size", value = document.readableSize)
        DetailRow(
            title = "Modified",
            value = try { dateFormatter.format(document.lastModified) } catch (_: Exception) { "Recently" }
        )
        DetailRow(
            title = "Indexed for AI",
            value = if (document.isIndexed) "Yes (Ready for AI search)" else "No"
        )
        if (com.docsphere.data.repository.DocumentChunker.isSpreadsheet(document.displayName)) {
            DetailRow(
                title = "Spreadsheet Cap",
                value = "Indexed up to 200 chunks (Optimized for speed & memory)"
            )
        }

        val path = document.localUri ?: document.id
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Location",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCopyPath),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = GoogleBlue,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCopyPath) {
                    Icon(Icons.Default.ContentCopy, "Copy path", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}
