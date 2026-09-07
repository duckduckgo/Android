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

package com.duckduckgo.feedback.impl.ui.common

import androidx.annotation.DrawableRes
import com.duckduckgo.feedback.impl.R

@DrawableRes
internal fun resolveFeedbackButtonAsset(
    isPositive: Boolean,
    isLightMode: Boolean,
    isPictogramsEnabled: Boolean,
): Int = when {
    isPictogramsEnabled && isPositive -> R.drawable.response_good_56
    isPictogramsEnabled -> R.drawable.response_bad_56
    isPositive && isLightMode -> R.drawable.button_happy_light_theme
    isPositive -> R.drawable.button_happy_dark_theme
    isLightMode -> R.drawable.button_sad_light_theme
    else -> R.drawable.button_sad_dark_theme
}

@DrawableRes
internal fun resolveFeedbackFaceAsset(
    isPositive: Boolean,
    isPictogramsEnabled: Boolean,
): Int = when {
    isPictogramsEnabled && isPositive -> R.drawable.response_good_56
    isPictogramsEnabled -> R.drawable.response_bad_56
    isPositive -> R.drawable.ic_happy_face
    else -> R.drawable.ic_sad_face
}
