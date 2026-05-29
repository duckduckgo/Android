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

package com.duckduckgo.common.ui.compose.infopannel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R

/**
 * Base composable for the Dax info panel family. Renders a rounded, full-width panel with a
 * leading icon and a body of text.
 *
 * This is the shared implementation backing the public variants such as [DaxInfoPanel] and
 * [DaxAlertPanel]. Prefer one of those variants over calling this directly, as they provide the
 * correct icon and background colour for each use case.
 *
 * The body is an [AnnotatedString] so panels can carry inline styling and clickable links. Plain
 * text is supported by wrapping it, e.g. `AnnotatedString("...")`.
 *
 * @param body The text to display in the panel, as an [AnnotatedString].
 * @param color The background colour of the panel.
 * @param icon The leading [Painter] icon displayed at the start of the panel.
 * @param modifier The [Modifier] to be applied to this panel.
 */
@Composable
internal fun DaxPanel(
    body: AnnotatedString,
    color: Color,
    icon: Painter,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = color,
                    shape = DuckDuckGoTheme.shapes.small,
                )
                .padding(dimensionResource(R.dimen.keyline_4)),
        ) {
            Image(
                painter = icon,
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.keyline_1))
                    .size(dimensionResource(R.dimen.infoPanelIconSize)),
                contentDescription = null,
            )
            DaxText(
                text = body.withDaxPanelLinkStyle(),
                modifier = Modifier
                    .padding(start = dimensionResource(R.dimen.keyline_4)),
                style = DuckDuckGoTheme.typography.body2,
            )
        }
    }
}

/**
 * Re-styles every link in this [AnnotatedString] with the design-system link style (primary text
 * colour, underlined), discarding any [TextLinkStyles] the caller set so links render consistently
 * across all panels. The text and any non-link spans are preserved, and the original link
 * destination/click behaviour is kept. Strings with no links are returned unchanged.
 */
@Composable
private fun AnnotatedString.withDaxPanelLinkStyle(): AnnotatedString {
    val source = this
    val links = source.getLinkAnnotations(0, source.length)
    if (links.isEmpty()) return source

    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = DuckDuckGoTheme.textColors.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    return buildAnnotatedString {
        append(source.text)
        source.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
        source.paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
        links.forEach { range ->
            when (val link = range.item) {
                is LinkAnnotation.Url -> addLink(
                    LinkAnnotation.Url(link.url, linkStyles, link.linkInteractionListener),
                    range.start,
                    range.end,
                )
                is LinkAnnotation.Clickable -> addLink(
                    LinkAnnotation.Clickable(link.tag, linkStyles, link.linkInteractionListener),
                    range.start,
                    range.end,
                )
            }
        }
    }
}
