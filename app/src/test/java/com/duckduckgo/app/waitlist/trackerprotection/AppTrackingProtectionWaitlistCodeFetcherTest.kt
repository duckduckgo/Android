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

package com.duckduckgo.app.waitlist.trackerprotection

import androidx.work.WorkManager
import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.waitlist.trackerprotection.AppTPWaitlistWorkRequestBuilder.Companion.APP_TP_WAITLIST_SYNC_WORK_TAG
import com.duckduckgo.mobile.android.vpn.waitlist.FetchCodeResult
import com.duckduckgo.mobile.android.vpn.waitlist.TrackingProtectionWaitlistManager
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AppTrackingProtectionWaitlistCodeFetcherTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockNotification: SchedulableNotification = mock()
    private val mockNotificationSender: NotificationSender = mock()
    private val workManager: WorkManager = mock()
    private val waitlistManager: TrackingProtectionWaitlistManager = mock()

    private lateinit var testee: AppTrackingProtectionWaitlistCodeFetcher

    @Before
    fun before() {
        testee = AppTrackingProtectionWaitlistCodeFetcher(
            workManager,
            waitlistManager,
            mockNotification,
            mockNotificationSender,
            coroutineRule.testDispatcherProvider,
            TestScope()
        )
    }

    @Test
    fun whenFetchingInviteCodeAndCodeAlreadyExistedThenWorkIsCancelled() = runTest {
        whenever(waitlistManager.fetchInviteCode()).thenReturn(FetchCodeResult.CodeExisted)
        whenever(workManager.cancelAllWorkByTag(APP_TP_WAITLIST_SYNC_WORK_TAG)).thenReturn(mock())
        testee.fetchInviteCode()
        verify(workManager).cancelAllWorkByTag(APP_TP_WAITLIST_SYNC_WORK_TAG)
    }

    @Test
    fun whenCodeIsFetchedThenWorkIsCancelledAndNotificationIsSent() = runTest {
        whenever(waitlistManager.fetchInviteCode()).thenReturn(FetchCodeResult.Code)
        testee.fetchInviteCode()
        whenever(workManager.cancelAllWorkByTag(APP_TP_WAITLIST_SYNC_WORK_TAG)).thenReturn(mock())
        verify(workManager).cancelAllWorkByTag(APP_TP_WAITLIST_SYNC_WORK_TAG)
        verify(mockNotificationSender).sendNotification(any())
    }
}
