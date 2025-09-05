/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.compose.component.core.text

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.asTextStyle

@Composable
fun DaxTextPrimary(
    text: String,
    modifier: Modifier = Modifier,
    style: DuckDuckGoTextStyle = DuckDuckGoTheme.typography.body1,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE,
) {
    DaxText(
        text = text,
        color = DuckDuckGoTheme.textColors.primary,
        modifier = modifier,
        style = style,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
    )
}

@Composable
fun DaxTextSecondary(
    text: String,
    modifier: Modifier = Modifier,
    style: DuckDuckGoTextStyle = DuckDuckGoTheme.typography.body1,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE,
) {
    DaxText(
        text = text,
        color = DuckDuckGoTheme.textColors.secondary,
        modifier = modifier,
        style = style,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
    )
}

@Composable
private fun DaxText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: DuckDuckGoTextStyle = DuckDuckGoTheme.typography.body1,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.asTextStyle,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
    )
}

@PreviewLightDark
@Composable
private fun DaxTextPrimaryPreview() {
    DuckDuckGoTheme {
        DaxTextPrimary("Primary Body1 Text")
    }
}

@PreviewLightDark
@Composable
private fun DaxTextSecondaryPreview() {
    DuckDuckGoTheme {
        DaxTextSecondary("Secondary Body1 Text")
    }
}

@PreviewLightDark
@Composable
private fun DaxTextTitlePreview() {
    DuckDuckGoTheme {
        DaxTextPrimary("Title Text", style = DuckDuckGoTheme.typography.title)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextH1Preview() {
    DuckDuckGoTheme {
        DaxTextPrimary("H1 Text", style = DuckDuckGoTheme.typography.h1)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextCaptionPreview() {
    DuckDuckGoTheme {
        DaxTextPrimary("Caption Text", style = DuckDuckGoTheme.typography.caption)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextCaptionSecondaryPreview() {
    DuckDuckGoTheme {
        DaxTextSecondary("Caption Secondary", style = DuckDuckGoTheme.typography.caption)
    }
}

