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

package com.duckduckgo.windows.impl.waitlist.ui

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.windows.api.WindowsWaitlistState.JoinedWaitlist
import com.duckduckgo.windows.api.WindowsWaitlistState.NotJoinedQueue
import com.duckduckgo.windows.impl.WindowsPixelNames.WINDOWS_WAITLIST_SHARE_PRESSED
import com.duckduckgo.windows.impl.waitlist.WindowsWaitlistManager
import com.duckduckgo.windows.impl.waitlist.api.WindowsInviteCodeResponse
import com.duckduckgo.windows.impl.waitlist.api.WindowsWaitlistResponse
import com.duckduckgo.windows.impl.waitlist.api.WindowsWaitlistService
import com.duckduckgo.windows.impl.waitlist.api.WindowsWaitlistStatusResponse
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command.CopyInviteToClipboard
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command.GoToMacClientSettings
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command.ShareInviteCode
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command.ShowErrorMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class WindowsWaitlistViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockWindowsWaitlistManager: WindowsWaitlistManager = mock()
    private var mockWindowsWaitlistService: WindowsWaitlistService = mock()
    private var mockPixel: Pixel = mock()
    private val waitlistBuilder = RealWindowsWaitlistWorkRequestBuilder()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var workManager: WorkManager
    private lateinit var testee: WindowsWaitlistViewModel

    @Before
    fun before() {
        whenever(mockWindowsWaitlistManager.getState()).thenReturn(NotJoinedQueue)
        initializeWorkManager()
        testee = WindowsWaitlistViewModel(mockWindowsWaitlistManager, mockWindowsWaitlistService, workManager, waitlistBuilder, mockPixel)
    }

    @Test
    fun whenViewModelCreatedThenEmitWaitlistState() = runTest {
        testee.viewState.test {
            assert(awaitItem().waitlist is NotJoinedQueue)
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTimestampIsNullThenEmitShowErrorMessageCommand() = runTest {
        whenever(mockWindowsWaitlistService.joinWaitlist()).thenReturn(WindowsWaitlistResponse("token", null))
        whenever(mockWindowsWaitlistManager.getState()).thenReturn(JoinedWaitlist)

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTokenIsNullThenEmitShowErrorMessageCommand() = runTest {
        whenever(mockWindowsWaitlistService.joinWaitlist()).thenReturn(WindowsWaitlistResponse(null, 12345))
        whenever(mockWindowsWaitlistManager.getState()).thenReturn(JoinedWaitlist)

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTokenIsEmptyThenEmitShowErrorMessageCommand() = runTest {
        whenever(mockWindowsWaitlistService.joinWaitlist()).thenReturn(WindowsWaitlistResponse("", 12345))
        whenever(mockWindowsWaitlistManager.getState()).thenReturn(JoinedWaitlist)

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallIsSuccessfulThenJoinWaitlistCalled() = runTest {
        givenJoinWaitlistSuccessful()

        testee.joinTheWaitlist()

        verify(mockWindowsWaitlistManager).joinWaitlist(12345, "token")
    }

    @Test
    fun whenJoinTheWaitlistAndCallIsSuccessfulThenViewStateIsJoinedWaitlist() = runTest {
        givenJoinWaitlistSuccessful()

        testee.viewState.test {
            assert(awaitItem().waitlist is NotJoinedQueue)
            testee.joinTheWaitlist()
            assert(awaitItem().waitlist is JoinedWaitlist)
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallIsSuccessfulThenEnqueueEmailWaitlistWork() = runTest {
        givenJoinWaitlistSuccessful()

        testee.joinTheWaitlist()

        assertWaitlistWorkerIsEnqueued()
    }

    @Test
    fun whenJoinTheWaitlistAndCallFailsThenEmitShowErrorMessageCommand() = runTest {
        testee = WindowsWaitlistViewModel(mockWindowsWaitlistManager, TestWindowsService(), workManager, waitlistBuilder, mockPixel)

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenOnShareClickedAndInviteCodeExistsThenEmitCommandShareInviteCodeWithCorrectCode() = runTest {
        whenever(mockWindowsWaitlistManager.getInviteCode()).thenReturn("inviteCode")

        testee.commands.test {
            testee.onShareClicked()
            assertEquals(ShareInviteCode("inviteCode"), awaitItem())
        }
    }

    @Test
    fun whenOnShareClickedAndInviteCodeDoesNotExistThenCommandShareInviteCodeNotEmitted() = runTest {
        whenever(mockWindowsWaitlistManager.getInviteCode()).thenReturn(null)

        testee.commands.test {
            testee.onShareClicked()
            expectNoEvents()
        }
    }

    @Test
    fun whenOnShareClickedAThenPixelFired() = runTest {
        whenever(mockWindowsWaitlistManager.getInviteCode()).thenReturn("inviteCode")

        testee.onShareClicked()

        verify(mockPixel).fire(WINDOWS_WAITLIST_SHARE_PRESSED)
    }

    @Test
    fun whenOnCopyToClipboardAndInviteCodeExistsThenEmitCommandCopyInviteToClipboardWithCorrectCode() = runTest {
        whenever(mockWindowsWaitlistManager.getInviteCode()).thenReturn("inviteCode")

        testee.commands.test {
            testee.onCopyToClipboard(true)
            assertEquals(CopyInviteToClipboard("inviteCode", true), awaitItem())
        }
    }

    @Test
    fun whenOnCopyToClipboardAndInviteCodeDoesNotExistThenCommandCopyInviteToClipboardNotEmitted() = runTest {
        whenever(mockWindowsWaitlistManager.getInviteCode()).thenReturn(null)

        testee.commands.test {
            testee.onCopyToClipboard(true)
            expectNoEvents()
        }
    }

    @Test
    fun whenGoToMacOSSettingsClickedThenEmitCommandGoToMacClientSettings() = runTest {
        testee.commands.test {
            testee.onGoToMacClicked()
            assertEquals(GoToMacClientSettings, awaitItem())
        }
    }

    @Test
    fun whenOnLaunchedFromNotificationCalledWithPixelNameThePixelFired() {
        val pixelName = "pixel_name"
        testee.onLaunchedFromNotification(pixelName)

        verify(mockPixel).fire(pixelName)
    }

    private fun assertWaitlistWorkerIsEnqueued() {
        val scheduledWorkers = getScheduledWorkers()
        assertFalse(scheduledWorkers.isEmpty())
    }

    private fun givenJoinWaitlistSuccessful() = runTest {
        whenever(mockWindowsWaitlistService.joinWaitlist()).thenReturn(WindowsWaitlistResponse("token", 12345))
        whenever(mockWindowsWaitlistManager.getState()).thenReturn(JoinedWaitlist)
    }

    private fun getScheduledWorkers(): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(WindowsWaitlistWorkRequestBuilder.WINDOWS_WAITLIST_SYNC_WORK_TAG)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }

    private fun initializeWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    class TestWindowsService : WindowsWaitlistService {
        override suspend fun joinWaitlist(): WindowsWaitlistResponse {
            throw Exception()
        }

        override suspend fun waitlistStatus(): WindowsWaitlistStatusResponse = WindowsWaitlistStatusResponse(1234)

        override suspend fun getCode(token: String): WindowsInviteCodeResponse = WindowsInviteCodeResponse("token")
    }
}
