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

package com.duckduckgo.common.ui.compose.theme

import android.annotation.SuppressLint
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.duckduckgo.mobile.android.R

/**
 * Default typography for DuckDuckGo theme.
 *
 * Figma: https://www.figma.com/design/jHLwh4erLbNc2YeobQpGFt/Design-System-Guidelines?node-id=1313-19967
 */

private val RobotoMono = FontFamily(
    Font(R.font.roboto_mono, FontWeight.Normal),
)

@Immutable
data class DuckDuckGoTypography(

    val title: DuckDuckGoTextStyle =
        DuckDuckGoTextStyle(
            TextStyle(
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
            ),
        ),

    val h1: DuckDuckGoTextStyle =
        DuckDuckGoTextStyle(
            TextStyle(
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
            ),
        ),

    val h2: DuckDuckGoTextStyle =
        DuckDuckGoTextStyle(
            TextStyle(
                fontSize = 20.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.3.sp,
                fontWeight = FontWeight.Medium,
            ),
        ),

    val h3: DuckDuckGoTextStyle =
        DuckDuckGoTextStyle(
            TextStyle(
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
            ),
        ),

    val h4: DuckDuckGoTextStyle =
        DuckDuckGoTextStyle(
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.3.sp,
                fontWeight = FontWeight.Medium,
            ),
        ),

    val h5: DuckDuckGoTextStyle =
        DuckDuckGoTextStyle(
            TextStyle(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        ),

    val body1: DuckDuckGoTextStyle = DuckDuckGoTextStyle(
        TextStyle(
            fontSize = 16.sp,
            lineHeight = 20.sp,
        ),
    ),

    val body1Bold: DuckDuckGoTextStyle = DuckDuckGoTextStyle(
        body1.textStyle.copy(
            fontWeight = FontWeight.Bold,
        ),
    ),

    val body1Mono: DuckDuckGoTextStyle = DuckDuckGoTextStyle(
        body1.textStyle.copy(
            fontFamily = RobotoMono,
        ),
    ),

    val body2: DuckDuckGoTextStyle = DuckDuckGoTextStyle(
        TextStyle(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.2.sp,
        ),
    ),

    val body2Bold: DuckDuckGoTextStyle = DuckDuckGoTextStyle(
        body2.textStyle.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        ),
    ),

    val button: DuckDuckGoTextStyle = DuckDuckGoTextStyle(
        TextStyle(
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
        ),
    ),

    val caption: DuckDuckGoTextStyle = DuckDuckGoTextStyle(
        TextStyle(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp,
        ),
    ),
)

val Typography = DuckDuckGoTypography()

@SuppressLint("ComposeCompositionLocalUsage")
val LocalDuckDuckGoTypography = staticCompositionLocalOf<DuckDuckGoTypography> {
    error("No DuckDuckGoTypography provided")
}

@JvmInline
@Immutable
value class DuckDuckGoTextStyle internal constructor(
    internal val textStyle: TextStyle,
)

// Internal extension to extract TextStyle - only accessible within the design system
internal val DuckDuckGoTextStyle.asTextStyle: TextStyle
    get() = textStyle
