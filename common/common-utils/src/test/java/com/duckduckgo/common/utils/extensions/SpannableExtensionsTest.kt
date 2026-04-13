package com.duckduckgo.common.utils.extensions

import android.text.Annotation
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpannableExtensionsTest {

    @Test
    fun whenSinglePlaceholderThenReplacedCorrectly() {
        val input = "Hello %1\$s world"
        val result = input.formatWithSpans("DuckDuckGo")
        assertEquals("Hello DuckDuckGo world", result.toString())
    }

    @Test
    fun whenMultiplePlaceholdersThenAllReplacedCorrectly() {
        val input = "%1\$s wants to access %2\$s"
        val result = input.formatWithSpans("example.com", "DRM")
        assertEquals("example.com wants to access DRM", result.toString())
    }

    @Test
    fun whenNoPlaceholdersThenTextUnchanged() {
        val input = "No placeholders here"
        val result = input.formatWithSpans("unused")
        assertEquals("No placeholders here", result.toString())
    }

    @Test
    fun whenAnnotationSpanPresentThenPreservedAfterReplacement() {
        val builder = SpannableStringBuilder("Visit %1\$s for details. ")
        val learnMore = "Learn More"
        builder.append(learnMore)
        builder.setSpan(
            Annotation("type", "learn_more_link"),
            builder.length - learnMore.length,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        val result = builder.formatWithSpans("example.com")

        assertEquals("Visit example.com for details. Learn More", result.toString())
        val annotations = result.getSpans(0, result.length, Annotation::class.java)
        assertEquals(1, annotations.size)
        assertEquals("learn_more_link", annotations[0].value)
        assertTrue(result.getSpanStart(annotations[0]) > 0)
    }

    @Test
    fun whenPlaceholderAfterAnnotationThenBothPreserved() {
        val builder = SpannableStringBuilder("")
        val linkText = "Learn More"
        builder.append(linkText)
        builder.setSpan(
            Annotation("type", "link"),
            0,
            linkText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        builder.append(" about %1\$s")

        val result = builder.formatWithSpans("DRM")

        assertEquals("Learn More about DRM", result.toString())
        val annotations = result.getSpans(0, result.length, Annotation::class.java)
        assertEquals(1, annotations.size)
        assertEquals(0, result.getSpanStart(annotations[0]))
        assertEquals(linkText.length, result.getSpanEnd(annotations[0]))
    }
}
