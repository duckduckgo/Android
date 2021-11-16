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

package com.duckduckgo.app.email.waitlist

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.email.AppEmailManager
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.job.TestWorker
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.waitlist.email.AppEmailWaitlistCodeFetcher
import com.duckduckgo.app.waitlist.email.EmailWaitlistCodeFetcher
import com.duckduckgo.app.waitlist.email.EmailWaitlistWorkRequestBuilder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class AppEmailWaitlistCodeFetcherTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mockEmailManager: EmailManager = mock()
    private val mockNotification: SchedulableNotification = mock()
    private val mockNotificationSender: NotificationSender = mock()

    private lateinit var workManager: WorkManager
    private lateinit var testee: EmailWaitlistCodeFetcher

    @Before
    fun before() {
        initializeWorkManager()
        testee = AppEmailWaitlistCodeFetcher(workManager, mockEmailManager, mockNotification, mockNotificationSender, coroutineRule.testDispatcherProvider, TestCoroutineScope())
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueCodeAlreadyExistsAndWorkerIsRunningThenCancelWorker() = coroutineRule.runBlocking {
        givenUserIsInTheQueueAndCodeAlreadyExists()
        enqueueWaitlistWorker()

        testee.fetchInviteCode()

        assertWaitlistWorkerIsNotEnqueued()
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueAndCodeReturnedThenCancelWorker() = coroutineRule.runBlocking {
        givenUserIsInTheQueueAndCodeReturned()
        enqueueWaitlistWorker()

        testee.fetchInviteCode()

        assertWaitlistWorkerIsNotEnqueued()
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueAndCodeReturnedThenNotificationSent() = coroutineRule.runBlocking {
        givenUserIsInTheQueueAndCodeReturned()
        enqueueWaitlistWorker()

        testee.fetchInviteCode()

        verify(mockNotificationSender).sendNotification(mockNotification)
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueAndNoCodeReturnedThenDoNothing() = coroutineRule.runBlocking {
        givenUserIsInTheQueueAndNoCodeReturned()
        enqueueWaitlistWorker()

        testee.fetchInviteCode()

        verify(mockNotificationSender, never()).sendNotification(mockNotification)
        assertWaitlistWorkerIsEnqueued()
    }

    @Test
    fun whenExecuteWaitlistCodeFetcherIfUserInNotInQueueThenDoNothing() = coroutineRule.runBlocking {
        whenever(mockEmailManager.waitlistState()).thenReturn(AppEmailManager.WaitlistState.NotJoinedQueue)

        (testee as AppEmailWaitlistCodeFetcher).executeWaitlistCodeFetcher()

        verify(mockEmailManager, never()).fetchInviteCode()
    }

    @Test
    fun whenExecuteWaitlistCodeFetcherIfUserIsInBetaThenDoNothing() = coroutineRule.runBlocking {
        whenever(mockEmailManager.waitlistState()).thenReturn(AppEmailManager.WaitlistState.InBeta)

        (testee as AppEmailWaitlistCodeFetcher).executeWaitlistCodeFetcher()

        verify(mockEmailManager, never()).fetchInviteCode()
    }

    private fun givenUserIsInTheQueueAndCodeAlreadyExists() = coroutineRule.runBlocking {
        whenever(mockEmailManager.waitlistState()).thenReturn(AppEmailManager.WaitlistState.JoinedQueue())
        whenever(mockEmailManager.fetchInviteCode()).thenReturn(AppEmailManager.FetchCodeResult.CodeExisted)
    }

    private fun givenUserIsInTheQueueAndCodeReturned() = coroutineRule.runBlocking {
        whenever(mockEmailManager.waitlistState()).thenReturn(AppEmailManager.WaitlistState.JoinedQueue())
        whenever(mockEmailManager.fetchInviteCode()).thenReturn(AppEmailManager.FetchCodeResult.Code)
    }

    private fun givenUserIsInTheQueueAndNoCodeReturned() = coroutineRule.runBlocking {
        whenever(mockEmailManager.waitlistState()).thenReturn(AppEmailManager.WaitlistState.JoinedQueue())
        whenever(mockEmailManager.fetchInviteCode()).thenReturn(AppEmailManager.FetchCodeResult.NoCode)
    }

    private fun initializeWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    private fun enqueueWaitlistWorker() {
        val requestBuilder = OneTimeWorkRequestBuilder<TestWorker>()
        val request = requestBuilder
            .addTag(EmailWaitlistWorkRequestBuilder.EMAIL_WAITLIST_SYNC_WORK_TAG)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()
        workManager.enqueue(request)
    }

    private fun assertWaitlistWorkerIsEnqueued() {
        val scheduledWorkers = getScheduledWorkers()
        assertFalse(scheduledWorkers.isEmpty())
    }

    private fun assertWaitlistWorkerIsNotEnqueued() {
        val scheduledWorkers = getScheduledWorkers()
        assertTrue(scheduledWorkers.isEmpty())
    }

    private fun getScheduledWorkers(): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(EmailWaitlistWorkRequestBuilder.EMAIL_WAITLIST_SYNC_WORK_TAG)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }
}
