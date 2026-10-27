package com.docsphere.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import com.docsphere.ai.offline.LlmModel
import com.docsphere.ai.offline.ModelConfig
import com.docsphere.data.model.ChatMessage
import com.docsphere.data.model.MessageRole
import com.docsphere.ui.components.*
import com.docsphere.ui.theme.*
import com.docsphere.R

private val suggestedPrompts = listOf(
    "Summarize my documents",
    "What files do I have?",
    "Draft a professional email summary",
    "Extract all dates and financial figures",
    "Compare my documents",
    "What can you do?"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onOpenFile: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message or streaming chunk
    LaunchedEffect(state.messages.size, state.streamingMessage) {
        val totalCount = state.messages.size + (if (state.streamingMessage != null || state.isLoading) 1 else 0)
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    val dynamicTitle = remember(state.messages) {
        state.messages.firstOrNull { it.role == MessageRole.USER }?.content?.trim()?.take(20) ?: "Gemini"
    }

    var showModelSheet by remember { mutableStateOf(false) }
    var showModelSettingsSheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131314)) // Google Workspace dark canvas
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Google Workspace Header with Model Selector & New Chat ──
            ChatHeader(
                selectedModel = state.selectedModel,
                isPersonalIntelligence = state.isPersonalIntelligence,
                onTogglePersonalIntelligence = { viewModel.togglePersonalIntelligence() },
                onOpenModelPicker = { showModelSheet = true },
                onOpenModelSettings = { showModelSettingsSheet = true },
                onNewChat = { viewModel.newSession() }
            )

            // Warm-up loading indicator with elapsed-time counter & Cancel button (Requirement 3)
            if (state.isModelLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1F20))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = ElectricBlue
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = state.modelLoadingMessage ?: "Loading model...",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = { viewModel.cancelModelLoading() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoogleRedDark
                        )
                    }
                }
            }

            // GPU Fallback Notice (Requirement 1)
            state.gpuFallbackNotice?.let { notice ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberGold.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE3E3E3),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissGpuNotice() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // ── Messages Area ──────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                if (state.messages.isEmpty()) {
                    ChatEmptyState(
                        isPersonalIntelligence = state.isPersonalIntelligence,
                        onSuggestedPrompt = { prompt ->
                            viewModel.onInputChange(prompt)
                            viewModel.sendMessage()
                        }
                    )
                } else {
                    val onLookupField = remember(viewModel) { viewModel::getFieldValue }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = state.messages,
                            key = { it.id },
                            contentType = { it.role }
                        ) { message ->
                            ChatBubble(
                                message = message,
                                onCitationClick = { docId ->
                                    viewModel.openDocument(context, docId)
                                    onOpenFile(docId)
                                },
                                onLookupSensitiveField = onLookupField,
                                showFullSensitiveValues = state.showFullSensitiveValues
                            )
                        }
                        if (state.streamingMessage != null || (state.isLoading && state.streamingSources.isNotEmpty())) {
                            item(key = "streaming_preview", contentType = "streaming") {
                                ChatBubble(
                                    message = ChatMessage(
                                        id = "streaming_preview",
                                        sessionId = state.sessionId,
                                        role = MessageRole.ASSISTANT,
                                        content = state.streamingMessage ?: "",
                                        routingDecision = state.routingDecision,
                                        citations = state.streamingSources,
                                        isStreaming = true
                                    ),
                                    onCitationClick = { docId ->
                                        viewModel.openDocument(context, docId)
                                        onOpenFile(docId)
                                    },
                                    onLookupSensitiveField = onLookupField,
                                    showFullSensitiveValues = state.showFullSensitiveValues
                                )
                            }
                        } else if (state.isWaitingForModel) {
                            item(key = "waiting_for_model", contentType = "waiting") {
                                Row(
                                    modifier = Modifier
                                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                                        .background(Color(0xFF1E1F20), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = ElectricBlue
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Waiting for model to load...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFAAAAAA)
                                    )
                                }
                            }
                        } else if (state.isLoading) {
                            item(key = "loading_bubble", contentType = "loading") {
                                LoadingBubble()
                            }
                        }
                    }
                }
            }

            // ── Workspace Pill Input Bar (+  Enter a prompt here  ↑/⏹) ────
            ChatInputBar(
                value = state.inputText,
                onValueChange = viewModel::onInputChange,
                onSend = { viewModel.sendMessage() },
                onStop = { viewModel.stopGeneration() },
                isLoading = state.isLoading
            )
        }

        // ── Model Selection Bottom Sheet (Downloaded & Available Models Only) ──
        if (showModelSheet) {
            val availableModels = remember(state.allModelStates) {
                LlmModel.entries.filter { model ->
                    model.isBuiltIn || (state.allModelStates[model]?.isDownloaded == true && state.allModelStates[model]?.isRunnable != false)
                }
            }

            ModalBottomSheet(
                onDismissRequest = { showModelSheet = false },
                containerColor = Color(0xFF1E1F20),
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF5F6368)) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select AI Model",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFE3E3E3),
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showModelSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF3C4043), modifier = Modifier.padding(bottom = 4.dp))

                    availableModels.forEach { model ->
                        val isSelected = state.selectedModel == model

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) ElectricBlue.copy(alpha = 0.15f)
                                    else Color(0xFF282A2C)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) ElectricBlue else Color(0xFF3C4043),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.selectModel(model)
                                    showModelSheet = false
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Image(
                                painter = painterResource(id = model.iconRes),
                                contentDescription = model.displayName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF202124))
                                    .padding(3.dp)
                            )
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) Color.White else Color(0xFFE3E3E3),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Model Settings Bottom Sheet (Sliders Icon like Gallery) ──
        if (showModelSettingsSheet) {
            ModelSettingsBottomSheet(
                model = state.selectedModel,
                config = state.currentModelConfig,
                onDismiss = { showModelSettingsSheet = false },
                onSaveConfig = { newCfg -> viewModel.updateModelConfig(newCfg) },
                onResetToDefaults = { viewModel.resetModelConfigToDefaults() }
            )
        }
    }
}

