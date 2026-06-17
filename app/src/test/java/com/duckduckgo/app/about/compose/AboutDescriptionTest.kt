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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Test

class AboutDescriptionTest {

    @Test
    fun whenBuildingDescriptionThenPlainTextPreserved() {
        val text = "Read the policy here and learn more."
        val result = buildAboutDescription(
            text = text,
            links = listOf(AboutLinkSpan("policy_link", 9, 15)),
            linkColor = Color.Blue,
            onLinkClick = {},
        )
        assertEquals(text, result.text)
    }

    @Test
    fun whenBuildingDescriptionThenOneLinkAnnotationPerSpanWithMatchingTagAndRange() {
        val text = "Read the policy here and learn more."
        val spans = listOf(
            AboutLinkSpan("policy_link", 9, 15),
            AboutLinkSpan("learn_more_link", 25, 35),
        )
        val result = buildAboutDescription(
            text = text,
            links = spans,
            linkColor = Color.Blue,
            onLinkClick = {},
        )
        val annotations = result.getLinkAnnotations(0, result.length)
        assertEquals(2, annotations.size)
        annotations.forEachIndexed { index, range ->
            assertEquals(spans[index].start, range.start)
            assertEquals(spans[index].end, range.end)
            assertEquals(spans[index].value, (range.item as LinkAnnotation.Clickable).tag)
        }
    }
}
