package com.docsphere.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docsphere.ui.theme.*

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String = "") : MarkdownBlock()
}

/**
 * High-performance markdown renderer for chat bubbles.
 * Parses headers, bold (**text**), italics (*text*), inline code (`code`),
 * bullet lists, numbered lists, and code blocks.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Header -> {
                    Text(
                        text = block.text,
                        fontSize = when (block.level) {
                            1 -> 18.sp
                            2 -> 16.sp
                            else -> 15.sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlueLight,
                        lineHeight = 22.sp
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = formatInlineMarkdown(block.text, color),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = color
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text("•", color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = formatInlineMarkdown(block.text, color),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.NumberedItem -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text("${block.number}.", color = TealEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = formatInlineMarkdown(block.text, color),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NavyDark.copy(alpha = 0.9f))
                            .padding(10.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = NeonCyan,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

fun formatInlineMarkdown(input: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = input.length
        while (i < len) {
            when {
                // **bold**
                i + 1 < len && input[i] == '*' && input[i + 1] == '*' -> {
                    val end = input.indexOf("**", i + 2)
                    if (end != -1) {
                        val boldText = input.substring(i + 2, end)
                        val startIdx = length
                        append(boldText)
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, color = Color.White),
                            startIdx,
                            length
                        )
                        i = end + 2
                    } else {
                        append(input[i])
                        i++
                    }
                }
                // *italic*
                input[i] == '*' -> {
                    val end = input.indexOf('*', i + 1)
                    if (end != -1 && end > i + 1) {
                        val italicText = input.substring(i + 1, end)
                        val startIdx = length
                        append(italicText)
                        addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor),
                            startIdx,
                            length
                        )
                        i = end + 1
                    } else {
                        append(input[i])
                        i++
                    }
                }
                // `code`
                input[i] == '`' -> {
                    val end = input.indexOf('`', i + 1)
                    if (end != -1 && end > i + 1) {
                        val codeText = input.substring(i + 1, end)
                        val startIdx = length
                        append(codeText)
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = NavySurface.copy(alpha = 0.5f),
                                color = NeonCyan
                            ),
                            startIdx,
                            length
                        )
                        i = end + 1
                    } else {
                        append(input[i])
                        i++
                    }
                }
                else -> {
                    append(input[i])
                    i++
                }
            }
        }
    }
}

fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var inCodeBlock = false
    val codeBuilder = StringBuilder()
    var codeLang = ""

    for (rawLine in lines) {
        val line = rawLine.trimEnd()
        if (line.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeBuilder.toString().trimEnd(), codeLang))
                codeBuilder.clear()
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLang = line.removePrefix("```").trim()
            }
            continue
        }

        if (inCodeBlock) {
            codeBuilder.append(rawLine).append("\n")
            continue
        }

        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        when {
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ") -> {
                val bulletContent = trimmed.substring(2).trim()
                blocks.add(MarkdownBlock.BulletItem(bulletContent))
            }
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val num = trimmed.substringBefore('.')
                val content = trimmed.substringAfter('.').trim()
                blocks.add(MarkdownBlock.NumberedItem(num, content))
            }
            else -> {
                blocks.add(MarkdownBlock.Paragraph(line))
            }
        }
    }

    if (inCodeBlock && codeBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeBuilder.toString().trimEnd(), codeLang))
    }

    return blocks
}
