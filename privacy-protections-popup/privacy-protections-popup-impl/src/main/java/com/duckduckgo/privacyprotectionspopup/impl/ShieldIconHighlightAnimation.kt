/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.privacyprotectionspopup.impl

import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation

fun buildShieldIconHighlightAnimation(): Animation {
    val fadeInAnimation = AlphaAnimation(0.0f, 1.0f).apply {
        duration = 500
        startOffset = 500
    }

    val scaleAnimation = ScaleAnimation(
        1.0f, // Start scale for X
        1.2f, // End scale for X
        1.0f, // Start scale for Y
        1.2f, // End scale for Y
        Animation.RELATIVE_TO_SELF,
        0.5f, // Pivot X at the center
        Animation.RELATIVE_TO_SELF,
        0.5f, // Pivot Y at the center
    ).apply {
        duration = 800
        repeatCount = Animation.INFINITE
        repeatMode = Animation.REVERSE
    }

    return AnimationSet(false).apply {
        addAnimation(fadeInAnimation)
        addAnimation(scaleAnimation)
    }
}
