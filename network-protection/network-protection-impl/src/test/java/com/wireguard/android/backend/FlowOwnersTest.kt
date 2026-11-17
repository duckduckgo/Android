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
import org.junit.Test

class FlowOwnersTest {

    private val flowOwners = FlowOwners(capacity = 2)
    private val flow = Flow(TCP, "10.0.0.2", 40000, "185.151.204.15", 443)

    @Test
    fun whenOwnerIsKnownThenItIsReturned() {
        assertEquals(10193, flowOwners.ownerOf(flow, 10193))
    }

    @Test
    fun whenOwnerIsGoneThenTheOwnerSeenEarlierIsReturned() {
        flowOwners.ownerOf(flow, 10193)

        assertEquals(10193, flowOwners.ownerOf(flow, 0))
        assertEquals(10193, flowOwners.ownerOf(flow, -1))
    }

    @Test
    fun whenOwnerOfANewFlowIsUnknownThenItStaysUnknown() {
        flowOwners.ownerOf(flow, 10193)

        assertEquals(0, flowOwners.ownerOf(flow.copy(sport = 40001), 0))
    }

    @Test
    fun whenCapacityIsExceededThenTheLeastRecentFlowIsForgotten() {
        flowOwners.ownerOf(flow, 10193)
        flowOwners.ownerOf(flow.copy(sport = 40001), 10194)
        flowOwners.ownerOf(flow.copy(sport = 40002), 10195)

        assertEquals(0, flowOwners.ownerOf(flow, 0))
        assertEquals(10195, flowOwners.ownerOf(flow.copy(sport = 40002), 0))
    }

    private companion object {
        const val TCP = 6
    }
}
