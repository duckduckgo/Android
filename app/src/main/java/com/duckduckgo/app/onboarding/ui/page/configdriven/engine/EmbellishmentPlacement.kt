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

import com.duckduckgo.app.onboarding.ui.page.configdriven.Embellishment

/**
 * Where each [Embellishment] puts the card, kept free of views so the whole table is unit-testable.
 *
 * Every embellishment declares its own placement, including [Embellishment.None], whose undrawn band reserves
 * the room a decoration would have occupied. Nothing here may depend on which screen rendered previously: the
 * onboarding step order is meant to be reshuffled freely.
 */
object EmbellishmentPlacement {

    fun of(embellishment: Embellishment): Placement = when (embellishment) {
        // Bias 1 presses the card down against the dax on a phone; a tablet has room to spare above it, so the
        // card centres instead of hanging off the artwork.
        Embellishment.WalkingDax -> Placement(anchorsCardOnPhone = true, biasPhone = 1f, biasTablet = 0.5f, drawsArtwork = true)
        Embellishment.BottomWing -> Placement(anchorsCardOnPhone = true, biasPhone = 0f, biasTablet = 0.5f, drawsArtwork = true)
        // The side decorations reserve no room on a phone, where the card runs down past them instead.
        Embellishment.LeftWing -> Placement(anchorsCardOnPhone = false, biasPhone = 0f, biasTablet = 0.5f, drawsArtwork = true)
        Embellishment.BobbingDax -> Placement(anchorsCardOnPhone = false, biasPhone = 0f, biasTablet = 0.5f, drawsArtwork = true)
        Embellishment.None -> Placement(anchorsCardOnPhone = true, biasPhone = 0f, biasTablet = 0.5f, drawsArtwork = false)
    }

    /**
     * @param anchorsCardOnPhone whether this embellishment reserves room below the card on a phone. False means
     *   the card pins to the parent bottom and the artwork overlaps it.
     * @param drawsArtwork false for [Embellishment.None]'s band, which reserves room without drawing, so the
     *   card's bubble arrow has nothing to point at.
     */
    data class Placement(
        val anchorsCardOnPhone: Boolean,
        val biasPhone: Float,
        val biasTablet: Float,
        val drawsArtwork: Boolean,
    )
}
