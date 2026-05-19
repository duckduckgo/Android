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

import android.animation.LayoutTransition
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.R
import com.google.android.material.card.MaterialCardView

class NativeInputLayoutCoordinator(
    private val rootView: ViewGroup,
    private val omnibarState: OmnibarState,
) {
    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun View.snapshotPadding() = Padding(paddingLeft, paddingTop, paddingRight, paddingBottom)

    private var pendingContentLayoutTransition: Pair<ViewGroup, LayoutTransition>? = null

    /** Set in [configureContentOffset]; invoked per-frame from the enter/exit animators. */
    private var widgetAnimationFrameHandler: ((card: View) -> Unit)? = null

    /**
     * While true, the [configureContentOffset] layout listener no-ops. Set by the manager
     * around `animateEnter` / `animateExit` so the snapshot/setup phases of those animators
     * (which briefly mutate the card's layoutParams before translation is applied) don't
     * cause the content offset to snap to an intermediate state. During the actual animation,
     * [onWidgetAnimationFrame] drives the offset from the animator's `onUpdate`.
     */
    private var isWidgetAnimating: Boolean = false

    fun buildWidgetLayoutParams(isBottom: Boolean): ViewGroup.LayoutParams {
        return CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = if (isBottom) Gravity.BOTTOM else Gravity.TOP
        }
    }

    fun applyBottomCardCorners(widgetView: View, isBottom: Boolean) {
        if (!isBottom) return
        val card = widgetView.findViewById<MaterialCardView?>(R.id.inputModeWidgetCard) ?: return
        val radius = card.resources.getDimension(R.dimen.extraLargeShapeCornerRadius)
        card.shapeAppearanceModel =
            card.shapeAppearanceModel
                .toBuilder()
                .setTopLeftCornerSize(radius)
                .setTopRightCornerSize(radius)
                .setBottomLeftCornerSize(0f)
                .setBottomRightCornerSize(0f)
                .build()
    }

    fun applyBottomCardShape(widgetView: View, isBottom: Boolean) {
        if (!isBottom) return
        applyBottomCardCorners(widgetView, isBottom)
        val card = widgetView.findViewById<MaterialCardView?>(R.id.inputModeWidgetCard) ?: return
        val params = card.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.marginStart = 0
        params.marginEnd = 0
        params.bottomMargin = 0
        card.layoutParams = params
    }

    fun configureAutocompleteLayout(widgetView: View, isBottom: Boolean) {
        val autoCompleteList = rootView.findViewById<View?>(R.id.autoCompleteSuggestionsList) ?: return
        val focusedView = rootView.findViewById<View?>(R.id.focusedView)

        // In duck.ai mode the legacy AppBar stays visible; float overlays over it so AppBar
        // behavior/elevation doesn't push or cover them. Restored on widget detach.
        val restoreFloats: List<() -> Unit> = if (omnibarState.isDuckAiMode()) {
            listOfNotNull(autoCompleteList, focusedView).map { it.floatOverLegacyOmnibar() }
        } else {
            emptyList()
        }

        // Keep widget above the (possibly raised) overlay views.
        val baseElevation = maxOf(autoCompleteList.elevation, focusedView?.elevation ?: 0f)
        val targetElevation = baseElevation + widgetView.resources.displayMetrics.density
        widgetView.elevation = maxOf(widgetView.elevation, targetElevation)
        widgetView.bringToFront()

        val targets =
            buildList {
                add(autoCompleteList to autoCompleteList.snapshotPadding())
                focusedView?.let { add(it to it.snapshotPadding()) }
            }
        fun applyPadding(deltaTop: Int, deltaBottom: Int) {
            targets.forEach { (view, padding) ->
                val newTop = padding.top + deltaTop
                val newBottom = padding.bottom + deltaBottom
                // Only post requestLayout when padding actually changed to avoid extra layout passes in steady state
                if (view.paddingTop != newTop || view.paddingBottom != newBottom) {
                    view.setPadding(padding.left, newTop, padding.right, newBottom)
                    // Force RecyclerView to reposition items after padding changes during widget enter animation
                    view.post { view.requestLayout() }
                }
            }
        }

        fun applyForWidgetPosition() {
            val topOffset = if (isBottom) 0 else maxOf(0, widgetView.bottom - autoCompleteList.top)
            val bottomOffset = if (isBottom) maxOf(0, autoCompleteList.bottom - widgetView.top) else 0
            applyPadding(deltaTop = topOffset, deltaBottom = bottomOffset)
        }

        widgetView.post { applyForWidgetPosition() }
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                applyForWidgetPosition()
            }
        widgetView.addOnLayoutChangeListener(layoutListener)
        autoCompleteList.addOnLayoutChangeListener(layoutListener)
        focusedView?.addOnLayoutChangeListener(layoutListener)
        widgetView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) {
                    applyPadding(deltaTop = 0, deltaBottom = 0)
                    restoreFloats.forEach { it() }
                    v.removeOnLayoutChangeListener(layoutListener)
                    autoCompleteList.removeOnLayoutChangeListener(layoutListener)
                    focusedView?.removeOnLayoutChangeListener(layoutListener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }

    // Clears the CoordinatorLayout behavior and raises elevation so the view floats over
    // the legacy AppBar. Returns a lambda that restores the previous state.
    private fun View.floatOverLegacyOmnibar(): () -> Unit {
        val previousBehavior = (layoutParams as? CoordinatorLayout.LayoutParams)?.behavior
        val previousElevation = elevation
        val raisedElevation = resources.getDimension(com.duckduckgo.mobile.android.R.dimen.omnibarFloatElevation)
        updateLayoutParams<CoordinatorLayout.LayoutParams> { behavior = null }
        elevation = maxOf(elevation, raisedElevation)
        return {
            updateLayoutParams<CoordinatorLayout.LayoutParams> { behavior = previousBehavior }
            elevation = previousElevation
        }
    }

    fun configureContentOffset(widgetView: View, isBottom: Boolean) {
        data class Target(val view: View, val basePadding: Padding)
        val newTabContent =
            rootView.findViewById<View?>(R.id.newTabPage)
                ?: rootView.findViewById(R.id.includeNewBrowserTab)
        val targets =
            listOfNotNull(
                rootView.findViewById(R.id.browserLayout),
                newTabContent,
            ).map { Target(it, it.snapshotPadding()) }
        if (targets.isEmpty()) return
        val anchor = widgetView.findViewById(R.id.inputModeWidgetCard) ?: widgetView

        // Animate child reflows when the widget toggles Search ↔ DuckAI changes our padding.
        // The transition is staged here but only assigned to the parent once the enter animation
        // completes (see enableContentLayoutTransition). Otherwise the per-frame setPadding
        // calls during the enter animation would each spawn a fresh CHANGING animator and the
        // content would visibly lag behind the widget growth.
        val ntpGroup = newTabContent as? ViewGroup
        val previousNtpTransition = ntpGroup?.layoutTransition
        if (ntpGroup != null) {
            pendingContentLayoutTransition =
                ntpGroup to
                LayoutTransition().apply {
                    disableTransitionType(LayoutTransition.APPEARING)
                    disableTransitionType(LayoutTransition.DISAPPEARING)
                    disableTransitionType(LayoutTransition.CHANGE_APPEARING)
                    disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
                    enableTransitionType(LayoutTransition.CHANGING)
                    setDuration(RealNativeInputAnimator.ANIMATION_DURATION_MS)
                    setAnimateParentHierarchy(false)
                }
        }

        fun applyPadding(view: View, padding: Padding, deltaTop: Int, deltaBottom: Int) {
            val newTop = padding.top + deltaTop
            val newBottom = padding.bottom + deltaBottom
            if (view.paddingTop == newTop && view.paddingBottom == newBottom) return
            view.setPadding(padding.left, newTop, padding.right, newBottom)
            // Force a layout pass for children (e.g. WebView) that cache their measured size and
            // otherwise leave a gap until the next user interaction.
            view.post { view.requestLayout() }
        }

        fun isLogoOnlyContent(view: View): Boolean {
            if (view != newTabContent) return false
            val logoVisible = rootView.findViewById<View?>(R.id.ddgLogo)?.visibility == View.VISIBLE
            val hatchVisible = rootView.findViewById<View?>(R.id.newTabReturnHatchView)?.visibility == View.VISIBLE
            return logoVisible && !hatchVisible
        }

        fun computeDeltaTop(view: View, anchorBottomInWindow: Int): Int {
            if (isBottom || isLogoOnlyContent(view)) return 0
            val viewLocation = IntArray(2).also { view.getLocationInWindow(it) }
            return maxOf(0, anchorBottomInWindow - viewLocation[1])
        }

        fun computeDeltaBottom(): Int {
            if (!isBottom) return 0
            return maxOf(0, widgetView.height)
        }

        fun applyOffsetWithBottom(anchorBottomInWindow: Int) {
            val deltaBottom = computeDeltaBottom()
            targets.forEach { target ->
                val deltaTop = computeDeltaTop(target.view, anchorBottomInWindow)
                applyPadding(target.view, target.basePadding, deltaTop, deltaBottom)
            }
        }

        fun applyOffset() {
            if (!widgetView.isShown) {
                targets.forEach { applyPadding(it.view, it.basePadding, deltaTop = 0, deltaBottom = 0) }
                return
            }
            val anchorLocation = IntArray(2).also { anchor.getLocationInWindow(it) }
            val anchorBottomInWindow = anchorLocation[1] + anchor.height
            applyOffsetWithBottom(anchorBottomInWindow)
        }

        // Called from the enter/exit animators' onUpdate, BEFORE the layout pass that
        // processes the new card layoutParams. We project the card's current visual bottom
        // from the values the animator has just written (layoutParams + translation), so the
        // setPadding here and the card's own requestLayout coalesce into the same
        // measure/layout pass — content tracks the widget's growth/shrinkage in the same
        // frame instead of lagging by one.
        widgetAnimationFrameHandler = lambda@{ card ->
            if (!widgetView.isShown) return@lambda
            val parent = card.parent as? View ?: return@lambda
            val params = card.layoutParams as? FrameLayout.LayoutParams
            if (params == null) {
                // Layout params don't carry position info we can project — fall back to reading
                // the anchor's actual position for this frame.
                applyOffset()
                return@lambda
            }
            val parentLocation = IntArray(2).also { parent.getLocationInWindow(it) }
            val cardVisualTopInWindow = parentLocation[1] + params.topMargin + card.translationY.toInt()
            val cardVisualBottomInWindow = cardVisualTopInWindow + params.height
            applyOffsetWithBottom(cardVisualBottomInWindow)
        }

        widgetView.post { applyOffset() }
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (isWidgetAnimating) return@OnLayoutChangeListener
                applyOffset()
            }
        widgetView.addOnLayoutChangeListener(layoutListener)
        rootView.addOnLayoutChangeListener(layoutListener)
        widgetView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) {
                    ntpGroup?.layoutTransition = previousNtpTransition
                    pendingContentLayoutTransition = null
                    widgetAnimationFrameHandler = null
                    isWidgetAnimating = false
                    targets.forEach { target ->
                        applyPadding(target.view, target.basePadding, deltaTop = 0, deltaBottom = 0)
                    }
                    v.removeOnLayoutChangeListener(layoutListener)
                    rootView.removeOnLayoutChangeListener(layoutListener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }

    fun onWidgetAnimationFrame(card: View) {
        widgetAnimationFrameHandler?.invoke(card)
    }

    fun setWidgetAnimating(animating: Boolean) {
        isWidgetAnimating = animating
    }

    fun enableContentLayoutTransition() {
        val (ntpGroup, transition) = pendingContentLayoutTransition ?: return
        ntpGroup.layoutTransition = transition
        pendingContentLayoutTransition = null
    }

    fun applyForcedBottomTranslation(widgetView: View, isBottom: Boolean) {
        val shouldForce = isBottom && !omnibarState.isOmnibarBottom()
        if (!shouldForce) {
            widgetView.translationY = 0f
            return
        }
        fun applyOffset() {
            val gap = maxOf(0, rootView.height - widgetView.bottom)
            if (widgetView.translationY != gap.toFloat()) {
                widgetView.translationY = gap.toFloat()
            }
        }
        applyOffset()
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                applyOffset()
            }
        rootView.addOnLayoutChangeListener(layoutListener)
        widgetView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) {
                    rootView.removeOnLayoutChangeListener(layoutListener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }
}
