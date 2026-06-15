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

package com.duckduckgo.app.browser.nativeinput

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.duckduckgo.app.browser.R
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.card.MaterialCardView
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

data class Margins(val top: Int, val bottom: Int)

interface NativeInputAnimator {
    fun init(widgetCard: View, omnibarCard: View, omnibarWidth: Int, omnibarHeight: Int, isBottom: Boolean): Margins?
    fun animateEnter(
        widgetCard: View,
        omnibarCard: View,
        widgetView: View,
        margins: Margins,
        onUpdate: (fraction: Float) -> Unit = {},
        onCancel: () -> Unit = {},
        onComplete: () -> Unit = {},
    )
    fun animateExit(
        widgetCard: View,
        widgetView: View,
        omnibarCard: View,
        isBottom: Boolean,
        onUpdate: (fraction: Float) -> Unit = {},
        onCancel: () -> Unit = {},
        onComplete: () -> Unit,
    )
    fun cancelAnimation()
    fun applyLayoutTransitions(widgetView: View)
    fun applyLayoutTransitions(widgetView: View, isBottom: Boolean)
    fun clearLayoutTransitions(widgetView: View)
}

@ContributesBinding(FragmentScope::class)
class RealNativeInputAnimator @Inject constructor() : NativeInputAnimator {

    private var transitionAnimator: ValueAnimator? = null
    private var animationCleanup: (() -> Unit)? = null
    private var pendingPreDraw: Pair<View, ViewTreeObserver.OnPreDrawListener>? = null

    private var omnibarCornerRadius = 0f
    private var widgetCornerRadius = 0f
    private var widgetCardElevation = 0f
    private var widgetCompatPaddingWidth = 0
    private var widgetCompatPaddingHeight = 0
    private var isBottomCard = false

    // In bottom mode the card is a weighted LinearLayout child (width=0dp), which ignores an
    // explicit width. We zero the weight for the duration of the morph so the width lerps take
    // effect, saving the original here to restore the resting layout afterwards.
    private var savedCardWeight = 0f

    override fun init(
        widgetCard: View,
        omnibarCard: View,
        omnibarWidth: Int,
        omnibarHeight: Int,
        isBottom: Boolean,
    ): Margins? {
        if (omnibarWidth <= 0 || omnibarHeight <= 0) return null
        val params = widgetCard.layoutParams as? ViewGroup.MarginLayoutParams ?: return null

        captureCardProperties(widgetCard, omnibarCard)
        isBottomCard = isBottom

        val margins = Margins(params.topMargin, params.bottomMargin)

        detachWeightForMorph(params)
        shrinkCardToMatchOmnibar(params, omnibarWidth, omnibarHeight, isBottom)

        animateCornerRadius(widgetCard, omnibarCornerRadius)
        widgetCard.visibility = View.INVISIBLE

        return margins
    }

    override fun animateEnter(
        widgetCard: View,
        omnibarCard: View,
        widgetView: View,
        margins: Margins,
        onUpdate: (fraction: Float) -> Unit,
        onCancel: () -> Unit,
        onComplete: () -> Unit,
    ) {
        cancelAnimation()

        val startWidth = (widgetCard.layoutParams as ViewGroup.MarginLayoutParams).width
        val startHeight = (widgetCard.layoutParams as ViewGroup.MarginLayoutParams).height
        animationCleanup = { omnibarCard.alpha = 1f }

        waitForLayout(widgetCard) {
            val omnibarPosition = visibleSurfacePosition(omnibarCard)
            performEnterAnimation(
                widgetCard,
                omnibarCard,
                widgetView,
                margins,
                startWidth,
                startHeight,
                omnibarPosition,
                onUpdate,
                onCancel,
                onComplete,
            )
        }
    }

    override fun animateExit(
        widgetCard: View,
        widgetView: View,
        omnibarCard: View,
        isBottom: Boolean,
        onUpdate: (fraction: Float) -> Unit,
        onCancel: () -> Unit,
        onComplete: () -> Unit,
    ) {
        cancelAnimation()
        clearLayoutTransitions(widgetView)
        (widgetView as? ViewGroup)?.clipChildren = false
        (widgetView.parent as? ViewGroup)?.clipChildren = false

        val snapshot = snapshotBeforeExit(widgetCard, omnibarCard, isBottom)

        waitForLayout(widgetCard) {
            performExitAnimation(widgetCard, omnibarCard, snapshot, isBottom, onUpdate, onCancel, onComplete)
        }
    }

