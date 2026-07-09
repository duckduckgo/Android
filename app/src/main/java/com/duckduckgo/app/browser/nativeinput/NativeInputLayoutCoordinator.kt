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
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
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
     * While true the [configureContentOffset] layout listener no-ops. Set by the manager around
     * animateEnter/animateExit so their setup phase doesn't snap the offset to an intermediate state;
     * [onWidgetAnimationFrame] drives the offset during the animation instead.
     */
    private var isWidgetAnimating: Boolean = false

    fun buildWidgetLayoutParams(isBottom: Boolean, topInsetPx: Int = 0): ViewGroup.LayoutParams {
        return CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = if (isBottom) Gravity.BOTTOM else Gravity.TOP
            // In top mode the persistent nav bar occupies the top strip, so offset the widget below it
            // instead of overlapping. Bottom mode sits at the bottom and needs no offset.
            topMargin = if (isBottom) 0 else topInsetPx
        }
    }

    fun buildNavBarLayoutParams(heightPx: Int): ViewGroup.LayoutParams {
        return CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            heightPx,
        ).apply {
            gravity = Gravity.TOP
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

        // Cached once: isLogoOnlyContent runs on every layout pass and findViewById walks the tree.
        // Safe to cache (they inflate with newTabContent); live visibility/height are read each pass.
        val ddgLogoView = rootView.findViewById<View?>(R.id.ddgLogo)
        val returnHatchView = rootView.findViewById<View?>(R.id.newTabReturnHatchView)
        // NTP content that renders above the logo and must clear the widget. Checked by height, not
        // visibility: these containers stay attached and collapse to zero height when empty.
        val nonLogoContentViews = listOfNotNull(
            rootView.findViewById(R.id.appTrackingProtectionStateView),
            rootView.findViewById(R.id.indonesiaNewTabSectionView),
            rootView.findViewById(R.id.messageCta),
        )
        // Onboarding CTA bubbles live inside newTabContent and must clear the widget, so a visible
        // one overrides the logo-only suppression below.
        val onboardingCtaViews = listOfNotNull(
            rootView.findViewById(R.id.brandDesignDialogScrollView),
            rootView.findViewById(R.id.includeOnboardingDaxDialogBubble),
        )

        // Animate content reflow when the widget toggles Search ↔ DuckAI. Staged here but only assigned
        // once the enter animation completes (enableContentLayoutTransition); otherwise per-frame
        // setPadding during the enter would spawn CHANGING animators and the content would lag the widget.
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
            val logoVisible = ddgLogoView?.visibility == View.VISIBLE
            val hatchHeightPx = returnHatchView?.height ?: 0
            val onboardingCtaVisible = onboardingCtaViews.any { it.isVisible }
            val sectionsVisible = nonLogoContentViews.any { it.isVisible && it.height > 0 }
            return isLogoOnly(logoVisible, hatchHeightPx, onboardingCtaVisible, sectionsVisible)
        }

        fun computeDeltaTop(view: View, anchorBottomInWindow: Int): Int {
            if (isBottom || isLogoOnlyContent(view)) return 0
            val viewLocation = IntArray(2).also { view.getLocationInWindow(it) }
            return maxOf(0, anchorBottomInWindow - viewLocation[1])
        }

        fun computeDeltaBottom(view: View, anchorTopInWindow: Int): Int {
            if (!isBottom) return 0
            val viewLocation = IntArray(2).also { view.getLocationInWindow(it) }
            val viewBottomInWindow = viewLocation[1] + view.height
            return maxOf(0, viewBottomInWindow - anchorTopInWindow)
        }

        fun applyOffsetWithBottom(anchorTopInWindow: Int, anchorBottomInWindow: Int) {
            targets.forEach { target ->
                val deltaTop = computeDeltaTop(target.view, anchorBottomInWindow)
                val deltaBottom = computeDeltaBottom(target.view, anchorTopInWindow)
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
            applyOffsetWithBottom(anchorTopInWindow = anchorLocation[1], anchorBottomInWindow = anchorBottomInWindow)
        }

        // Called from the animators' onUpdate before the layout pass. Projects the card's visual bottom
        // from the values the animator just wrote (layoutParams + translation) so this setPadding and the
        // card's requestLayout coalesce into one pass — content tracks the widget in the same frame.
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
            applyOffsetWithBottom(anchorTopInWindow = cardVisualTopInWindow, anchorBottomInWindow = cardVisualBottomInWindow)
        }

        widgetView.post { applyOffset() }
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (isWidgetAnimating) return@OnLayoutChangeListener
                applyOffset()
            }
        widgetView.addOnLayoutChangeListener(layoutListener)
        rootView.addOnLayoutChangeListener(layoutListener)

        // Some offset inputs change without moving rootView/widgetView (hatch height, logo visibility,
        // NTP reflow), which the per-view listeners miss. The window-shared viewTreeObserver fires on
        // every global layout pass — broad by design; kept cheap via the isWidgetAnimating skip and
        // no-op applyPadding.
        val ntpContentView = newTabContent?.takeIf { !isBottom }
        val globalLayoutListener =
            ViewTreeObserver.OnGlobalLayoutListener {
                if (isWidgetAnimating) return@OnGlobalLayoutListener
                applyOffset()
            }
        ntpContentView?.viewTreeObserver?.addOnGlobalLayoutListener(globalLayoutListener)
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
                    ntpContentView?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(globalLayoutListener)
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

/**
 * Whether the new-tab page shows only the dax logo. When true the content is NOT offset below the widget
 * so the logo stays centered (otherwise it shifts between Search and the taller Duck.ai tab).
 *
 * [hatchHeightPx] is used instead of hatch visibility: the container is always VISIBLE and only collapses
 * to zero height when no hatch is shown. [onboardingCtaVisible]/[sectionsVisible] force a non-logo-only
 * result — a CTA bubble or a section above the logo (AppTP banner, RMF, Indonesia message) must clear the widget.
 */
internal fun isLogoOnly(logoVisible: Boolean, hatchHeightPx: Int, onboardingCtaVisible: Boolean, sectionsVisible: Boolean): Boolean =
    logoVisible && hatchHeightPx <= 0 && !onboardingCtaVisible && !sectionsVisible
