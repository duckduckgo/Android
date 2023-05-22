/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.waitlist.store

import com.duckduckgo.networkprotection.impl.fakes.FakeNetPWaitlistDataStore
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations

class RealNetPWaitlistRepositoryTest {

    private val fakeNetPWaitlistDataStore = FakeNetPWaitlistDataStore()

    private val fakeToken = "fake_token"

    private lateinit var testee: RealNetPWaitlistRepository

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        testee = RealNetPWaitlistRepository(fakeNetPWaitlistDataStore)
    }

    @Test
    fun whenStartingInternalStateIsPendingInviteCode() {
        assertEquals(NetPWaitlistState.PendingInviteCode, testee.getState(true))
    }

    @Test
    fun whenStartingInternalAndAuthTokenStateIsInBeta() {
        testee.setAuthenticationToken(fakeToken)
        assertEquals(NetPWaitlistState.InBeta, testee.getState(true))
    }

    @Test
    fun whenStartingNonInternalStateIsLocked() {
        assertEquals(NetPWaitlistState.NotUnlocked, testee.getState(false))
    }

    @Test
    fun whenAuthTokenSetStateIsInBeta() {
        testee.setAuthenticationToken(fakeToken)
        assertEquals(NetPWaitlistState.InBeta, testee.getState(true))
        assertEquals(NetPWaitlistState.InBeta, testee.getState(false))
    }

    @Test
    fun whenAuthTokenNotSetGetTokenReturnsNull() {
        assertNull(testee.getAuthenticationToken())
    }

    @Test
    fun whenAuthTokenSetGetTokenReturnsToken() {
        testee.setAuthenticationToken(fakeToken)
        assertEquals(fakeToken, testee.getAuthenticationToken())
    }
}
