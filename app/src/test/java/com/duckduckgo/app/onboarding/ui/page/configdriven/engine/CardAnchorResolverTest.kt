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
import com.duckduckgo.app.onboarding.ui.page.configdriven.Embellishment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock

class CardAnchorResolverTest {

    private val decorationView: View = mock()

    private fun settled(embellishment: Embellishment) = SettledDecoration(
        view = decorationView,
        placement = EmbellishmentPlacement.of(embellishment),
    )

    @Test
    fun `on a phone the card anchors above a bottom wing at its phone bias`() {
        val resolution = CardAnchorResolver(isTablet = false).resolve(settled(Embellishment.BottomWing))

        assertSame(decorationView, resolution.anchorTo)
        assertEquals(0f, resolution.verticalBias)
        assertEquals(1f, resolution.arrowDepthFraction)
    }

    @Test
    fun `on a tablet the card anchors above a bottom wing at its tablet bias`() {
        val resolution = CardAnchorResolver(isTablet = true).resolve(settled(Embellishment.BottomWing))

        assertSame(decorationView, resolution.anchorTo)
        assertEquals(0.5f, resolution.verticalBias)
    }

    @Test
    fun `on a phone a side decoration leaves the card pinned to the parent bottom`() {
        val resolution = CardAnchorResolver(isTablet = false).resolve(settled(Embellishment.LeftWing))

        assertNull(resolution.anchorTo)
        assertEquals(0f, resolution.verticalBias)
    }

    @Test
    fun `on a phone a side decoration still gives the card's arrow something to point at`() {
        val resolution = CardAnchorResolver(isTablet = false).resolve(settled(Embellishment.BobbingDax))

        assertEquals(1f, resolution.arrowDepthFraction)
    }

    @Test
    fun `on a tablet a side decoration anchors the card above it`() {
        val resolution = CardAnchorResolver(isTablet = true).resolve(settled(Embellishment.BobbingDax))

        assertSame(decorationView, resolution.anchorTo)
        assertEquals(0.5f, resolution.verticalBias)
    }

    @Test
    fun `the walking dax presses the card down onto itself`() {
        val resolution = CardAnchorResolver(isTablet = false).resolve(settled(Embellishment.WalkingDax))

        assertSame(decorationView, resolution.anchorTo)
        assertEquals(1f, resolution.verticalBias)
    }

    @Test
    fun `on a phone the undecorated band anchors the card so it sits where a decorated card sits`() {
        val resolution = CardAnchorResolver(isTablet = false).resolve(settled(Embellishment.None))

        assertSame(decorationView, resolution.anchorTo)
        assertEquals(0f, resolution.verticalBias)
    }

    @Test
    fun `on a tablet the undecorated band anchors the card`() {
        val resolution = CardAnchorResolver(isTablet = true).resolve(settled(Embellishment.None))

        assertSame(decorationView, resolution.anchorTo)
        assertEquals(0.5f, resolution.verticalBias)
    }

    @Test
    fun `the undecorated band leaves the card's arrow with nothing to point at`() {
        val resolution = CardAnchorResolver(isTablet = true).resolve(settled(Embellishment.None))

        assertEquals(0f, resolution.arrowDepthFraction)
    }

    @Test
    fun `a decoration that did not fit pins the card high, since the card is already near-filling its space`() {
        val resolution = CardAnchorResolver(isTablet = true).resolve(null)

        assertNull(resolution.anchorTo)
        assertEquals(0f, resolution.verticalBias)
        assertEquals(0f, resolution.arrowDepthFraction)
    }
}
