package com.docsphere.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docsphere.data.model.ChatMessage
import com.docsphere.data.model.MessageRole
import com.docsphere.data.model.RetrievedChunk
import com.docsphere.data.model.RoutingDecision
import com.docsphere.ui.theme.*
import com.docsphere.R

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Sleek, professional muted colors instead of over-bright neons
val DecoxPrimaryBlue = Color(0xFFA8C7FA)
val WorkspaceUserBubble = Color(0xFF28362D)
val WorkspaceTextPrimary = Color(0xFFE3E3E3)
val WorkspaceSourceCardBg = Color(0xFF212822)
val WorkspaceActionIconColor = Color(0xFF8E918F)
val WorkspaceMutedGreen = Color(0xFF81A290)

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onCitationClick: (String) -> Unit = {},
    onLookupSensitiveField: (suspend (documentId: String, fieldName: String) -> String?)? = null,
    showFullSensitiveValues: Boolean = true
) {
    val isUser = message.role == MessageRole.USER
    val context = LocalContext.current

    if (isUser) {
        // ── USER MESSAGE: Right-aligned Workspace Dark Green Pill ──
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(WorkspaceUserBubble)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = WorkspaceTextPrimary
                )
            }
        }
    } else {
        // ── GEMINI ASSISTANT MESSAGE: Seamless on background with Sparkle icon ──
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Assistant Avatar: Unified Docsphere AI Logo
            val modelIcon = R.drawable.docsphere_ai_logo
            Image(
                painter = painterResource(id = modelIcon),
                contentDescription = "AI Assistant",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            // Message Body (Markdown with interactive sensitive field cards)
            MessageBodyWithSensitiveContent(
                content = message.content,
                isStreaming = message.isStreaming,
                onLookupFullValue = onLookupSensitiveField,
                showFullSensitiveValues = showFullSensitiveValues
            )

            val isErrorMessage = message.content.startsWith("❌") ||
                    message.content.startsWith("⚠️") ||
                    message.content.startsWith("⏱️") ||
                    message.content.startsWith("Model error:", ignoreCase = true)

            // ── Item 4: Sources row of up to 3 tappable chips with document names actually used in the prompt ──
            // Requirement B.4: Hide Sources chips on error messages
            if (message.citations.isNotEmpty() && !isErrorMessage) {
                val distinctCitations = message.citations.distinctBy { it.documentId }.take(3)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sources:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8E918F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    distinctCitations.forEach { citation ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF202124),
                            border = BorderStroke(1.dp, Color(0xFF3C4043)),
                            modifier = Modifier.clickable { onCitationClick(citation.documentId) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = DecoxPrimaryBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = citation.documentName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE3E3E3),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Item 4: Response time in small grey text (UI only, never in saved or copied text) ──
            if (message.responseTimeMs != null) {
                Text(
                    text = "${message.responseTimeMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8E918F),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                )
            }

            // ── Bottom Action Bar (Thumbs Up, Thumbs Down, Report, Copy) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var liked by remember { mutableStateOf<Boolean?>(null) }

                IconButton(
                    onClick = { liked = if (liked == true) null else true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (liked == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Good response",
                        tint = if (liked == true) DecoxPrimaryBlue else WorkspaceActionIconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { liked = if (liked == false) null else false },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (liked == false) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Bad response",
                        tint = if (liked == false) GoogleRed else WorkspaceActionIconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        Toast.makeText(context, "Feedback noted for model evaluation", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Report issue",
                        tint = WorkspaceActionIconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val cleanText = message.content
                            .replace(Regex("<think>[\\s\\S]*?</think>"), "")
                            .replace(Regex("<think>[\\s\\S]*"), "")
                            .trim()
                            .ifEmpty { message.content }
                            .replace(Regex("\\[\\[SENSITIVE:(.*?):(.*?):(.*?)]]")) { m ->
                                val fieldName = m.groupValues[2]
                                val masked = m.groupValues[3]
                                val label = getSensitiveFieldLabel(fieldName)
                                "$label: $masked"
                            }
                        clipboard.setPrimaryClip(ClipData.newPlainText("DocSphere", cleanText))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy text",
                        tint = WorkspaceActionIconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Google Workspace style rounded source card with document/mail icon.
 */
@Composable
fun WorkspaceSourceCard(citation: RetrievedChunk, onClick: () -> Unit = {}) {
    val context = LocalContext.current
    val nameLower = citation.documentName.lowercase()
    val isMail = nameLower.contains("subject:") ||
                 nameLower.contains("name change") ||
                 nameLower.contains("account data") ||
                 nameLower.contains("@") ||
                 nameLower.contains("email")

    val (iconVector, iconColor) = when {
        isMail -> Icons.Default.Mail to Color(0xFFB06259)
        nameLower.endsWith(".pdf") -> Icons.Default.PictureAsPdf to Color(0xFFB06259)
        nameLower.endsWith(".xlsx") || nameLower.endsWith(".csv") -> Icons.Default.TableChart to Color(0xFF5E8B6D)
        nameLower.endsWith(".docx") || nameLower.endsWith(".doc") -> Icons.Default.Description to Color(0xFF5985C7)
        else -> Icons.Default.Description to DecoxPrimaryBlue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WorkspaceSourceCardBg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = citation.documentName,
            style = MaterialTheme.typography.bodyMedium,
            color = WorkspaceTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CitationChip(citation: RetrievedChunk, onClick: () -> Unit = {}) {
    WorkspaceSourceCard(citation = citation, onClick = onClick)
}

@Composable
fun RoutingBadge(decision: RoutingDecision) {
    val (label, color) = when (decision) {
        RoutingDecision.LOCAL_ONLY -> "⚡ Offline · Drive AI" to GoogleGreen
        RoutingDecision.CLOUD_BOOSTED -> "🌐 Online · Gemini Cloud" to GoogleBlue
        RoutingDecision.FUSED -> "✨ Fused · Gemini + Drive" to GooglePurple
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    val alpha1 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = 0), repeatMode = RepeatMode.Reverse), label = "dot1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = 200), repeatMode = RepeatMode.Reverse), label = "dot2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = 400), repeatMode = RepeatMode.Reverse), label = "dot3"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("●", style = MaterialTheme.typography.labelSmall, color = TextMuted.copy(alpha = alpha1))
        Text("●", style = MaterialTheme.typography.labelSmall, color = TextMuted.copy(alpha = alpha2))
        Text("●", style = MaterialTheme.typography.labelSmall, color = TextMuted.copy(alpha = alpha3))
    }
}

/**
 * Resolves user-friendly display labels for sensitive fields.
 */
fun getSensitiveFieldLabel(fieldName: String): String = when (fieldName.lowercase()) {
    "aadhaar" -> "Aadhaar Number"
    "pan" -> "PAN Card Number"
    "voter_id" -> "Voter ID / EPIC Number"
    "driving_licence" -> "Driving Licence Number"
    "passport" -> "Passport Number"
    "full_name" -> "Full Name"
    "address" -> "Address"
    "bank_account" -> "Bank Account Number"
    "ifsc" -> "IFSC Code"
    "invoice_total" -> "Total Amount Due"
    "date_of_birth" -> "Date of Birth"
    "invoice_date" -> "Invoice Date"
    "due_date" -> "Payment Due Date"
    "phone" -> "Phone Number"
    "email" -> "Email Address"
    else -> fieldName.replace('_', ' ').split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

/**
 * Copies a sensitive value to the Android clipboard.
 * On Android 13+ (API 33+), marks ClipDescription.EXTRA_IS_SENSITIVE = true.
 * Clears the clipboard automatically after ~60 seconds if it still holds our value.
 */
fun copySensitiveValueToClipboard(
    context: Context,
    label: String,
    fullValue: String,
    scope: CoroutineScope
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, fullValue)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()

    scope.launch(Dispatchers.Main) {
        delay(60_000L)
        try {
            val currentClip = clipboard.primaryClip
            if (currentClip != null && currentClip.itemCount > 0) {
                val currentText = currentClip.getItemAt(0)?.text?.toString()
                if (currentText == fullValue) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
        } catch (_: Throwable) {
            // Ignore clipboard access security exceptions on background
        }
    }
}

private val SENSITIVE_TAG_REGEX = Regex("""\[\[SENSITIVE:([^:]+):([^:]+):([^\]]+)]]""")

/**
 * Collapsible Thinking Process card for reasoning models like DeepSeek-R1.
 * Requirement A.2: DeepSeek-R1 prints reasoning inside <think>...</think>: hide that block from the answer (collapsible)
 */
@Composable
fun ThinkingProcessCard(
    thinkingText: String,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E2421),
        border = BorderStroke(1.dp, Color(0xFF334237)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = WorkspaceMutedGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isThinking) "Thinking..." else "Thinking Process",
                    style = MaterialTheme.typography.labelMedium,
                    color = WorkspaceMutedGreen,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = WorkspaceActionIconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = Color(0xFF334237), thickness = 0.5.dp, modifier = Modifier.padding(bottom = 8.dp))
                    Text(
                        text = thinkingText.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = WorkspaceTextPrimary.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * Parses message markdown and renders interactive sensitive field cards
 * for sensitive data (Aadhaar, PAN, IDs) with on-demand lookup, tap-to-reveal, and sensitive copy.
 */
@Composable
fun MessageBodyWithSensitiveContent(
    content: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    onLookupFullValue: (suspend (documentId: String, fieldName: String) -> String?)? = null,
    showFullSensitiveValues: Boolean = true
) {
    if (content.contains("<think>")) {
        val thinkStart = content.indexOf("<think>")
        val thinkEnd = content.indexOf("</think>")

        val (thinkingText, answerText, isStillThinking) = if (thinkEnd != -1) {
            val thought = content.substring(thinkStart + 7, thinkEnd).trim()
            val answer = (content.substring(0, thinkStart) + content.substring(thinkEnd + 8)).trim()
            Triple(thought, answer, false)
        } else {
            val thought = content.substring(thinkStart + 7).trim()
            val answer = content.substring(0, thinkStart).trim()
            Triple(thought, answer, isStreaming)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
            if (thinkingText.isNotBlank()) {
                ThinkingProcessCard(thinkingText = thinkingText, isThinking = isStillThinking)
            }
            if (answerText.isNotBlank()) {
                RenderBodyContent(
                    content = answerText,
                    isStreaming = isStreaming && !isStillThinking,
                    onLookupFullValue = onLookupFullValue,
                    showFullSensitiveValues = showFullSensitiveValues
                )
            } else if (isStillThinking) {
                TypingDots()
            }
        }
        return
    }

    RenderBodyContent(
        content = content,
        isStreaming = isStreaming,
        modifier = modifier,
        onLookupFullValue = onLookupFullValue,
        showFullSensitiveValues = showFullSensitiveValues
    )
}

@Composable
private fun RenderBodyContent(
    content: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    onLookupFullValue: (suspend (documentId: String, fieldName: String) -> String?)? = null,
    showFullSensitiveValues: Boolean = true
) {
    if (isStreaming) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                color = WorkspaceTextPrimary
            )
            TypingDots()
        }
    } else {
        MarkdownText(markdown = content, color = WorkspaceTextPrimary, modifier = modifier)
    }
}

/**
 * Interactive card for sensitive fields (Aadhaar, PAN, Passport, DL, etc.):
 * - Contains ONLY a reference (documentId, fieldName, maskedValue) - never full value in message
 * - When showFullSensitiveValues is ON, displays full value directly with copy button
 * - Clears full value from Compose state immediately when user taps Hide
 * - Sensitive clipboard marking (Android 13+) and 60-second auto-clear
 */
@Composable
fun SensitiveValueCard(
    documentId: String,
    fieldName: String,
    maskedValue: String,
    modifier: Modifier = Modifier,
    onLookupFullValue: (suspend (documentId: String, fieldName: String) -> String?)? = null,
    showFullSensitiveValues: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val label = remember(fieldName) { getSensitiveFieldLabel(fieldName) }
    var isRevealed by remember(showFullSensitiveValues) { mutableStateOf(showFullSensitiveValues) }
    var revealedValue by remember { mutableStateOf<String?>(null) }
    var isActionLoading by remember { mutableStateOf(false) }

    LaunchedEffect(documentId, fieldName, showFullSensitiveValues) {
        if (showFullSensitiveValues && revealedValue == null && onLookupFullValue != null) {
            try {
                isActionLoading = true
                val full = onLookupFullValue(documentId, fieldName)
                if (full != null) {
                    revealedValue = full
                    isRevealed = true
                }
            } catch (_: Throwable) {
            } finally {
                isActionLoading = false
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E2822),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF35483C)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Encrypted / Sensitive",
                    tint = DecoxPrimaryBlue,
                    modifier = Modifier.size(18.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = WorkspaceActionIconColor
                    )
                    Text(
                        text = if (isRevealed && revealedValue != null) revealedValue!! else maskedValue,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        ),
                        color = WorkspaceTextPrimary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Tap-to-reveal toggle button (if full sensitive values setting is OFF or to hide)
                IconButton(
                    onClick = {
                        if (isRevealed) {
                            isRevealed = false
                        } else {
                            if (revealedValue != null) {
                                isRevealed = true
                            } else if (onLookupFullValue != null) {
                                isActionLoading = true
                                scope.launch {
                                    try {
                                        val full = onLookupFullValue(documentId, fieldName)
                                        if (full != null) {
                                            revealedValue = full
                                            isRevealed = true
                                        } else {
                                            Toast.makeText(context, "Field value not found in document", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Throwable) {
                                        Toast.makeText(context, "Failed to load field value", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isActionLoading = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Lookup not available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isActionLoading && !isRevealed) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = WorkspaceMutedGreen
                        )
                    } else {
                        Icon(
                            imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isRevealed) "Hide value" else "Reveal value",
                            tint = WorkspaceMutedGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Copy button (looks up on demand, marks sensitive on Android 13+, clears clipboard in 60s)
                IconButton(
                    onClick = {
                        if (revealedValue != null) {
                            copySensitiveValueToClipboard(context, label, revealedValue!!, scope)
                        } else if (onLookupFullValue != null) {
                            scope.launch {
                                try {
                                    val full = onLookupFullValue(documentId, fieldName)
                                    if (full != null) {
                                        revealedValue = full
                                        copySensitiveValueToClipboard(context, label, full, scope)
                                    } else {
                                        Toast.makeText(context, "Field value not found in document", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Throwable) {
                                    Toast.makeText(context, "Failed to copy field value", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Lookup not available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy $label",
                        tint = WorkspaceActionIconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
