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

package com.duckduckgo.app.browser.state

import com.duckduckgo.app.browser.state.IdleReturnLandingGate.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleReturnLandingGateTest {

    private val gate = IdleReturnLandingGate()

    @Test
    fun whenHeldAndResolvedWithTabThenAwaitsThatTab() {
        gate.hold()
        gate.resolve("ntp")

        assertEquals(State.AwaitingTab("ntp"), gate.state)
    }

    @Test
    fun whenHeldAndResolvedWithoutTabThenOpens() {
        gate.hold()
        gate.resolve(null)

        assertEquals(State.Open, gate.state)
    }

    @Test
    fun whenResolvedAfterOpeningThenStaysOpen() {
        gate.hold()
        gate.open()
        gate.resolve("ntp")

        assertEquals(State.Open, gate.state)
    }

    @Test
    fun whenResolvedWithNullAfterResolvingTabThenKeepsAwaitedTab() {
        gate.hold()
        gate.resolve("ntp")
        gate.resolve(null)

        assertEquals(State.AwaitingTab("ntp"), gate.state)
    }

    @Test
    fun whenResolvedWithNullAfterResolvingTabAwaitingHatchThenKeepsAwaitingHatch() {
        gate.hold()
        gate.resolve("ntp", awaitHatch = true)
        gate.resolve(null)

        assertEquals(State.AwaitingTab("ntp", awaitHatch = true), gate.state)
    }

    @Test
    fun whenAwaitingHatchThenNotReadyUntilItsHatchSettles() {
        gate.hold()
        gate.resolve("ntp", awaitHatch = true)
        assertFalse((gate.state as State.AwaitingTab).isReady)

        gate.onHatchSettled("other")
        assertFalse((gate.state as State.AwaitingTab).isReady)

        gate.onHatchSettled("ntp")
        assertTrue((gate.state as State.AwaitingTab).isReady)
    }

    @Test
    fun whenHatchSettlesBeforeResolveThenAwaitedTabIsReady() {
        gate.hold()
        gate.onHatchSettled("ntp")
        gate.resolve("ntp", awaitHatch = true)

        assertTrue((gate.state as State.AwaitingTab).isReady)
    }

    @Test
    fun whenNotAwaitingHatchThenReady() {
        gate.hold()
        gate.resolve("ntp")

        assertTrue((gate.state as State.AwaitingTab).isReady)
    }

    @Test
    fun whenOpenedThenEarlierHatchSettlesAreForgotten() {
        gate.hold()
        gate.onHatchSettled("ntp")
        gate.open()
        gate.hold()
        gate.resolve("ntp", awaitHatch = true)

        assertFalse((gate.state as State.AwaitingTab).isReady)
    }

    @Test
    fun whenHatchSettlesWhileOpenThenItIsIgnored() {
        gate.onHatchSettled("ntp")
        gate.hold()
        gate.resolve("ntp", awaitHatch = true)

        assertFalse((gate.state as State.AwaitingTab).isReady)
    }

    @Test
    fun whenHoldResetsWithoutOpenThenEarlierHatchSettlesAreForgotten() {
        gate.hold()
        gate.onHatchSettled("ntp")
        gate.hold()
        gate.resolve("ntp", awaitHatch = true)

        assertFalse((gate.state as State.AwaitingTab).isReady)
    }

    @Test
    fun whenResolvedAgainAfterHatchSettledThenStaysReady() {
        gate.hold()
        gate.resolve("ntp", awaitHatch = true)
        gate.onHatchSettled("ntp")
        gate.resolve(null)

        assertTrue((gate.state as State.AwaitingTab).isReady)
    }
}
