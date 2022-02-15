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

package com.duckduckgo.macos_impl.waitlist.ui

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
import com.duckduckgo.macos_impl.waitlist.MacOsWaitlistManager
import com.duckduckgo.macos_impl.waitlist.api.MacOsInviteCodeResponse
import com.duckduckgo.macos_impl.waitlist.api.MacOsWaitlistResponse
import com.duckduckgo.macos_impl.waitlist.api.MacOsWaitlistService
import com.duckduckgo.macos_impl.waitlist.api.MacOsWaitlistStatusResponse
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.Command.CopyInviteToClipboard
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.Command.ShareInviteCode
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.Command.ShowErrorMessage
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.Command.ShowNotificationDialog
import com.duckduckgo.macos_store.MacOsWaitlistState.JoinedWaitlist
import com.duckduckgo.macos_store.MacOsWaitlistState.NotJoinedQueue
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
class MacOsWaitlistViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockMacOsWaitlistManager: MacOsWaitlistManager = mock()
    private var mockMacOsWaitlistService: MacOsWaitlistService = mock()
    private var mockPixel: Pixel = mock()
    private val waitlistBuilder = RealMacOsWaitlistWorkRequestBuilder()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var workManager: WorkManager
    private lateinit var testee: MacOsWaitlistViewModel

    @Before
    fun before() {
        whenever(mockMacOsWaitlistManager.getState()).thenReturn(NotJoinedQueue)
        initializeWorkManager()
        testee = MacOsWaitlistViewModel(mockMacOsWaitlistManager, mockMacOsWaitlistService, workManager, waitlistBuilder, mockPixel)
    }

    @Test
    fun whenViewModelCreatedThenEmitWaitlistState() = runTest {
        testee.viewState.test {
            assert(awaitItem().waitlist is NotJoinedQueue)
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTimestampIsNullThenEmitShowErrorMessageCommand() = runTest {
        whenever(mockMacOsWaitlistService.joinWaitlist()).thenReturn(MacOsWaitlistResponse("token", null))
        whenever(mockMacOsWaitlistManager.getState()).thenReturn(JoinedWaitlist())

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTokenIsNullThenEmitShowErrorMessageCommand() = runTest {
        whenever(mockMacOsWaitlistService.joinWaitlist()).thenReturn(MacOsWaitlistResponse(null, 12345))
        whenever(mockMacOsWaitlistManager.getState()).thenReturn(JoinedWaitlist())

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTokenIsEmptyThenEmitShowErrorMessageCommand() = runTest {
        whenever(mockMacOsWaitlistService.joinWaitlist()).thenReturn(MacOsWaitlistResponse("", 12345))
        whenever(mockMacOsWaitlistManager.getState()).thenReturn(JoinedWaitlist())

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallIsSuccessfulThenJoinWaitlistCalled() = runTest {
        givenJoinWaitlistSuccessful()

        testee.joinTheWaitlist()

        verify(mockMacOsWaitlistManager).joinWaitlist(12345, "token")
    }

    @Test
    fun whenJoinTheWaitlistAndCallIsSuccessfulThenEmitShowNotificationDialogCommand() = runTest {
        givenJoinWaitlistSuccessful()

        testee.commands.test {
            testee.joinTheWaitlist()

            assertEquals(ShowNotificationDialog, awaitItem())
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
        testee = MacOsWaitlistViewModel(mockMacOsWaitlistManager, TestMacOsService(), workManager, waitlistBuilder, mockPixel)

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenOnNotifyMeClickedThenNotifyOnJoinedWaitlistCalled() = runTest {
        testee.onNotifyMeClicked()

        verify(mockMacOsWaitlistManager).notifyOnJoinedWaitlist()
    }

    @Test
    fun whenOnDialogDismissedThenEmitWaitlistState() = runTest {
        givenJoinWaitlistSuccessful()

        testee.viewState.test {
            assert(awaitItem().waitlist is NotJoinedQueue)

            testee.onDialogDismissed()

            assert(awaitItem().waitlist is JoinedWaitlist)
        }
    }

    @Test
    fun whenOnShareClickedAndInviteCodeExistsThenEmitCommandShareInviteCodeWithCorrectCode() = runTest {
        whenever(mockMacOsWaitlistManager.getInviteCode()).thenReturn("inviteCode")

        testee.commands.test {
            testee.onShareClicked()
            assertEquals(ShareInviteCode("inviteCode"), awaitItem())
        }
    }

    @Test
    fun whenOnShareClickedAndInviteCodeDoesNotExistThenCommandShareInviteCodeNotEmitted() = runTest {
        whenever(mockMacOsWaitlistManager.getInviteCode()).thenReturn(null)

        testee.commands.test {
            testee.onShareClicked()
            expectNoEvents()
        }
    }

    @Test
    fun whenOnCopyToClipboardAndInviteCodeExistsThenEmitCommandCopyInviteToClipboardWithCorrectCode() = runTest {
        whenever(mockMacOsWaitlistManager.getInviteCode()).thenReturn("inviteCode")

        testee.commands.test {
            testee.onCopyToClipboard(true)
            assertEquals(CopyInviteToClipboard("inviteCode", true), awaitItem())
        }
    }

    @Test
    fun whenOnCopyToClipboardAndInviteCodeDoesNotExistThenCommandCopyInviteToClipboardNotEmitted() = runTest {
        whenever(mockMacOsWaitlistManager.getInviteCode()).thenReturn(null)

        testee.commands.test {
            testee.onCopyToClipboard(true)
            expectNoEvents()
        }
    }

    private fun assertWaitlistWorkerIsEnqueued() {
        val scheduledWorkers = getScheduledWorkers()
        assertFalse(scheduledWorkers.isEmpty())
    }

    private fun givenJoinWaitlistSuccessful() = runTest {
        whenever(mockMacOsWaitlistService.joinWaitlist()).thenReturn(MacOsWaitlistResponse("token", 12345))
        whenever(mockMacOsWaitlistManager.getState()).thenReturn(JoinedWaitlist())
    }

    private fun getScheduledWorkers(): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(MacOsWaitlistWorkRequestBuilder.MACOS_WAITLIST_SYNC_WORK_TAG)
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

    class TestMacOsService : MacOsWaitlistService {
        override suspend fun joinWaitlist(): MacOsWaitlistResponse {
            throw Exception()
        }

        override suspend fun waitlistStatus(): MacOsWaitlistStatusResponse = MacOsWaitlistStatusResponse(1234)

        override suspend fun getCode(token: String): MacOsInviteCodeResponse = MacOsInviteCodeResponse("token")
    }
}
