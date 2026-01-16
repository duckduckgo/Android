/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.notification.vpnreminder

import android.annotation.SuppressLint
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class VpnReminderNotificationSchedulerImplTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val workManager: WorkManager = mock()
    private val notificationManager: NotificationManagerCompat = mock()
    private val privacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java)

    private lateinit var testee: VpnReminderNotificationSchedulerImpl

    @Before
    fun before() {
        testee = VpnReminderNotificationSchedulerImpl(
            workManager,
            notificationManager,
            Lazy { privacyProFeature },
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenFeatureDisabledThenNotificationNotScheduled() = runTest {
        privacyProFeature.vpnReminderNotification().setRawStoredState(Toggle.State(enable = false))
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(true)

        testee.scheduleVpnReminderNotification()

        verify(workManager, never()).enqueue(any<androidx.work.WorkRequest>())
    }

    @Test
    fun whenNotificationsDisabledThenNotificationNotScheduled() = runTest {
        privacyProFeature.vpnReminderNotification().setRawStoredState(Toggle.State(enable = true))
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(false)

        testee.scheduleVpnReminderNotification()

        verify(workManager, never()).enqueue(any<androidx.work.WorkRequest>())
    }

    @Test
    fun whenFeatureEnabledAndNotificationsEnabledThenNotificationScheduled() = runTest {
        privacyProFeature.vpnReminderNotification().setRawStoredState(Toggle.State(enable = true))
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(true)

        testee.scheduleVpnReminderNotification()

        verify(workManager).enqueue(any<androidx.work.WorkRequest>())
    }

    @Test
    fun whenCancelScheduledNotificationThenWorkCancelled() = runTest {
        testee.cancelScheduledNotification()

        verify(workManager).cancelAllWorkByTag(VpnReminderNotificationWorker.VPN_REMINDER_WORK_REQUEST_TAG)
    }
}
