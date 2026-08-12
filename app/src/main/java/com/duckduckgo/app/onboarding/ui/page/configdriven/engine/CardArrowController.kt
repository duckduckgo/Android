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

package com.duckduckgo.app.onboarding.ui.page.configdriven.engine

import android.animation.ValueAnimator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.duckduckgo.app.onboarding.ui.page.configdriven.CardArrowConfig
import com.duckduckgo.common.ui.view.shape.DaxOnboardingBubbleBrandDesignUpdateCardView
import com.duckduckgo.common.ui.view.toPx

interface CardArrowController {
    fun apply(previous: CardArrowConfig?, next: CardArrowConfig, animate: Boolean)
    fun skipRunning()
    fun release()
}

/**
 * Owns the card's bubble arrow: whether it shows, and where along the card's edge it sits. The position only
 * animates across a transition that actually moves it; every other render snaps it.
 */
class CardArrowControllerImpl(
    private val cardView: DaxOnboardingBubbleBrandDesignUpdateCardView,
) : CardArrowController {

    private var slide: ValueAnimator? = null

    override fun apply(
        previous: CardArrowConfig?,
        next: CardArrowConfig,
        animate: Boolean,
    ) {
        slide?.cancel()
        slide = null

        cardView.setShowArrow(next != CardArrowConfig.Hidden)
        cardView.setArrowAnimationTarget(ARROW_TARGET_OFFSET_END_DP.toPx().toFloat())

        val target = if (next == CardArrowConfig.AtEnd) 1f else 0f
        val moves = previous != null && previous != next &&
            previous != CardArrowConfig.Hidden && next != CardArrowConfig.Hidden
        if (animate && moves) {
            slide = ValueAnimator.ofFloat(1f - target, target).apply {
                duration = SLIDE_DURATION_MS
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { cardView.setArrowAnimationFraction(it.animatedValue as Float) }
                start()
            }
        } else {
            cardView.setArrowAnimationFraction(target)
        }
    }

    override fun skipRunning() {
        slide?.end()
        slide = null
    }

    override fun release() {
        slide?.cancel()
        slide = null
    }

    private companion object {
        const val ARROW_TARGET_OFFSET_END_DP = 80
        const val SLIDE_DURATION_MS = 400L
    }
}
