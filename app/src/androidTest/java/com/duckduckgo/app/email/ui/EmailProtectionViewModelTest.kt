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
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.api.WaitlistResponse
import com.duckduckgo.app.email.ui.EmailProtectionViewModel.Companion.LOGIN_URL
import com.duckduckgo.app.email.ui.EmailProtectionViewModel.Command.*
import com.duckduckgo.app.email.ui.EmailProtectionViewModel.Companion.ADDRESS_BLOG_POST
import com.duckduckgo.app.email.ui.EmailProtectionViewModel.Companion.PRIVACY_GUARANTEE
import com.duckduckgo.app.email.ui.EmailProtectionViewModel.Companion.SIGN_UP_URL
import com.duckduckgo.app.email.waitlist.WaitlistSyncWorkRequestBuilder
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.time.ExperimentalTime

@ExperimentalTime
@FlowPreview
@ExperimentalCoroutinesApi
class EmailProtectionViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockEmailManager: EmailManager = mock()
    private var mockEmailService: EmailService = mock()
    private lateinit var emailService: EmailService
    private val waitlistBuilder: WaitlistSyncWorkRequestBuilder = WaitlistSyncWorkRequestBuilder()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = MockWebServer()

    private lateinit var workManager: WorkManager
    private lateinit var testee: EmailProtectionViewModel

    @Before
    fun before() {
        whenever(mockEmailManager.waitlistState()).thenReturn(NotJoinedQueue)
        initializeWorkManager()
        testee = EmailProtectionViewModel(mockEmailManager, mockEmailService, workManager, waitlistBuilder)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun whenViewModelCreatedThenEmitWaitlistState() = coroutineRule.runBlocking {
        testee.viewState.test {
            assert(expectItem().waitlistState is NotJoinedQueue)
        }
    }

    @Test
    fun whenHaveADuckAddressThenEmitCommandOpenUrlInBrowserWithCorrectUrl() = coroutineRule.runBlocking {
        testee.commands.test {
            testee.haveADuckAddress()
            assertEquals(OpenUrl(url = LOGIN_URL, openInBrowser = true), expectItem())
        }
    }

    @Test
    fun whenHaveAnInviteCodeThenEmitCommandOpenUrlInBrowserWithCorrectUrl() = coroutineRule.runBlocking {
        val inviteCode = "abcde"
        whenever(mockEmailManager.getInviteCode()).thenReturn(inviteCode)
        val expectedURL = "$SIGN_UP_URL$inviteCode"

        testee.commands.test {
            testee.haveAnInviteCode()
            assertEquals(OpenUrl(url = expectedURL, openInBrowser = true), expectItem())
        }
    }

    @Test
    fun whenReadBlogPostThenEmitCommandOpenUrlNotInBrowserWithCorrectUrl() = coroutineRule.runBlocking {
        testee.commands.test {
            testee.readBlogPost()
            assertEquals(OpenUrl(url = ADDRESS_BLOG_POST, openInBrowser = false), expectItem())
        }
    }

    @Test
    fun whenReadPrivacyGuaranteeThenEmitCommandOpenUrlNotInBrowserWithCorrectUrl() = coroutineRule.runBlocking {
        testee.commands.test {
            testee.readPrivacyGuarantees()
            assertEquals(OpenUrl(url = PRIVACY_GUARANTEE, openInBrowser = false), expectItem())
        }
    }

    @Test
    fun whenJoinTheWaitlistAndCallIsSuccessfulThenJoinWaitlistCalled() = coroutineRule.runBlocking {
        givenJoinWaitlistSuccessful()

        testee.joinTheWaitlist()

        verify(mockEmailManager).joinWaitlist(12345, "token")
    }

    @Test
    fun whenJoinTheWaitlistAndCallIsSuccessfulThenEmitWaitlistState() = coroutineRule.runBlocking {
        givenJoinWaitlistSuccessful()

        testee.viewState.test {
            assert(expectItem().waitlistState is NotJoinedQueue)

            testee.joinTheWaitlist()

            assert(expectItem().waitlistState is JoinedQueue)
        }
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
    fun whenJoinTheWaitlistAndCallFailsThenEmitShowErrorMessageCommand() = runBlocking {
        givenJoinWaitlistFails()
        testee = EmailProtectionViewModel(mockEmailManager, emailService, workManager, waitlistBuilder)

        testee.commands.test {
            testee.joinTheWaitlist()
            assertEquals(ShowErrorMessage, expectItem())
        }
        assertTrue(true)
    }

    private fun assertWaitlistWorkerIsEnqueued() {
        val scheduledWorkers = getScheduledWorkers()
        assertFalse(scheduledWorkers.isEmpty())
    }

    private fun givenJoinWaitlistSuccessful() = coroutineRule.runBlocking {
        whenever(mockEmailService.joinWaitlist()).thenReturn(WaitlistResponse("token", 12345))
        whenever(mockEmailManager.waitlistState()).thenReturn(JoinedQueue)
    }

    private fun getScheduledWorkers(): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(WaitlistSyncWorkRequestBuilder.EMAIL_WAITLIST_SYNC_WORK_TAG)
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

    private fun givenJoinWaitlistFails() {
        configureStubNetworking()
        queueError()
    }

    private fun queueError() {
        server.enqueue(MockResponse().setResponseCode(400))
    }

    private fun configureStubNetworking() {
        server.start()

        val okHttpClient = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getLocalHost(), server.port)))
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("localhost/").toString())
            .client(okHttpClient)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()

        emailService = retrofit.create(EmailService::class.java)
    }
}
