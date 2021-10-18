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
import com.duckduckgo.app.email.AppEmailManager.WaitlistState.*
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.email.api.EmailAlias
import com.duckduckgo.app.email.api.EmailInviteCodeResponse
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.api.WaitlistResponse
import com.duckduckgo.app.email.api.WaitlistStatusResponse
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Companion.LOGIN_URL
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Command.*
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Companion.ADDRESS_BLOG_POST
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Companion.GET_STARTED_URL
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Companion.PRIVACY_GUARANTEE
import com.duckduckgo.app.email.ui.EmailProtectionSignInViewModel.Companion.SIGN_UP_URL
import com.duckduckgo.app.waitlist.email.EmailWaitlistWorkRequestBuilder
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.Exception
import kotlin.time.ExperimentalTime

@ExperimentalTime
@FlowPreview
@ExperimentalCoroutinesApi
class EmailProtectionSignInViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockEmailManager: EmailManager = mock()
    private var mockEmailService: EmailService = mock()
    private val waitlistBuilder: EmailWaitlistWorkRequestBuilder = EmailWaitlistWorkRequestBuilder()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var workManager: WorkManager
    private lateinit var testee: EmailProtectionSignInViewModel

    @Before
    fun before() {
        whenever(mockEmailManager.waitlistState()).thenReturn(NotJoinedQueue)
        initializeWorkManager()
        testee = EmailProtectionSignInViewModel(mockEmailManager, mockEmailService, workManager, waitlistBuilder)
    }

    @Test
    fun whenViewModelCreatedThenEmitWaitlistState() = coroutineRule.runBlocking {
        testee.viewState.test {
            assert(expectItem().waitlistState is NotJoinedQueue)
        }
    }

    @Test
    fun whenHaveADuckAddressThenEmitCommandOpenUrlWithCorrectUrl() = coroutineRule.runBlocking {
        testee.commands.test {
            testee.haveADuckAddress()
            assertEquals(OpenUrl(url = LOGIN_URL), expectItem())
        }
    }

    @Test
    fun whenHaveAnInviteCodeThenEmitCommandOpenUrlWithCorrectUrl() = coroutineRule.runBlocking {
        val inviteCode = "abcde"
        whenever(mockEmailManager.getInviteCode()).thenReturn(inviteCode)
        val expectedURL = "$SIGN_UP_URL$inviteCode"

        testee.commands.test {
            testee.haveAnInviteCode()
            assertEquals(OpenUrl(url = expectedURL), expectItem())
        }
    }

    @Test
    fun whenGetStartedThenEmitCommandOpenUrlWithCorrectUrl() = coroutineRule.runBlocking {
        val inviteCode = "abcde"
        whenever(mockEmailManager.getInviteCode()).thenReturn(inviteCode)
        val expectedURL = "$GET_STARTED_URL$inviteCode"

        testee.commands.test {
            testee.getStarted()
            assertEquals(OpenUrl(url = expectedURL), expectItem())
        }
    }

    @Test
    fun whenReadBlogPostThenEmitCommandOpenUrlWithCorrectUrl() = coroutineRule.runBlocking {
        testee.commands.test {
            testee.readBlogPost()
            assertEquals(OpenUrl(url = ADDRESS_BLOG_POST), expectItem())
        }
    }

    @Test
    fun whenReadPrivacyGuaranteeThenEmitCommandOpenUrlWithCorrectUrl() = coroutineRule.runBlocking {
        testee.commands.test {
            testee.readPrivacyGuarantees()
            assertEquals(OpenUrl(url = PRIVACY_GUARANTEE), expectItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTimestampIsNullThenEmitShowErrorMessageCommand() = coroutineRule.runBlocking {
        whenever(mockEmailService.joinWaitlist()).thenReturn(WaitlistResponse("token", null))
        whenever(mockEmailManager.waitlistState()).thenReturn(JoinedQueue())

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, expectItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTokenIsNullThenEmitShowErrorMessageCommand() = coroutineRule.runBlocking {
        whenever(mockEmailService.joinWaitlist()).thenReturn(WaitlistResponse(null, 12345))
        whenever(mockEmailManager.waitlistState()).thenReturn(JoinedQueue())

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, expectItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallTokenIsEmptyThenEmitShowErrorMessageCommand() = coroutineRule.runBlocking {
        whenever(mockEmailService.joinWaitlist()).thenReturn(WaitlistResponse("", 12345))
        whenever(mockEmailManager.waitlistState()).thenReturn(JoinedQueue())

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, expectItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallIsSuccessfulThenJoinWaitlistCalled() = coroutineRule.runBlocking {
        givenJoinWaitlistSuccessful()

        testee.joinTheWaitlist()

        verify(mockEmailManager).joinWaitlist(12345, "token")
    }

    @Test
    fun whenJoinTheWaitlistAndCallIsSuccessfulThenEmitShowNotificationDialogCommand() = coroutineRule.runBlocking {
        givenJoinWaitlistSuccessful()

        testee.commands.test {
            testee.joinTheWaitlist()

            assertEquals(ShowNotificationDialog, expectItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallIsSuccessfulThenEnqueueEmailWaitlistWork() = coroutineRule.runBlocking {
        givenJoinWaitlistSuccessful()

        testee.joinTheWaitlist()

        assertWaitlistWorkerIsEnqueued()
    }

    @Test
    fun whenJoinTheWaitlistAndCallFailsThenEmitShowErrorMessageCommand() = coroutineRule.runBlocking {
        testee = EmailProtectionSignInViewModel(mockEmailManager, TestEmailService(), workManager, waitlistBuilder)

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, expectItem())
        }
    }

    @Test
    fun whenOnNotifyMeClickedThenNotifyOnJoinedWaitlistCalled() = coroutineRule.runBlocking {
        testee.onNotifyMeClicked()

        verify(mockEmailManager).notifyOnJoinedWaitlist()
    }

    @Test
    fun whenOnDialogDismissedThenEmitWaitlistState() = coroutineRule.runBlocking {
        givenJoinWaitlistSuccessful()

        testee.viewState.test {
            assert(expectItem().waitlistState is NotJoinedQueue)

            testee.onDialogDismissed()

            assert(expectItem().waitlistState is JoinedQueue)
        }
    }

    private fun assertWaitlistWorkerIsEnqueued() {
        val scheduledWorkers = getScheduledWorkers()
        assertFalse(scheduledWorkers.isEmpty())
    }

    private fun givenJoinWaitlistSuccessful() = coroutineRule.runBlocking {
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

        override suspend fun joinWaitlist(): WaitlistResponse { throw Exception() }

        override suspend fun waitlistStatus(): WaitlistStatusResponse = WaitlistStatusResponse(1234)

        override suspend fun getCode(token: String): EmailInviteCodeResponse = EmailInviteCodeResponse("token")
    }
}
