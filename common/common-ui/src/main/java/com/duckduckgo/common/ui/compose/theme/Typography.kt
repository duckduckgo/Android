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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class DuckDuckGoTypography(

    val defaultTextColor: Color,

    val title: TextStyle = TextStyle(
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        color = defaultTextColor,
    ),

    val h1: TextStyle = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        color = defaultTextColor,
    ),

    val h2: TextStyle = TextStyle(
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        color = defaultTextColor,
    ),

    val h3: TextStyle= TextStyle(
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
        color = defaultTextColor,
    ),

    val h4: TextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        color = defaultTextColor,
    ),

    val h5: TextStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        color = defaultTextColor,
    ),

    val body1: TextStyle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = defaultTextColor,
    ),

    val body2: TextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = defaultTextColor,
    ),

    val button: TextStyle = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
        color = defaultTextColor,
    ),

    val caption: TextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = defaultTextColor,
    ),
)

val LocalDuckDuckGoTypography = staticCompositionLocalOf<DuckDuckGoTypography> {
    error("No DuckDuckGoTypography provided")
}
