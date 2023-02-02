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

package com.duckduckgo.windows.impl

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.windows.api.WindowsWaitlistState
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.Code
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.CodeExisted
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.NoCode
import com.duckduckgo.windows.impl.waitlist.RealWindowsWaitlistManager
import com.duckduckgo.windows.impl.waitlist.api.WindowsInviteCodeResponse
import com.duckduckgo.windows.impl.waitlist.api.WindowsWaitlistResponse
import com.duckduckgo.windows.impl.waitlist.api.WindowsWaitlistService
import com.duckduckgo.windows.impl.waitlist.api.WindowsWaitlistStatusResponse
import com.duckduckgo.windows.store.WindowsWaitlistRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealWindowsWaitlistManagerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mockWindowsWaitlistService: WindowsWaitlistService = mock()
    private val mockWindowsWaitlistRepository: WindowsWaitlistRepository = mock()
    lateinit var testee: RealWindowsWaitlistManager

    @Before
    fun setup() {
        testee = RealWindowsWaitlistManager(mockWindowsWaitlistService, mockWindowsWaitlistRepository, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenFetchInviteCodeIfCodeAlreadyExistsThenReturnCodeExisted() = runTest {
        whenever(mockWindowsWaitlistRepository.getState()).thenReturn(WindowsWaitlistState.InBeta("inviteCode"))

        assertEquals(CodeExisted, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfTimestampIsSmallerThanQueueTimestampThenCallGetCode() = runTest {
        givenUserIsInWaitlist()
        whenever(mockWindowsWaitlistService.waitlistStatus()).thenReturn(WindowsWaitlistStatusResponse(12345))

        testee.fetchInviteCode()

        verify(mockWindowsWaitlistService).getCode("token")
    }

    @Test
    fun whenFetchInviteCodeIfTimestampIsEqualsThanQueueTimestampThenCallGetCode() = runTest {
        givenUserIsInWaitlist()
        whenever(mockWindowsWaitlistService.waitlistStatus()).thenReturn(WindowsWaitlistStatusResponse(1234))

        testee.fetchInviteCode()

        verify(mockWindowsWaitlistService).getCode("token")
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeAvailableThenReturnCode() = runTest {
        givenUserIsTopOfTheQueue()
        whenever(mockWindowsWaitlistService.getCode(any())).thenReturn(WindowsInviteCodeResponse("code"))

        assertEquals(Code, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeNotAvailableThenReturnNoCode() = runTest {
        givenUserIsTopOfTheQueue()
        whenever(mockWindowsWaitlistService.getCode(any())).thenReturn(WindowsInviteCodeResponse(""))

        assertEquals(NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeServiceNotAvailableThenReturnNoCode() = runTest {
        testee = RealWindowsWaitlistManager(TestWindowsService(), mockWindowsWaitlistRepository, coroutineRule.testDispatcherProvider)
        givenUserIsTopOfTheQueue()

        assertEquals(NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserInTheQueueAndStatusServiceNotAvailableThenReturnNoCode() = runTest {
        testee = RealWindowsWaitlistManager(TestWindowsService(), mockWindowsWaitlistRepository, coroutineRule.testDispatcherProvider)
        givenUserIsInWaitlist()

        assertEquals(NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenJoinWaitlistThenCallRepository() {
        testee.joinWaitlist(1234, "token")
        verify(mockWindowsWaitlistRepository).joinWaitlist(1234, "token")
    }

    @Test
    fun whenGetStateThenCallRepository() {
        testee.getState()
        verify(mockWindowsWaitlistRepository).getState()
    }

    @Test
    fun whenGetInviteCodeThenCallRepository() {
        testee.getInviteCode()
        verify(mockWindowsWaitlistRepository).getInviteCode()
    }

    private fun givenUserIsInWaitlist() {
        whenever(mockWindowsWaitlistRepository.getToken()).thenReturn("token")
        whenever(mockWindowsWaitlistRepository.getTimestamp()).thenReturn(1234)
        whenever(mockWindowsWaitlistRepository.getState()).thenReturn(WindowsWaitlistState.JoinedWaitlist)
    }

    private fun givenUserIsTopOfTheQueue() = runTest {
        givenUserIsInWaitlist()
        whenever(mockWindowsWaitlistService.waitlistStatus()).thenReturn(WindowsWaitlistStatusResponse(1234))
    }

    class TestWindowsService : WindowsWaitlistService {
        override suspend fun joinWaitlist(): WindowsWaitlistResponse = WindowsWaitlistResponse("token", 12345)
        override suspend fun waitlistStatus(): WindowsWaitlistStatusResponse {
            throw Exception()
        }

        override suspend fun getCode(token: String): WindowsInviteCodeResponse {
            throw Exception()
        }
    }
}
