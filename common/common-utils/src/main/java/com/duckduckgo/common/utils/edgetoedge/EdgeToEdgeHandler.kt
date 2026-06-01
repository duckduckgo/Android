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

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    fun applyStatusBarInsets(view: View) {
        val initialTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
            ).top
            v.updatePadding(top = initialTop + top)
            insets
        }
    }

    fun applyNavigationBarInsets(view: View) {
        val initialLeft = view.paddingLeft
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            // The nav bar sits at the bottom in portrait but moves to a side edge in landscape
            // (where bottom is then 0); the display cutout (camera notch) likewise moves to a side
            // edge in landscape. Union both so content clears the nav bar AND the cutout on whichever
            // side/bottom edge they land. The top edge is handled by applyStatusBarInsets.
            val barsAndCutout = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.updatePadding(
                left = initialLeft + barsAndCutout.left,
                right = initialRight + barsAndCutout.right,
                bottom = initialBottom + barsAndCutout.bottom,
            )
            insets
        }
    }
}
