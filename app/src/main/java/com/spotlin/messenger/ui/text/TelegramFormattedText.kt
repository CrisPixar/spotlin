package com.spotlin.messenger.ui.text

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Parses Telegram-like Markdown formatting:
 * **bold** -> Bold
 * *italic* or _italic_ -> Italic
 * `code` -> Monospace
 * ||spoiler|| -> Spoiler (hidden until clicked)
 * @username -> Clickable mention
 */
@Composable
fun TelegramFormattedText(
    text: String,
    fontSize: TextUnit = 15.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    mentionColor: Color = MaterialTheme.colorScheme.primary,
    onMentionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var spoilerRevealed by remember { mutableStateOf(false) }

    val annotated = remember(text, spoilerRevealed, color, mentionColor) {
        buildTelegramAnnotatedString(
            rawText = text,
            defaultColor = color,
            mentionColor = mentionColor,
            spoilerRevealed = spoilerRevealed
        )
    }

    ClickableText(
        text = annotated,
        style = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.35f).sp
        ),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "mention", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onMentionClick(annotation.item)
                    return@ClickableText
                }

            annotated.getStringAnnotations(tag = "spoiler", start = offset, end = offset)
                .firstOrNull()?.let {
                    spoilerRevealed = !spoilerRevealed
                }
        }
    )
}

fun buildTelegramAnnotatedString(
    rawText: String,
    defaultColor: Color,
    mentionColor: Color,
    spoilerRevealed: Boolean
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val len = rawText.length

        // Simple regex-based token parsing
        val tokenRegex = Regex("""(\*\*.*?\*\*|\*.*?\*|_.*?_|`.*?`|\|\|.*?\|\||@[a-zA-Z0-9_]{3,30})""")
        val matches = tokenRegex.findAll(rawText).toList()

        for (match in matches) {
            val range = match.range
            if (range.first > cursor) {
                append(rawText.substring(cursor, range.first))
            }

            val value = match.value
            when {
                value.startsWith("**") && value.endsWith("**") && value.length >= 4 -> {
                    val inner = value.substring(2, value.length - 2)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor))
                    append(inner)
                    pop()
                }
                ((value.startsWith("*") && value.endsWith("*")) || (value.startsWith("_") && value.endsWith("_"))) && value.length >= 2 -> {
                    val inner = value.substring(1, value.length - 1)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor))
                    append(inner)
                    pop()
                }
                value.startsWith("`") && value.endsWith("`") && value.length >= 2 -> {
                    val inner = value.substring(1, value.length - 1)
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = defaultColor.copy(alpha = 0.12f)))
                    append(inner)
                    pop()
                }
                value.startsWith("||") && value.endsWith("||") && value.length >= 4 -> {
                    val inner = value.substring(2, value.length - 2)
                    pushStringAnnotation(tag = "spoiler", annotation = inner)
                    if (spoilerRevealed) {
                        pushStyle(SpanStyle(color = defaultColor, background = Color(0x33888888)))
                        append(inner)
                    } else {
                        pushStyle(SpanStyle(color = Color.Transparent, background = Color(0x99555555)))
                        append(inner)
                    }
                    pop()
                    pop()
                }
                value.startsWith("@") -> {
                    val username = value.removePrefix("@")
                    pushStringAnnotation(tag = "mention", annotation = username)
                    pushStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.SemiBold))
                    append(value)
                    pop()
                    pop()
                }
                else -> {
                    append(value)
                }
            }
            cursor = range.last + 1
        }

        if (cursor < len) {
            append(rawText.substring(cursor))
        }
    }
}
