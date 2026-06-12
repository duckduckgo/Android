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
) {

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
        val deco = decoration ?: return true
        if (deco.isGone) return true
        if (root.height == 0) return true
        if (BrandDesignUpdateOnboardingLayoutHelper.isInScrollableContainer(dialog, root)) return true

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
        val dialogSpace = dialogHeight + overflow + dialogParams.topMargin + dialogParams.bottomMargin
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
            dialog.updateLayoutParams<ConstraintLayout.LayoutParams> {
                verticalBias = 0f
                bottomToTop = ConstraintLayout.LayoutParams.UNSET
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
            onDecorationHidden()
            return false
        }

        if (target < decorationParams.height) {
            deco.updateLayoutParams { height = target }
            return false
        }

        return true
    }
}
