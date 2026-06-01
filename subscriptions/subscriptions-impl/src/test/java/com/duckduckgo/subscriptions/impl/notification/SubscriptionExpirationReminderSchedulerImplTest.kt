/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.notification

import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class SubscriptionExpirationReminderSchedulerImplTest {

    private val workManager: WorkManager = mock()
    private val notificationManager: NotificationManagerCompat = mock()
    private val subscriptionsManager: SubscriptionsManager = mock()

    private lateinit var testee: SubscriptionExpirationReminderSchedulerImpl

    @Before
    fun before() {
        testee = SubscriptionExpirationReminderSchedulerImpl(
            workManager,
            notificationManager,
            subscriptionsManager,
        )
    }

    @Test
    fun whenDaysBeforeCancelIsZeroThenNotificationNotScheduled() = runTest {
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(true)

        testee.scheduleReminderNotification(0)

        verify(workManager, never()).enqueue(any<WorkRequest>())
    }

    @Test
    fun whenNotificationsDisabledThenNotificationNotScheduled() = runTest {
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(false)

        testee.scheduleReminderNotification(1)

        verify(workManager, never()).enqueue(any<WorkRequest>())
    }

    @Test
    fun whenAllConditionsMetThenNotificationScheduled() = runTest {
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(true)
        whenever(subscriptionsManager.getSubscription()).thenReturn(activeSubscriptionExpiringIn(days = 30))

        testee.scheduleReminderNotification(7)

        verify(workManager).enqueue(any<WorkRequest>())
    }

    @Test
    fun whenCancelScheduledNotificationThenWorkCancelled() {
        testee.cancelScheduledNotification()

        verify(workManager).cancelAllWorkByTag(SubscriptionExpirationReminderSchedulerImpl.EXPIRATION_REMINDER_WORK_TAG)
    }

    private fun activeSubscriptionExpiringIn(days: Int): Subscription = Subscription(
        productId = "test-product",
        billingPeriod = "monthly",
        startedAt = 0L,
        expiresOrRenewsAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days.toLong()),
        status = SubscriptionStatus.AUTO_RENEWABLE,
        platform = "google",
        activeOffers = emptyList(),
    )
}
