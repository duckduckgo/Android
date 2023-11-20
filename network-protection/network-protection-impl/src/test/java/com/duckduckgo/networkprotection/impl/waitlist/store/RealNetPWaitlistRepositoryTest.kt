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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.impl.fakes.FakeNetPWaitlistDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
class RealNetPWaitlistRepositoryTest {

    private val coroutineRule = CoroutineTestRule()

    private val fakeNetPWaitlistDataStore = FakeNetPWaitlistDataStore()

    private val fakeToken = "fake_token"

    private lateinit var testee: RealNetPWaitlistRepository

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        testee = RealNetPWaitlistRepository(fakeNetPWaitlistDataStore, coroutineRule.testDispatcherProvider, coroutineRule.testScope)
    }

    @Test
    fun whenAuthTokenNotSetGetTokenReturnsNull() = runTest {
        assertNull(testee.getAuthenticationToken())
    }

    @Test
    fun whenAuthTokenSetGetTokenReturnsToken() = runTest {
        testee.setAuthenticationToken(fakeToken)
        assertEquals(fakeToken, testee.getAuthenticationToken())
    }

    @Test
    fun whenClearStoreThenClearData() = runTest {
        testee.setAuthenticationToken(fakeToken)
        testee.setWaitlistToken(fakeToken)
        testee.setWaitlistTimestamp(11)
        testee.acceptWaitlistTerms()

        testee.clearStore()
        assertNotEquals(fakeToken, testee.getAuthenticationToken())
        assertNotEquals(fakeToken, testee.getWaitlistToken())
        assertNotEquals(11, testee.getWaitlistTimestamp())
        assertFalse(testee.didAcceptWaitlistTerms())
    }
}
