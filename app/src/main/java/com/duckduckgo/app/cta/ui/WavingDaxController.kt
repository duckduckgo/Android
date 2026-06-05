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
import android.graphics.RectF
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.view.shape.DaxOnboardingBubbleBrandDesignUpdateCardView
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.isTablet
import kotlin.math.abs

/**
 * Keeps the waving Dax below a new-tab bubble in sync with the space available: decides whether the
 * Dax fits beneath the card, drives its show/hide, and is the single writer of the shark-fin depth.
 *
 * Hide eagerly, reveal lazily: a hide (does not fit) applies immediately so the Dax never lingers
 * as the keyboard rises; a reveal (fits) is debounced by [FIT_SETTLE_DELAY_MS] so transient
 * measurements during a content transition or keyboard settle never flash the Dax/fin in then out.
 */
internal class WavingDaxController(
    private val showArrow: Boolean,
    private val deviceInfo: DeviceInfo,
) {

    private var fitArrowAnimator: ValueAnimator? = null
    private var lastDaxFits: Boolean? = null
    private var container: View? = null
    private val fitRunnable = Runnable { container?.let { decideFit(it) } }

    fun applyFit(container: View) {
        this.container = container
        container.removeCallbacks(fitRunnable)
        val fits = computeDaxFits(container) ?: return
        if (fits) {
            container.postDelayed(fitRunnable, FIT_SETTLE_DELAY_MS)
        } else {
            applyFitResult(container, false)
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
     * Pure fit test. The Dax fits unless its head intrudes into the card body (above the
     * top-of-fin line) or its rect overlaps the fin's own region. The Dax may sit in the
     * fin's vertical band as long as it is horizontally clear of the fin.
     */
    internal fun daxFits(
        daxTop: Int,
        daxLeft: Int,
        daxRight: Int,
        cardBodyBottom: Int,
        finBottom: Int,
        finLeft: Int,
        finRight: Int,
    ): Boolean {
        if (daxTop < cardBodyBottom) return false
        val overlapsFinHorizontally = daxRight > finLeft && daxLeft < finRight
        if (daxTop < finBottom && overlapsFinHorizontally) return false
        return true
    }

    /**
     * Half the gap between a rotated view's axis-aligned bounding box and its true content size on one
     * axis. A rotated Dax's bounding box gains empty triangular corners; subtracting this inset from each
     * edge recovers the content box so [daxFits] tests the artwork rather than a corner that merely grazes
     * the card (which otherwise hid the rotated subscription Dax in landscape). Zero for a non-rotated Dax.
     */
    internal fun rotationInset(boundingBoxExtent: Float, contentExtent: Float): Float =
        ((boundingBoxExtent - contentExtent) / 2f).coerceAtLeast(0f)

    private fun decideFit(container: View) {
        if (computeDaxFits(container) == true) applyFitResult(container, true)
    }

    private fun computeDaxFits(container: View): Boolean? {
        if (!container.isShown || container.isPhoneLandscape()) return null

        val dax = container.findViewById<LottieAnimationView>(R.id.wavingDax) ?: return null
        val cardView = container.findViewById<DaxOnboardingBubbleBrandDesignUpdateCardView>(R.id.brandDesignCardView) ?: return null
        val finBounds = cardView.arrowBounds() ?: return null

        val daxRect = RectF()
        viewBoundsOnScreen(dax, daxRect)

        val insetX = rotationInset(daxRect.width(), dax.width * abs(dax.scaleX))
        val insetY = rotationInset(daxRect.height(), dax.height * abs(dax.scaleY))

        val cardLoc = IntArray(2)
        cardView.getLocationOnScreen(cardLoc)

        return daxFits(
            daxTop = (daxRect.top + insetY).toInt(),
            daxLeft = (daxRect.left + insetX).toInt(),
            daxRight = (daxRect.right - insetX).toInt(),
            cardBodyBottom = cardLoc[1] + finBounds.top.toInt(),
            finBottom = cardLoc[1] + finBounds.bottom.toInt(),
            finLeft = cardLoc[0] + finBounds.left.toInt(),
            finRight = cardLoc[0] + finBounds.right.toInt(),
        )
    }

    private fun applyFitResult(container: View, fits: Boolean) {
        if (fits == lastDaxFits) return
        lastDaxFits = fits

        val dax = container.findViewById<LottieAnimationView>(R.id.wavingDax) ?: return
        val cardView = container.findViewById<DaxOnboardingBubbleBrandDesignUpdateCardView>(R.id.brandDesignCardView) ?: return

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
        cardView: DaxOnboardingBubbleBrandDesignUpdateCardView,
        from: Float,
        to: Float,
    ): ValueAnimator =
        ValueAnimator.ofFloat(from, to).apply {
            duration = ARROW_DEPTH_ANIMATION_DURATION
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { cardView.setArrowDepthFraction(it.animatedValue as Float) }
        }

    private fun viewBoundsOnScreen(view: View, out: RectF) {
        out.set(0f, 0f, view.width.toFloat(), view.height.toFloat())
        view.matrix.mapRect(out)
        val loc = IntArray(2)
        (view.parent as? View)?.getLocationOnScreen(loc)
        out.offset((loc[0] + view.left).toFloat(), (loc[1] + view.top).toFloat())
    }

    private fun View.isPhoneLandscape(): Boolean =
        !deviceInfo.isTablet() &&
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    companion object {
        private const val FIT_SETTLE_DELAY_MS = 100L
        private const val DAX_WAVE_START_FRAME = 17
        private const val ARROW_DEPTH_ANIMATION_DURATION = 200L
    }
}
