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
    private val resolver: CardAnchorResolver,
) : CardAnchorController {

    /**
     * Arrow visibility is deliberately not handled here: it is screen data off the config, whereas the depth
     * below follows from what the embellishment axis settled on.
     */
    override fun apply(settled: SettledDecoration?) {
        val resolution = resolver.resolve(settled)

        binding.daxDialogCta.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
            val anchorTo = resolution.anchorTo
            if (anchorTo != null) {
                bottomToTop = anchorTo.id
                bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            } else {
                bottomToTop = ConstraintLayout.LayoutParams.UNSET
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
            verticalBias = resolution.verticalBias
        }

        binding.daxDialogCta.cardView.setArrowDepthFraction(resolution.arrowDepthFraction)
    }
}
