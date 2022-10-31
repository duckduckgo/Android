/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.waitlist.store

import org.junit.Assert.*
import org.junit.Test

class WaitlistStateRepositoryTest {

    private val testee = WaitlistStateRepository()

    @Test
    fun whenGettingStateAndUserInBetaTheReturnInBeta() {
        val state = testee.getState()

        assertEquals(WaitlistState.InBeta, state)
    }

    @Test
    fun whenUserJoinedWaitlistAfterCuttingEdgeThenReturnsTrue() {
        assertTrue(testee.joinedAfterCuttingDate())
    }
}
