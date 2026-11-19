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

package com.wireguard.android.backend

import com.wireguard.android.backend.FlowOwners.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowOwnersTest {

    private val flowOwners = FlowOwners(capacity = 2)
    private val flow = Flow(TCP, "10.0.0.2", 40000, "185.151.204.15", 443)

    /** The owners [judge] was asked about, in order. */
    private val judged = mutableListOf<Int>()

    private fun hello(flow: Flow = this.flow, sni: String = TRACKER, lookedUp: Int = APP, blockFor: Set<Int> = setOf(APP)) =
        flowOwners.allow(flow, sni, lookup = { lookedUp }, judge = { owner ->
            judged += owner
            owner !in blockFor
        })

    @Test
    fun whenOwnerIsKnownThenTheHelloIsJudgedAsItsOwners() {
        assertFalse(hello())
        assertEquals(listOf(APP), judged)
    }

    @Test
    fun whenOwnerIsGoneThenTheHelloIsJudgedAsTheOwnerSeenEarlier() {
        hello(blockFor = emptySet())

        assertTrue(hello(lookedUp = 0, blockFor = emptySet()))
        assertTrue(hello(lookedUp = -1, blockFor = emptySet()))
        assertEquals(listOf(APP, APP, APP), judged)
    }

    @Test
    fun whenOwnerOfANewFlowIsUnknownThenItIsJudgedAsUnknown() {
        hello(blockFor = emptySet())

        hello(flow = flow.copy(sport = 40001), lookedUp = 0)
        assertEquals(listOf(APP, 0), judged)
    }

    @Test
    fun whenCapacityIsExceededThenTheLeastRecentOwnerIsForgotten() {
        hello(blockFor = emptySet())
        hello(flow = flow.copy(sport = 40001), lookedUp = 10194, blockFor = emptySet())
        hello(flow = flow.copy(sport = 40002), lookedUp = 10195, blockFor = emptySet())

        hello(lookedUp = 0, blockFor = emptySet())
        assertEquals(0, judged.last())
    }

    @Test
    fun whenAConnectionIsBlockedThenItsRetransmittedHelloIsBlockedWithoutBeingJudgedAgain() {
        assertFalse(hello())

        assertFalse(hello(lookedUp = 0))
        assertFalse(hello())
        assertEquals(listOf(APP), judged)
    }

    @Test
    fun whenAConnectionIsAllowedThenEachHelloIsJudged() {
        assertTrue(hello(blockFor = emptySet()))
        assertTrue(hello(blockFor = emptySet()))

        assertEquals(listOf(APP, APP), judged)
    }

    @Test
    fun whenAConnectionIsBlockedThenAnotherServerNameOrFlowIsJudged() {
        hello()

        hello(sni = "example.com")
        hello(flow = flow.copy(sport = 40001))
        assertEquals(listOf(APP, APP, APP), judged)
    }

    @Test
    fun whenAllowedTrafficFillsTheOwnersThenABlockedConnectionStaysBlocked() {
        hello()
        repeat(5) { hello(flow = flow.copy(sport = 41000 + it), lookedUp = 10194, blockFor = emptySet()) }

        assertFalse(hello(lookedUp = 0))
        assertEquals(6, judged.size)
    }

    @Test
    fun whenMoreConnectionsAreBlockedThanItsCapacityThenTheLeastRecentIsJudgedAgain() {
        hello()
        hello(flow = flow.copy(sport = 40001))
        hello(flow = flow.copy(sport = 40002))

        hello()
        assertEquals(4, judged.size)
    }

    private companion object {
        const val TCP = 6
        const val APP = 10193
        const val TRACKER = "app.adjust.com"
    }
}
