/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.about.compose

import android.content.Context
import android.text.Annotation
import android.text.SpannedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

/** One inline clickable link extracted from the styled description resource. */
data class AboutLinkSpan(
    val value: String,
    val start: Int,
    val end: Int,
)

/**
 * Pure, JVM-testable builder: applies a clickable, underlined, colored [LinkAnnotation]
 * over each [AboutLinkSpan] range. [onLinkClick] receives the annotation value (tag).
 */
fun buildAboutDescription(
    text: String,
    links: List<AboutLinkSpan>,
    linkColor: Color,
    onLinkClick: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    append(text)
    val styles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
    )
    links.forEach { link ->
        addLink(
            LinkAnnotation.Clickable(
                tag = link.value,
                styles = styles,
                linkInteractionListener = { onLinkClick(link.value) },
            ),
            link.start,
            link.end,
        )
    }
}

/**
 * Reads the styled description resource and extracts its [Annotation] spans into
 * [AboutLinkSpan]s, then delegates to [buildAboutDescription]. Verified via preview / manual run.
 */
fun Context.aboutDescription(
    resId: Int,
    linkColor: Color,
    onLinkClick: (String) -> Unit,
): AnnotatedString {
    val styled = getText(resId) as SpannedString
    val links = styled.getSpans(0, styled.length, Annotation::class.java).map { annotation ->
        AboutLinkSpan(
            value = annotation.value,
            start = styled.getSpanStart(annotation),
            end = styled.getSpanEnd(annotation),
        )
    }
    return buildAboutDescription(styled.toString(), links, linkColor, onLinkClick)
}
