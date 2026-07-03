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

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SubscriptionExpirationReminderWorkerTest {

    private val notificationSender: NotificationSender = mock()
    private val notificationManager: NotificationManagerCompat = mock()
    private val notification: SubscriptionExpirationReminderNotification = mock()
    private val pixelSender: SubscriptionPixelSender = mock()

    private lateinit var context: Context

    @Before
    fun before() {
        context = mock()
    }

    @Test
    fun whenNotificationsDisabledThenPermissionsRejectedPixelFiredAndNotSent() = runTest {
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(false)

        val result = buildWorker().doWork()

        verify(pixelSender).reportExpirationReminderNotFiredPermissionsRejected()
        verifyNoInteractions(notificationSender)
        assertThat(result, `is`(ListenableWorker.Result.success()))
    }

    @Test
    fun whenSubscriptionInactiveThenInactiveSubscriptionPixelFiredAndNotSent() = runTest {
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(true)
        whenever(notification.canShow()).thenReturn(false)

        val result = buildWorker().doWork()

        verify(pixelSender).reportExpirationReminderNotFiredInactiveSubscription()
        verifyNoInteractions(notificationSender)
        assertThat(result, `is`(ListenableWorker.Result.success()))
    }

    @Test
    fun whenNotificationsEnabledAndSubscriptionActiveThenNotificationSentAndNoNotFiredPixel() = runTest {
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(true)
        whenever(notification.canShow()).thenReturn(true)

        val result = buildWorker().doWork()

        verify(notificationSender).sendNotification(notification)
        verify(pixelSender, never()).reportExpirationReminderNotFiredPermissionsRejected()
        verify(pixelSender, never()).reportExpirationReminderNotFiredInactiveSubscription()
        assertThat(result, `is`(ListenableWorker.Result.success()))
    }

    private fun buildWorker(): SubscriptionExpirationReminderWorker =
        TestListenableWorkerBuilder<SubscriptionExpirationReminderWorker>(context = context).build().apply {
            notificationSender = this@SubscriptionExpirationReminderWorkerTest.notificationSender
            notificationManager = this@SubscriptionExpirationReminderWorkerTest.notificationManager
            subscriptionExpirationReminderNotification = notification
            pixelSender = this@SubscriptionExpirationReminderWorkerTest.pixelSender
        }
}