@Composable
private fun ChatHeader(
    selectedModel: LlmModel,
    isPersonalIntelligence: Boolean,
    onTogglePersonalIntelligence: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenModelSettings: () -> Unit,
    onNewChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Model Selection Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF202124))
                    .border(1.dp, Color(0xFF3C4043), RoundedCornerShape(20.dp))
                    .clickable { onOpenModelPicker() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(id = selectedModel.iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = selectedModel.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFE3E3E3),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }

            // Small "Personal Intelligence: off" label with quick toggle (Section B.2)
            if (!isPersonalIntelligence) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF282A2C))
                        .border(1.dp, AmberGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable { onTogglePersonalIntelligence() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Personal Intelligence: off",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sliders icon in the chat top bar, like Gallery (Section A.5)
            IconButton(
                onClick = onOpenModelSettings,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF202124))
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Model settings",
                    tint = Color(0xFFE3E3E3),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Action Button: New Chat
            IconButton(
                onClick = onNewChat,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF202124))
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = "New Chat",
                    tint = Color(0xFFE3E3E3),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatEmptyState(
    isPersonalIntelligence: Boolean,
    onSuggestedPrompt: (String) -> Unit
) {
    val isKeyboardOpen = WindowInsets.isImeVisible
    val prompts = if (isPersonalIntelligence) {
        suggestedPrompts
    } else {
        listOf(
            "Draft a professional email",
            "Help me brainstorm ideas",
            "Tell me an interesting historical fact",
            "Write a creative short story",
            "Explain quantum computing simply",
            "What can you do?"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = if (isKeyboardOpen) 8.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isKeyboardOpen) 10.dp else 24.dp)
    ) {
        if (!isKeyboardOpen) {
            Spacer(Modifier.height(32.dp))
        }
        Image(
            painter = painterResource(id = R.drawable.docsphere_ai_logo),
            contentDescription = "Docsphere AI",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(if (isKeyboardOpen) 36.dp else 54.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Ask Docsphere AI",
                style = if (isKeyboardOpen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                color = Color(0xFFE3E3E3),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (!isKeyboardOpen) {
                Text(
                    text = if (isPersonalIntelligence) {
                        "Search files, summarize documents, and ask targeted questions."
                    } else {
                        "On-device AI assistant. Fast, private, and runs 100% locally."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8E918F),
                    textAlign = TextAlign.Center
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prompts.take(if (isKeyboardOpen) 2 else 6).forEach { prompt ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1F20))
                        .clickable { onSuggestedPrompt(prompt) }
                        .padding(horizontal = 16.dp, vertical = if (isKeyboardOpen) 10.dp else 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.docsphere_ai_logo),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(prompt, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE3E3E3))
                    }
                }
            }
        }
        if (isKeyboardOpen) {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSettingsBottomSheet(
    model: LlmModel,
    config: ModelConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (ModelConfig) -> Unit,
    onResetToDefaults: () -> Unit
) {
    var accelerator by remember(config) { mutableStateOf(config.accelerator) }
    var maxTokens by remember(config) { mutableStateOf(config.maxTokens) }
    var temperature by remember(config) { mutableStateOf(config.temperature) }
    var topK by remember(config) { mutableStateOf(config.topK) }
    var topP by remember(config) { mutableStateOf(config.topP) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1F20),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF5F6368)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Model Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE3E3E3),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricBlue
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            HorizontalDivider(color = Color(0xFF3C4043))

            // Accelerator (GPU/CPU)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Accelerator",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFE3E3E3),
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("GPU", "CPU").forEach { acc ->
                        val isSelected = accelerator.equals(acc, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ElectricBlue.copy(alpha = 0.2f) else Color(0xFF282A2C))
                                .border(1.dp, if (isSelected) ElectricBlue else Color(0xFF3C4043), RoundedCornerShape(8.dp))
                                .clickable {
                                    accelerator = acc
                                    onSaveConfig(config.copy(accelerator = acc, maxTokens = maxTokens, temperature = temperature, topK = topK, topP = topP))
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (acc == "GPU") "GPU (OpenCL)" else "CPU",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) ElectricBlue else Color(0xFFE3E3E3),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                if (config.gpuFailed) {
                    Text(
                        text = "Note: GPU acceleration previously failed on this device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold
                    )
                }
            }

            // Max Tokens
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Max Tokens", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE3E3E3))
                    Text("$maxTokens", style = MaterialTheme.typography.bodyMedium, color = ElectricBlue, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = {
                        maxTokens = ((it / 256).toInt() * 256).coerceIn(512, 4096)
                    },
                    onValueChangeFinished = {
                        onSaveConfig(config.copy(accelerator = accelerator, maxTokens = maxTokens, temperature = temperature, topK = topK, topP = topP))
                    },
                    valueRange = 512f..4096f,
                    steps = 13,
                    colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                )
            }

            // Temperature
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Temperature", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE3E3E3))
                    Text(String.format(Locale.US, "%.2f", temperature), style = MaterialTheme.typography.bodyMedium, color = ElectricBlue, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = temperature,
                    onValueChange = {
                        temperature = String.format(Locale.US, "%.2f", it).toFloat()
                    },
                    onValueChangeFinished = {
                        onSaveConfig(config.copy(accelerator = accelerator, maxTokens = maxTokens, temperature = temperature, topK = topK, topP = topP))
                    },
                    valueRange = 0.0f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                )
            }

            // Top-K
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Top-K", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE3E3E3))
                    Text("$topK", style = MaterialTheme.typography.bodyMedium, color = ElectricBlue, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = topK.toFloat(),
                    onValueChange = {
                        topK = it.toInt()
                    },
                    onValueChangeFinished = {
                        onSaveConfig(config.copy(accelerator = accelerator, maxTokens = maxTokens, temperature = temperature, topK = topK, topP = topP))
                    },
                    valueRange = 1f..100f,
                    steps = 98,
                    colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                )
            }

            // Top-P
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Top-P", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE3E3E3))
                    Text(String.format(Locale.US, "%.2f", topP), style = MaterialTheme.typography.bodyMedium, color = ElectricBlue, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = topP,
                    onValueChange = {
                        topP = String.format(Locale.US, "%.2f", it).toFloat()
                    },
                    onValueChangeFinished = {
                        onSaveConfig(config.copy(accelerator = accelerator, maxTokens = maxTokens, temperature = temperature, topK = topK, topP = topP))
                    },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                )
            }

            HorizontalDivider(color = Color(0xFF3C4043), modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onResetToDefaults,
                    border = BorderStroke(1.dp, Color(0xFF5F6368)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE3E3E3))
                ) {
                    Text("Reset to defaults")
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Done", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LoadingBubble() {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.docsphere_ai_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = Color(0xFFA8C7FA),
            strokeWidth = 2.dp
        )
        Text(
            text = "Thinking…",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E918F)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isLoading: Boolean
) {
    val isKeyboardOpen = WindowInsets.isImeVisible
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Pill container matching Google Workspace input box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1E1F20))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFE3E3E3)),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            "Enter a prompt here",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF8E918F)
                        )
                    }
                    innerTextField()
                },
                maxLines = 4
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isLoading) Color(0xFFD93025)
                        else if (value.isNotBlank()) Color(0xFF37393B)
                        else Color(0xFF282A2C)
                    )
                    .clickable(enabled = isLoading || value.isNotBlank()) {
                        if (isLoading) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generation",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (value.isNotBlank()) Color(0xFFE3E3E3) else Color(0xFF6E716F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bottom Disclaimer - hidden when keyboard is open
        if (!isKeyboardOpen) {
            Text(
                text = "Docsphere AI can make mistakes. Learn more",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E918F),
                fontSize = 11.sp
            )
        }
    }
}
