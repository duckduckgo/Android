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

package com.duckduckgo.serp.logos.api

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Animations for SERP Easter Egg logos in the omnibar.
 *
 * This object is stateless - callers are responsible for managing the returned
 * [ObjectAnimator] if they need to cancel it.
 */
object SerpEasterEggLogoAnimator {

    private const val WIGGLE_DURATION_MS = 500L

    /**
     * Wiggles the view back and forth with damping. 500ms duration.
     *
     * @param view The view to animate
     * @return The [ObjectAnimator] that was started. Caller can use this to cancel if needed.
     */
    fun playWiggle(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(
            view,
            View.ROTATION,
            0f, -12f, 12f, -8f, 8f, -4f, 4f, 0f,
        ).apply {
            duration = WIGGLE_DURATION_MS
            interpolator = LinearInterpolator()
            start()
        }
    }
}
