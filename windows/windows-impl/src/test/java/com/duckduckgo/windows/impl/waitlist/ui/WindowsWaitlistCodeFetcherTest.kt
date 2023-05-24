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
import com.duckduckgo.windows.api.WindowsWaitlistState.InBeta
import com.duckduckgo.windows.api.WindowsWaitlistState.JoinedWaitlist
import com.duckduckgo.windows.api.WindowsWaitlistState.NotJoinedQueue
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.Code
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.CodeExisted
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.NoCode
import com.duckduckgo.windows.impl.waitlist.WindowsWaitlistManager
import java.util.concurrent.TimeUnit
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

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class WindowsWaitlistCodeFetcherTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mockWindowsWaitlistManager: WindowsWaitlistManager = mock()
    private val mockNotification: SchedulableNotification = mock()
    private val mockNotificationSender: NotificationSender = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private lateinit var workManager: WorkManager
    private lateinit var testee: WindowsWaitlistCodeFetcher

    @Before
    fun before() {
        initializeWorkManager()
        testee = WindowsWaitlistCodeFetcher(
            workManager,
            mockWindowsWaitlistManager,
            mockNotification,
            mockNotificationSender,
            coroutineRule.testDispatcherProvider,
            TestScope(),
        )
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueCodeAlreadyExistsAndWorkerIsRunningThenCancelWorker() = runTest {
        givenUserIsInTheQueueAndCodeAlreadyExists()
        enqueueWaitlistWorker()

        testee.onStart(lifecycleOwner)

        assertWaitlistWorkerIsNotEnqueued()
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueAndCodeReturnedThenCancelWorker() = runTest {
        givenUserIsInTheQueueAndCodeReturned()
        enqueueWaitlistWorker()

        testee.onStart(lifecycleOwner)

        assertWaitlistWorkerIsNotEnqueued()
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueAndCodeReturnedThenNotificationSent() = runTest {
        givenUserIsInTheQueueAndCodeReturned()
        enqueueWaitlistWorker()

        testee.onStart(lifecycleOwner)

        verify(mockNotificationSender).sendNotification(mockNotification)
    }

    @Test
    fun whenFetchInviteCodeIfUserInQueueAndNoCodeReturnedThenDoNothing() = runTest {
        givenUserIsInTheQueueAndNoCodeReturned()
        enqueueWaitlistWorker()

        testee.onStart(lifecycleOwner)

        verify(mockNotificationSender, never()).sendNotification(mockNotification)
        assertWaitlistWorkerIsEnqueued()
    }

    @Test
    fun whenExecuteWaitlistCodeFetcherIfUserInNotInQueueThenDoNothing() = runTest {
        whenever(mockWindowsWaitlistManager.getState()).thenReturn(NotJoinedQueue)

        testee.onStart(lifecycleOwner)

        verify(mockWindowsWaitlistManager, never()).fetchInviteCode()
    }

    @Test
    fun whenExecuteWaitlistCodeFetcherIfUserIsInBetaThenDoNothing() = runTest {
        whenever(mockWindowsWaitlistManager.getState()).thenReturn(InBeta("inviteCode"))

        testee.onStart(lifecycleOwner)

        verify(mockWindowsWaitlistManager, never()).fetchInviteCode()
    }

    private fun givenUserIsInTheQueueAndCodeAlreadyExists() = runTest {
        whenever(mockWindowsWaitlistManager.getState()).thenReturn(JoinedWaitlist)
        whenever(mockWindowsWaitlistManager.fetchInviteCode()).thenReturn(CodeExisted)
    }

    private fun givenUserIsInTheQueueAndCodeReturned() = runTest {
        whenever(mockWindowsWaitlistManager.getState()).thenReturn(JoinedWaitlist)
        whenever(mockWindowsWaitlistManager.fetchInviteCode()).thenReturn(Code)
    }

    private fun givenUserIsInTheQueueAndNoCodeReturned() = runTest {
        whenever(mockWindowsWaitlistManager.getState()).thenReturn(JoinedWaitlist)
        whenever(mockWindowsWaitlistManager.fetchInviteCode()).thenReturn(NoCode)
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
            .addTag(WindowsWaitlistWorkRequestBuilder.WINDOWS_WAITLIST_SYNC_WORK_TAG)
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
            .getWorkInfosByTag(WindowsWaitlistWorkRequestBuilder.WINDOWS_WAITLIST_SYNC_WORK_TAG)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }

    class TestWorker(
        context: Context,
        parameters: WorkerParameters,
    ) : Worker(context, parameters) {
        override fun doWork(): Result {
            return Result.success()
        }
    }
}
