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

package com.duckduckgo.app.browser.omnibar.animations.addressbar

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import javax.inject.Inject

class CommonAddressBarAnimationHelper @Inject constructor() {

    fun animateViewsOut(views: List<View>, durationInMs: Long = DEFAULT_ANIMATION_DURATION): AnimatorSet {
        val animators = views.map {
            animateFadeOut(it, durationInMs)
        }
        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    fun animateViewsIn(views: List<View>, durationInMs: Long = DEFAULT_ANIMATION_DURATION): AnimatorSet {
        val animators = views.map {
            animateFadeIn(it, durationInMs)
        }
        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    fun animateFadeOut(
        view: View,
        durationInMs: Long = DEFAULT_ANIMATION_DURATION,
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            duration = durationInMs
        }
    }

    fun animateFadeIn(
        view: View,
        durationInMs: Long = DEFAULT_ANIMATION_DURATION,
    ): ObjectAnimator {
        if (view.alpha == 1f) {
            return ObjectAnimator.ofFloat(view, "alpha", 1f, 1f).apply {
                duration = durationInMs
            }
        }

        return ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = durationInMs
        }
    }

    companion object {
        const val DEFAULT_ANIMATION_DURATION = 150L
    }
}
