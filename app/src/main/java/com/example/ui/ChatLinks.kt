package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

private data class ChatLink(
    val start: Int,
    val end: Int,
    val target: String,
    val label: String
)

private val markdownLinkPattern = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)""")
private val plainUrlPattern = Regex("""https?://[^\s<>]+""")

internal fun linkifyChatText(text: String, linkColor: Color): AnnotatedString {
    val markdownLinks = markdownLinkPattern.findAll(text).map { match ->
        val target = match.groupValues[2].trimEnd('.', ',', ';', ':', '!', '?', ']', '}')
        ChatLink(match.range.first, match.range.last + 1, target, match.groupValues[1])
    }.toList()
    val links = markdownLinks.toMutableList()

    plainUrlPattern.findAll(text).forEach { match ->
        if (markdownLinks.any { match.range.first in it.start until it.end }) return@forEach
        val target = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
        if (target.isNotBlank()) {
            links += ChatLink(match.range.first, match.range.first + target.length, target, target)
        }
    }

    return buildAnnotatedString {
        var cursor = 0
        links.sortedBy(ChatLink::start).forEach { link ->
            if (link.start < cursor || link.start >= link.end) return@forEach
            append(text, cursor, link.start)
            pushStringAnnotation("URL", link.target)
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                append(link.label)
            }
            pop()
            cursor = link.end
        }
        append(text, cursor, text.length)
    }
}
