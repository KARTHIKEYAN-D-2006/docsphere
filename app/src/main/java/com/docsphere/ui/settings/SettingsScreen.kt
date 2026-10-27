package com.docsphere.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsphere.R
import com.docsphere.ai.offline.LlmModel
import com.docsphere.ai.offline.ModelDownloadState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.docsphere.ui.components.*
import com.docsphere.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearIndexDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showGracePeriodDialog by remember { mutableStateOf(false) }
    val tokenFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientStart, GradientMid, GradientEnd)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Screen Header ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DocSphereEmblem(size = 32.dp)
                    Text(
                        text = "SETTINGS",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            letterSpacing = 1.sp
                        ),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Configure on-device AI models, indexing, and privacy preferences",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            // ── Migration Notice Banner ───────────────────────────────
            state.migrationNotice?.let { notice ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ElectricBlue.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(24.dp))
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissMigrationNotice() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted)
                        }
                    }
                }
            }

            // ── 1. On-Device AI Models ─────────────────────────────────
            SettingsSection(
                title = "ON-DEVICE AI MODEL (LITERT-LM)",
                iconDrawable = R.drawable.ic_agent_robot
            ) {
                SettingsToggleRow(
                    icon = Icons.Default.AutoAwesome,
                    iconColor = ElectricBlue,
                    title = "Personal Intelligence",
                    subtitle = "When on, answers can use your indexed files and show sources. When off, the AI answers on its own, like a normal chat.",
                    checked = state.personalIntelligence,
                    onCheckedChange = { viewModel.setPersonalIntelligence(it) }
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                Text(
                    text = "Select the local LiteRT-LM model that powers document queries. All models execute 100% on-device with zero data sharing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    lineHeight = 18.sp
                )

                LlmModel.entries.forEach { model ->
                    val dlState = state.allModelStates[model]
                    val isSelected = state.selectedModel == model
                    LlmModelCard(
                        model = model,
                        downloadState = dlState,
                        isSelected = isSelected,
                        hasHfToken = state.huggingFaceToken.isNotBlank(),
                        onSelect = { viewModel.selectModel(model) },
                        onDownload = { viewModel.downloadModel(model) },
                        onDownloadCellular = { viewModel.downloadWithCellular(model) },
                        onCancel = { viewModel.cancelDownload(model) },
                        onDelete = { viewModel.deleteModel(model) },
                        onAddToken = {
                            coroutineScope.launch {
                                tokenFocusRequester.requestFocus()
                            }
                        }
                    )
                }

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsToggleRow(
                    icon = Icons.Default.Wifi,
                    iconColor = TealEmerald,
                    title = "Download Models on Wi-Fi Only",
                    subtitle = "Avoid consuming cellular data when downloading models",
                    checked = state.requireWifiForDownload,
                    onCheckedChange = { viewModel.setRequireWifi(it) }
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsToggleRow(
                    icon = Icons.Default.Bolt,
                    iconColor = AmberGold,
                    title = "Use GPU acceleration (experimental)",
                    subtitle = if (state.gpuFailedOnDevice) {
                        "Disabled: GPU acceleration failed on this device (using CPU)"
                    } else {
                        "Offload tensors to GPU OpenCL/Vulkan where supported (default: CPU)"
                    },
                    checked = state.useGpuAcceleration,
                    onCheckedChange = { viewModel.setUseGpuAcceleration(it) }
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                var showMaxTokensDialog by remember { mutableStateOf(false) }

                SettingsActionRow(
                    icon = Icons.Default.Tune,
                    iconColor = GooglePurple,
                    title = "Max Context Window",
                    subtitle = "${state.maxTokens} tokens (Gallery default: 2048)",
                    onClick = { showMaxTokensDialog = true }
                )

                if (showMaxTokensDialog) {
                    AlertDialog(
                        onDismissRequest = { showMaxTokensDialog = false },
                        containerColor = NavyDark,
                        title = { Text("Max Context Window", color = TextPrimary, fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1024, 2048, 4096).forEach { tokens ->
                                    val isSelected = state.maxTokens == tokens
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.setMaxTokens(tokens)
                                                showMaxTokensDialog = false
                                            }
                                            .background(if (isSelected) ElectricBlue.copy(alpha = 0.15f) else Color.Transparent)
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "$tokens tokens" + if (tokens == 2048) " (Recommended)" else "",
                                            color = if (isSelected) ElectricBlue else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = ElectricBlue)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showMaxTokensDialog = false }) {
                                Text("Cancel", color = TextSecondary)
                            }
                        }
                    )
                }

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                // Hugging Face token input
                var hfTokenInput by remember(state.huggingFaceToken) { mutableStateOf(state.huggingFaceToken) }
                var isTokenSaved by remember { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = AmberGold, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hugging Face Access Token (Optional)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Required for gated community models. Saved only on-device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hfTokenInput,
                            onValueChange = {
                                hfTokenInput = it
                                isTokenSaved = false
                            },
                            placeholder = { Text("hf_...", color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(tokenFocusRequester),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = {
                                viewModel.saveHuggingFaceToken(hfTokenInput)
                                isTokenSaved = true
                            },
                            modifier = Modifier.height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isTokenSaved) TealEmerald else ElectricBlue),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !state.isHfTokenValidating
                        ) {
                            if (state.isHfTokenValidating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(if (isTokenSaved) "Saved" else "Save")
                            }
                        }
                    }

                    if (state.isHfTokenValidating) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = ElectricBlue,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Checking token with Hugging Face...",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricBlue
                            )
                        }
                    } else if (!state.hfTokenStatus.isNullOrBlank()) {
                        val isOk = state.hfTokenStatus?.startsWith("Token OK") == true
                        Text(
                            text = state.hfTokenStatus ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOk) TealEmerald else ErrorRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── 2. Privacy & Offline ───────────────────────────────────
            SettingsSection(
                title = "PRIVACY & OFFLINE PREFERENCES",
                icon = Icons.Default.Security
            ) {
                SettingsToggleRow(
                    icon = Icons.Default.WifiOff,
                    iconColor = TealEmerald,
                    title = "Force Offline Mode",
                    subtitle = "Always use on-device AI; disable external network access",
                    checked = state.forceOffline,
                    onCheckedChange = { viewModel.setForceOffline(it) }
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsToggleRow(
                    icon = Icons.Default.Visibility,
                    iconColor = ElectricBlue,
                    title = "Show full sensitive values",
                    subtitle = "Display full Aadhaar, PAN, and ID values directly in chat cards with copy button",
                    checked = state.showFullSensitiveValues,
                    onCheckedChange = { viewModel.setShowFullSensitiveValues(it) }
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsInfoRow(
                    icon = Icons.Default.CheckCircle,
                    iconColor = TealEmerald,
                    title = "Cloud Telemetry",
                    value = "Zero Transmission"
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                var showGracePeriodDialog by remember { mutableStateOf(false) }

                SettingsActionRow(
                    icon = Icons.Default.Timer,
                    iconColor = AmberGold,
                    title = "Chat Reset Grace Period",
                    subtitle = state.chatGracePeriod,
                    onClick = { showGracePeriodDialog = true }
                )

                if (showGracePeriodDialog) {
                    AlertDialog(
                        onDismissRequest = { showGracePeriodDialog = false },
                        containerColor = NavyDark,
                        title = { Text("Chat Reset Grace Period", color = TextPrimary, fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Immediately", "2 minutes", "10 minutes").forEach { period ->
                                    val isSelected = state.chatGracePeriod.equals(period, ignoreCase = true)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.setChatGracePeriod(period)
                                                showGracePeriodDialog = false
                                            }
                                            .background(if (isSelected) AmberGold.copy(alpha = 0.15f) else Color.Transparent)
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = period,
                                            color = if (isSelected) AmberGold else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = AmberGold)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showGracePeriodDialog = false }) {
                                Text("Cancel", color = TextSecondary)
                            }
                        }
                    )
                }

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsInfoRow(
                    icon = Icons.Default.Lock,
                    iconColor = ElectricBlue,
                    title = "Document Storage",
                    value = "100% On-Device Only"
                )
            }

            // ── 3. Document Indexing ───────────────────────────────────
            SettingsSection(
                title = "DOCUMENT INDEXING",
                icon = Icons.Default.Folder
            ) {
                SettingsInfoRow(
                    icon = Icons.Default.CheckCircle,
                    iconColor = ElectricBlue,
                    title = "Indexed Documents",
                    value = "${state.totalIndexedDocs} files"
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsInfoRow(
                    icon = Icons.Default.Schedule,
                    iconColor = AmberGold,
                    title = "Last Catalog Scan",
                    value = state.lastScanTime
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsToggleRow(
                    icon = Icons.Default.Autorenew,
                    iconColor = TealEmerald,
                    title = "Auto-scan on Launch",
                    subtitle = "Scan and catalog new or modified files when app opens",
                    checked = state.autoScanOnLaunch,
                    onCheckedChange = { viewModel.setAutoScan(it) }
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsToggleRow(
                    icon = Icons.Default.Image,
                    iconColor = GooglePurple,
                    title = "Scan All Images (OCR)",
                    subtitle = "Index screenshots, Downloads, Documents and Scans folders",
                    checked = state.scanAllImages,
                    onCheckedChange = { viewModel.setScanAllImages(it) }
                )

                if (state.scanAllImages) {
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                    SettingsToggleRow(
                        icon = Icons.Default.CameraAlt,
                        iconColor = AmberGold,
                        title = "Include Camera Roll",
                        subtitle = "Also scan photos in DCIM/Camera for text",
                        checked = state.scanCameraRoll,
                        onCheckedChange = { viewModel.setScanCameraRoll(it) }
                    )
                }

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsActionRow(
                    icon = Icons.Default.Refresh,
                    iconColor = ElectricBlue,
                    title = "Re-scan All Files",
                    subtitle = "Re-index full local storage catalog and metadata",
                    onClick = { viewModel.triggerRescan() }
                )
            }

            // ── 4. Data Management & Privacy ───────────────────────────
            SettingsSection(
                title = "DATA MANAGEMENT & PRIVACY",
                icon = Icons.Default.Security
            ) {
                SettingsActionRow(
                    icon = Icons.Default.Timer,
                    iconColor = ElectricBlue,
                    title = "Chat Grace Period",
                    subtitle = "Keep in-memory chat when app is in background: ${state.chatGracePeriod}",
                    onClick = { showGracePeriodDialog = true }
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsActionRow(
                    icon = Icons.Default.DeleteSweep,
                    iconColor = WarningOrange,
                    title = "Clear File Index",
                    subtitle = "Remove catalog database (your local files are untouched)",
                    onClick = { showClearIndexDialog = true }
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsActionRow(
                    icon = Icons.Default.ChatBubbleOutline,
                    iconColor = ErrorRed,
                    title = "Clear Current Chat",
                    subtitle = "Immediately discard active conversation from memory",
                    onClick = { showClearChatDialog = true }
                )
            }

            // ── 5. About Docsphere ─────────────────────────────────────
            SettingsSection(
                title = "ABOUT DOCSPHERE",
                icon = Icons.Default.Info
            ) {
                DocSphereBrandCard(height = 135.dp)

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsInfoRow(
                    icon = Icons.Default.Apps,
                    iconColor = ElectricBlue,
                    title = "App Version",
                    value = "Docsphere v1.0 (Release)"
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsInfoRow(
                    iconDrawable = R.drawable.ic_agent_robot,
                    title = "Active AI Model",
                    value = state.selectedModel.displayName
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsInfoRow(
                    icon = Icons.Default.Lock,
                    iconColor = NeonCyan,
                    title = "AI Architecture",
                    value = "Local Vector Store + On-Device LLM"
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                SettingsInfoRow(
                    icon = Icons.Default.Security,
                    iconColor = AmberGold,
                    title = "Privacy Standard",
                    value = "Local-First • Zero Cloud Retention"
                )
            }
        }
    }

    // Grace Period Dialog
    if (showGracePeriodDialog) {
        val gracePeriodOptions = listOf("Immediately", "2 minutes", "10 minutes")
        AlertDialog(
            onDismissRequest = { showGracePeriodDialog = false },
            containerColor = NavyDark,
            title = { Text("Chat Grace Period", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Chats are kept only in memory and never saved to storage. Choose how long the current conversation is preserved when leaving the app:",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    gracePeriodOptions.forEach { option ->
                        val isSelected = state.chatGracePeriod == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ElectricBlue.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    viewModel.setChatGracePeriod(option)
                                    showGracePeriodDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setChatGracePeriod(option)
                                    showGracePeriodDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ElectricBlue,
                                    unselectedColor = TextMuted
                                )
                            )
                            Text(
                                text = if (option == "2 minutes") "$option (Default)" else option,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGracePeriodDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }

    // Clear Index Dialog
    if (showClearIndexDialog) {
        AlertDialog(
            onDismissRequest = { showClearIndexDialog = false },
            containerColor = NavyDark,
            title = { Text("Clear Document Index?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all cataloged documents from the database. Your actual files on storage will not be deleted, and you can re-scan anytime.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearIndex(); showClearIndexDialog = false }) {
                    Text("Clear Index", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearIndexDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Clear Chat Dialog
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            containerColor = NavyDark,
            title = { Text("Clear Current Chat?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("All current conversation messages will be discarded from memory, and the model session will be reset.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearChatHistory(); showClearChatDialog = false }) {
                    Text("Clear Chat", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector? = null,
    iconDrawable: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (iconDrawable != null) {
                Image(
                    painter = painterResource(id = iconDrawable),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = ElectricBlue
            )
        }
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LlmModelCard(
    model: LlmModel,
    downloadState: ModelDownloadState?,
    isSelected: Boolean,
    hasHfToken: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDownloadCellular: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onAddToken: () -> Unit
) {
    val isInstalled = downloadState?.isDownloaded == true || model.isBuiltIn
    val isDownloading = downloadState?.isDownloading == true
    val isRunnable = downloadState?.isRunnable != false
    val incompatibilityReason = downloadState?.incompatibilityReason

    val borderColor = when {
        isSelected -> ElectricBlue
        isInstalled -> TealEmerald.copy(alpha = 0.5f)
        !isRunnable -> WarningOrange.copy(alpha = 0.4f)
        else -> GlassBorder
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .background(
                if (isSelected) ElectricBlue.copy(alpha = 0.08f)
                else NavyMedium.copy(alpha = 0.25f)
            )
            .then(if (isInstalled) Modifier.clickable(onClick = onSelect) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header Row: fixed 40dp logo + Column(weight 1f) with name (titleMedium, maxLines 2) + subtitle (bodySmall, grey)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = model.iconRes),
                contentDescription = model.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(min = 36.dp, max = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = cleanModelSubtitle(model),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 2. Badges in a FlowRow under the header (FREE / GATED / RECOMMENDED / size like "3.7 GB")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSelected) {
                ModelBadge(text = "ACTIVE", color = ElectricBlue)
            } else if (isInstalled) {
                ModelBadge(text = "READY", color = TealEmerald)
            }

            if (model.isRecommended) {
                ModelBadge(text = "RECOMMENDED", color = ElectricBlue)
            }

            if (model.isGated) {
                ModelBadge(text = "GATED", color = AmberGold)
            } else if (model.isReasoning) {
                ModelBadge(text = "REASONING", color = Color(0xFFBA68C8))
            } else if (!model.isBuiltIn) {
                ModelBadge(text = "FREE", color = TealEmerald)
            } else {
                ModelBadge(text = "BUILT-IN", color = TealEmerald)
            }

            if (!model.isBuiltIn) {
                ModelBadge(text = model.sizeLabel, color = TextMuted)
            }
        }

        // 3. Description text (bodyMedium) with no extra blank space above or below it
        Text(
            text = cleanModelDescription(model),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            lineHeight = 20.sp
        )

        // 4. Incompatibility Notice if device RAM is insufficient
        if (!isRunnable && incompatibilityReason != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(WarningOrange.copy(alpha = 0.12f))
                    .border(1.dp, WarningOrange.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$incompatibilityReason. Performance may be degraded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningOrange
                    )
                }
            }
        }

        // Visible Download Error & Resolution Banner
        val statusMsg = downloadState?.statusText.orEmpty().trim()
        if (!isDownloading && !isInstalled && statusMsg.isNotBlank() && statusMsg != "INSTALLED" && statusMsg != "CANCELLED" && !statusMsg.startsWith("Requires")) {
            val isWifiError = statusMsg.contains("Wi-Fi", ignoreCase = true)
            val isTokenError = statusMsg.contains("Token", ignoreCase = true) || statusMsg.contains("authorization", ignoreCase = true)
            val isLicenseError = statusMsg.contains("403") || statusMsg.contains("license", ignoreCase = true) || statusMsg.contains("accepted", ignoreCase = true) || statusMsg.contains("model page", ignoreCase = true)
            val bannerColor = if (isWifiError) TealEmerald else ErrorRed
            val uriHandler = LocalUriHandler.current

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = bannerColor.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isWifiError -> Icons.Default.WifiOff
                                isTokenError || isLicenseError -> Icons.Default.Key
                                else -> Icons.Default.ErrorOutline
                            },
                            contentDescription = null,
                            tint = bannerColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = statusMsg.removePrefix("FAILED: ").trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isWifiError) {
                        Button(
                            onClick = onDownloadCellular,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.align(Alignment.End).height(36.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Download with Mobile Data", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    } else if (isLicenseError && model.modelWebUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                try {
                                    uriHandler.openUri(model.modelWebUrl)
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.align(Alignment.End).height(36.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = NavyDark)
                            Spacer(Modifier.width(6.dp))
                            Text("Open Model Page", style = MaterialTheme.typography.labelSmall, color = NavyDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    } else if (isTokenError && !hasHfToken) {
                        Button(
                            onClick = onAddToken,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.align(Alignment.End).height(36.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp), tint = NavyDark)
                            Spacer(Modifier.width(6.dp))
                            Text("Add HF Token", style = MaterialTheme.typography.labelSmall, color = NavyDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Download progress bar & Cancel action
        if (isDownloading) {
            val progress = downloadState?.downloadProgress ?: 0f
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ElectricBlue,
                    trackColor = NavyMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = downloadState?.statusText ?: "Downloading…",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel", color = ErrorRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 5. Action buttons (full width, fixed 48 dp height)
        if (!isDownloading) {
            val needsToken = model.isGated && !hasHfToken && !isInstalled

            if (isSelected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricBlue.copy(alpha = 0.15f))
                        .border(1.dp, ElectricBlue.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Active Model", color = ElectricBlue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            } else if (isInstalled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onSelect,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isRunnable) WarningOrange else ElectricBlue
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_agent_robot),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (!isRunnable) "Use Anyway (Low RAM)" else "Use This Model",
                            color = if (!isRunnable) Color.Black else Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!model.isBuiltIn) {
                        OutlinedIconButton(
                            onClick = onDelete,
                            colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Model", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            } else if (needsToken) {
                Button(
                    onClick = onAddToken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Add token to download",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (!model.isBuiltIn) {
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isRunnable) WarningOrange else TealEmerald
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (!isRunnable) Color.Black else Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (!isRunnable) "Download Anyway" else "Download",
                        color = if (!isRunnable) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelBadge(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

private fun cleanModelSubtitle(model: LlmModel): String = when (model) {
    LlmModel.BUILT_IN -> "Built-in · Always Available"
    LlmModel.GEMMA_4_E2B -> "LiteRT-LM · Balanced 2.6 GB"
    LlmModel.GEMMA_4_E4B -> "LiteRT-LM · High Quality"
    LlmModel.GEMMA_3N_E2B -> "LiteRT-LM · Multimodal"
    LlmModel.GEMMA_3N_E4B -> "LiteRT-LM · Multimodal High-Fi"
    LlmModel.GEMMA3_1B -> "General chat & conversation · 584 MB"
    LlmModel.QWEN2_5_1_5B -> "LiteRT-LM · Multilingual"
    LlmModel.DEEPSEEK_R1_1_5B -> "LiteRT-LM · Reasoning, slower"
}

private fun cleanModelDescription(model: LlmModel): String {
    return model.description
        .replace("Gated model requiring Hugging Face token.", "")
        .replace("Gated model requiring Hugging Face token", "")
        .trim()
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                softWrap = true
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                softWrap = true
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ElectricBlue,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = NavyMedium
            )
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                softWrap = true
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                softWrap = true
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector? = null,
    iconDrawable: Int? = null,
    iconColor: Color = ElectricBlue,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (iconDrawable != null) {
            Image(
                painter = painterResource(id = iconDrawable),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        } else if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                softWrap = true
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                softWrap = true
            )
        }
    }
}

@Preview(name = "Settings - 360dp Font 1.0", widthDp = 360, fontScale = 1.0f, showBackground = true)
@Composable
private fun SettingsPreview360Normal() {
    DocsphereTheme {
        Surface(color = NavyDeep) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LlmModelCard(
                    model = LlmModel.GEMMA_4_E2B,
                    downloadState = null,
                    isSelected = true,
                    hasHfToken = true,
                    onSelect = {},
                    onDownload = {},
                    onDownloadCellular = {},
                    onCancel = {},
                    onDelete = {},
                    onAddToken = {}
                )
                LlmModelCard(
                    model = LlmModel.GEMMA3_1B,
                    downloadState = null,
                    isSelected = false,
                    hasHfToken = false,
                    onSelect = {},
                    onDownload = {},
                    onDownloadCellular = {},
                    onCancel = {},
                    onDelete = {},
                    onAddToken = {}
                )
                SettingsInfoRow(
                    icon = Icons.Default.Lock,
                    iconColor = NeonCyan,
                    title = "AI Architecture",
                    value = "Local Vector Store + On-Device LLM"
                )
                SettingsInfoRow(
                    icon = Icons.Default.Security,
                    iconColor = AmberGold,
                    title = "Privacy Standard",
                    value = "Local-First • Zero Cloud Retention"
                )
            }
        }
    }
}

@Preview(name = "Settings - 360dp Font 1.3", widthDp = 360, fontScale = 1.3f, showBackground = true)
@Composable
private fun SettingsPreview360LargeFont() {
    DocsphereTheme {
        Surface(color = NavyDeep) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LlmModelCard(
                    model = LlmModel.GEMMA_4_E2B,
                    downloadState = null,
                    isSelected = true,
                    hasHfToken = true,
                    onSelect = {},
                    onDownload = {},
                    onDownloadCellular = {},
                    onCancel = {},
                    onDelete = {},
                    onAddToken = {}
                )
                LlmModelCard(
                    model = LlmModel.GEMMA3_1B,
                    downloadState = null,
                    isSelected = false,
                    hasHfToken = false,
                    onSelect = {},
                    onDownload = {},
                    onDownloadCellular = {},
                    onCancel = {},
                    onDelete = {},
                    onAddToken = {}
                )
                SettingsInfoRow(
                    icon = Icons.Default.Lock,
                    iconColor = NeonCyan,
                    title = "AI Architecture",
                    value = "Local Vector Store + On-Device LLM"
                )
                SettingsInfoRow(
                    icon = Icons.Default.Security,
                    iconColor = AmberGold,
                    title = "Privacy Standard",
                    value = "Local-First • Zero Cloud Retention"
                )
            }
        }
    }
}

@Preview(name = "Settings - 360dp Light Theme", widthDp = 360, fontScale = 1.0f, showBackground = true)
@Composable
private fun SettingsPreview360Light() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = GoogleBackgroundLight,
            surface = GoogleSurfaceLight,
            onSurface = Color(0xFF202124)
        )
    ) {
        Surface(color = GoogleBackgroundLight) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LlmModelCard(
                    model = LlmModel.GEMMA_4_E2B,
                    downloadState = null,
                    isSelected = true,
                    hasHfToken = true,
                    onSelect = {},
                    onDownload = {},
                    onDownloadCellular = {},
                    onCancel = {},
                    onDelete = {},
                    onAddToken = {}
                )
                SettingsInfoRow(
                    icon = Icons.Default.Security,
                    iconColor = AmberGold,
                    title = "Privacy Standard",
                    value = "Local-First • Zero Cloud Retention"
                )
            }
        }
    }
}

