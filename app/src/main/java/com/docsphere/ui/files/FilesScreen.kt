package com.docsphere.ui.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docsphere.data.model.DocumentEntity
import com.docsphere.data.model.FileCategory
import com.docsphere.ui.components.*
import com.docsphere.ui.theme.*
import com.docsphere.util.FileOpener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onOpenFile: (DocumentEntity) -> Unit,
    onAskAi: (DocumentEntity) -> Unit,
    initialCategory: FileCategory? = null,
    viewModel: FilesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val displayFiles = if (state.searchQuery.isNotBlank()) state.searchResults else state.files
    val context = LocalContext.current

    LaunchedEffect(initialCategory) {
        if (initialCategory != null) {
            viewModel.setCategory(initialCategory)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importDocuments(uris)
        }
    }

    Scaffold(
        containerColor = GoogleBackgroundDark,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GoogleBackgroundDark)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Title & Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Files",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Import / Add button
                        IconButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GoogleSurfaceDark)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Import files",
                                tint = GoogleBlue
                            )
                        }

                        // Toggle Grid/List
                        IconButton(
                            onClick = { viewModel.toggleViewMode() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GoogleSurfaceDark)
                        ) {
                            Icon(
                                imageVector = if (state.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle view",
                                tint = GoogleBlue
                            )
                        }
                    }
                }

                // Google Files Pill Search Bar
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = { Text("Search files by name or content…", color = GoogleTextSecondary, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = GoogleTextSecondary) },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, null, tint = GoogleTextSecondary)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoogleBlue,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = GoogleSurfaceDark,
                        unfocusedContainerColor = GoogleSurfaceDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                // Category Chips Row (Google Files style)
                ScrollableTabRow(
                    selectedTabIndex = FileCategory.entries.indexOf(state.selectedCategory),
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    contentColor = GoogleBlue,
                    divider = {}
                ) {
                    FileCategory.entries.forEach { category ->
                        val isSelected = category == state.selectedCategory
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.setCategory(category) },
                            text = {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) GoogleBlue else GoogleSurfaceDark)
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = category.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else TextSecondary
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isSearching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GoogleBlue)
                    }
                }
                displayFiles.isEmpty() -> {
                    EmptyFilesState(onImport = { filePickerLauncher.launch(arrayOf("*/*")) })
                }
                state.isGridView -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = displayFiles,
                            key = { it.id },
                            contentType = { "grid_card" }
                        ) { doc ->
                            GridFileCard(
                                document = doc,
                                onClick = { onOpenFile(doc) },
                                onRetryOcr = { viewModel.retryOcr(doc) }
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = displayFiles,
                            key = { it.id },
                            contentType = { "list_card" }
                        ) { doc ->
                            FileCard(
                                document = doc,
                                onClick = { onOpenFile(doc) },
                                onAskAi = { onAskAi(doc) },
                                onShare = { FileOpener.shareFile(context, doc) },
                                onDelete = { viewModel.deleteDocument(doc) },
                                onInfo = { onOpenFile(doc) },
                                onRetryOcr = { viewModel.retryOcr(doc) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFilesState(onImport: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(GoogleSurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FolderOpen, null, tint = GoogleBlue, modifier = Modifier.size(32.dp))
            }
            Text("No files in this category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "Scan your device or tap below to import documents, photos, and files directly.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onImport,
                colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import file")
            }
        }
    }
}
