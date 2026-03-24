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
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.duckduckgo.app.browser.R

data class SavedMargins(val top: Int, val bottom: Int)

interface NativeInputAnimator {
    fun init(card: View, omnibarWidth: Int, omnibarHeight: Int, isBottom: Boolean): SavedMargins?
    fun animateEntrance(card: View, omnibarCard: View, widgetView: View, savedMargins: SavedMargins, onComplete: () -> Unit = {})
    fun animateExit(card: View, widgetView: View, omnibarCard: View, isBottom: Boolean, onComplete: () -> Unit)
    fun cancelRunningAnimation()
    fun applyLayoutTransitions(widgetView: View)
}

class RealNativeInputAnimator : NativeInputAnimator {

    private var runningAnimator: ValueAnimator? = null
    private var animationCleanup: (() -> Unit)? = null
    private var pendingPreDraw: Pair<View, ViewTreeObserver.OnPreDrawListener>? = null

    override fun init(
        card: View,
        omnibarWidth: Int,
        omnibarHeight: Int,
        isBottom: Boolean,
    ): SavedMargins? {
        if (omnibarWidth <= 0 || omnibarHeight <= 0) return null
        val params = card.layoutParams as? FrameLayout.LayoutParams ?: return null

        val saved = SavedMargins(params.topMargin, params.bottomMargin)

        params.width = omnibarWidth + card.paddingLeft + card.paddingRight
        params.height = omnibarHeight + card.paddingTop + card.paddingBottom
        params.topMargin = 0
        params.bottomMargin = 0
        val verticalGravity = if (isBottom) Gravity.BOTTOM else Gravity.TOP
        params.gravity = Gravity.CENTER_HORIZONTAL or verticalGravity

        card.visibility = View.INVISIBLE
        return saved
    }