    private fun snapshotBeforeExit(widgetCard: View, omnibarCard: View, isBottom: Boolean): ExitSnapshot {
        val preSurface = visibleSurfacePosition(widgetCard)
        val omnibarPosition = visibleSurfacePosition(omnibarCard)
        val visibleBounds = stripCompatPadding(widgetCard, isBottom)

        val params = widgetCard.layoutParams as ViewGroup.MarginLayoutParams

        return ExitSnapshot(
            preSurface = preSurface,
            omnibarPosition = omnibarPosition,
            visibleBounds = visibleBounds,
            targetWidth = omnibarCard.width,
            targetHeight = omnibarCard.height,
            params = params,
        )
    }

    private fun performExitAnimation(
        widgetCard: View,
        omnibarCard: View,
        snapshot: ExitSnapshot,
        isBottom: Boolean,
        onUpdate: (fraction: Float) -> Unit,
        onCancel: () -> Unit,
        onComplete: () -> Unit,
    ) {
        val postPosition = windowPosition(widgetCard)
        val holdTranslation = Offset(
            x = (snapshot.preSurface.x - postPosition.x).toFloat(),
            y = (snapshot.preSurface.y - postPosition.y).toFloat(),
        )
        widgetCard.translationX = holdTranslation.x
        widgetCard.translationY = holdTranslation.y

        // For bottom mode the snapshot's omnibarPosition is stale: the omnibar view is GONE
        // during IME-up state and returns its last-known coords. stripCompatPadding preserves
        // the card's bottomMargin in bottom mode, so widgetView's bottom-anchored wrap_content
        // shrinks down to exactly the omnibar's eventual position — translationY=0 lands there.
        val endTranslation = if (isBottom) {
            Offset(x = (snapshot.omnibarPosition.x - postPosition.x).toFloat(), y = 0f)
        } else {
            Offset(
                x = (snapshot.omnibarPosition.x - postPosition.x).toFloat(),
                y = (snapshot.omnibarPosition.y - postPosition.y).toFloat(),
            )
        }

        val widgetContent = widgetCard.findViewById<View?>(R.id.inputModeWidget)
        omnibarCard.alpha = 0f

        runAnimator(
            cleanup = { omnibarCard.alpha = 1f },
            onUpdate = { fraction ->
                snapshot.params.width = lerp(snapshot.visibleBounds.width, snapshot.targetWidth, fraction)
                snapshot.params.height = lerp(snapshot.visibleBounds.height, snapshot.targetHeight, fraction)
                widgetCard.layoutParams = snapshot.params

                widgetCard.translationX = lerpF(holdTranslation.x, endTranslation.x, fraction)
                widgetCard.translationY = lerpF(holdTranslation.y, endTranslation.y, fraction)
                (widgetCard as? MaterialCardView)?.radius = lerpF(widgetCornerRadius, omnibarCornerRadius, fraction)
                widgetContent?.alpha = 1 - fraction
                omnibarCard.alpha = fraction
                onUpdate(fraction)
            },
            onCancel = onCancel,
            onEnd = {
                omnibarCard.alpha = 1f
                onComplete()
            },
        )
    }

    override fun cancelAnimation() {
        pendingPreDraw?.let { (view, listener) -> view.viewTreeObserver.removeOnPreDrawListener(listener) }
        pendingPreDraw = null
        animationCleanup?.invoke()
        animationCleanup = null
        transitionAnimator?.cancel()
        transitionAnimator = null
    }

    override fun applyLayoutTransitions(widgetView: View) {
        applyLayoutTransitions(widgetView, isBottomCard)
    }

    override fun applyLayoutTransitions(widgetView: View, isBottom: Boolean) {
        val changingTransition = {
            LayoutTransition().apply {
                enableTransitionType(LayoutTransition.CHANGING)
                setDuration(ANIMATION_DURATION_MS)
                setAnimateParentHierarchy(false)
            }
        }
        widgetView.findViewById<ViewGroup?>(R.id.inputModeWidgetCard)?.layoutTransition = changingTransition()
        widgetView.findViewById<ViewGroup?>(R.id.inputModeWidget)?.layoutTransition = changingTransition()
        if (isBottom) {
            (widgetView as? ViewGroup)?.layoutTransition = changingTransition()
        }
    }

    private fun captureCardProperties(widgetCard: View, omnibarCard: View) {
        widgetCornerRadius = (widgetCard as? MaterialCardView)?.radius ?: 0f
        omnibarCornerRadius = (omnibarCard as? MaterialCardView)?.radius ?: 0f
        widgetCardElevation = (widgetCard as? MaterialCardView)?.cardElevation ?: 0f
        widgetCompatPaddingWidth = widgetCard.paddingLeft + widgetCard.paddingRight
        widgetCompatPaddingHeight = widgetCard.paddingTop + widgetCard.paddingBottom
    }

