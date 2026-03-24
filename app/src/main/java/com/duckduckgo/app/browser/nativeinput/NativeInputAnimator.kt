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
import com.google.android.material.card.MaterialCardView

data class Margins(val top: Int, val bottom: Int)

data class CardWidthTarget(
    val width: Int,
    val marginStart: Int,
    val marginEnd: Int,
    val bottomMargin: Int,
)

interface NativeInputAnimator {
    fun init(widgetCard: View, omnibarCard: View, omnibarWidth: Int, omnibarHeight: Int, isBottom: Boolean): Margins?
    fun animateEnter(widgetCard: View, omnibarCard: View, widgetView: View, margins: Margins, onComplete: () -> Unit = {})
    fun animateExit(widgetCard: View, widgetView: View, omnibarCard: View, isBottom: Boolean, onComplete: () -> Unit)
    fun animateCardWidth(card: View, widgetView: View, target: CardWidthTarget, onComplete: () -> Unit = {})
    fun cancelAnimation()
    fun applyLayoutTransitions(widgetView: View)
    fun clearLayoutTransitions(widgetView: View)
}

class RealNativeInputAnimator : NativeInputAnimator {

    private var transitionAnimator: ValueAnimator? = null
    private var cardWidthAnimator: ValueAnimator? = null
    private var animationCleanup: (() -> Unit)? = null
    private var pendingPreDraw: Pair<View, ViewTreeObserver.OnPreDrawListener>? = null

    private var omnibarCornerRadius = 0f
    private var widgetCornerRadius = 0f
    private var widgetCardElevation = 0f
    private var widgetCompatPaddingWidth = 0
    private var widgetCompatPaddingHeight = 0
    private var isBottomCard = false

    override fun init(
        widgetCard: View,
        omnibarCard: View,
        omnibarWidth: Int,
        omnibarHeight: Int,
        isBottom: Boolean,
    ): Margins? {
        if (omnibarWidth <= 0 || omnibarHeight <= 0) return null
        val params = widgetCard.layoutParams as? FrameLayout.LayoutParams ?: return null

        captureCardProperties(widgetCard, omnibarCard)
        isBottomCard = isBottom

        val margins = Margins(params.topMargin, params.bottomMargin)

        shrinkCardToMatchOmnibar(params, omnibarWidth, omnibarHeight, isBottom)

        animateCornerRadius(widgetCard, isBottom, omnibarCornerRadius)
        widgetCard.visibility = View.INVISIBLE

        return margins
    }

