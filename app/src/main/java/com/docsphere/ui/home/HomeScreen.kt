package com.docsphere.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.docsphere.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsphere.data.model.DocumentEntity
import com.docsphere.data.model.FileCategory
import com.docsphere.ui.components.*
import com.docsphere.ui.theme.*
import com.docsphere.util.FileOpener
import com.docsphere.util.StorageStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToFiles: (FileCategory?) -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToDrive: () -> Unit,
    onOpenFile: (DocumentEntity) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importDocuments(uris)
        }
    }

    var hasAllFilesAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        )
    }

    var showAllFilesDialog by remember { mutableStateOf(false) }

    // Refresh permission state on resume when returning from system settings
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showAllFilesDialog) {
        AlertDialog(
            onDismissRequest = { showAllFilesDialog = false },
            title = {
                Text(
                    "All Files Access Needed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    "To discover and index all your PDF, Office, and text documents across your storage, Android requires All Files Access in System Settings.\n\nWould you like to open settings now?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAllFilesDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open System Settings", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAllFilesDialog = false
                        viewModel.triggerScan()
                    }
                ) {
                    Text("Scan Media Only", color = TextSecondary)
                }
            },
            containerColor = GoogleSurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        containerColor = GoogleBackgroundDark,
        topBar = {
            // Google Files Floating Search Bar
            GoogleFilesSearchBar(
                onSearchClick = { onNavigateToFiles(null) },
                onDriveClick = onNavigateToDrive
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {


            // ── 2. Categories Section (2x3 Grid like Google Files) ──────
            Text(
                text = "Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            GoogleFilesCategoriesGrid(
                state = state,
                onCategoryClick = { category -> onNavigateToFiles(category) },
                onDriveClick = onNavigateToDrive
            )

            // ── 3. AI Spotlight Card (Docsphere Superpower) ─────────────
            AiSpotlightCard(onClick = onNavigateToChat)

            // ── 3.5 Storage Capacity Card (Moved to bottom) ───────
            GoogleFilesStorageCard(
                storageStats = state.storageStats,
                lastScanTime = state.lastScanTime,
                onScanClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAllFilesAccess) {
                        showAllFilesDialog = true
                    } else {
                        viewModel.triggerScan()
                    }
                }
            )

            if (!hasAllFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
                    border = BorderStroke(1.dp, GoogleYellow.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.FolderSpecial, null, tint = GoogleYellow, modifier = Modifier.size(24.dp))
                            Text(
                                "Enable document access",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Required on Android 11+ to discover and index your PDF, Word, and text documents across your storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoogleYellow),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Allow in System Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            ScanProgressBanner(progress = state.scanProgress)

            // ── 4. Recent Files Section ─────────────────────────────────
            RecentFilesSection(
                files = state.recentFiles,
                onViewAll = { onNavigateToFiles(null) },
                onFileClick = onOpenFile,
                onAskAi = { doc -> onNavigateToChat() },
                onShare = { doc -> FileOpener.shareFile(context, doc) },
                onDelete = { doc -> viewModel.deleteDocument(doc) },
                onRetryOcr = { doc -> viewModel.retryOcr(doc) }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Google Files pill-shaped floating top search bar.
 */
@Composable
private fun GoogleFilesSearchBar(
    onSearchClick: () -> Unit,
    onDriveClick: () -> Unit
) {
    Surface(
        color = GoogleBackgroundDark,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(onClick = onSearchClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = GoogleTextSecondary
                    )
                    Text(
                        text = "Search in Docsphere…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GoogleTextSecondary
                    )
                }

                // Google Drive Icon Button
                IconButton(
                    onClick = onDriveClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_cat_drive),
                        contentDescription = "Google Drive",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/**
 * Google Files storage indicator card with progress bar.
 */
@Composable
private fun GoogleFilesStorageCard(
    storageStats: StorageStats,
    lastScanTime: String,
    onScanClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GoogleBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_storage),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Internal storage",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${storageStats.usedFormatted} of ${storageStats.totalFormatted} used • ${storageStats.freeFormatted} free",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                // Scan / Refresh Button
                IconButton(
                    onClick = onScanClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GoogleSurfaceVariantDark)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_reload),
                        contentDescription = "Scan files",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Storage Progress Bar (Google Files style)
            LinearProgressIndicator(
                progress = { storageStats.usedPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = GoogleBlue,
                trackColor = GoogleSurfaceVariantDark
            )

            // Last Scan Status
            if (lastScanTime.isNotBlank() && lastScanTime != "Never") {
                Text(
                    text = "Indexed & synced: $lastScanTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoogleGreen
                )
            }
        }
    }
}

/**
 * 2x3 Grid of Categories matching Files by Google.
 */
@Composable
private fun GoogleFilesCategoriesGrid(
    state: HomeUiState,
    onCategoryClick: (FileCategory) -> Unit,
    onDriveClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryTile(
                modifier = Modifier.weight(1f),
                title = "Downloads",
                subtitle = "Browse files",
                iconDrawable = R.drawable.ic_cat_downloads,
                containerColor = GoogleBlueContainerDark,
                onClick = { onCategoryClick(FileCategory.ALL) }
            )
            CategoryTile(
                modifier = Modifier.weight(1f),
                title = "Images",
                subtitle = "${state.imageCount} files",
                iconDrawable = R.drawable.ic_cat_images,
                containerColor = GooglePurpleContainerDark,
                onClick = { onCategoryClick(FileCategory.IMAGE) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryTile(
                modifier = Modifier.weight(1f),
                title = "Videos",
                subtitle = "${state.videoCount} files",
                iconDrawable = R.drawable.ic_cat_videos,
                containerColor = GoogleYellowContainerDark,
                onClick = { onCategoryClick(FileCategory.VIDEO) }
            )
            CategoryTile(
                modifier = Modifier.weight(1f),
                title = "Audio",
                subtitle = "${state.audioCount} files",
                iconDrawable = R.drawable.ic_cat_audio,
                containerColor = GoogleRedContainerDark,
                onClick = { onCategoryClick(FileCategory.AUDIO) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryTile(
                modifier = Modifier.weight(1f),
                title = "Documents",
                subtitle = "${state.totalDocs} files",
                iconDrawable = R.drawable.ic_cat_documents,
                containerColor = GoogleGreenContainerDark,
                onClick = { onCategoryClick(FileCategory.DOCUMENT) }
            )
            CategoryTile(
                modifier = Modifier.weight(1f),
                title = "Google Drive",
                subtitle = "Cloud files",
                iconDrawable = R.drawable.ic_cat_drive,
                containerColor = GoogleBlueContainerDark,
                onClick = onDriveClick
            )
        }
    }
}

@Composable
private fun CategoryTile(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    iconDrawable: Int? = null,
    color: Color = GoogleBlue,
    containerColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconDrawable != null) {
                Image(
                    painter = painterResource(id = iconDrawable),
                    contentDescription = title,
                    modifier = Modifier.size(38.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * AI Spotlight Card (Docsphere Superpower).
 */
@Composable
private fun AiSpotlightCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.docsphere_ai_logo),
                    contentDescription = "Docsphere AI",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ask Docsphere AI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Chat with your PDFs, Word docs & Drive files",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = GoogleBlue
            )
        }
    }
}

/**
 * Recent Files list with click-to-open and kebab menu.
 */
@Composable
private fun RecentFilesSection(
    files: List<DocumentEntity>,
    onViewAll: () -> Unit,
    onFileClick: (DocumentEntity) -> Unit,
    onAskAi: (DocumentEntity) -> Unit,
    onShare: (DocumentEntity) -> Unit,
    onDelete: (DocumentEntity) -> Unit,
    onRetryOcr: (DocumentEntity) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (files.isNotEmpty()) {
                TextButton(onClick = onViewAll) {
                    Text("View all", color = GoogleBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (files.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No files indexed yet. Tap refresh above to scan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                files.take(6).forEach { doc ->
                    FileCard(
                        document = doc,
                        onClick = { onFileClick(doc) },
                        onAskAi = { onAskAi(doc) },
                        onShare = { onShare(doc) },
                        onDelete = { onDelete(doc) },
                        onRetryOcr = { onRetryOcr(doc) }
                    )
                }
            }
        }
    }
}
