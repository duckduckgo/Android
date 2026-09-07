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
 * Owns the card's bubble arrow: whether it shows, where along the card's edge it sits, and which way its tail
 * hooks. Either property only animates across a transition that actually changes it; every other render snaps
 * it. When a transition changes both, one animator drives them so the tail travels and reflects as a single
 * gesture instead of the reflection popping before the slide.
 */
class CardArrowControllerImpl(
    private val cardView: DaxOnboardingBubbleBrandDesignUpdateCardView,
) : CardArrowController {

    private var transition: ValueAnimator? = null

    override fun apply(
        previous: CardArrowConfig?,
        next: CardArrowConfig,
        animate: Boolean,
    ) {
        transition?.cancel()
        transition = null

        cardView.setShowArrow(next != CardArrowConfig.Hidden)
        cardView.setArrowAnimationTarget(ARROW_TARGET_OFFSET_END_DP.toPx().toFloat())

        val positionTarget = if (next.atEnd) 1f else 0f
        val mirrorTarget = if (next.mirrored) 1f else 0f

        val from = previous?.takeIf { it != CardArrowConfig.Hidden && next != CardArrowConfig.Hidden }
        val moves = animate && from != null && from.atEnd != next.atEnd
        val flips = animate && from != null && from.mirrored != next.mirrored

        if (!moves) cardView.setArrowAnimationFraction(positionTarget)
        if (!flips) cardView.setArrowMirrorFraction(mirrorTarget)

        if (moves || flips) {
            transition = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SLIDE_DURATION_MS
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener {
                    val progress = it.animatedValue as Float
                    if (moves) cardView.setArrowAnimationFraction(lerp(1f - positionTarget, positionTarget, progress))
                    if (flips) cardView.setArrowMirrorFraction(lerp(1f - mirrorTarget, mirrorTarget, progress))
                }
                start()
            }
        }
    }

    override fun skipRunning() {
        transition?.end()
        transition = null
    }

    override fun release() {
        transition?.cancel()
        transition = null
    }

    private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

    private companion object {
        const val ARROW_TARGET_OFFSET_END_DP = 80
        const val SLIDE_DURATION_MS = 400L
    }
}
