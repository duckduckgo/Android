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

package com.duckduckgo.macos_impl.waitlist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.Code
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.CodeExisted
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.NoCode
import com.duckduckgo.macos_impl.waitlist.api.MacOsInviteCodeResponse
import com.duckduckgo.macos_impl.waitlist.api.MacOsWaitlistResponse
import com.duckduckgo.macos_impl.waitlist.api.MacOsWaitlistService
import com.duckduckgo.macos_impl.waitlist.api.MacOsWaitlistStatusResponse
import com.duckduckgo.macos_store.MacOsWaitlistRepository
import com.duckduckgo.macos_store.MacOsWaitlistState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealMacOsWaitlistManagerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mockMacOsWaitlistService: MacOsWaitlistService = mock()
    private val mockMacOsWaitlistRepository: MacOsWaitlistRepository = mock()
    lateinit var testee: RealMacOsWaitlistManager

    @Before
    fun setup() {
        testee = RealMacOsWaitlistManager(mockMacOsWaitlistService, mockMacOsWaitlistRepository, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenFetchInviteCodeIfCodeAlreadyExistsThenReturnCodeExisted() = runTest {
        whenever(mockMacOsWaitlistRepository.getState()).thenReturn(MacOsWaitlistState.InBeta("inviteCode"))

        assertEquals(CodeExisted, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfTimestampIsSmallerThanQueueTimestampThenCallGetCode() = runTest {
        givenUserIsInWaitlist()
        whenever(mockMacOsWaitlistService.waitlistStatus()).thenReturn(MacOsWaitlistStatusResponse(12345))

        testee.fetchInviteCode()

        verify(mockMacOsWaitlistService).getCode("token")
    }

    @Test
    fun whenFetchInviteCodeIfTimestampIsEqualsThanQueueTimestampThenCallGetCode() = runTest {
        givenUserIsInWaitlist()
        whenever(mockMacOsWaitlistService.waitlistStatus()).thenReturn(MacOsWaitlistStatusResponse(1234))

        testee.fetchInviteCode()

        verify(mockMacOsWaitlistService).getCode("token")
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeAvailableThenReturnCode() = runTest {
        givenUserIsTopOfTheQueue()
        whenever(mockMacOsWaitlistService.getCode(any())).thenReturn(MacOsInviteCodeResponse("code"))

        assertEquals(Code, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeNotAvailableThenReturnNoCode() = runTest {
        givenUserIsTopOfTheQueue()
        whenever(mockMacOsWaitlistService.getCode(any())).thenReturn(MacOsInviteCodeResponse(""))

        assertEquals(NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeServiceNotAvailableThenReturnNoCode() = runTest {
        testee = RealMacOsWaitlistManager(TestMacOsService(), mockMacOsWaitlistRepository, coroutineRule.testDispatcherProvider)
        givenUserIsTopOfTheQueue()

        assertEquals(NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserInTheQueueAndStatusServiceNotAvailableThenReturnNoCode() = runTest {
        testee = RealMacOsWaitlistManager(TestMacOsService(), mockMacOsWaitlistRepository, coroutineRule.testDispatcherProvider)
        givenUserIsInWaitlist()

        assertEquals(NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenJoinWaitlistThenCallRepository() {
        testee.joinWaitlist(1234, "token")
        verify(mockMacOsWaitlistRepository).joinWaitlist(1234, "token")
    }

    @Test
    fun whenGetStateThenCallRepository() {
        testee.getState()
        verify(mockMacOsWaitlistRepository).getState()
    }

    @Test
    fun whenGetInviteCodeThenCallRepository() {
        testee.getInviteCode()
        verify(mockMacOsWaitlistRepository).getInviteCode()
    }

    private fun givenUserIsInWaitlist() {
        whenever(mockMacOsWaitlistRepository.getToken()).thenReturn("token")
        whenever(mockMacOsWaitlistRepository.getTimestamp()).thenReturn(1234)
        whenever(mockMacOsWaitlistRepository.getState()).thenReturn(MacOsWaitlistState.JoinedWaitlist)
    }

    private fun givenUserIsTopOfTheQueue() = runTest {
        givenUserIsInWaitlist()
        whenever(mockMacOsWaitlistService.waitlistStatus()).thenReturn(MacOsWaitlistStatusResponse(1234))
    }

    class TestMacOsService : MacOsWaitlistService {
        override suspend fun joinWaitlist(): MacOsWaitlistResponse = MacOsWaitlistResponse("token", 12345)
        override suspend fun waitlistStatus(): MacOsWaitlistStatusResponse {
            throw Exception()
        }

        override suspend fun getCode(token: String): MacOsInviteCodeResponse {
            throw Exception()
        }
    }
}
