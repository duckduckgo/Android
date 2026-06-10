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

package com.duckduckgo.app.browser.animations

import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

private const val ANIMATION_DURATION = 300L
private const val SLIDE_OFFSET_RATIO = 0.33f

fun View.slideAndFadeInFromLeft(onComplete: (() -> Unit)? = null) = startSlideAndFadeAnimation(
    translationXFrom = -width.toFloat() * SLIDE_OFFSET_RATIO,
    translationXTo = 0f,
    alphaFrom = 0f,
    alphaTo = 1.0f,
    onComplete = onComplete,
)

fun View.slideAndFadeOutToLeft(onComplete: (() -> Unit)? = null) = startSlideAndFadeAnimation(
    translationXFrom = 0f,
    translationXTo = -width.toFloat() * SLIDE_OFFSET_RATIO,
    alphaFrom = 1f,
    alphaTo = 0f,
    onComplete = onComplete,
)

fun View.slideAndFadeInFromRight(onComplete: (() -> Unit)? = null) = startSlideAndFadeAnimation(
    translationXFrom = width.toFloat() * SLIDE_OFFSET_RATIO,
    translationXTo = 0f,
    alphaFrom = 0f,
    alphaTo = 1.0f,
    onComplete = onComplete,
)

fun View.slideAndFadeOutToRight(onComplete: (() -> Unit)? = null) = startSlideAndFadeAnimation(
    translationXFrom = 0f,
    translationXTo = width.toFloat() * SLIDE_OFFSET_RATIO,
    alphaFrom = 1f,
    alphaTo = 0.0f,
    onComplete = onComplete,
)

private fun View.startSlideAndFadeAnimation(
    translationXFrom: Float,
    translationXTo: Float,
    alphaFrom: Float,
    alphaTo: Float,
    onComplete: (() -> Unit)? = null,
) {
    translationX = translationXFrom
    alpha = alphaFrom

    animate()
        .translationX(translationXTo)
        .alpha(alphaTo)
        .setDuration(ANIMATION_DURATION)
        .setInterpolator(FastOutSlowInInterpolator())
        .apply { onComplete?.let { withEndAction(it) } }
        .start()
}
