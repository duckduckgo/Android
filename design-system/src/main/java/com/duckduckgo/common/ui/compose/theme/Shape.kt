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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.dimensionResource
import com.duckduckgo.mobile.android.R

/**
 * Default shapes for DuckDuckGo theme, using dimensions from resources to match values used in View/XML layouts.
 *
 * Figma: https://www.figma.com/design/jHLwh4erLbNc2YeobQpGFt/Design-System-Guidelines?node-id=8796-21531
 */
val Shapes
    @Composable
    @ReadOnlyComposable
    get() = DuckDuckGoShapes(
        small = RoundedCornerShape(dimensionResource(R.dimen.smallShapeCornerRadius)),
        medium = RoundedCornerShape(dimensionResource(R.dimen.mediumShapeCornerRadius)),
        large = RoundedCornerShape(dimensionResource(R.dimen.largeShapeCornerRadius)),
    )

@Immutable
data class DuckDuckGoShapes(
    val small: Shape,
    val medium: Shape,
    val large: Shape,
)

@SuppressLint("ComposeCompositionLocalUsage")
val LocalDuckDuckGoShapes = staticCompositionLocalOf<DuckDuckGoShapes> {
    error("No DuckDuckGoShapes provided")
}
