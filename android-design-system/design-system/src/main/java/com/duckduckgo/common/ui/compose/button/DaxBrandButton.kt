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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.duckduckgo.common.ui.compose.button

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.Mandarin50
import com.duckduckgo.common.ui.compose.theme.Mandarin70
import com.duckduckgo.common.ui.compose.theme.Pollen100
import com.duckduckgo.common.ui.compose.theme.Pollen40
import com.duckduckgo.common.ui.compose.theme.Pollen60
import com.duckduckgo.common.ui.compose.theme.ProvideDuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.Typography
import com.duckduckgo.common.ui.compose.theme.White
import com.duckduckgo.common.ui.compose.theme.asTextStyle
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo brand button with filled background, for promotional surfaces and prominent CTAs.
 *
 * Pill-shaped and taller than the other Dax buttons.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1215496415658080/task/1219057348194086
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=25814-64811
 *
 * @param text The button label.
 * @param onClick Called when the button is clicked.
 * @param modifier Modifier for this button.
 * @param size The button size — [DaxButtonSize.Small] or [DaxButtonSize.Large].
 * @param enabled Whether the button is enabled for interaction.
 * @param typography The label typeface.
 * @param leadingIconPainter Optional icon [Painter] displayed before the button text.
 *   When non-null, renders a 16dp ([DaxButtonSize.Small]) or 24dp ([DaxButtonSize.Large])
 *   icon tinted with the button's content color.
 *   Pass `null` (default) for a text-only button.
 */
@Composable
fun DaxBrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: DaxButtonSize = DaxButtonSize.Small,
    enabled: Boolean = true,
    typography: DaxBrandButtonTypography = DaxBrandButtonTypography.Standard,
    leadingIconPainter: Painter? = null,
) {
    ProvideDuckDuckGoTheme(
        colors = DuckDuckGoTheme.colors,
        shapes = DuckDuckGoTheme.shapes.copy(medium = DaxBrandButtonDefaults.Shape),
        typography = DuckDuckGoTheme.typography.copy(
            button = DaxBrandButtonDefaults.textStyle(size, typography),
        ),
    ) {
        DaxButton(
            text = text,
            onClick = onClick,
            size = size,
            colors = DaxBrandButtonDefaults.colors(),
            rippleConfiguration = DaxBrandButtonDefaults.rippleConfiguration(),
            leadingIconPainter = leadingIconPainter,
            modifier = modifier,
            enabled = enabled,
            height = DaxBrandButtonDefaults.height(size),
        )
    }
}

/** The label typeface of a [DaxBrandButton]. */
enum class DaxBrandButtonTypography {
    Standard,
    DuckSans,
}

private object DaxBrandButtonDefaults {

    private const val DisabledAlpha = 0.38f

    private val SmallTextStyle = DuckDuckGoTextStyle(
        Typography.button.asTextStyle.copy(fontWeight = FontWeight.Medium),
    )

    private val LargeTextStyle = DuckDuckGoTextStyle(
        SmallTextStyle.asTextStyle.copy(fontSize = 16.sp),
    )

    val Shape: Shape = RoundedCornerShape(percent = 50)

    val adsColorButtonBrandContainer: Color
        @Composable @ReadOnlyComposable get() = if (DuckDuckGoTheme.colors.isDark) {
            Pollen40
        } else {
            Mandarin50
        }

    val adsColorButtonBrandContainerPressed: Color
        @Composable @ReadOnlyComposable get() = if (DuckDuckGoTheme.colors.isDark) {
            Pollen60
        } else {
            Mandarin70
        }

    val adsColorButtonBrandText: Color
        @Composable @ReadOnlyComposable get() = if (DuckDuckGoTheme.colors.isDark) {
            Pollen100
        } else {
            White
        }

    val adsColorButtonBrandContainerDisabled: Color
        @Composable @ReadOnlyComposable get() = adsColorButtonBrandContainer.copy(alpha = DisabledAlpha)

    val adsColorButtonBrandTextDisabled: Color
        @Composable @ReadOnlyComposable get() = adsColorButtonBrandText.copy(alpha = DisabledAlpha)

    fun height(size: DaxButtonSize): DaxButtonHeight = when (size) {
        DaxButtonSize.Small -> DaxButtonHeight(min = 40.dp, verticalPadding = 6.dp)
        DaxButtonSize.Large -> DaxButtonHeight(min = 56.dp, verticalPadding = 12.dp)
    }

    @Composable @ReadOnlyComposable
    fun textStyle(size: DaxButtonSize, typography: DaxBrandButtonTypography): DuckDuckGoTextStyle = when (typography) {
        DaxBrandButtonTypography.DuckSans -> DuckDuckGoTheme.typography.onboardingButton
        DaxBrandButtonTypography.Standard -> when (size) {
            DaxButtonSize.Small -> SmallTextStyle
            DaxButtonSize.Large -> LargeTextStyle
        }
    }

    @Composable @ReadOnlyComposable
    fun colors(): DaxButtonColors = DaxButtonColors(
        containerColor = adsColorButtonBrandContainer,
        contentColor = adsColorButtonBrandText,
        disabledContainerColor = adsColorButtonBrandContainerDisabled,
        disabledContentColor = adsColorButtonBrandTextDisabled,
    )

    @Composable
    fun rippleConfiguration(): RippleConfiguration {
        val color = adsColorButtonBrandContainerPressed
        return remember(color) { daxRippleConfiguration(color) }
    }
}

@PreviewLightDark
@Composable
private fun DaxBrandButtonSmallPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxBrandButton(
            text = "Brand Small",
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxBrandButtonLargePreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxBrandButton(
            text = "Brand Large",
            size = DaxButtonSize.Large,
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxBrandButtonWithIconSmallPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxBrandButton(
            text = "Brand Small",
            onClick = { },
            enabled = enabled,
            leadingIconPainter = painterResource(R.drawable.ic_add_24_solid_color),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxBrandButtonWithIconLargePreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxBrandButton(
            text = "Brand Large",
            size = DaxButtonSize.Large,
            onClick = { },
            enabled = enabled,
            leadingIconPainter = painterResource(R.drawable.ic_add_24_solid_color),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxBrandButtonDuckSansPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxBrandButton(
            text = "Brand DuckSans",
            onClick = { },
            enabled = enabled,
            typography = DaxBrandButtonTypography.DuckSans,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxBrandButtonWrappedSmallPreview() {
    PreviewBox {
        DaxBrandButton(
            text = WrappedPreviewLabel,
            onClick = { },
            modifier = Modifier.width(WrappedPreviewWidth),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxBrandButtonWrappedLargePreview() {
    PreviewBox {
        DaxBrandButton(
            text = WrappedPreviewLabel,
            onClick = { },
            modifier = Modifier.width(WrappedPreviewWidth),
            size = DaxButtonSize.Large,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxBrandButtonDuckSansWrappedSmallPreview() {
    PreviewBox {
        DaxBrandButton(
            text = WrappedPreviewLabel,
            onClick = { },
            modifier = Modifier.width(WrappedPreviewWidth),
            typography = DaxBrandButtonTypography.DuckSans,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxBrandButtonDuckSansWrappedLargePreview() {
    PreviewBox {
        DaxBrandButton(
            text = WrappedPreviewLabel,
            onClick = { },
            modifier = Modifier.width(WrappedPreviewWidth),
            typography = DaxBrandButtonTypography.DuckSans,
            size = DaxButtonSize.Large,
        )
    }
}
