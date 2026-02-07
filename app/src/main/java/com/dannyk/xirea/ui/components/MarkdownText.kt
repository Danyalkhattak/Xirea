package com.dannyk.xirea.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders Markdown-formatted text with proper styling.
 * Supports: code blocks, inline code, bold, italic, strikethrough, headers, links, lists.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    val elements = remember(text) { parseMarkdown(text) }
    
    SelectionContainer {
        Column(modifier = modifier) {
            elements.forEach { element ->
                when (element) {
                    is MarkdownElement.CodeBlock -> {
                        CodeBlockView(
                            code = element.code,
                            language = element.language,
                            context = context
                        )
                    }
                    is MarkdownElement.Text -> {
                        Text(
                            text = buildStyledText(element.content, color, style),
                            style = style
                        )
                    }
                    is MarkdownElement.Header -> {
                        HeaderText(level = element.level, text = element.text, color = color)
                    }
                    is MarkdownElement.ListItem -> {
                        ListItemView(text = element.text, color = color, style = style)
                    }
                    is MarkdownElement.Divider -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = color.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(
    code: String,
    language: String?,
    context: Context
) {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeTextColor = MaterialTheme.colorScheme.onSurface
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(codeBackground)
    ) {
        // Header with language and copy button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language?.lowercase() ?: "code",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            IconButton(
                onClick = { copyToClipboard(context, code, "Code copied!") },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Code content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = codeTextColor
            )
        }
    }
}

@Composable
private fun HeaderText(level: Int, text: String, color: Color) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        4 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    
    Text(
        text = text,
        style = style.copy(fontWeight = FontWeight.Bold),
        color = color,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun ListItemView(text: String, color: Color, style: TextStyle) {
    Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
        Text(
            text = "•  ",
            color = color,
            style = style.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = buildStyledText(text, color, style),
            style = style
        )
    }
}

/**
 * Builds an AnnotatedString with inline styling (bold, italic, code, etc.)
 */
private fun buildStyledText(text: String, color: Color, baseStyle: TextStyle): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        
        while (remaining.isNotEmpty()) {
            when {
                // Bold + Italic ***text***
                remaining.startsWith("***") -> {
                    val endIndex = remaining.indexOf("***", 3)
                    if (endIndex != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = color)) {
                            append(remaining.substring(3, endIndex))
                        }
                        remaining = remaining.substring(endIndex + 3)
                    } else {
                        append("***")
                        remaining = remaining.substring(3)
                    }
                }
                // Bold **text**
                remaining.startsWith("**") -> {
                    val endIndex = remaining.indexOf("**", 2)
                    if (endIndex != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                            append(remaining.substring(2, endIndex))
                        }
                        remaining = remaining.substring(endIndex + 2)
                    } else {
                        append("**")
                        remaining = remaining.substring(2)
                    }
                }
                // Italic *text* or _text_
                remaining.startsWith("*") || remaining.startsWith("_") -> {
                    val marker = remaining[0]
                    val endIndex = remaining.indexOf(marker, 1)
                    if (endIndex != -1 && endIndex > 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color)) {
                            append(remaining.substring(1, endIndex))
                        }
                        remaining = remaining.substring(endIndex + 1)
                    } else {
                        append(marker)
                        remaining = remaining.substring(1)
                    }
                }
                // Strikethrough ~~text~~
                remaining.startsWith("~~") -> {
                    val endIndex = remaining.indexOf("~~", 2)
                    if (endIndex != -1) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = color)) {
                            append(remaining.substring(2, endIndex))
                        }
                        remaining = remaining.substring(endIndex + 2)
                    } else {
                        append("~~")
                        remaining = remaining.substring(2)
                    }
                }
                // Inline code `code`
                remaining.startsWith("`") -> {
                    val endIndex = remaining.indexOf("`", 1)
                    if (endIndex != -1) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = color.copy(alpha = 0.1f),
                            color = color
                        )) {
                            append(" ${remaining.substring(1, endIndex)} ")
                        }
                        remaining = remaining.substring(endIndex + 1)
                    } else {
                        append("`")
                        remaining = remaining.substring(1)
                    }
                }
                // Regular character
                else -> {
                    val nextSpecial = findNextSpecialIndex(remaining)
                    if (nextSpecial > 0) {
                        withStyle(SpanStyle(color = color)) {
                            append(remaining.substring(0, nextSpecial))
                        }
                        remaining = remaining.substring(nextSpecial)
                    } else if (nextSpecial == -1) {
                        withStyle(SpanStyle(color = color)) {
                            append(remaining)
                        }
                        remaining = ""
                    } else {
                        withStyle(SpanStyle(color = color)) {
                            append(remaining[0])
                        }
                        remaining = remaining.substring(1)
                    }
                }
            }
        }
    }
}