    private fun shrinkCardToMatchOmnibar(
        params: ViewGroup.MarginLayoutParams,
        omnibarWidth: Int,
        omnibarHeight: Int,
        isBottom: Boolean,
    ) {
        params.width = omnibarWidth + widgetCompatPaddingWidth
        params.height = omnibarHeight + widgetCompatPaddingHeight
        params.topMargin = 0
        params.bottomMargin = 0
        // Gravity only applies to FrameLayout children. For the LinearLayout (bottom) case the card's
        // horizontal placement is driven by translationX toward the omnibar, so no gravity is needed.
        if (params is FrameLayout.LayoutParams) {
            params.gravity = Gravity.CENTER_HORIZONTAL or if (isBottom) Gravity.BOTTOM else Gravity.TOP
        }
    }

    private fun waitForLayout(card: View, block: () -> Unit) {
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                card.viewTreeObserver.removeOnPreDrawListener(this)
                pendingPreDraw = null
                block()
                return true
            }
        }
        pendingPreDraw = Pair(card, listener)
        card.viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun performEnterAnimation(
        card: View,
        omnibarCard: View,
        widgetView: View,
        margins: Margins,
        startWidth: Int,
        startHeight: Int,
        omnibarPosition: Position,
        onUpdate: (fraction: Float) -> Unit,
        onCancel: () -> Unit,
        onComplete: () -> Unit,
    ) {
        val offsetToOmnibar = positionCardOverOmnibar(card, omnibarPosition)
        val widgetContent = card.findViewById<View?>(R.id.inputModeWidget)
        widgetContent?.alpha = 0f

        val params = card.layoutParams as ViewGroup.MarginLayoutParams
        // Subtract the parent's horizontal padding as well as the card's margins: in bottom mode
        // #8730 moved the keyline_2 inset from the card's margins onto the LinearLayout's padding,
        // so omitting it would land the card 2×keyline_2 too wide. Top-mode parent has no padding.
        val parent = card.parent as View
        val fullWidth = parent.width - parent.paddingLeft - parent.paddingRight - params.leftMargin - params.rightMargin
        // MaterialCardView compat padding scales with corner radius, but setRadius doesn't
        // trigger updatePadding — only setMaxCardElevation does. Measure with the final radius
        // (re-set maxCardElevation to force the padding update); otherwise fullHeight is short
        // by the padding delta and lands as a bottom step after restoreLayout's wrap_content.
        val materialCard = card as? MaterialCardView
        val savedRadius = materialCard?.radius ?: 0f
        val savedMaxElevation = materialCard?.maxCardElevation ?: 0f
        materialCard?.apply {
            radius = widgetCornerRadius
            maxCardElevation = savedMaxElevation
        }
        val fullHeight = measureUnconstrainedHeight(card, fullWidth)
        materialCard?.apply {
            radius = savedRadius
            maxCardElevation = savedMaxElevation
        }

        runAnimator(
            cleanup = { omnibarCard.alpha = 1f },
            onUpdate = { fraction ->
                params.width = lerp(startWidth, fullWidth, fraction)
                params.height = lerp(startHeight, fullHeight, fraction)
                params.topMargin = lerp(0, margins.top, fraction)
                params.bottomMargin = lerp(0, margins.bottom, fraction)
                card.layoutParams = params

                card.translationX = offsetToOmnibar.x * (1 - fraction)
                card.translationY = offsetToOmnibar.y * (1 - fraction)
                (card as? MaterialCardView)?.radius = lerpF(omnibarCornerRadius, widgetCornerRadius, fraction)
                widgetContent?.alpha = fraction
                omnibarCard.alpha = 1 - fraction
                onUpdate(fraction)
            },
            onCancel = onCancel,
            onEnd = {
                widgetContent?.alpha = 1f
                omnibarCard.alpha = 1f
                animateCornerRadius(card, widgetCornerRadius)
                restoreLayout(card, params, margins)
                card.post { applyLayoutTransitions(widgetView) }
                onComplete()
            },
        )
    }

    private fun positionCardOverOmnibar(card: View, omnibarPosition: Position): Offset {
        val cardPosition = visibleSurfacePosition(card)
        val offset = Offset(
            x = (omnibarPosition.x - cardPosition.x).toFloat(),
            y = (omnibarPosition.y - cardPosition.y).toFloat(),
        )
        card.translationX = offset.x
        card.translationY = offset.y
        card.visibility = View.VISIBLE
        return offset
    }

    private fun stripCompatPadding(card: View, isBottom: Boolean): Bounds {
        val visibleWidth = card.width - card.paddingLeft - card.paddingRight
        val visibleHeight = card.height - card.paddingTop - card.paddingBottom

        val materialCard = card as? MaterialCardView
        materialCard?.useCompatPadding = false
        materialCard?.cardElevation = widgetCardElevation

        val params = card.layoutParams as ViewGroup.MarginLayoutParams
        detachWeightForMorph(params)
        params.width = visibleWidth
        params.height = visibleHeight
        params.topMargin = 0
        // For bottom mode keep the card's bottomMargin (keyline_2) so that widgetView
        // (wrap_content, bottom-anchored) ends at activityContentBottom - bottomMargin —
        // the same place the omnibar sits when it reappears post-IME-hide.
        if (!isBottom) {
            params.bottomMargin = 0
        }
        params.marginEnd = 0
        card.layoutParams = params

        card.translationX = 0f
        card.translationY = 0f

        return Bounds(visibleWidth, visibleHeight)
    }

    private fun animateCornerRadius(card: View, topRadius: Float) {
        val materialCard = card as? MaterialCardView ?: return
        materialCard.radius = topRadius
    }

    private fun restoreLayout(card: View, params: ViewGroup.MarginLayoutParams, margins: Margins) {
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        params.topMargin = margins.top
        params.bottomMargin = margins.bottom
        when (params) {
            // Bottom mode: restore the card's XML resting state (width=0dp + weight) and hand width
            // distribution back to the LinearLayout, undoing detachWeightForMorph.
            is LinearLayout.LayoutParams -> {
                params.width = 0
                params.weight = savedCardWeight
            }
            is FrameLayout.LayoutParams -> {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.gravity = FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY
            }
            else -> params.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        card.layoutParams = params
        card.translationX = 0f
        card.translationY = 0f
    }

    // Bottom mode: a weighted (width=0dp) child ignores explicit widths, so zero the weight for the
    // morph and remember it. restoreLayout puts it back (enter); exit ends in widget removal.
    private fun detachWeightForMorph(params: ViewGroup.MarginLayoutParams) {
        if (params is LinearLayout.LayoutParams) {
            savedCardWeight = params.weight
            params.weight = 0f
        }
    }

    override fun clearLayoutTransitions(widgetView: View) {
        (widgetView as? ViewGroup)?.layoutTransition = null
        widgetView.findViewById<ViewGroup?>(R.id.inputModeWidgetCard)?.layoutTransition = null
        widgetView.findViewById<ViewGroup?>(R.id.inputModeWidget)?.layoutTransition = null
    }

    private fun visibleSurfacePosition(view: View): Position {
        val loc = IntArray(2).also { view.getLocationInWindow(it) }
        return Position(loc[0] + view.paddingLeft, loc[1] + view.paddingTop)
    }

    private fun windowPosition(view: View): Position {
        val loc = IntArray(2).also { view.getLocationInWindow(it) }
        return Position(loc[0], loc[1])
    }

    private fun measureUnconstrainedHeight(view: View, width: Int): Int {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return view.measuredHeight
    }

    private fun lerp(from: Int, to: Int, fraction: Float): Int =
        from + ((to - from) * fraction).toInt()

    private fun lerpF(from: Float, to: Float, fraction: Float): Float =
        from + (to - from) * fraction

    private fun runAnimator(
        cleanup: (() -> Unit)?,
        onUpdate: (fraction: Float) -> Unit,
        onCancel: () -> Unit = {},
        onEnd: () -> Unit,
    ) {
        cancelAnimation()
        animationCleanup = cleanup
        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { onUpdate(it.animatedFraction) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                    onCancel()
                }
                override fun onAnimationEnd(animation: Animator) {
                    transitionAnimator = null
                    animationCleanup = null
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    private data class Position(val x: Int, val y: Int)
    private data class Offset(val x: Float, val y: Float)
    private data class Bounds(val width: Int, val height: Int)

    private data class ExitSnapshot(
        val preSurface: Position,
        val omnibarPosition: Position,
        val visibleBounds: Bounds,
        val targetWidth: Int,
        val targetHeight: Int,
        val params: ViewGroup.MarginLayoutParams,
    )

    companion object {
        const val ANIMATION_DURATION_MS = 200L
    }
}
