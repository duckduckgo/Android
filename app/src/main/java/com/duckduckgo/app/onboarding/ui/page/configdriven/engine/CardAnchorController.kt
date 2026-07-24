/*
 * Copyright (c) 2025 DuckDuckGo
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
 * Owns the card-anchor axis: whether the dax card sits above the settled decoration or pinned to
 * the parent bottom, the card's vertical bias in either case, and the bubble arrow's depth/visibility.
 * Ported from the five repeated anchor blocks in `BrandDesignUpdateWelcomePage`
 * (:1057-1067 quick setup, :1257-1267 widget, :1288-1298 address bar, :1430-1440 input screen,
 * :1681-1691 comparison chart).
 *
 * Those blocks disagree dialog-by-dialog on which decorations anchor the card on phones (walking
 * dax and the bottom wing do; bobbing dax and the left wing only anchor on tablet) and on the card's
 * bias while anchored (welcome presses the card down onto the walking dax with the XML's bias 1; the
 * bottom wing keeps it top-biased on phones) — both encoded as [SettledDecoration] data
 * ([SettledDecoration.anchorsCardOnPhone] / the anchored-bias pair) so this controller never branches
 * on dialog/decoration identity. Unanchored, legacy uses bias 0 on phones everywhere and is
 * inconsistent on tablets (0.5 in quick-setup/add-to-dock, 0 elsewhere); normalized here to
 * tablet 0.5 / phone 0 — the one deliberate POC simplification left on this axis.
 */
class CardAnchorController(private val binding: ContentOnboardingWelcomePageUpdateBinding) {

    /**
     * @param settled The decoration the embellishment axis settled on, or null (no decoration /
     *   vetoed by fit). Card anchors above [SettledDecoration.view] when non-null and either
     *   [isTablet] or [SettledDecoration.anchorsCardOnPhone] is true; otherwise pins to parent bottom.
     *
     * Arrow *visibility* (`setShowArrow`) is deliberately not handled here: it is screen data the
     * engine applies synchronously at render time — this method fires from the embellishment axis's
     * settle, which waits out a previous decoration's exit, far too late for the arrow swap legacy
     * performs at the same moment it begins the card's bounds transition (:1535-1537).
     */
    fun apply(settled: SettledDecoration?, isTablet: Boolean) {
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
