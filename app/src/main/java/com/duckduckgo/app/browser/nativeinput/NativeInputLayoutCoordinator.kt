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

    private var navBarInsetPx: Int = 0
    private var reapplyContentOffset: (() -> Unit)? = null
    /** Restores content targets to the padding snapshotted in [configureContentOffset]. */
    private var resetContentOffset: (() -> Unit)? = null
    private var reapplyAutocompleteOffset: (() -> Unit)? = null

    // The content-reflow LayoutTransition is suspended while a per-frame content-offset drive is running
    // (nav bar slide or widget exit) so that drive is the only thing moving the content. Captured on suspend.
    private var contentTransitionGroup: ViewGroup? = null
    private var suspendedContentTransition: LayoutTransition? = null
    private var isContentReflowSuspended: Boolean = false

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
            // Top mode uses the widget's on-screen bottom (layout bottom + translationY): the nav bar hide
            // rides the widget up via translationY (a render transform, not a layout change), so .bottom
            // alone would offset for the old nav-bar-shown position and leave a gap the size of the nav bar.
            // Bottom mode's nav bar doesn't ride the widget, so its offset stays layout-based.
            val topOffset = autocompleteTopOffset(isBottom, widgetView.bottom + widgetView.translationY.toInt(), autoCompleteList.top, navBarInsetPx)
            val bottomOffset = autocompleteBottomOffset(isBottom, autoCompleteList.bottom, widgetView.top)
            applyPadding(deltaTop = topOffset, deltaBottom = bottomOffset)
        }

        reapplyAutocompleteOffset = { applyForWidgetPosition() }
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
                    reapplyAutocompleteOffset = null
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

    fun configureContentOffset(widgetView: View, isBottom: Boolean, navBarInsetPx: Int = 0) {
        this.navBarInsetPx = navBarInsetPx
        data class Target(val view: View, val basePadding: Padding)

        // Drop any prior session's applied inset before snapshotting. Otherwise a leftover top/bottom
        // padding (incomplete close, or ShowKeyboard → showNativeInput after a tab swipe) becomes the
        // new baseline and stacks on every reopen.
        resetContentOffset?.invoke()
        clearNtpScrollArtifacts()

        val newTabContent =
            rootView.findViewById<View?>(R.id.newTabPage)
                ?: rootView.findViewById(R.id.includeNewBrowserTab)
        // newTabPage has no XML padding — any top/bottom here is from a prior offset session. Zero it
        // so the snapshot can't inherit a dirty baseline. Leave browserLayout alone: its bottom padding
        // is owned by BottomOmnibarBrowserContainerLayoutBehavior.
        newTabContent?.setPadding(newTabContent.paddingLeft, 0, newTabContent.paddingRight, 0)

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
        // Fresh session: reset any stale nav bar slide bookkeeping and track this NTP group as the reflow
        // transition owner so begin/endNavBarSlide can suspend it.
        contentTransitionGroup = ntpGroup
        isContentReflowSuspended = false
        suspendedContentTransition = null
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
            val logoOnly = isLogoOnlyContent(view)
            val widgetTopOffsetPx = if (!logoOnly && !isBottom) {
                val viewLocation = IntArray(2).also { view.getLocationInWindow(it) }
                anchorBottomInWindow - viewLocation[1]
            } else {
                0
            }
            return contentTopInset(isBottom, logoOnly, this@NativeInputLayoutCoordinator.navBarInsetPx, widgetTopOffsetPx)
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
                clearNtpScrollArtifacts()
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

        reapplyContentOffset = { applyOffset() }
        resetContentOffset = {
            targets.forEach { applyPadding(it.view, it.basePadding, deltaTop = 0, deltaBottom = 0) }
            clearNtpScrollArtifacts()
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
        // no-op applyPadding. Needed in both modes: bottom mode's nav bar top inset also flips with
        // logo-only transitions (hatch/RMF/AppTP banner appearing).
        val ntpContentView = newTabContent
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
                    reapplyContentOffset = null
                    resetContentOffset = null
                    isWidgetAnimating = false
                    contentTransitionGroup = null
                    suspendedContentTransition = null
                    isContentReflowSuspended = false
                    targets.forEach { target ->
                        applyPadding(target.view, target.basePadding, deltaTop = 0, deltaBottom = 0)
                    }
                    clearNtpScrollArtifacts()
                    v.removeOnLayoutChangeListener(layoutListener)
                    rootView.removeOnLayoutChangeListener(layoutListener)
                    ntpContentView?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(globalLayoutListener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }

    /**
     * LayoutTransition CHANGING (Search ↔ Duck.ai reflow) can leave temporary margins/translations on
     * [R.id.newTabContainerScrollView]. Clear them whenever we restore the content baseline so tab-swipe
     * reopen cycles can't accumulate a growing gap above the NTP content.
     */
    private fun clearNtpScrollArtifacts() {
        val scrollView = rootView.findViewById<View?>(R.id.newTabContainerScrollView) ?: return
        val lp = scrollView.layoutParams as? ViewGroup.MarginLayoutParams
        if (lp != null && (lp.topMargin != 0 || lp.bottomMargin != 0 || lp.leftMargin != 0 || lp.rightMargin != 0)) {
            lp.setMargins(0, 0, 0, 0)
            scrollView.layoutParams = lp
        }
        if (scrollView.translationX != 0f) scrollView.translationX = 0f
        if (scrollView.translationY != 0f) scrollView.translationY = 0f
    }

    /**
     * Around a nav bar show/hide slide (widget stays open): silence the layout listeners and suspend the
     * content-reflow transition so the per-frame [updateNavBarInset] drive moves the content in lock-step
     * with the bar. Paired with [endNavBarSlide]; both idempotent, so a rapid re-toggle that cancels the
     * previous slide stays suspended until the last slide ends. [configureContentOffset]/detach reset it.
     */
    fun beginNavBarSlide() {
        isWidgetAnimating = true
        suspendContentReflow()
    }

    /**
     * Ends a [beginNavBarSlide] session. Resumes content reflow but leaves [isWidgetAnimating] alone —
     * the slide may run alongside enter/exit, which owns that flag until its own complete/cancel.
     * Callers that solely own the session (mid-session show/hide) should clear it after this returns.
     */
    fun endNavBarSlide() {
        resumeContentReflow()
    }

    /**
     * Suspends the content-reflow [LayoutTransition] so a per-frame content-offset drive (nav bar slide or
     * widget exit) moves the content instantly, instead of the transition animating each change on its own
     * clock and lagging the driver. Idempotent; paired with [resumeContentReflow].
     */
    fun suspendContentReflow() {
        if (isContentReflowSuspended) return
        val group = contentTransitionGroup ?: return
        isContentReflowSuspended = true
        suspendedContentTransition = group.layoutTransition
        group.layoutTransition = null
    }

    fun resumeContentReflow() {
        if (!isContentReflowSuspended) return
        isContentReflowSuspended = false
        contentTransitionGroup?.layoutTransition = suspendedContentTransition
        suspendedContentTransition = null
    }

    /**
     * Instantly restores content padding to the [configureContentOffset] baseline. Call while the
     * content-reflow transition is suspended (e.g. exit teardown) so a [LayoutTransition] can't race
     * the reset and leave stale top padding after the omnibar returns.
     */
    fun resetContentOffsetToBase() {
        resetContentOffset?.invoke()
    }

    fun updateNavBarInset(px: Int) {
        navBarInsetPx = px
        reapplyContentOffset?.invoke()
        // Recompute the autocomplete offset too: the nav bar toggle rides the widget via translationY,
        // which fires no layout listener, so this is the only signal that the widget's screen edges moved.
        reapplyAutocompleteOffset?.invoke()
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
            // Only reset bottom-anchored widgets. A top-anchored widget's translationY is owned by the
            // nav bar visibility — the widget rides up by the nav bar height when the bar hides — so
            // zeroing it here (this runs a frame after attach) would clobber that ride-up and leave a
            // gap under the top chrome when reopening with prefilled text.
            if (isBottom) widgetView.translationY = 0f
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

/**
 * The top padding the NTP/browser content needs to clear the input-mode chrome.
 *
 * - Logo-only content is never inset, so the centered logo doesn't shift between Search and the taller
 *   Duck.ai tab (and it can't collide with the short top nav bar anyway).
 * - Bottom mode: the widget sits at the bottom, so the only top chrome is the persistent nav bar — inset by
 *   [navBarInsetPx] so the content isn't drawn under it.
 * - Top mode: the widget sits below the nav bar, so [widgetTopOffsetPx] (content top → widget bottom) already
 *   accounts for the nav bar; clamp negatives to zero.
 */
internal fun contentTopInset(isBottom: Boolean, isLogoOnly: Boolean, navBarInsetPx: Int, widgetTopOffsetPx: Int): Int =
    when {
        isLogoOnly -> 0
        isBottom -> navBarInsetPx
        else -> maxOf(0, widgetTopOffsetPx)
    }

/**
 * Top padding the autocomplete list needs to clear the top chrome.
 * - Top mode: sit below the input widget. [widgetVisualBottomPx] must be the widget's on-screen bottom —
 *   its layout bottom plus translationY — because the nav bar hide slides the widget up via translationY,
 *   and a layout-only bottom would leave a gap the size of the nav bar once the bar is gone.
 * - Bottom mode: the widget is at the bottom, so the only top chrome is the nav bar — inset by
 *   [navBarInsetPx] (zero when the bar is hidden) so the suggestions aren't drawn under it.
 */
internal fun autocompleteTopOffset(isBottom: Boolean, widgetVisualBottomPx: Int, autoCompleteListTopPx: Int, navBarInsetPx: Int): Int =
    if (isBottom) navBarInsetPx else maxOf(0, widgetVisualBottomPx - autoCompleteListTopPx)

/**
 * Bottom counterpart of [autocompleteTopOffset]: in bottom mode the list clears the widget from below.
 * Layout-based [widgetTopPx] is fine here — the bottom-mode nav bar doesn't ride the widget.
 */
internal fun autocompleteBottomOffset(isBottom: Boolean, autoCompleteListBottomPx: Int, widgetTopPx: Int): Int =
    if (isBottom) maxOf(0, autoCompleteListBottomPx - widgetTopPx) else 0