    override fun animateEntrance(
        card: View,
        omnibarCard: View,
        widgetView: View,
        savedMargins: SavedMargins,
        onComplete: () -> Unit,
    ) {
        cancelRunningAnimation()
        val startWidth = (card.layoutParams as FrameLayout.LayoutParams).width
        val startHeight = (card.layoutParams as FrameLayout.LayoutParams).height

        animationCleanup = { omnibarCard.alpha = 1f }

        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                card.viewTreeObserver.removeOnPreDrawListener(this)
                pendingPreDraw = null
                val omnibarSurface = visibleSurfacePosition(omnibarCard)
                runEntranceAnimation(
                    card, startWidth, startHeight,
                    omnibarSurface.x, omnibarSurface.y,
                    omnibarCard, widgetView, savedMargins, onComplete,
                )
                return true
            }
        }
        pendingPreDraw = Pair(card, listener)
        card.viewTreeObserver.addOnPreDrawListener(listener)
    }

    override fun animateExit(card: View, widgetView: View, omnibarCard: View, isBottom: Boolean, onComplete: () -> Unit) {
        cancelRunningAnimation()
        clearLayoutTransitions(widgetView)

        val params = card.layoutParams as FrameLayout.LayoutParams
        val start = captureCardState(card, params)
        freezeCardDimensions(params, start)

        val targetSize = computeOmnibarSize(card, omnibarCard)
        val offset = computeExitOffset(card, omnibarCard, start, isBottom, targetSize)
        val content = card.findViewById<View?>(R.id.inputModeWidget)

        omnibarCard.alpha = 0f

        runAnimator(
            cleanup = { omnibarCard.alpha = 1f },
            onUpdate = { fraction ->
                params.width = lerp(start.width, targetSize.width, fraction)
                params.height = lerp(start.height, targetSize.height, fraction)
                params.topMargin = lerp(start.topMargin, 0, fraction)
                params.bottomMargin = lerp(start.bottomMargin, 0, fraction)
                card.layoutParams = params

                card.translationX = offset.x * fraction
                card.translationY = offset.y * fraction
                content?.alpha = 1 - fraction
                omnibarCard.alpha = fraction
            },
            onEnd = {
                omnibarCard.alpha = 1f
                onComplete()
            },
        )
    }

    override fun cancelRunningAnimation() {
        pendingPreDraw?.let { (card, listener) -> card.viewTreeObserver.removeOnPreDrawListener(listener) }
        pendingPreDraw = null
        animationCleanup?.invoke()
        animationCleanup = null
        runningAnimator?.cancel()
        runningAnimator = null
    }

    override fun applyLayoutTransitions(widgetView: View) {
        widgetView.findViewById<ViewGroup?>(R.id.inputModeWidgetCard)?.layoutTransition = createLayoutTransition()
        widgetView.findViewById<ViewGroup?>(R.id.inputModeWidget)?.layoutTransition = createLayoutTransition()
    }

    private fun createLayoutTransition(): LayoutTransition {
        return LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(ANIMATION_DURATION_MS)
        }
    }

    private fun runEntranceAnimation(
        card: View,
        startWidth: Int,
        startHeight: Int,
        omnibarSurfaceX: Int,
        omnibarSurfaceY: Int,
        omnibarCard: View,
        widgetView: View,
        savedMargins: SavedMargins,
        onComplete: () -> Unit,
    ) {
        val offset = alignCardWithOmnibar(card, omnibarSurfaceX, omnibarSurfaceY)
        val content = card.findViewById<View?>(R.id.inputModeWidget)
        content?.alpha = 0f

        val endWidth = (card.parent as View).width
        val endHeight = measureFullHeight(card, endWidth)
        val params = card.layoutParams as FrameLayout.LayoutParams

        runAnimator(
            cleanup = { omnibarCard.alpha = 1f },
            onUpdate = { fraction ->
                params.width = lerp(startWidth, endWidth, fraction)
                params.height = lerp(startHeight, endHeight, fraction)
                params.topMargin = lerp(0, savedMargins.top, fraction)
                params.bottomMargin = lerp(0, savedMargins.bottom, fraction)
                card.layoutParams = params

                card.translationX = offset.x * (1 - fraction)
                card.translationY = offset.y * (1 - fraction)
                content?.alpha = fraction
                omnibarCard.alpha = 1 - fraction
            },
            onEnd = {
                content?.alpha = 1f
                omnibarCard.alpha = 1f
                resetCardToFlexibleLayout(card, params, savedMargins)
                applyLayoutTransitions(widgetView)
                onComplete()
            },
        )
    }

    private fun alignCardWithOmnibar(card: View, omnibarSurfaceX: Int, omnibarSurfaceY: Int): Offset {
        val cardSurface = visibleSurfacePosition(card)
        val offset = Offset(
            x = (omnibarSurfaceX - cardSurface.x).toFloat(),
            y = (omnibarSurfaceY - cardSurface.y).toFloat(),
        )
        card.translationX = offset.x
        card.translationY = offset.y
        card.visibility = View.VISIBLE
        return offset
    }

    private fun resetCardToFlexibleLayout(card: View, params: FrameLayout.LayoutParams, savedMargins: SavedMargins) {
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        params.topMargin = savedMargins.top
        params.bottomMargin = savedMargins.bottom
        params.gravity = FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY
        card.layoutParams = params
        card.translationX = 0f
        card.translationY = 0f
    }

    private fun captureCardState(card: View, params: FrameLayout.LayoutParams): CardState {
        return CardState(card.width, card.height, params.topMargin, params.bottomMargin)
    }

    private fun freezeCardDimensions(params: FrameLayout.LayoutParams, state: CardState) {
        params.width = state.width
        params.height = state.height
    }

    private fun computeOmnibarSize(card: View, omnibarCard: View): Size {
        return Size(
            width = omnibarCard.width + card.paddingLeft + card.paddingRight,
            height = omnibarCard.height + card.paddingTop + card.paddingBottom,
        )
    }

    private fun computeExitOffset(card: View, omnibarCard: View, state: CardState, isBottom: Boolean, targetSize: Size): Offset {
        val omnibarSurface = visibleSurfacePosition(omnibarCard)
        val cardSurface = visibleSurfacePosition(card)
        val heightShift = if (isBottom) state.height - targetSize.height else 0
        return Offset(
            x = (omnibarSurface.x - cardSurface.x).toFloat(),
            y = (omnibarSurface.y - cardSurface.y).toFloat() + state.topMargin - state.bottomMargin - heightShift,
        )
    }

    private fun clearLayoutTransitions(widgetView: View) {
        (widgetView as? ViewGroup)?.layoutTransition = null
        widgetView.findViewById<ViewGroup?>(R.id.inputModeWidgetCard)?.layoutTransition = null
        widgetView.findViewById<ViewGroup?>(R.id.inputModeWidget)?.layoutTransition = null
    }

    private fun visibleSurfacePosition(view: View): Position {
        val loc = IntArray(2).also { view.getLocationInWindow(it) }
        return Position(
            x = loc[0] + view.paddingLeft,
            y = loc[1] + view.paddingTop,
        )
    }

    private fun measureFullHeight(card: View, targetWidth: Int): Int {
        card.measure(
            View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return card.measuredHeight
    }

    private fun lerp(from: Int, to: Int, fraction: Float): Int {
        return from + ((to - from) * fraction).toInt()
    }

    private fun runAnimator(cleanup: (() -> Unit)?, onUpdate: (fraction: Float) -> Unit, onEnd: () -> Unit) {
        cancelRunningAnimation()
        animationCleanup = cleanup
        runningAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { onUpdate(it.animatedFraction) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    runningAnimator = null
                    animationCleanup = null
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    private data class Position(val x: Int, val y: Int)
    private data class Offset(val x: Float, val y: Float)
    private data class Size(val width: Int, val height: Int)
    private data class CardState(val width: Int, val height: Int, val topMargin: Int, val bottomMargin: Int)

    companion object {
        const val ANIMATION_DURATION_MS = 200L
    }
}
