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

package com.duckduckgo.subscriptions.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SubscriptionPixelSenderImplTest {

    private val pixel: Pixel = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val paywallMetricsManager: PaywallMetricsManager = mock()

    private lateinit var testee: SubscriptionPixelSenderImpl

    @Before
    fun before() {
        testee = SubscriptionPixelSenderImpl(pixel, appBuildConfig, paywallMetricsManager)
    }

    @Test
    fun whenReportExpirationReminderScheduledThenFiresScheduledPixel() {
        testee.reportExpirationReminderScheduled()

        verify(pixel).fire(
            pixelName = "m_subscription_expiration_reminder_scheduled",
            type = Count,
        )
    }

    @Test
    fun whenReportExpirationReminderSchedulingErrorThenFiresSchedulingErrorPixel() {
        testee.reportExpirationReminderSchedulingError()

        verify(pixel).fire(
            pixelName = "m_subscription_expiration_reminder_scheduling_error",
            type = Count,
        )
    }

    @Test
    fun whenReportExpirationReminderNotFiredInactiveSubscriptionThenFiresInactiveSubscriptionPixel() {
        testee.reportExpirationReminderNotFiredInactiveSubscription()

        verify(pixel).fire(
            pixelName = "m_subscription_expiration_reminder_not_fired_inactive_subscription",
            type = Count,
        )
    }

    @Test
    fun whenReportExpirationReminderNotFiredPermissionsRejectedThenFiresPermissionsRejectedPixel() {
        testee.reportExpirationReminderNotFiredPermissionsRejected()

        verify(pixel).fire(
            pixelName = "m_subscription_expiration_reminder_not_fired_permissions_rejected",
            type = Count,
        )
    }
}
