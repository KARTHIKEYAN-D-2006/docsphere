package com.docsphere.ui.drive

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.docsphere.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsphere.data.model.DocumentEntity
import com.docsphere.ui.theme.*
import com.docsphere.util.FileOpener
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

// Google Drive brand colors
private val DriveBlue = Color(0xFF4285F4)
private val DriveGreen = Color(0xFF0F9D58)
private val DriveYellow = Color(0xFFF4B400)
private val DriveRed = Color(0xFFEA4335)

enum class DriveFilter(val label: String) {
    ALL("All files"),
    DOCUMENTS("Documents"),
    SPREADSHEETS("Spreadsheets"),
    PRESENTATIONS("Slides"),
    PDFS("PDFs")
}

@Composable
fun DriveScreen(
    viewModel: DriveViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(DriveFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val drivePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importDriveFiles(uris)
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                viewModel.onSignInResult(true, account?.email)
            } catch (e: ApiException) {
                val msg = when (e.statusCode) {
                    10 -> "DEVELOPER_ERROR (Code 10): SHA-1 fingerprint or package name (com.docsphere) is not registered in Google Cloud Console."
                    12500 -> "SIGN_IN_FAILED (Code 12500): Check Google Cloud OAuth Consent Screen & Drive API."
                    else -> "Sign-in error: ${e.message ?: "Code ${e.statusCode}"}"
                }
                viewModel.onSignInResult(false, null, msg)
            }
        } else {
            viewModel.onSignInResult(false, null, "Sign-in cancelled.")
        }
    }

    // Filter documents by category and search
    val filteredDocs = remember(state.driveDocuments, selectedFilter, searchQuery) {
        state.driveDocuments.filter { doc ->
            val matchesSearch = searchQuery.isBlank() || doc.displayName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                DriveFilter.ALL -> true
                DriveFilter.DOCUMENTS -> {
                    val m = doc.mimeType.lowercase()
                    m.contains("word") || m.contains("document") || doc.displayName.endsWith(".docx", true) || doc.displayName.endsWith(".doc", true)
                }
                DriveFilter.SPREADSHEETS -> {
                    val m = doc.mimeType.lowercase()
                    m.contains("sheet") || m.contains("excel") || m.contains("csv") || doc.displayName.endsWith(".xlsx", true) || doc.displayName.endsWith(".csv", true)
                }
                DriveFilter.PRESENTATIONS -> {
                    val m = doc.mimeType.lowercase()
                    m.contains("presentation") || m.contains("powerpoint") || doc.displayName.endsWith(".pptx", true)
                }
                DriveFilter.PDFS -> {
                    doc.mimeType.equals("application/pdf", true) || doc.displayName.endsWith(".pdf", true)
                }
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        containerColor = GoogleBackgroundDark,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { drivePickerLauncher.launch(arrayOf("*/*")) },
                containerColor = GoogleSurfaceVariantDark,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(16.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New",
                        tint = DriveBlue,
                        modifier = Modifier.size(24.dp)
                    )
                },
                text = {
                    Text(
                        text = "New from Drive",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── 1. Google Drive Top Search Bar ──────────────────────────────
            GoogleDriveTopBar(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                userEmail = state.userEmail,
                isSignedIn = state.isSignedIn,
                onAccountClick = {
                    if (!state.isSignedIn) {
                        try {
                            signInLauncher.launch(viewModel.getSignInIntent())
                        } catch (e: Exception) {
                            viewModel.onSignInResult(false, null, e.message)
                        }
                    } else {
                        viewModel.signOut()
                    }
                }
            )

            // ── 2. Google Drive Filter Chips ───────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = DriveFilter.entries.indexOf(selectedFilter),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                DriveFilter.entries.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    Tab(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        text = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) GoogleSurfaceVariantDark else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) DriveBlue else GoogleSurfaceVariantDark,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) DriveBlue else TextSecondary
                                )
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 3. Notification Banners ────────────────────────────────────
            state.error?.let { err ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DriveRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = DriveRed, modifier = Modifier.size(20.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = DriveRed)
                    }
                }
            }

            state.statusMessage?.let { status ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DriveGreen.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = DriveGreen, modifier = Modifier.size(20.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall, color = DriveGreen)
                    }
                }
            }

            // ── 4. Google Drive Files List ─────────────────────────────────
            if (filteredDocs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_cat_drive),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = if (state.driveDocuments.isEmpty()) "No files in Google Drive yet" else "No matching files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tap 'New from Drive' to select and index PDF, Word, and Excel files from your Google Drive account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { drivePickerLauncher.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Select from Google Drive", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Files (${filteredDocs.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = DriveBlue
                                )
                            }
                        }
                    }

                    items(filteredDocs, key = { it.id }) { doc ->
                        GoogleDriveFileCard(
                            doc = doc,
                            onClick = { FileOpener.openWithSystem(context, doc) },
                            onShare = { FileOpener.shareFile(context, doc) },
                            onDelete = { viewModel.deleteDriveDocument(doc.id) }
                        )
                    }

                    item {
                        Spacer(Modifier.height(80.dp)) // Space for FAB
                    }
                }
            }
        }
    }
}

/**
 * Google Drive Top Search Bar matching the official Google Drive Android app.
 */
@Composable
private fun GoogleDriveTopBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    userEmail: String?,
    isSignedIn: Boolean,
    onAccountClick: () -> Unit
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Google Drive colored triangle icon
                Image(
                    painter = painterResource(id = R.drawable.ic_cat_drive),
                    contentDescription = "Google Drive",
                    modifier = Modifier.size(24.dp)
                )

                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            text = "Search in Drive",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GoogleTextSecondary
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                // Google Account / Sign-In Icon
                if (isSignedIn && !userEmail.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DriveGreen)
                            .clickable(onClick = onAccountClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userEmail.first().uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_google),
                        contentDescription = "Sign in with Google",
                        modifier = Modifier
                            .size(30.dp)
                            .clickable(onClick = onAccountClick)
                    )
                }
            }
        }
    }
}

/**
 * Google Drive styled file card item with authentic file icon and metadata.
 */
@Composable
private fun GoogleDriveFileCard(
    doc: DocumentEntity,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val (iconVector, iconColor) = when {
        doc.mimeType.equals("application/pdf", true) || doc.displayName.endsWith(".pdf", true) ->
            Icons.Default.PictureAsPdf to DriveRed
        doc.mimeType.contains("sheet") || doc.mimeType.contains("excel") || doc.displayName.endsWith(".xlsx", true) || doc.displayName.endsWith(".csv", true) ->
            Icons.Default.TableChart to DriveGreen
        doc.mimeType.contains("presentation") || doc.mimeType.contains("powerpoint") || doc.displayName.endsWith(".pptx", true) ->
            Icons.Default.Slideshow to DriveYellow
        doc.mimeType.contains("word") || doc.displayName.endsWith(".docx", true) || doc.displayName.endsWith(".doc", true) ->
            Icons.Default.Description to DriveBlue
        else ->
            Icons.Default.InsertDriveFile to GoogleTextSecondary
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurfaceDark),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Google Drive Document Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Document Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.displayName,
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
                        text = doc.readableSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text("•", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        text = "Cloud synced",
                        style = MaterialTheme.typography.labelSmall,
                        color = DriveGreen
                    )
                }
            }

            // Kebab Menu Actions
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = GoogleTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(GoogleSurfaceDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Open file", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, null, tint = DriveBlue) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Share, null, tint = TextSecondary) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from Drive", color = DriveRed) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = DriveRed) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