    override fun animateEnter(
        widgetCard: View,
        omnibarCard: View,
        widgetView: View,
        margins: Margins,
        onComplete: () -> Unit,
    ) {
        cancelAnimation()

        val startWidth = (widgetCard.layoutParams as FrameLayout.LayoutParams).width
        val startHeight = (widgetCard.layoutParams as FrameLayout.LayoutParams).height
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
                onComplete,
            )
        }
    }

    override fun animateExit(
        widgetCard: View,
        widgetView: View,
        omnibarCard: View,
        isBottom: Boolean,
        onComplete: () -> Unit,
    ) {
        cancelAnimation()
        clearLayoutTransitions(widgetView)
        (widgetView as? ViewGroup)?.clipChildren = false
        (widgetView.parent as? ViewGroup)?.clipChildren = false

        val snapshot = snapshotBeforeExit(widgetCard, omnibarCard)

        waitForLayout(widgetCard) {
            performExitAnimation(widgetCard, omnibarCard, snapshot, isBottom, onComplete)
        }
    }

    private fun snapshotBeforeExit(widgetCard: View, omnibarCard: View): ExitSnapshot {
        val preSurface = visibleSurfacePosition(widgetCard)
        val omnibarPosition = visibleSurfacePosition(omnibarCard)
        val visibleBounds = stripCompatPadding(widgetCard)

        val params = widgetCard.layoutParams as FrameLayout.LayoutParams
        params.marginEnd = 0

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
        onComplete: () -> Unit,
    ) {
        val postPosition = windowPosition(widgetCard)
        val holdTranslation = Offset(
            x = (snapshot.preSurface.x - postPosition.x).toFloat(),
            y = (snapshot.preSurface.y - postPosition.y).toFloat(),
        )
        widgetCard.translationX = holdTranslation.x
        widgetCard.translationY = holdTranslation.y

        val bottomAnchorShift = if (isBottom) snapshot.visibleBounds.height - snapshot.targetHeight else 0
        val endTranslation = Offset(
            x = (snapshot.omnibarPosition.x - postPosition.x).toFloat(),
            y = (snapshot.omnibarPosition.y - postPosition.y).toFloat() - bottomAnchorShift,
        )

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
            },
            onEnd = {
                omnibarCard.alpha = 1f
                onComplete()
            },
        )
    }

    override fun animateCardWidth(
        card: View,
        widgetView: View,
        target: CardWidthTarget,
        onComplete: () -> Unit,
    ) {
        cardWidthAnimator?.cancel()
        cardWidthAnimator = null
        clearLayoutTransitions(widgetView)

        val params = card.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val oldWidth = card.width

        applyTargetMargins(params, card, target)

        if (oldWidth <= 0 || oldWidth == target.width) {
            onCardWidthAnimated(params, card, widgetView, onComplete)
            return
        }

        params.width = oldWidth
        card.layoutParams = params

        startCardWidthAnimation(params, card, widgetView, oldWidth, target.width, onComplete)
    }

    private fun applyTargetMargins(params: ViewGroup.MarginLayoutParams, card: View, target: CardWidthTarget) {
        params.marginStart = target.marginStart
        params.marginEnd = target.marginEnd
        params.bottomMargin = target.bottomMargin
        card.layoutParams = params
    }

    private fun onCardWidthAnimated(
        params: ViewGroup.MarginLayoutParams,
        card: View,
        widgetView: View,
        onComplete: () -> Unit,
    ) {
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        card.layoutParams = params
        applyLayoutTransitions(widgetView)
        onComplete()
    }

    private fun startCardWidthAnimation(
        params: ViewGroup.MarginLayoutParams,
        card: View,
        widgetView: View,
        fromWidth: Int,
        toWidth: Int,
        onComplete: () -> Unit,
    ) {
        cardWidthAnimator = ValueAnimator.ofInt(fromWidth, toWidth).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                params.width = anim.animatedValue as Int
                card.layoutParams = params
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    cardWidthAnimator = null
                    if (!cancelled) onCardWidthAnimated(params, card, widgetView, onComplete)
                }
            })
            start()
        }
    }

    override fun cancelAnimation() {
        cardWidthAnimator?.cancel()
        cardWidthAnimator = null
        pendingPreDraw?.let { (view, listener) -> view.viewTreeObserver.removeOnPreDrawListener(listener) }
        pendingPreDraw = null
        animationCleanup?.invoke()
        animationCleanup = null
        transitionAnimator?.cancel()
        transitionAnimator = null
    }

    override fun applyLayoutTransitions(widgetView: View) {
        val changingTransition = {
            LayoutTransition().apply {
                enableTransitionType(LayoutTransition.CHANGING)
                setDuration(ANIMATION_DURATION_MS)
            }
        }
        widgetView.findViewById<ViewGroup?>(R.id.inputModeWidgetCard)?.layoutTransition = changingTransition()
        widgetView.findViewById<ViewGroup?>(R.id.inputModeWidget)?.layoutTransition = changingTransition()
        if (isBottomCard) {
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
        params: FrameLayout.LayoutParams,
        omnibarWidth: Int,
        omnibarHeight: Int,
        isBottom: Boolean,
    ) {
        params.width = omnibarWidth + widgetCompatPaddingWidth
        params.height = omnibarHeight + widgetCompatPaddingHeight
        params.topMargin = 0
        params.bottomMargin = 0
        params.gravity = Gravity.CENTER_HORIZONTAL or if (isBottom) Gravity.BOTTOM else Gravity.TOP
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
        onComplete: () -> Unit,
    ) {
        val offsetToOmnibar = positionCardOverOmnibar(card, omnibarPosition)
        val widgetContent = card.findViewById<View?>(R.id.inputModeWidget)
        widgetContent?.alpha = 0f

        val fullWidth = (card.parent as View).width
        val fullHeight = measureUnconstrainedHeight(card, fullWidth)
        val params = card.layoutParams as FrameLayout.LayoutParams

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
            },
            onEnd = {
                widgetContent?.alpha = 1f
                omnibarCard.alpha = 1f
                animateCornerRadius(card, isBottomCard, widgetCornerRadius)
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

    private fun stripCompatPadding(card: View): Bounds {
        val visibleWidth = card.width - card.paddingLeft - card.paddingRight
        val visibleHeight = card.height - card.paddingTop - card.paddingBottom

        val materialCard = card as? MaterialCardView
        materialCard?.useCompatPadding = false
        materialCard?.cardElevation = widgetCardElevation

        val params = card.layoutParams as FrameLayout.LayoutParams
        params.width = visibleWidth
        params.height = visibleHeight
        params.topMargin = 0
        params.bottomMargin = 0
        card.layoutParams = params

        card.translationX = 0f
        card.translationY = 0f

        return Bounds(visibleWidth, visibleHeight)
    }

    private fun animateCornerRadius(card: View, isBottom: Boolean, topRadius: Float) {
        val materialCard = card as? MaterialCardView ?: return
        if (isBottom) {
            materialCard.shapeAppearanceModel = materialCard.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(topRadius)
                .setTopRightCornerSize(topRadius)
                .setBottomLeftCornerSize(0f)
                .setBottomRightCornerSize(0f)
                .build()
        } else {
            materialCard.radius = topRadius
        }
    }

    private fun restoreLayout(card: View, params: FrameLayout.LayoutParams, margins: Margins) {
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        params.topMargin = margins.top
        params.bottomMargin = margins.bottom
        params.gravity = FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY
        card.layoutParams = params
        card.translationX = 0f
        card.translationY = 0f
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

    private fun runAnimator(cleanup: (() -> Unit)?, onUpdate: (fraction: Float) -> Unit, onEnd: () -> Unit) {
        cancelAnimation()
        animationCleanup = cleanup
        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { onUpdate(it.animatedFraction) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
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
        val params: FrameLayout.LayoutParams,
    )

    companion object {
        const val ANIMATION_DURATION_MS = 200L
    }
}
