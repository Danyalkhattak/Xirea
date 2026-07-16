package com.dannyk.xirea.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.CodeBlockSpan
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock

/**
 * Renders Markdown-formatted text using Markwon (CommonMark-based parser).
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
            .usePlugin(CodeCopyPlugin(context))
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
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
            markwon.setMarkdown(view, text)
        }
    )
}

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

private class CodeCopyPlugin(
    private val context: Context
) : AbstractMarkwonPlugin() {
    override fun configureTheme(builder: MarkwonTheme.Builder) {
        builder.codeBlockBackgroundColor(0xFF1E1E1E.toInt())
            .codeBlockTextColor(0xFFE6E6E6.toInt())
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(FencedCodeBlock::class.java) { visitor, codeBlock ->
            renderCodeBlock(visitor, codeBlock.literal)
        }
        builder.on(IndentedCodeBlock::class.java) { visitor, codeBlock ->
            renderCodeBlock(visitor, codeBlock.literal)
        }
    }

    private fun renderCodeBlock(visitor: MarkwonVisitor, code: String) {
        val builder = visitor.builder()
        val start = builder.length
        builder.append("Copy\n")
        builder.append(code)
        builder.append("\n")
        val end = builder.length

        val theme = visitor.configuration().theme()
        builder.setSpan(CodeBlockSpan(theme), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val copyEnd = (start + 4).coerceAtMost(end)
        builder.setSpan(CopyClickableSpan(context, code), start, copyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(ForegroundColorSpan(0xFF9CDCFE.toInt()), start, copyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(StyleSpan(Typeface.BOLD), start, copyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private class CopyClickableSpan(
    private val context: Context,
    private val code: String
) : ClickableSpan() {
    override fun onClick(widget: View) {
        copyToClipboard(context, code, "Code copied!")
    }
}

fun copyToClipboard(context: Context, text: String, toastMessage: String = "Copied!") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
