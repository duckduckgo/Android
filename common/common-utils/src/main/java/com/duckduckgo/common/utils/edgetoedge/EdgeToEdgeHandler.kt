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

import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
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
     */
    fun applyStatusBarAndHorizontalInsets(view: View) {
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
    }

    /**
     * Pads [view]'s top by the status-bar + cutout inset.
     *
     * @param view The view to pad at the top edge.
     */
    fun applyStatusBarInsets(view: View) {
        val initialTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
            ).top
            v.updatePadding(top = initialTop + top)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * Pads [view]'s bottom by the navigation-bar (plus display cutout and IME) inset.
     *
     * When [drawBehindGestureNav] is true, the padding uses the *tappable* navigation inset, which is
     * 0 in gesture navigation (so content draws edge-to-edge behind the transparent gesture handle) and
     * the button-bar height in 2/3-button navigation (so content sits above the buttons). When false it
     * uses the full navigation-bar inset, keeping content clear of the bar in every navigation mode.
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
     * Sets [window]'s display-cutout mode from [orientation]: in landscape the window avoids the
     * cutout (the notch/camera area is reserved as a black letterbox), in portrait it keeps the
     * default behaviour and draws normally around the (typically top-centre) cutout.
     *
     * @param window The activity window whose layout params are updated.
     * @param orientation A [Configuration] orientation, e.g. [Configuration.ORIENTATION_LANDSCAPE].
     */
    fun applyDisplayCutoutMode(
        window: Window,
        orientation: Int,
    ) {
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
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
}
