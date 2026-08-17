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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbellishmentPlacementTest {

    @Test
    fun `the walking dax presses the card down onto itself on a phone and centres it on a tablet`() {
        val placement = EmbellishmentPlacement.of(Embellishment.WalkingDax)

        assertTrue(placement.anchorsCardOnPhone)
        assertEquals(1f, placement.biasPhone)
        assertEquals(0.5f, placement.biasTablet)
        assertTrue(placement.drawsArtwork)
    }

    @Test
    fun `the bottom wing reserves room on both form factors`() {
        val placement = EmbellishmentPlacement.of(Embellishment.BottomWing)

        assertTrue(placement.anchorsCardOnPhone)
        assertEquals(0f, placement.biasPhone)
        assertEquals(0.5f, placement.biasTablet)
        assertTrue(placement.drawsArtwork)
    }

    @Test
    fun `the left wing reserves room on tablet only`() {
        val placement = EmbellishmentPlacement.of(Embellishment.LeftWing)

        assertFalse(placement.anchorsCardOnPhone)
        assertEquals(0.5f, placement.biasTablet)
        assertTrue(placement.drawsArtwork)
    }

    @Test
    fun `the bobbing dax reserves room on tablet only`() {
        val placement = EmbellishmentPlacement.of(Embellishment.BobbingDax)

        assertFalse(placement.anchorsCardOnPhone)
        assertEquals(0.5f, placement.biasTablet)
        assertTrue(placement.drawsArtwork)
    }

    @Test
    fun `the undecorated band reserves room on both form factors and draws nothing`() {
        val placement = EmbellishmentPlacement.of(Embellishment.None)

        assertTrue(placement.anchorsCardOnPhone)
        assertEquals(0f, placement.biasPhone)
        assertEquals(0.5f, placement.biasTablet)
        assertFalse(placement.drawsArtwork)
    }
}
