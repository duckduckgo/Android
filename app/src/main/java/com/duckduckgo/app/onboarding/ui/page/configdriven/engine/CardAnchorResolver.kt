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

package com.duckduckgo.app.onboarding.ui.page.configdriven.engine

import android.view.View

/**
 * Resolves where the card's bottom edge goes from the placement the settled decoration declares. Free of views
 * beyond the one it passes through, so the placement table is unit-testable.
 *
 * The card anchors above the decoration when it is a tablet or the decoration reserves room on a phone, at the
 * bias that decoration declares for the form factor. Otherwise it pins to the parent bottom.
 */
class CardAnchorResolver(private val isTablet: Boolean) {

    /** @param settled null when the screen's decoration did not fit the room the card left it. */
    fun resolve(settled: SettledDecoration?): Resolution {
        if (settled == null) {
            return Resolution(anchorTo = null, verticalBias = UNANCHORED_CARD_BIAS, arrowDepthFraction = 0f)
        }
        val placement = settled.placement
        val arrowDepthFraction = if (placement.drawsArtwork) 1f else 0f
        if (!isTablet && !placement.anchorsCardOnPhone) {
            return Resolution(anchorTo = null, verticalBias = UNANCHORED_CARD_BIAS, arrowDepthFraction = arrowDepthFraction)
        }
        return Resolution(
            anchorTo = settled.view,
            verticalBias = if (isTablet) placement.biasTablet else placement.biasPhone,
            arrowDepthFraction = arrowDepthFraction,
        )
    }

    /** @param anchorTo the view the card's bottom constrains to, or null to pin it to the parent bottom. */
    data class Resolution(
        val anchorTo: View?,
        val verticalBias: Float,
        val arrowDepthFraction: Float,
    )

    private companion object {
        /**
         * The card pins to the parent bottom either because its decoration did not fit, in which case the card
         * is already near-filling its space, or because a side decoration reserves no room on a phone.
         */
        const val UNANCHORED_CARD_BIAS = 0f
    }
}
