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

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageUpdateBinding

/**
 * Owns the card-anchor axis: whether the dax card sits above the settled decoration or pinned to the parent
 * bottom, the card's vertical bias in either case, and the bubble arrow's depth.
 */
interface CardAnchorController {
    fun apply(settled: SettledDecoration?)
}

class CardAnchorControllerImpl(
    private val binding: ContentOnboardingWelcomePageUpdateBinding,
    private val isTablet: Boolean,
) : CardAnchorController {

    /**
     * @param settled The decoration the embellishment axis settled on, or null when there is none or the fit
     *   check vetoed it. The card anchors above [SettledDecoration.view] when non-null and either [isTablet] or
     *   [SettledDecoration.anchorsCardOnPhone] is true; otherwise it pins to the parent bottom.
     *
     * Arrow visibility is deliberately not handled here: it is screen data off the config, whereas the depth
     * below follows from what the embellishment axis settled on.
     */
    override fun apply(settled: SettledDecoration?) {
        val card = binding.daxDialogCta.root

        card.updateLayoutParams<ConstraintLayout.LayoutParams> {
            if (settled != null && (isTablet || settled.anchorsCardOnPhone)) {
                bottomToTop = settled.view.id
                bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                verticalBias = if (isTablet) settled.anchoredCardBiasTablet else settled.anchoredCardBiasPhone
            } else {
                bottomToTop = ConstraintLayout.LayoutParams.UNSET
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                verticalBias = if (isTablet) 0.5f else 0f
            }
        }

        binding.daxDialogCta.cardView.setArrowDepthFraction(if (settled != null) 1f else 0f)
    }
}
