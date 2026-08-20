package com.vasu.codeagent.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Small dependency-free Markdown renderer for the Agent chat.
 * Supports headings, bullets, numbered lines, bold, italic, inline code,
 * and fenced code blocks without adding a Markdown library dependency.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val blocks = parseMarkdown(text)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> {
                    Text(
                        text = block.value,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color.Black.copy(alpha = 0.25f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(8.dp),
                    )
                }

                is MarkdownBlock.Heading -> {
                    Text(
                        text = inlineStyled(block.value),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        color = color,
                    )
                }

                is MarkdownBlock.Bullet -> {
                    Text(
                        text = buildAnnotatedString {
                            append("•  ")
                            append(inlineStyled(block.value))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }

                is MarkdownBlock.Text -> {
                    Text(
                        text = inlineStyled(block.value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Text(val value: String) : MarkdownBlock
    data class Heading(val level: Int, val value: String) : MarkdownBlock
    data class Bullet(val value: String) : MarkdownBlock
    data class Code(val value: String) : MarkdownBlock
}

private fun parseMarkdown(input: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val lines = input.replace("\r\n", "\n").split('\n')
    val codeLines = mutableListOf<String>()
    var inCode = false

    fun flushCode() {
        if (codeLines.isNotEmpty()) {
            result += MarkdownBlock.Code(codeLines.joinToString("\n"))
            codeLines.clear()
        }
    }

    for (rawLine in lines) {
        val line = rawLine.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                inCode = true
            }
            continue
        }

        if (inCode) {
            codeLines += line
            continue
        }

        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> Unit
            trimmed.startsWith("### ") -> result += MarkdownBlock.Heading(3, trimmed.removePrefix("### "))
            trimmed.startsWith("## ") -> result += MarkdownBlock.Heading(2, trimmed.removePrefix("## "))
            trimmed.startsWith("# ") -> result += MarkdownBlock.Heading(1, trimmed.removePrefix("# "))
            trimmed.startsWith("- ") -> result += MarkdownBlock.Bullet(trimmed.removePrefix("- "))
            trimmed.startsWith("* ") -> result += MarkdownBlock.Bullet(trimmed.removePrefix("* "))
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> result += MarkdownBlock.Text(trimmed)
            else -> result += MarkdownBlock.Text(line)
        }
    }

    if (inCode) flushCode()
    return result
}

private fun inlineStyled(raw: String): AnnotatedString = buildAnnotatedString {
    val pattern = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`|\\*[^*]+\\*)")
    var cursor = 0

    for (match in pattern.findAll(raw)) {
        if (match.range.first > cursor) {
            append(raw.substring(cursor, match.range.first))
        }

        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.substring(2, token.length - 2))
            }

            token.startsWith("`") -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace),
            ) {
                append(token.substring(1, token.length - 1))
            }

            token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.substring(1, token.length - 1))
            }
        }
        cursor = match.range.last + 1
    }

    if (cursor < raw.length) append(raw.substring(cursor))
}
