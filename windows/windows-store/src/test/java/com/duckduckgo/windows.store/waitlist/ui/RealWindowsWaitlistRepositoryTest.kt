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

package com.duckduckgo.windows.store.waitlist.ui

import com.duckduckgo.windows.api.WindowsWaitlistState.InBeta
import com.duckduckgo.windows.api.WindowsWaitlistState.JoinedWaitlist
import com.duckduckgo.windows.api.WindowsWaitlistState.NotJoinedQueue
import com.duckduckgo.windows.store.RealWindowsWaitlistRepository
import com.duckduckgo.windows.store.WindowsWaitlistDataStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RealWindowsWaitlistRepositoryTest {

    private lateinit var testee: RealWindowsWaitlistRepository
    private lateinit var fakeDataStore: WindowsWaitlistDataStore

    @Before
    fun before() {
        fakeDataStore = FakeWindowsWaitlistDataStore()
        testee = RealWindowsWaitlistRepository(fakeDataStore)
    }

    @Test
    fun whenJoinWaitlistIfTimestampAndTokenDidNotExistThenStoreTimestampAndToken() {
        fakeDataStore.waitlistTimestamp = -1
        fakeDataStore.waitlistToken = null

        testee.joinWaitlist(1234, "abcde")

        assertEquals(1234, fakeDataStore.waitlistTimestamp)
        assertEquals("abcde", fakeDataStore.waitlistToken)
    }

    @Test
    fun whenJoinWaitlistIfTimestampAndTokenDidExistThenStoreTimestampAndTokenAreNotStored() {
        fakeDataStore.waitlistTimestamp = 1234
        fakeDataStore.waitlistToken = "abcde"

        testee.joinWaitlist(4321, "edcba")

        assertEquals(1234, fakeDataStore.waitlistTimestamp)
        assertEquals("abcde", fakeDataStore.waitlistToken)
    }

    @Test
    fun whenGetTokenThenWaitlistTokenReturned() {
        fakeDataStore.waitlistToken = "token"
        assertEquals("token", testee.getToken())
    }

    @Test
    fun whenGetTimestampThenTimestampReturned() {
        fakeDataStore.waitlistTimestamp = 1234
        assertEquals(1234, testee.getTimestamp())
    }

    @Test
    fun whenGetInviteCodeThenInviteCodeReturned() {
        fakeDataStore.inviteCode = "inviteCode"
        assertEquals("inviteCode", testee.getInviteCode())
    }

    @Test
    fun whenSetInviteCodeThenValueStored() {
        testee.setInviteCode("myInviteCode")
        assertEquals("myInviteCode", fakeDataStore.inviteCode)
    }

    @Test
    fun whenGetStateIfUserIsInWaitlistCodeDoesNotExistThenReturnJoinedQueue() {
        givenUserIsInWaitlist()

        assertEquals(JoinedWaitlist, testee.getState())
    }

    @Test
    fun whenGetStateIfUserIsInWaitlistAndCodeExistsThenReturnInBeta() {
        givenUserIsInWaitlist()
        fakeDataStore.inviteCode = "abcde"

        assertEquals(InBeta("abcde"), testee.getState())
    }

    @Test
    fun whenGetStateIfTimestampAndCodeDoesNotExistThenReturnNotJoinedQueue() {
        fakeDataStore.waitlistTimestamp = -1
        fakeDataStore.waitlistToken = null

        assertEquals(NotJoinedQueue, testee.getState())
    }

    private fun givenUserIsInWaitlist() {
        fakeDataStore.waitlistTimestamp = 1234
        fakeDataStore.waitlistToken = "token"
    }
}

class FakeWindowsWaitlistDataStore : WindowsWaitlistDataStore {
    override var inviteCode: String? = null
    override var waitlistTimestamp: Int = -1
    override var waitlistToken: String? = null
}
