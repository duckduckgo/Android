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
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
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
    fun whenReportOfferSubscribeClickWithPirFreeScanOriginThenOriginIsAttached() {
        testee.reportOfferSubscribeClick(origin = "funnel_freescan_android")

        verify(pixel).fire(
            pixelName = "m_privacy-pro_terms-conditions_subscribe_click_c",
            type = Count,
            parameters = mapOf("origin" to "funnel_freescan_android"),
        )
    }

    @Test
    fun whenReportUndoCancellationClickThenFiresCountAndDailyWithFreeTrial() {
        testee.reportSubscriptionSettingsUndoCancellationClick(isFreeTrial = false)

        val params = mapOf("free_trial" to "false")
        verify(pixel).fire(pixelName = "subscription_settings_undo_cancellation_click_c", type = Count, parameters = params)
        verify(pixel).fire(pixelName = "subscription_settings_undo_cancellation_click_d", type = Daily(), parameters = params)
    }

    @Test
    fun whenReportCancellationFlowCancelClickThenFiresCountAndDaily() {
        testee.reportCancellationFlowCancelClick()

        verify(pixel).fire(pixelName = "subscription_cancellation_cancel_click_c", type = Count, parameters = emptyMap())
        verify(pixel).fire(pixelName = "subscription_cancellation_cancel_click_d", type = Daily(), parameters = emptyMap())
    }

    @Test
    fun whenReportCancellationFlowPlayStoreCancelledThenFiresWithEntryTrialAndPurchaseAge() {
        testee.reportCancellationFlowPlayStoreCancelled(
            entry = CancellationEntry.CANCELLATION,
            isFreeTrial = true,
            daysSincePurchase = DaysSincePurchaseBucket.D2_7,
        )

        val params = mapOf("entry" to "cancellation", "free_trial" to "true", "days_since_purchase" to "d2_7")
        verify(pixel).fire(pixelName = "subscription_cancellation_play_store_cancelled_c", type = Count, parameters = params)
        verify(pixel).fire(pixelName = "subscription_cancellation_play_store_cancelled_d", type = Daily(), parameters = params)
    }

    @Test
    fun whenReportCancellationFlowLoadFailureThenFiresWithReasonAndErrorCode() {
        testee.reportCancellationFlowLoadFailure(CancellationFlowLoadFailureReason.HTTP, 500)

        val params = mapOf("reason" to "http", "error_code" to "500")
        verify(pixel).fire(pixelName = "subscription_cancellation_flow_load_failure_c", type = Count, parameters = params)
        verify(pixel).fire(pixelName = "subscription_cancellation_flow_load_failure_d", type = Daily(), parameters = params)
    }

    @Test
    fun whenReportCancellationFlowFeatureInfoFailureThenFiresCountAndDaily() {
        testee.reportCancellationFlowFeatureInfoFailure()

        verify(pixel).fire(pixelName = "subscription_cancellation_feature-info_failure_c", type = Count, parameters = emptyMap())
        verify(pixel).fire(pixelName = "subscription_cancellation_feature-info_failure_d", type = Daily(), parameters = emptyMap())
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
    fun whenReportSubscriptionSwitchThenFiresSwitchPixel() {
        testee.reportSubscriptionSwitch()

        verify(pixel).fire(pixelName = "subscription_switch", type = Count)
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
