package com.dannyk.xirea.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.TypedValue
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin

/**
 * Renders Markdown-formatted text using Markwon for all markdown except fenced code blocks.
 * Code blocks are rendered with a dark background, language label, and a copy icon.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .build()
    }

    val segments = remember(text) { splitMarkdownIntoSegments(text) }

    Column(modifier = modifier.fillMaxWidth()) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> {
                    AndroidView(
                        factory = { ctx ->
                            TextView(ctx).apply {
                                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                                highlightColor = android.graphics.Color.TRANSPARENT
                                setTextColor(color.toArgb())
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSizeOrDefault())
                                setLineSpacing(0f, style.lineHeightMultiplier())
                            }
                        },
                        update = { view ->
                            view.setTextColor(color.toArgb())
                            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSizeOrDefault())
                            view.setLineSpacing(0f, style.lineHeightMultiplier())
                            markwon.setMarkdown(view, segment.content)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is MarkdownSegment.Code -> {
                    CodeBlock(
                        language = segment.language,
                        code = segment.code
                    )
                }
            }
        }
    }
}

// ========== Markdown Segmentation ==========

private sealed class MarkdownSegment {
    data class Text(val content: String) : MarkdownSegment()
    data class Code(val language: String?, val code: String) : MarkdownSegment()
}

/**
 * Splits markdown text into text segments and fenced code blocks.
 * Only fenced code blocks (```language ... ```) are extracted.
 */
private fun splitMarkdownIntoSegments(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val lines = text.lines()
    var currentText = StringBuilder()
    var inCode = false
    var codeLanguage: String? = null
    var codeBuilder = StringBuilder()

    for (line in lines) {
        val trimmed = line.trimStart()
        when {
            !inCode && trimmed.startsWith("```") -> {
                // Start of a fenced code block
                if (currentText.isNotEmpty()) {
                    segments.add(MarkdownSegment.Text(currentText.toString()))
                    currentText.clear()
                }
                inCode = true
                codeLanguage = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
                codeBuilder.clear()
            }
            inCode && trimmed.startsWith("```") -> {
                // End of code block
                inCode = false
                segments.add(MarkdownSegment.Code(codeLanguage, codeBuilder.toString()))
                codeLanguage = null
                codeBuilder.clear()
            }
            inCode -> {
                codeBuilder.append(line).append('\n')
            }
            else -> {
                currentText.append(line).append('\n')
            }
        }
    }

    // If still inside a code block (unclosed), treat as text
    if (inCode) {
        currentText.append(codeBuilder)
    }
    if (currentText.isNotEmpty()) {
        segments.add(MarkdownSegment.Text(currentText.toString()))
    }

    // Remove trailing newlines from text segments to avoid extra spacing
    return segments.map {
        when (it) {
            is MarkdownSegment.Text -> it.copy(content = it.content.trimEnd())
            else -> it
        }
    }.filter {
        when (it) {
            is MarkdownSegment.Text -> it.content.isNotBlank()
            is MarkdownSegment.Code -> it.code.isNotBlank()
        }
    }
}

// ========== Code Block Composable ==========

@Composable
private fun CodeBlock(
    language: String?,
    code: String
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language ?: "",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = {
                        copyToClipboard(context, code, "Code copied!")
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = code,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 14.sp
                ),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

// ========== Utility Functions ==========

private fun TextStyle.fontSizeOrDefault(): Float {
    return if (fontSize != TextUnit.Unspecified) fontSize.value else 14f
}

private fun TextStyle.lineHeightMultiplier(): Float {
    val size = fontSizeOrDefault()
    return if (lineHeight != TextUnit.Unspecified && size > 0f) {
        (lineHeight.value / size).coerceAtLeast(1f)
    } else {
        1f
    }
}

/**
 * Copies text to the clipboard and shows a toast.
 */
fun copyToClipboard(context: Context, text: String, toastMessage: String = "Copied!") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}