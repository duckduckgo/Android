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

package com.duckduckgo.common.ui.compose.text

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
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * Base text component for the DuckDuckGo design system.
 *
 * @param color The text color. Should use colors from [DuckDuckGoTheme.textColors] for consistency
 * with the design system (e.g., [DuckDuckGoTheme.textColors.primary], [DuckDuckGoTheme.textColors.secondary]).
 * A lint rule will warn if arbitrary colors are used.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1211634956773768
 * Figma reference: https://www.figma.com/design/jHLwh4erLbNc2YeobQpGFt/Design-System-Guidelines?node-id=1313-19967
 */
@Composable
fun DaxText(
    text: String,
    modifier: Modifier = Modifier,
    style: DuckDuckGoTextStyle = DuckDuckGoTheme.typography.body1,
    color: Color = DuckDuckGoTheme.textColors.primary,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        color = color,
        style = style.asTextStyle,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DaxTextTitlePreview() {
    PreviewBox {
        DaxText(text = "Title Text", style = DuckDuckGoTheme.typography.title)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextH1Preview() {
    PreviewBox {
        DaxText(text = "H1 Text", style = DuckDuckGoTheme.typography.h1)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextH2Preview() {
    PreviewBox {
        DaxText(text = "H2 Text", style = DuckDuckGoTheme.typography.h2)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextH3Preview() {
    PreviewBox {
        DaxText(text = "H3 Text", style = DuckDuckGoTheme.typography.h3)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextH4Preview() {
    PreviewBox {
        DaxText(text = "H4 Text", style = DuckDuckGoTheme.typography.h4)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextH5Preview() {
    PreviewBox {
        DaxText(text = "H5 Text", style = DuckDuckGoTheme.typography.h5)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextBody1Preview() {
    PreviewBox {
        DaxText(text = "Body1 Text", style = DuckDuckGoTheme.typography.body1)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextBody1BoldPreview() {
    PreviewBox {
        DaxText(text = "Body1Bold Text", style = DuckDuckGoTheme.typography.body1Bold)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextBody1MonoPreview() {
    PreviewBox {
        DaxText(text = "Body1Mono Text", style = DuckDuckGoTheme.typography.body1Mono)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextBody2Preview() {
    PreviewBox {
        DaxText(text = "Body2 Text", style = DuckDuckGoTheme.typography.body2)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextBody2BoldPreview() {
    PreviewBox {
        DaxText(text = "Body2Bold Text", style = DuckDuckGoTheme.typography.body2Bold)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextButtonPreview() {
    PreviewBox {
        DaxText(text = "Button Text", style = DuckDuckGoTheme.typography.button)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextCaptionPreview() {
    PreviewBox {
        DaxText(text = "Caption Text", style = DuckDuckGoTheme.typography.caption)
    }
}

@PreviewLightDark
@Composable
private fun DaxTextColorPrimaryPreview() {
    PreviewBox {
        DaxText(
            text = "Primary Color",
            color = DuckDuckGoTheme.textColors.primary,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextColorPrimaryInvertedPreview() {
    DaxTextInvertedPreviewBox {
        DaxText(
            text = "Primary Inverted",
            color = DuckDuckGoTheme.textColors.primaryInverted,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextColorSecondaryPreview() {
    PreviewBox {
        DaxText(
            text = "Secondary Color",
            color = DuckDuckGoTheme.textColors.secondary,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextColorSecondaryInvertedPreview() {
    DaxTextInvertedPreviewBox {
        DaxText(
            text = "Secondary Inverted",
            color = DuckDuckGoTheme.textColors.secondaryInverted,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextColorTertiaryPreview() {
    PreviewBox {
        DaxText(
            text = "Tertiary Color",
            color = DuckDuckGoTheme.textColors.tertiary,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextColorDisabledPreview() {
    PreviewBox {
        DaxText(
            text = "Disabled Color",
            color = DuckDuckGoTheme.textColors.disabled,
        )
    }
}

@Composable
private fun DaxTextInvertedPreviewBox(
    content: @Composable () -> Unit,
) {
    DuckDuckGoTheme {
        PreviewBox(backgroundColor = { DuckDuckGoTheme.colors.backgroundInverted }) {
            content()
        }
    }
}
