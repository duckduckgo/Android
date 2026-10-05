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
import org.mockito.kotlin.times
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

    @Test
    fun whenReportOfferScreenShownWithOriginThenFiresImpressionWithOrigin() {
        testee.reportOfferScreenShown("funnel_appsettings_android")

        verify(pixel).fire(
            pixelName = "m_privacy-pro_offer_screen_impression_c",
            type = Count,
            parameters = mapOf("origin" to "funnel_appsettings_android"),
        )
    }

    @Test
    fun whenReportOfferScreenShownWithoutOriginThenFiresImpressionWithNoParams() {
        testee.reportOfferScreenShown(null)

        verify(pixel).fire(
            pixelName = "m_privacy-pro_offer_screen_impression_c",
            type = Count,
        )
    }

    @Test
    fun whenReportOfferScreenShownWithOriginNotInAllowlistThenOriginIsDropped() {
        testee.reportOfferScreenShown("javascript:alert(1)") // malformed
        testee.reportOfferScreenShown("") // blank
        testee.reportOfferScreenShown("funnel_unique_user_id_1234") // well-formed but not an allowlisted entry point

        verify(pixel, times(3)).fire(
            pixelName = "m_privacy-pro_offer_screen_impression_c",
            type = Count,
        )
    }

    @Test
    fun whenReportOfferSubscribeClickWithOriginThenFiresWithOrigin() {
        testee.reportOfferSubscribeClick("funnel_duckai_android__modelpicker")

        verify(pixel).fire(
            pixelName = "m_privacy-pro_terms-conditions_subscribe_click_c",
            type = Count,
            parameters = mapOf("origin" to "funnel_duckai_android__modelpicker"),
        )
    }

    @Test
    fun whenReportAppSettingsGetSubscriptionClickThenFiresWithAppSettingsOrigin() {
        testee.reportAppSettingsGetSubscriptionClick()

        verify(pixel).fire(
            pixelName = "m_privacy-pro_app-settings_get_click_c",
            type = Count,
            parameters = mapOf("origin" to "funnel_appsettings_android"),
        )
    }
}
