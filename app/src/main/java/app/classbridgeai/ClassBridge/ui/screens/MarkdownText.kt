package app.classbridgeai.ClassBridge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp

// Typography renderer component for rich formatted text, headers, and code styles
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified
) {
    Column(modifier = modifier) {
        for (line in text.split("\n")) {
            when {
                line.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(line.drop(4)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(line.drop(3)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(line.drop(2)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text("\u2022  ", style = style, color = color)
                        Text(
                            text = parseInlineMarkdown(line.drop(2)),
                            style = style,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                line.length >= 3 && line[0].isDigit() && line.contains(". ") &&
                        line.indexOf(". ").let { it in 1..3 } -> {
                    val dotIdx = line.indexOf(". ")
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text("${line.substring(0, dotIdx)}.  ", style = style, color = color)
                        Text(
                            text = parseInlineMarkdown(line.drop(dotIdx + 2)),
                            style = style,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                line.matches(Regex("[-_*]{3,}")) -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                line.isBlank() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(line),
                        style = style,
                        color = color,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

fun parseInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // Bold text styling spans
            i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else append(text[i++])
            }
            // Bold text styling spans
            i + 1 < text.length && text[i] == '_' && text[i + 1] == '_' -> {
                val end = text.indexOf("__", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else append(text[i++])
            }
            // Italic text styling spans
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else append(text[i++])
            }
            // Italic text styling spans
            text[i] == '_' -> {
                val end = text.indexOf('_', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else append(text[i++])
            }
            // Monospace code snippet typography formatting
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else append(text[i++])
            }
            else -> append(text[i++])
        }
    }
}
