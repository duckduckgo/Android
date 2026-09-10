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

package com.duckduckgo.app.cta.ui

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboarding.ui.page.OnboardingDecorationSizing
import com.duckduckgo.common.ui.view.shape.DaxOnboardingBubbleCardView
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.isTablet

internal class WavingDaxController(
    private val showArrow: Boolean,
    private val deviceInfo: DeviceInfo,
    private val wavingDaxSpec: DaxBubbleCta.WavingDaxSpec,
) {

    private var fitArrowAnimator: ValueAnimator? = null
    private var lastDaxFits: Boolean? = null
    private var container: View? = null
    private val fitRunnable = Runnable { container?.let { decideFit(it) } }

    fun applyFit(container: View) {
        this.container = container
        container.removeCallbacks(fitRunnable)
        val height = computeDaxFitHeight(container)
        if (height == null) {
            applyFitResult(container, false)
        } else {
            applyDaxHeight(container, height)
            container.postDelayed(fitRunnable, FIT_SETTLE_DELAY_MS)
        }
    }

    fun cancel(container: View) {
        fitArrowAnimator?.removeAllUpdateListeners()
        fitArrowAnimator?.cancel()
        fitArrowAnimator = null
        lastDaxFits = null
        container.removeCallbacks(fitRunnable)
        this.container = null
    }

    fun reset() {
        lastDaxFits = null
    }

    /**
     * Height for a bottom-anchored waving Dax based on available room. Pure so it can be unit-tested without a device.
     */
    internal fun daxFitHeight(
        usableBottom: Int,
        cardBottom: Int,
        marginPx: Int,
        minHeightPx: Int,
        maxHeightPx: Int,
    ): Int? = OnboardingDecorationSizing.fitHeight(
        availablePx = usableBottom - cardBottom - marginPx,
        minHeightPx = minHeightPx,
        maxHeightPx = maxHeightPx,
    )

    private fun decideFit(container: View) {
        val height = computeDaxFitHeight(container)
        if (height == null) {
            applyFitResult(container, false)
            return
        }
        applyDaxHeight(container, height)
        applyFitResult(container, true)
    }

    private fun applyDaxHeight(container: View, height: Int) {
        val dax = container.findViewById<LottieAnimationView>(R.id.wavingDax) ?: return
        val density = dax.resources.displayMetrics.density
        val maxHeightPx = (wavingDaxSpec.maxHeightDp * density).toInt()
        val scale = daxHorizontalScale(height, maxHeightPx)
        dax.translationX = wavingDaxSpec.translationXDp * density * scale
        if (dax.layoutParams.height != height) {
            dax.layoutParams = dax.layoutParams.apply { this.height = height }
        }
    }

    /**
     * Fraction by which to scale the Dax's horizontal peek so it shrinks in step with its height.
     */
    internal fun daxHorizontalScale(heightPx: Int, maxHeightPx: Int): Float =
        if (maxHeightPx > 0) heightPx.toFloat() / maxHeightPx else 1f

    private fun computeDaxFitHeight(container: View): Int? {
        if (!container.isShown || container.isPhoneLandscape()) return null

        val cardView = container.findViewById<DaxOnboardingBubbleCardView>(R.id.brandDesignCardView) ?: return null

        val density = container.resources.displayMetrics.density
        val marginPx = (DAX_FIT_MARGIN_DP * density).toInt()
        val minHeightPx = (wavingDaxSpec.minHeightDp * density).toInt()
        val maxHeightPx = (wavingDaxSpec.maxHeightDp * density).toInt()

        val cardLoc = IntArray(2)
        cardView.getLocationOnScreen(cardLoc)
        val cardBottom = cardLoc[1] + cardView.height

        val visibleFrame = Rect()
        container.getWindowVisibleDisplayFrame(visibleFrame)
        val usableBottom = visibleFrame.bottom

        return daxFitHeight(
            usableBottom = usableBottom,
            cardBottom = cardBottom,
            marginPx = marginPx,
            minHeightPx = minHeightPx,
            maxHeightPx = maxHeightPx,
        )
    }

    private fun applyFitResult(container: View, fits: Boolean) {
        if (fits == lastDaxFits) return
        lastDaxFits = fits

        val dax = container.findViewById<LottieAnimationView>(R.id.wavingDax) ?: return
        val cardView = container.findViewById<DaxOnboardingBubbleCardView>(R.id.brandDesignCardView) ?: return

        if (fits) {
            if (!dax.isVisible) {
                dax.setMinFrame(DAX_WAVE_START_FRAME)
                dax.progress = 0f
                dax.isVisible = true
                dax.post { dax.playAnimation() }
            }
        } else {
            dax.isInvisible = true
        }

        val finTarget = if (fits && showArrow) 1f else 0f
        val current = cardView.arrowDepthFraction
        fitArrowAnimator?.removeAllUpdateListeners()
        fitArrowAnimator?.cancel()
        if (current != finTarget) {
            fitArrowAnimator = buildArrowDepthAnimator(cardView, current, finTarget).apply { start() }
        } else {
            cardView.setArrowDepthFraction(finTarget)
        }
    }

    private fun buildArrowDepthAnimator(
        cardView: DaxOnboardingBubbleCardView,
        from: Float,
        to: Float,
    ): ValueAnimator =
        ValueAnimator.ofFloat(from, to).apply {
            duration = ARROW_DEPTH_ANIMATION_DURATION
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { cardView.setArrowDepthFraction(it.animatedValue as Float) }
        }

    private fun View.isPhoneLandscape(): Boolean =
        !deviceInfo.isTablet() &&
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    companion object {
        private const val FIT_SETTLE_DELAY_MS = 100L
        private const val DAX_WAVE_START_FRAME = 17
        private const val ARROW_DEPTH_ANIMATION_DURATION = 200L
        private const val DAX_FIT_MARGIN_DP = 8
    }
}
