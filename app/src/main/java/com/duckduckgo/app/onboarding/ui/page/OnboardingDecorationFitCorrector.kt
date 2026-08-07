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

package com.duckduckgo.app.onboarding.ui.page

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.VisibleForTesting
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams

class OnboardingDecorationFitCorrector(
    private val root: View,
    private val dialog: View,
    private val cardContainer: View,
    private val onDecorationHidden: () -> Unit = {},
    private val cardBottomInsetPx: () -> Int = { 0 },
) {

    // TODO: remove when onboardingImprovementsV2 flag is removed
    var enabled: Boolean = false

    /**
     * Whether a card anchored above a decoration still reserves the part of its bottom inset that the decoration
     * does not already cover. Only the config-driven renderer sets this: its undecorated band anchors the card on
     * screens the legacy renderer pinned to the parent bottom, so without this the card has no way to clear an
     * inset taller than the band, i.e. the keyboard.
     *
     * The whole reservation hangs off this flag, so the legacy renderer's geometry is untouched: the reserved
     * inset stays out of the decoration's fit, and the card's span above the decoration is what clamps it.
     */
    var reservesInsetAboveDecoration: Boolean = false

    private var decoration: View? = null
    private var minHeightPx = 0
    private var maxHeightPx = 0
    private var bottomOverlapPx = 0
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    fun attach() {
        if (preDrawListener != null) return
        val listener = ViewTreeObserver.OnPreDrawListener { correctOnce() }
        preDrawListener = listener
        root.viewTreeObserver.addOnPreDrawListener(listener)
    }

    fun detach() {
        preDrawListener?.let { root.viewTreeObserver.removeOnPreDrawListener(it) }
        preDrawListener = null
    }

    fun track(
        decoration: View,
        minHeightPx: Int,
        maxHeightPx: Int,
        bottomOverlapPx: Int = 0,
    ) {
        this.decoration = decoration
        this.minHeightPx = minHeightPx
        this.maxHeightPx = maxHeightPx
        this.bottomOverlapPx = bottomOverlapPx
    }

    fun clear() {
        decoration = null
    }

    @VisibleForTesting
    fun correctOnce(): Boolean {
        if (root.height == 0) return true

        val deco = decoration
        val decorationShown = deco != null && !deco.isGone
        // The card reserves the bottom-bar inset when it is the bottom-most element: bottom-anchored AND no
        // decoration shown below it. A shown decoration is at least its min height, which exceeds any bar
        // inset, so it covers the bar for the card; reserving the inset then would feed dialogSpace and hide
        // that very decoration. See [reservesInsetAboveDecoration] for the insets a decoration cannot cover.
        if (syncCardBottomInset(decorationShown)) return false

        // While onboardingImprovementsV2 is off the corrector stays inert: syncCardBottomInset above
        // has already reverted any reserved inset, and the shrink/hide logic below must not run.
        if (!enabled) return true

        if (deco == null) return !syncCardClamp()
        if (deco.isGone) return true
        if (BrandDesignUpdateOnboardingLayoutHelper.isInScrollableContainer(dialog, root)) return true
        if (reservesInsetAboveDecoration && syncCardClamp()) return false

        val viewport = cardContainer.parent as? View ?: return true

        // Use measured (settled) heights, not laid-out heights: during an inter-dialog ChangeBounds the
        // laid-out height is mid-animation and still reports the previous, taller dialog, which would
        // wrongly hide the decoration. measuredHeight reflects the post-transition target throughout.
        val dialogHeight = dialog.measuredHeight.takeIf { it > 0 } ?: dialog.height
        val viewportHeight = viewport.measuredHeight.takeIf { it > 0 } ?: viewport.height
        val cardContainerHeight = cardContainer.measuredHeight.takeIf { it > 0 } ?: cardContainer.height
        if (dialogHeight == 0 || viewportHeight == 0) return true

        val overflow = (cardContainerHeight - viewportHeight).coerceAtLeast(0)
        val available = root.height - root.paddingTop - root.paddingBottom
        val dialogParams = dialog.layoutParams as ViewGroup.MarginLayoutParams
        // An inset reserved above the decoration buys room the decoration is not competing for: a keyboard
        // overlays the window rather than shrinking it, so `available` already covers the room the card gave up.
        // Counting it would shrink the decoration, which grows the inset, which shrinks the decoration, until it
        // is gone. Without that reservation the margin is 0 here anyway, a decoration being shown.
        val cardBottomMargin = if (reservesInsetAboveDecoration) 0 else dialogParams.bottomMargin
        val dialogSpace = dialogHeight + overflow + dialogParams.topMargin + cardBottomMargin
        val decorationParams = deco.layoutParams as ViewGroup.MarginLayoutParams

        val target = BrandDesignUpdateOnboardingLayoutHelper.computeDecorationHeight(
            availableContentHeight = available,
            dialogSpace = dialogSpace,
            decorationBottomMargin = decorationParams.bottomMargin,
            maxHeightPx = maxHeightPx,
            minHeightPx = minHeightPx,
            bottomOverlapPx = bottomOverlapPx,
        )

        if (target == null) {
            deco.isGone = true
            onDecorationHidden()
            return false
        }

        if (target < decorationParams.height) {
            deco.updateLayoutParams { height = target }
            return false
        }

        return true
    }

    private fun syncCardBottomInset(decorationShown: Boolean): Boolean {
        val params = dialog.layoutParams as? ConstraintLayout.LayoutParams ?: return false
        val desired = desiredCardBottomInset(params, decorationShown)
        if (params.bottomMargin == desired) return false
        dialog.updateLayoutParams<ConstraintLayout.LayoutParams> { bottomMargin = desired }
        return true
    }

    private fun desiredCardBottomInset(
        params: ConstraintLayout.LayoutParams,
        decorationShown: Boolean,
    ): Int {
        if (!enabled) return 0
        if (params.bottomToBottom == ConstraintLayout.LayoutParams.PARENT_ID) {
            return if (decorationShown) 0 else cardBottomInsetPx()
        }
        val deco = decoration?.takeIf { decorationShown && reservesInsetAboveDecoration } ?: return 0
        return (cardBottomInsetPx() - roomBelowCard(deco)).coerceAtLeast(0)
    }

    private fun roomBelowCard(decoration: View): Int {
        val params = decoration.layoutParams as ViewGroup.MarginLayoutParams
        // The applied height, not the laid-out one: the corrector runs before the frame that lays the shrink out.
        val height = params.height.takeIf { it > 0 } ?: decoration.measuredHeight
        return height + params.bottomMargin
    }

    // Clamp only on overflow: constrainedHeight rounds a fitting wrap down a pixel → a permanent scrollbar.
    private fun syncCardClamp(): Boolean {
        val params = dialog.layoutParams as? ConstraintLayout.LayoutParams ?: return false
        val roomBelowCard = when {
            params.bottomToBottom == ConstraintLayout.LayoutParams.PARENT_ID -> 0
            // Anchored above a decoration: unclamped the card overruns it instead of scrolling, so a card lifted
            // clear of the keyboard would still spill under it.
            params.bottomToTop != ConstraintLayout.LayoutParams.UNSET ->
                decoration?.takeIf { !it.isGone }?.let { roomBelowCard(it) } ?: return false
            else -> return false
        }
        val content = cardContainer.measuredHeight.takeIf { it > 0 } ?: return false
        val span = root.height - root.paddingTop - root.paddingBottom -
            params.topMargin - params.bottomMargin - roomBelowCard
        val shouldClamp = dialog.paddingTop + content > span
        if (params.constrainedHeight == shouldClamp) return false
        dialog.updateLayoutParams<ConstraintLayout.LayoutParams> { constrainedHeight = shouldClamp }
        return true
    }
}
