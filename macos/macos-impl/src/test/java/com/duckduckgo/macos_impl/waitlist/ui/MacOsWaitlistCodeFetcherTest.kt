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
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.Code
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.CodeExisted
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.NoCode
import com.duckduckgo.macos_impl.waitlist.MacOsWaitlistManager
import com.duckduckgo.macos_store.MacOsWaitlistState.InBeta
import com.duckduckgo.macos_store.MacOsWaitlistState.JoinedWaitlist
import com.duckduckgo.macos_store.MacOsWaitlistState.NotJoinedQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class MacOsWaitlistCodeFetcherTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mockMacOsWaitlistManager: MacOsWaitlistManager = mock()
    private val mockNotification: SchedulableNotification = mock()
    private val mockNotificationSender: NotificationSender = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private lateinit var workManager: WorkManager
    private lateinit var testee: MacOsWaitlistCodeFetcher

    @Before
    fun before() {
        initializeWorkManager()
        testee = MacOsWaitlistCodeFetcher(
            workManager,
            mockMacOsWaitlistManager,
            mockNotification,
            mockNotificationSender,
            coroutineRule.testDispatcherProvider,
            TestScope()
        )
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueCodeAlreadyExistsAndWorkerIsRunningThenCancelWorker() = runTest {
        givenUserIsInTheQueueAndCodeAlreadyExists()
        enqueueWaitlistWorker()

        testee.onStateChanged(lifecycleOwner, Event.ON_START)

        assertWaitlistWorkerIsNotEnqueued()
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueAndCodeReturnedThenCancelWorker() = runTest {
        givenUserIsInTheQueueAndCodeReturned()
        enqueueWaitlistWorker()

        testee.onStateChanged(lifecycleOwner, Event.ON_START)

        assertWaitlistWorkerIsNotEnqueued()
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueAndCodeReturnedThenNotificationSent() = runTest {
        givenUserIsInTheQueueAndCodeReturned()
        enqueueWaitlistWorker()

        testee.onStateChanged(lifecycleOwner, Event.ON_START)

        verify(mockNotificationSender).sendNotification(mockNotification)
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueAndNoCodeReturnedThenDoNothing() = runTest {
        givenUserIsInTheQueueAndNoCodeReturned()
        enqueueWaitlistWorker()

        testee.onStateChanged(lifecycleOwner, Event.ON_START)

        verify(mockNotificationSender, never()).sendNotification(mockNotification)
        assertWaitlistWorkerIsEnqueued()
    }

    @Test
    fun whenExecuteWaitlistCodeFetcherIfUserInNotInQueueThenDoNothing() = runTest {
        whenever(mockMacOsWaitlistManager.getState()).thenReturn(NotJoinedQueue)

        testee.onStateChanged(lifecycleOwner, Event.ON_START)

        verify(mockMacOsWaitlistManager, never()).fetchInviteCode()
    }

    @Test
    fun whenExecuteWaitlistCodeFetcherIfUserIsInBetaThenDoNothing() = runTest {
        whenever(mockMacOsWaitlistManager.getState()).thenReturn(InBeta("inviteCode"))

        testee.onStateChanged(lifecycleOwner, Event.ON_START)

        verify(mockMacOsWaitlistManager, never()).fetchInviteCode()
    }

    private fun givenUserIsInTheQueueAndCodeAlreadyExists() = runTest {
        whenever(mockMacOsWaitlistManager.getState()).thenReturn(JoinedWaitlist())
        whenever(mockMacOsWaitlistManager.fetchInviteCode()).thenReturn(CodeExisted)
    }

    private fun givenUserIsInTheQueueAndCodeReturned() = runTest {
        whenever(mockMacOsWaitlistManager.getState()).thenReturn(JoinedWaitlist())
        whenever(mockMacOsWaitlistManager.fetchInviteCode()).thenReturn(Code)
    }

    private fun givenUserIsInTheQueueAndNoCodeReturned() = runTest {
        whenever(mockMacOsWaitlistManager.getState()).thenReturn(JoinedWaitlist())
        whenever(mockMacOsWaitlistManager.fetchInviteCode()).thenReturn(NoCode)
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
            .addTag(MacOsWaitlistWorkRequestBuilder.MACOS_WAITLIST_SYNC_WORK_TAG)
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
            .getWorkInfosByTag(MacOsWaitlistWorkRequestBuilder.MACOS_WAITLIST_SYNC_WORK_TAG)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }

    class TestWorker(
        context: Context,
        parameters: WorkerParameters
    ) : Worker(context, parameters) {
        override fun doWork(): Result {
            return Result.success()
        }
    }
}