private fun findNextSpecialIndex(text: String): Int {
    val markers = listOf("***", "**", "*", "_", "~~", "`")
    var minIndex = -1
    for (marker in markers) {
        val index = text.indexOf(marker)
        if (index != -1 && (minIndex == -1 || index < minIndex)) {
            minIndex = index
        }
    }
    return minIndex
}

/**
 * Parses markdown text into structured elements
 */
private fun parseMarkdown(text: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = text.split("\n")
    var i = 0
    
    while (i < lines.size) {
        val line = lines[i]
        
        when {
            // Code block
            line.trimStart().startsWith("```") -> {
                val language = line.trimStart().removePrefix("```").trim().ifEmpty { null }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                elements.add(MarkdownElement.CodeBlock(codeLines.joinToString("\n"), language))
            }
            // Header
            line.trimStart().startsWith("#") -> {
                val trimmed = line.trimStart()
                val level = trimmed.takeWhile { it == '#' }.length
                val headerText = trimmed.dropWhile { it == '#' }.trim()
                elements.add(MarkdownElement.Header(level, headerText))
            }
            // Divider
            line.trim().matches(Regex("^[-*_]{3,}$")) -> {
                elements.add(MarkdownElement.Divider)
            }
            // Unordered list
            line.trimStart().matches(Regex("^[-*+]\\s+.*")) -> {
                val itemText = line.trimStart().replaceFirst(Regex("^[-*+]\\s+"), "")
                elements.add(MarkdownElement.ListItem(itemText))
            }
            // Ordered list
            line.trimStart().matches(Regex("^\\d+\\.\\s+.*")) -> {
                val itemText = line.trimStart().replaceFirst(Regex("^\\d+\\.\\s+"), "")
                elements.add(MarkdownElement.ListItem(itemText))
            }
            // Regular text
            else -> {
                // Combine consecutive text lines
                val textLines = mutableListOf(line)
                while (i + 1 < lines.size && 
                       !lines[i + 1].trimStart().startsWith("```") &&
                       !lines[i + 1].trimStart().startsWith("#") &&
                       !lines[i + 1].trim().matches(Regex("^[-*_]{3,}$")) &&
                       !lines[i + 1].trimStart().matches(Regex("^[-*+]\\s+.*")) &&
                       !lines[i + 1].trimStart().matches(Regex("^\\d+\\.\\s+.*"))) {
                    i++
                    textLines.add(lines[i])
                }
                val combinedText = textLines.joinToString("\n")
                if (combinedText.isNotBlank()) {
                    elements.add(MarkdownElement.Text(combinedText))
                }
            }
        }
        i++
    }
    
    return elements
}

private sealed class MarkdownElement {
    data class Text(val content: String) : MarkdownElement()
    data class CodeBlock(val code: String, val language: String?) : MarkdownElement()
    data class Header(val level: Int, val text: String) : MarkdownElement()
    data class ListItem(val text: String) : MarkdownElement()
    data object Divider : MarkdownElement()
}

fun copyToClipboard(context: Context, text: String, toastMessage: String = "Copied!") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
