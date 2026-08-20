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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A small, dependency-free Markdown renderer for chat bubbles: headings,
 * **bold**, *italic*, `inline code`, ``` code blocks ```, and "- "/"1. " lists.
 * Not a full CommonMark implementation — just enough that model output
 * reads like a formatted answer instead of raw asterisks and hashes.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val lines = text.split("\n")
    var i = 0

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.trim().startsWith("```") -> {
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    Text(
                        text = codeLines.joinToString("\n"),
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

                line.trim().startsWith("### ") ->
                    Text(
                        text = inlineStyled(line.trim().removePrefix("### ")),
                        style = MaterialTheme.typography.titleSmall,
                        color = color,
                    )

                line.trim().startsWith("## ") ->
                    Text(
                        text = inlineStyled(line.trim().removePrefix("## ")),
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                    )

                line.trim().startsWith("# ") ->
                    Text(
                        text = inlineStyled(line.trim().removePrefix("# ")),
                        style = MaterialTheme.typography.titleLarge,
                        color = color,
                    )

                line.trim().startsWith("- ") || line.trim().startsWith("* ") ->
                    Text(
                        text = buildAnnotatedString {
                            append("•  ")
                            append(
                                inlineStyled(
                                    line.trim()
                                        .removePrefix("- ")
                                        .removePrefix("* "),
                                ),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )

                Regex("^\\d+\\.\\s").containsMatchIn(line.trim()) ->
                    Text(
                        text = inlineStyled(line.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )

                line.isBlank() ->
                    Text("", style = MaterialTheme.typography.bodySmall)

                else ->
                    Text(
                        text = inlineStyled(line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
            }
            i++
        }
    }
}

/** Handles **bold**, *italic*, and `inline code` within a single line. */
private fun inlineStyled(raw: String) = buildAnnotatedString {
    var idx = 0
    val pattern = Regex("(\\*\\*.+?\\*\\*|`.+?`|\\*[^*]+?\\*)")

    for (match in pattern.findAll(raw)) {
        if (match.range.first > idx) {
            append(raw.substring(idx, match.range.first))
        }

        val token = match.value
        when {
            token.startsWith("**") ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removePrefix("**").removeSuffix("**"))
                }

            token.startsWith("`") ->
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color.Black.copy(alpha = 0.15f),
                    ),
                ) {
                    append(token.removePrefix("`").removeSuffix("`"))
                }

            token.startsWith("*") ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.removePrefix("*").removeSuffix("*"))
                }
        }
        idx = match.range.last + 1
    }

    if (idx < raw.length) append(raw.substring(idx))
}
