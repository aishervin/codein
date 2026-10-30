package com.example

import androidx.compose.ui.graphics.Color
import com.example.ui.linkifyChatText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLinksTest {
    @Test
    fun plainUrlsAreClickableWithoutTrailingPunctuation() {
        val annotated = linkifyChatText("Open https://example.com/docs.", Color.Red)
        val link = annotated.getStringAnnotations("URL", 0, annotated.length).single()

        assertEquals("Open https://example.com/docs.", annotated.text)
        assertEquals("https://example.com/docs", link.item)
        assertEquals("https://example.com/docs", annotated.text.substring(link.start, link.end))
    }

    @Test
    fun markdownLinksRenderTheLabelAsClickableText() {
        val annotated = linkifyChatText("Open [the guide](https://example.com/guide).", Color.Red)
        val link = annotated.getStringAnnotations("URL", 0, annotated.length).single()

        assertEquals("Open the guide.", annotated.text)
        assertEquals("https://example.com/guide", link.item)
        assertEquals("the guide", annotated.text.substring(link.start, link.end))
    }

    @Test
    fun nonHttpLinksAreNotMarkedAsBrowserLinks() {
        val annotated = linkifyChatText("mailto:help@example.com", Color.Red)

        assertTrue(annotated.getStringAnnotations("URL", 0, annotated.length).isEmpty())
    }
}
