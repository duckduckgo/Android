/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.email.ui

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.email.AppEmailManager.WaitlistState.JoinedQueue
import com.duckduckgo.app.email.AppEmailManager.WaitlistState.NotJoinedQueue
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.email.api.EmailAlias
import com.duckduckgo.app.email.api.EmailInviteCodeResponse
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.api.WaitlistResponse
import com.duckduckgo.app.email.api.WaitlistStatusResponse
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Command.OpenUrl
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Command.OpenUrlInBrowserTab
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Command.ShowErrorMessage
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Command.ShowNotificationDialog
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Companion.ADDRESS_BLOG_POST
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Companion.GET_STARTED_URL
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Companion.LOGIN_URL
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Companion.SIGN_UP_URL
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.waitlist.email.EmailWaitlistWorkRequestBuilder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@FlowPreview
@ExperimentalCoroutinesApi
class EmailProtectionSignInViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockEmailManager: EmailManager = mock()
    private var mockEmailService: EmailService = mock()
    private var mockPixel: Pixel = mock()
    private val waitlistBuilder: EmailWaitlistWorkRequestBuilder = EmailWaitlistWorkRequestBuilder()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var workManager: WorkManager
    private lateinit var testee: EmailProtectionSignInViewModel

    @Before
    fun before() {
        whenever(mockEmailManager.waitlistState()).thenReturn(NotJoinedQueue)
        initializeWorkManager()
        testee = EmailProtectionSignInViewModel(mockEmailManager, mockEmailService, workManager, waitlistBuilder, mockPixel)
    }

    @Test
    fun whenViewModelCreatedThenEmitWaitlistState() = runTest {
        testee.viewState.test {
            assert(awaitItem().waitlistState is NotJoinedQueue)
        }
    }

    @Test
    fun whenHaveADuckAddressThenEmitCommandOpenUrlWithCorrectUrl() = runTest {
        testee.commands.test {
            testee.haveADuckAddress()
            assertEquals(OpenUrl(url = LOGIN_URL), awaitItem())
        }
    }

    @Test
    fun whenHaveAnInviteCodeThenEmitCommandOpenUrlInBrowserTabWithCorrectUrl() = runTest {
        val inviteCode = "abcde"
        whenever(mockEmailManager.getInviteCode()).thenReturn(inviteCode)
        val expectedURL = "$SIGN_UP_URL$inviteCode"

        testee.commands.test {
            testee.haveAnInviteCode()
            assertEquals(OpenUrlInBrowserTab(url = expectedURL), awaitItem())
        }
    }

    @Test
    fun whenGetStartedThenEmitCommandOpenUrlInBrowserTabWithCorrectUrl() = runTest {
        val inviteCode = "abcde"
        whenever(mockEmailManager.getInviteCode()).thenReturn(inviteCode)
        val expectedURL = "$GET_STARTED_URL$inviteCode"

        testee.commands.test {
            testee.getStarted()
            assertEquals(OpenUrlInBrowserTab(url = expectedURL), awaitItem())
        }
    }

    @Test
    fun whenReadBlogPostThenEmitCommandOpenUrlWithCorrectUrl() = runTest {
        testee.commands.test {
            testee.readBlogPost()
            assertEquals(OpenUrl(url = ADDRESS_BLOG_POST), awaitItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTimestampIsNullThenEmitShowErrorMessageCommand() = runTest {
        whenever(mockEmailService.joinWaitlist()).thenReturn(WaitlistResponse("token", null))
        whenever(mockEmailManager.waitlistState()).thenReturn(JoinedQueue())

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTokenIsNullThenEmitShowErrorMessageCommand() = runTest {
        whenever(mockEmailService.joinWaitlist()).thenReturn(WaitlistResponse(null, 12345))
        whenever(mockEmailManager.waitlistState()).thenReturn(JoinedQueue())

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTokenIsEmptyThenEmitShowErrorMessageCommand() = runTest {
        whenever(mockEmailService.joinWaitlist()).thenReturn(WaitlistResponse("", 12345))
        whenever(mockEmailManager.waitlistState()).thenReturn(JoinedQueue())

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallIsSuccessfulThenJoinWaitlistCalled() = runTest {
        givenJoinWaitlistSuccessful()

        testee.joinTheWaitlist()

        verify(mockEmailManager).joinWaitlist(12345, "token")
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
        testee = EmailProtectionSignInViewModel(mockEmailManager, TestEmailService(), workManager, waitlistBuilder, mockPixel)

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, awaitItem())
        }
    }

    @Test
    fun whenOnNotifyMeClickedThenNotifyOnJoinedWaitlistCalled() = runTest {
        testee.onNotifyMeClicked()

        verify(mockEmailManager).notifyOnJoinedWaitlist()
    }

    @Test
    fun whenOnDialogDismissedThenEmitWaitlistState() = runTest {
        givenJoinWaitlistSuccessful()

        testee.viewState.test {
            assert(awaitItem().waitlistState is NotJoinedQueue)

            testee.onDialogDismissed()

            assert(awaitItem().waitlistState is JoinedQueue)
        }
    }

    private fun assertWaitlistWorkerIsEnqueued() {
        val scheduledWorkers = getScheduledWorkers()
        assertFalse(scheduledWorkers.isEmpty())
    }

    private fun givenJoinWaitlistSuccessful() = runTest {
        whenever(mockEmailService.joinWaitlist()).thenReturn(WaitlistResponse("token", 12345))
        whenever(mockEmailManager.waitlistState()).thenReturn(JoinedQueue())
    }

    private fun getScheduledWorkers(): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(EmailWaitlistWorkRequestBuilder.EMAIL_WAITLIST_SYNC_WORK_TAG)
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

    class TestEmailService : EmailService {
        override suspend fun newAlias(authorization: String): EmailAlias = EmailAlias("test")

        override suspend fun joinWaitlist(): WaitlistResponse {
            throw Exception()
        }

        override suspend fun waitlistStatus(): WaitlistStatusResponse = WaitlistStatusResponse(1234)

        override suspend fun getCode(token: String): EmailInviteCodeResponse = EmailInviteCodeResponse("token")
    }
}
