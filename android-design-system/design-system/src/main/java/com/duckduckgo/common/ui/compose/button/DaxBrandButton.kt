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

import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
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
import com.duckduckgo.common.ui.compose.theme.White
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R
import com.duckduckgo.fonts.R as FontsR

/**
 * DuckDuckGo brand button with filled background, for promotional surfaces and prominent CTAs.
 *
 * Pill-shaped and taller than the other Dax buttons, matching the View
 * [com.duckduckgo.common.ui.view.button.DaxButtonBrand].
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1215496415658080/task/1219057348194086
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=25814-64811
 *
 * @param text The button label.
 * @param onClick Called when the button is clicked.
 * @param modifier Modifier for this button.
 * @param size The button size — [DaxButtonSize.Small] or [DaxButtonSize.Large].
 * @param enabled Whether the button is enabled for interaction.
 * @param typography The font style — [DaxBrandButtonTypography.Standard] (default) or
 *   [DaxBrandButtonTypography.DuckSans] for the proprietary DuckSans Product font.
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
        shapes = DuckDuckGoTheme.shapes.copy(medium = DaxBrandButtonDefaults.shape),
        typography = DuckDuckGoTheme.typography.copy(
            button = DaxBrandButtonDefaults.textStyle(size = size, typography = typography),
        ),
    ) {
        DaxButton(
            text = text,
            onClick = onClick,
            size = size,
            colors = DaxBrandButtonDefaults.colors(),
            rippleConfiguration = DaxBrandButtonDefaults.rippleConfiguration(),
            leadingIconPainter = leadingIconPainter,
            modifier = modifier.height(DaxBrandButtonDefaults.surfaceHeight(size)),
            enabled = enabled,
        )
    }
}

enum class DaxBrandButtonTypography {
    Standard,
    DuckSans,
}

private object DaxBrandButtonDefaults {

    private const val DisabledAlpha = 0.38f

    private val DuckSansProduct = FontFamily(
        Font(FontsR.font.ducksansproduct_regular, FontWeight.Normal),
        Font(FontsR.font.ducksansproduct_medium, FontWeight.Medium),
        Font(FontsR.font.ducksansproduct_bold, FontWeight.Bold),
    )

    private val SmallTextStyle = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    )

    private val LargeTextStyle = SmallTextStyle.copy(fontSize = 16.sp)

    private val DuckSansTextStyle = TextStyle(
        fontSize = 18.sp,
        fontFamily = DuckSansProduct,
        fontWeight = FontWeight.Normal,
    )

    val shape: Shape = RoundedCornerShape(percent = 50)

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

    fun surfaceHeight(size: DaxButtonSize): Dp = when (size) {
        DaxButtonSize.Small -> 40.dp
        DaxButtonSize.Large -> 56.dp
    }

    fun textStyle(
        size: DaxButtonSize,
        typography: DaxBrandButtonTypography,
    ): DuckDuckGoTextStyle = DuckDuckGoTextStyle(
        when (typography) {
            DaxBrandButtonTypography.DuckSans -> DuckSansTextStyle
            DaxBrandButtonTypography.Standard -> when (size) {
                DaxButtonSize.Small -> SmallTextStyle
                DaxButtonSize.Large -> LargeTextStyle
            }
        },
    )

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
        return remember(color) { RippleConfiguration(color = color) }
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
