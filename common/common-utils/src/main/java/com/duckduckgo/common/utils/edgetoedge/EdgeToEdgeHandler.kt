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

package com.duckduckgo.common.utils.edgetoedge

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import javax.inject.Inject

/**
 * Standardised window-insets handling for edge-to-edge screens.
 *
 * Each method installs an [androidx.core.view.OnApplyWindowInsetsListener] that adds the relevant
 * system-bar inset on top of the view's *original* padding (captured once), so repeated inset
 * dispatches never accumulate padding. Insets are requested as soon as the view is attached.
 *
 * Callers should only invoke these when edge-to-edge is enabled for the screen's bucket
 * (see [EdgeToEdgeProvider]); when disabled the window reserves the bars itself and no padding
 * should be added.
 */
class EdgeToEdgeHandler @Inject constructor() {

    /**
     * Pads [view]'s top (status bar + cutout) and left/right (side system bars + cutout) edges.
     *
     * @param view The view to pad, typically the screen's root.
     * @param installScrim When true (default), a status-bar scrim (see [installStatusBarScrim]) is drawn behind
     *   the transparent status bar. Pass false for screens that colour their own system bars (e.g. via SystemBarStyle).
     */
    fun applyStatusBarAndHorizontalInsets(view: View, installScrim: Boolean = true) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val windowInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            )
            v.updatePadding(
                left = initialLeft + windowInsets.left,
                top = initialTop + windowInsets.top,
                right = initialRight + windowInsets.right,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
        if (installScrim) installStatusBarScrim(view)
    }

    /**
     * Pads [view]'s top by the status-bar + cutout inset.
     *
     * @param view The view to pad at the top edge.
     * @param installScrim When true (default), a status-bar scrim (see [installStatusBarScrim]) is drawn behind
     *   the transparent status bar. Pass false for screens that colour their own system bars (e.g. via SystemBarStyle).
     */
    fun applyStatusBarInsets(view: View, installScrim: Boolean = true) {
        val initialTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
            ).top
            v.updatePadding(top = initialTop + top)
            insets
        }
        ViewCompat.requestApplyInsets(view)
        if (installScrim) installStatusBarScrim(view)
    }

    /**
     * Pads [view]'s bottom by the navigation-bar (plus display cutout and IME) inset.
     *
     * When [drawBehindGestureNav] is true, the padding uses the *tappable* navigation inset, which is
     * 0 in gesture navigation (so content draws edge-to-edge behind the transparent gesture handle) and
     * the button-bar height in 2/3-button navigation (so content sits above the buttons). When false it
     * uses the full navigation-bar inset, keeping content clear of the bar in every navigation mode.
     *
     * No scrim is painted: the navigation bar is transparent and the solid [android.R.attr.windowBackground]
     * shows through it, so on 2/3-button navigation the bar appears opaque and matches the activity body, while
     * gesture navigation stays transparent.
     *
     * @param view The view to pad at the bottom edge.
     */
    fun applyNavigationBarInsets(
        view: View,
        drawBehindGestureNav: Boolean = false,
    ) {
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bottomType = if (drawBehindGestureNav) {
                WindowInsetsCompat.Type.tappableElement()
            } else {
                WindowInsetsCompat.Type.navigationBars()
            }
            val bottom = insets.getInsets(
                bottomType or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            ).bottom

            v.updatePadding(bottom = initialBottom + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * Bottom insets for a **scrolling list**: reserves the navigation-bar (plus display cutout and IME) inset as
     * bottom padding (on top of the view's original padding) so the last item rests *above* the nav bar in every
     * mode — no overlap with the gesture pill or the buttons — while still letting content draw behind the
     * transparent **gesture** nav *while scrolling*.
     *
     * The trick is [ViewGroup.setClipToPadding], toggled per navigation mode on each inset dispatch:
     * - **Gesture navigation** (a navigation bar is present but nothing there is tappable — the pill):
     *   `clipToPadding = false`, so items scroll *through* the reserved padding, behind the transparent pill,
     *   and rest above it at the end.
     * - **2/3-button navigation** (or no bottom nav bar): `clipToPadding = true`, so items stay above the
     *   (opaque) button bar and never bleed behind it — the button bar keeps showing the window background.
     *
     * Use this for scrolling lists; use [applyNavigationBarInsets] for static content and full-bleed WebViews.
     * Pass the scrolling view itself (RecyclerView/ScrollView); for a scroll nested inside a fragment, the
     * hosting container only gets the reserved padding (last item clears the bar), not the in-scroll bleed.
     *
     * @param view The scrolling view (or its hosting container).
     */
    fun applyScrollableNavigationBarInsets(view: View) {
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navigationBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            ).bottom

            (v as? ViewGroup)?.clipToPadding = !(navigationBar > 0 && tappable == 0)
            v.updatePadding(bottom = initialBottom + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * Adds the navigation-bar + cutout + IME inset as [view]'s bottom margin, so a scrolling bottom bar clips at the nav-bar edge when hidden.
     *
     * @param view The view whose bottom margin is updated; must use [ViewGroup.MarginLayoutParams].
     */
    fun applyNavigationBarInsetsAsMargin(view: View) {
        val initialBottom = (view.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        view.applyInsets { insets ->
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            ).bottom

            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val newMargin = initialBottom + bottom
                if (lp.bottomMargin != newMargin) {
                    lp.bottomMargin = newMargin
                    view.requestLayout()
                }
            }
        }
    }

    /**
     * Pads [view]'s left/right by the side navigation-bar + cutout inset (landscape only; no-op in portrait).
     *
     * @param view The view to pad on the left/right edges.
     */
    fun applyHorizontalSystemBarInsets(view: View) {
        val initialLeft = view.paddingLeft
        val initialRight = view.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val barsAndCutout = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.updatePadding(
                left = initialLeft + barsAndCutout.left,
                right = initialRight + barsAndCutout.right,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * Combines [applyHorizontalSystemBarInsets] and [applyNavigationBarInsetsAsMargin] in a single listener, for
     * when both are needed on the *same* view: only one [androidx.core.view.OnApplyWindowInsetsListener] can be
     * attached per view, so calling both separately on the same view would silently drop the first one.
     *
     * @param view The view to pad on the left/right edges and set the bottom margin on; must use [ViewGroup.MarginLayoutParams].
     */
    fun applyHorizontalInsetsAndNavigationBarMargin(view: View) {
        val initialLeft = view.paddingLeft
        val initialRight = view.paddingRight
        val initialBottomMargin = (view.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        view.applyInsets { insets ->
            val barsAndCutout = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(
                left = initialLeft + barsAndCutout.left,
                right = initialRight + barsAndCutout.right,
            )

            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            ).bottom
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val newMargin = initialBottomMargin + bottom
                if (lp.bottomMargin != newMargin) {
                    lp.bottomMargin = newMargin
                    view.requestLayout()
                }
            }
        }
    }

    /**
     * Pads [view] on all four edges by the system-bar (status + navigation), display-cutout and IME insets, in a
     * single listener. Use for a screen whose root carries a full-bleed background and has no separate toolbar:
     * the background fills the padded bounds (so it still reaches every edge) while the content is inset clear of
     * every system bar. No scrim is painted — the view's own background is the full-bleed surface.
     *
     * @param view The full-bleed root to pad on every edge.
     */
    fun applySystemBarInsets(view: View) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            )
            v.updatePadding(
                left = initialLeft + bars.left,
                top = initialTop + bars.top,
                right = initialRight + bars.right,
                bottom = initialBottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun View.applyInsets(apply: (WindowInsetsCompat) -> Unit) {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            apply(insets)
            insets
        }
        doOnAttach { attached ->
            ViewCompat.getRootWindowInsets(attached)?.let(apply)
            ViewCompat.requestApplyInsets(attached)
        }
    }

    /**
     * Installs a status-bar scrim: a view sized to the top (status-bar + cutout) inset and filled with the
     * theme's status-bar colour (the toolbar colour), added over the content so it renders behind the transparent status bar.
     *
     * On Android 15 (targetSdk 35) `window.statusBarColor` is ignored, so painting this scrim is how the solid
     * status-bar colour the screen had before edge-to-edge is restored. Every edge-to-edge screen already pads
     * its content below the status bar, so the scrim only repaints the otherwise-empty strip. Idempotent per
     * screen; a no-op when the colour can't be resolved.
     */
    private fun installStatusBarScrim(anchor: View) {
        val contentRoot = anchor.rootView?.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (contentRoot.findViewWithTag<View>(STATUS_BAR_SCRIM_TAG) != null) return
        val scrimColor = anchor.context.resolveStatusBarScrimColor() ?: return

        val scrim = View(anchor.context).apply {
            tag = STATUS_BAR_SCRIM_TAG
            setBackgroundColor(scrimColor)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP)
        }
        contentRoot.addView(scrim)

        ViewCompat.setOnApplyWindowInsetsListener(scrim) { v, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
            ).top
            if (v.layoutParams.height != top) {
                v.updateLayoutParams { height = top }
            }
            insets
        }
        ViewCompat.requestApplyInsets(scrim)
    }

    /**
     * Resolves the scrim colour from the theme's framework [android.R.attr.statusBarColor], which the app themes
     * wire to the toolbar colour (`?attr/preferredStatusBarColor` -> `daxColorToolbar`). Using the framework attr
     * keeps this in `common-utils`, which can't reference the design-system's `daxColorToolbar` attr directly.
     */
    private fun Context.resolveStatusBarScrimColor(): Int? {
        val typedValue = TypedValue()
        return if (theme.resolveAttribute(android.R.attr.statusBarColor, typedValue, true)) typedValue.data else null
    }

    companion object {
        private const val STATUS_BAR_SCRIM_TAG = "edge_to_edge_status_bar_scrim"
    }
}
