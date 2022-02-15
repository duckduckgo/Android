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

package com.duckduckgo.macos_store

import com.duckduckgo.macos_store.MacOsWaitlistState.InBeta
import com.duckduckgo.macos_store.MacOsWaitlistState.JoinedWaitlist
import com.duckduckgo.macos_store.MacOsWaitlistState.NotJoinedQueue
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RealMacOsWaitlistRepositoryTest {
    lateinit var testee: RealMacOsWaitlistRepository

    lateinit var fakeDataStore: MacOsWaitlistDataStore

    @Before
    fun before() {
        fakeDataStore = FakeMacOsWaitlistDataStore()
        testee = RealMacOsWaitlistRepository(fakeDataStore)
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
    fun whenNotifyOnJoinedWaitlistThenSendNotificationSetToTrue() {
        fakeDataStore.sendNotification = false
        testee.notifyOnJoinedWaitlist()
        assertTrue(fakeDataStore.sendNotification)
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
    fun whenIsNotificationEnabledThenSendNotificationReturned() {
        fakeDataStore.sendNotification = true
        assertTrue(testee.isNotificationEnabled())
    }

    @Test
    fun whenGetStateIfUserIsInWaitlistCodeDoesNotExistAndSendNotificationIsTrueThenReturnJoinedQueueWithTrue() {
        givenUserIsInWaitlist()
        fakeDataStore.sendNotification = true

        assertEquals(JoinedWaitlist(true), testee.getState())
    }

    @Test
    fun whenGetStateIfUserIsInWaitlistAndCodeDoesNotExistAndSendNotificationIsFalseThenReturnJoinedQueueWithFalse() {
        givenUserIsInWaitlist()
        fakeDataStore.sendNotification = false

        assertEquals(JoinedWaitlist(false), testee.getState())
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

class FakeMacOsWaitlistDataStore : MacOsWaitlistDataStore {
    override var inviteCode: String? = null
    override var waitlistTimestamp: Int = -1
    override var waitlistToken: String? = null
    override var sendNotification: Boolean = false
}
