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

import com.duckduckgo.mobile.android.vpn.waitlist.AppTrackingProtectionWaitlistDataStore
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WaitlistStateRepositoryTest {

    private val dataStore: AppTrackingProtectionWaitlistDataStore = mock()
    private val testee = WaitlistStateRepository(dataStore)

    @Test
    fun whenGettingStateAndUserInBetaTheReturnInBeta() {
        whenever(dataStore.inviteCode).thenReturn("inviteCode")

        val state = testee.getState()

        assertEquals(WaitlistState.InBeta, state)
    }

    @Test
    fun whenGettingStateAndUserJoinedWaitlistTheReturnJoinedWaitlist() {
        whenever(dataStore.inviteCode).thenReturn(null)
        whenever(dataStore.waitlistTimestamp).thenReturn(1643404916)
        whenever(dataStore.waitlistToken).thenReturn("someToken")

        val state = testee.getState()

        assertTrue(state is WaitlistState.JoinedWaitlist)
    }

    @Test
    fun whenGettingStateAndUserNotIntBetaOrWaitlistTheReturnNotJoinedQueue() {
        whenever(dataStore.waitlistTimestamp).thenReturn(-1)
        whenever(dataStore.waitlistToken).thenReturn(null)

        val state = testee.getState()

        assertEquals(WaitlistState.NotJoinedQueue, state)
    }

    @Test
    fun whenUserJoinedWaitlistAfterCuttingEdgeThenReturnsTrue() {
        whenever(dataStore.waitlistTimestamp).thenReturn(1643404916)

        assertTrue(testee.joinedAfterCuttingDate())
    }

    @Test
    fun whenUserJoinedWaitlistBeforeCuttingEdgeThenReturnsFalse() {
        whenever(dataStore.waitlistTimestamp).thenReturn(1639343328)

        assertFalse(testee.joinedAfterCuttingDate())
    }
}
