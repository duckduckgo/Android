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

package com.duckduckgo.mobile.android.vpn.pixels

import androidx.core.content.edit
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.mobile.android.vpn.feature.AppTpTDSPixelsPlugin
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

class RealDeviceShieldPixelsTest {

    private val pixel = mock<Pixel>()
    private val sharedPreferencesProvider = mock<SharedPreferencesProvider>()
    private val prefs = InMemorySharedPreferences()

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Before
    fun setup() {
        whenever(
            sharedPreferencesProvider.getSharedPreferences(eq("com.duckduckgo.mobile.android.device.shield.pixels"), eq(true), eq(true)),
        ).thenReturn(prefs)

        deviceShieldPixels = RealDeviceShieldPixels(
            pixel,
            sharedPreferencesProvider,
            mock<AppTpTDSPixelsPlugin>(),
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenDeviceShieldEnabledOnSearchThenFireDailyPixel() {
        deviceShieldPixels.deviceShieldEnabledOnSearch()
        deviceShieldPixels.deviceShieldEnabledOnSearch()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_UPON_SEARCH_DAILY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDeviceShieldDisabledOnSearchThenFireDailyPixel() {
        deviceShieldPixels.deviceShieldDisabledOnSearch()
        deviceShieldPixels.deviceShieldDisabledOnSearch()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_DISABLE_UPON_SEARCH_DAILY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenReportEnableThenFireUniqueAndMonthlyAndDailyPixel() {
        deviceShieldPixels.reportEnabled()
        deviceShieldPixels.reportEnabled()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_DAILY.pixelName)
        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_MONTHLY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenReportExactly28DaysApartThenDoNotFireMonthlyPixel() {
        val pixelName = DeviceShieldPixelNames.ATP_ENABLE_MONTHLY.pixelName

        deviceShieldPixels.reportEnabled()

        val pastDate = Instant.now()
            .minus(28, ChronoUnit.DAYS)
            .atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
        prefs.edit(true) { putString("${pixelName}_timestamp", pastDate) }

        deviceShieldPixels.reportEnabled()

        verify(pixel).fire(pixelName)
    }

    @Test
    fun whenReportEnableMoreThan28DaysApartReportMonthlyPixel() {
        val pixelName = DeviceShieldPixelNames.ATP_ENABLE_MONTHLY.pixelName

        deviceShieldPixels.reportEnabled()

        val pastDate = Instant.now()
            .minus(29, ChronoUnit.DAYS)
            .atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
        prefs.edit(true) { putString("${pixelName}_timestamp", pastDate) }

        deviceShieldPixels.reportEnabled()

        verify(pixel, times(2)).fire(pixelName)
    }

    @Test
    fun whenReportDisableThenFireDailyPixel() {
        deviceShieldPixels.reportDisabled()
        deviceShieldPixels.reportDisabled()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_DISABLE_DAILY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromReminderNotificationThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.enableFromReminderNotification()
        deviceShieldPixels.enableFromReminderNotification()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_REMINDER_NOTIFICATION_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_REMINDER_NOTIFICATION_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_REMINDER_NOTIFICATION.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromSettingsThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.enableFromOnboarding()
        deviceShieldPixels.enableFromOnboarding()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromSettingsTileThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.enableFromQuickSettingsTile()
        deviceShieldPixels.enableFromQuickSettingsTile()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromPrivacyReportThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.enableFromSummaryTrackerActivity()
        deviceShieldPixels.enableFromSummaryTrackerActivity()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDisableFromSettingsTileThenFireDailyAndCountPixels() {
        deviceShieldPixels.disableFromQuickSettingsTile()
        deviceShieldPixels.disableFromQuickSettingsTile()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_DISABLE_FROM_SETTINGS_TILE_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_DISABLE_FROM_SETTINGS_TILE.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowDailyNotificationThenFireDailyPixel() {
        deviceShieldPixels.didShowDailyNotification(0)
        deviceShieldPixels.didShowDailyNotification(1)

        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_DAILY_NOTIFICATION.notificationVariant(0))
        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_DAILY_NOTIFICATION.notificationVariant(1))
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressDailyNotificationThenFireDailyPixel() {
        deviceShieldPixels.didPressOnDailyNotification(0)
        deviceShieldPixels.didPressOnDailyNotification(1)

        verify(pixel).fire(DeviceShieldPixelNames.DID_PRESS_DAILY_NOTIFICATION.notificationVariant(0))
        verify(pixel).fire(DeviceShieldPixelNames.DID_PRESS_DAILY_NOTIFICATION.notificationVariant(1))
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowWeeklyNotificationThenFireDailyPixel() {
        deviceShieldPixels.didShowWeeklyNotification(0)
        deviceShieldPixels.didShowWeeklyNotification(1)

        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_WEEKLY_NOTIFICATION.notificationVariant(0))
        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_WEEKLY_NOTIFICATION.notificationVariant(1))
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressWeeklyNotificationThenFireDailyPixel() {
        deviceShieldPixels.didPressOnWeeklyNotification(0)
        deviceShieldPixels.didPressOnWeeklyNotification(1)

        verify(pixel).fire(DeviceShieldPixelNames.DID_PRESS_WEEKLY_NOTIFICATION.notificationVariant(0))
        verify(pixel).fire(DeviceShieldPixelNames.DID_PRESS_WEEKLY_NOTIFICATION.notificationVariant(1))
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressOngoingNotificationThenFireDailyAndCountPixels() {
        deviceShieldPixels.didPressOngoingNotification()
        deviceShieldPixels.didPressOngoingNotification()

        verify(pixel).fire(DeviceShieldPixelNames.DID_PRESS_ONGOING_NOTIFICATION_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_PRESS_ONGOING_NOTIFICATION.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowReminderNotificationThenFireDailyAndCountPixels() {
        deviceShieldPixels.didShowReminderNotification()
        deviceShieldPixels.didShowReminderNotification()

        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_REMINDER_NOTIFICATION_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_SHOW_REMINDER_NOTIFICATION.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressReminderNotificationThenFireDailyAndCountPixels() {
        deviceShieldPixels.didPressReminderNotification()
        deviceShieldPixels.didPressReminderNotification()

        verify(pixel).fire(DeviceShieldPixelNames.DID_PRESS_REMINDER_NOTIFICATION_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_PRESS_REMINDER_NOTIFICATION.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowNewTabSummaryThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.didShowNewTabSummary()
        deviceShieldPixels.didShowNewTabSummary()

        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_NEW_TAB_SUMMARY_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_NEW_TAB_SUMMARY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_SHOW_NEW_TAB_SUMMARY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressNewTabSummaryThenFireDailyAndCountPixels() {
        deviceShieldPixels.didPressNewTabSummary()
        deviceShieldPixels.didPressNewTabSummary()

        verify(pixel).fire(DeviceShieldPixelNames.DID_PRESS_NEW_TAB_SUMMARY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_PRESS_NEW_TAB_SUMMARY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowSummaryTrackerActivityThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.didShowSummaryTrackerActivity()
        deviceShieldPixels.didShowSummaryTrackerActivity()

        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_SUMMARY_TRACKER_ACTIVITY_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_SUMMARY_TRACKER_ACTIVITY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_SHOW_SUMMARY_TRACKER_ACTIVITY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowDetailedTrackerActivityThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.didShowDetailedTrackerActivity()
        deviceShieldPixels.didShowDetailedTrackerActivity()

        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_DETAILED_TRACKER_ACTIVITY_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_DETAILED_TRACKER_ACTIVITY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_SHOW_DETAILED_TRACKER_ACTIVITY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenStartErrorThenFireDailyAndCountPixels() {
        deviceShieldPixels.startError()
        deviceShieldPixels.startError()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_START_ERROR_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_START_ERROR.pixelName)
        verify(pixel, times(2)).enqueueFire(DeviceShieldPixelNames.VPN_START_ATTEMPT_FAILURE.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenAutomaticRestartThenFireDailyAndCountPixels() {
        deviceShieldPixels.automaticRestart()
        deviceShieldPixels.automaticRestart()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_AUTOMATIC_RESTART_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_AUTOMATIC_RESTART.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenSuddenKillBySystemThenFireDailyAndCountPixels() {
        deviceShieldPixels.suddenKillBySystem()
        deviceShieldPixels.suddenKillBySystem()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_KILLED_BY_SYSTEM_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_KILLED_BY_SYSTEM.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_KILLED.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenSuddenKillByVpnRevokedThenFireDailyAndCountPixels() {
        deviceShieldPixels.suddenKillByVpnRevoked()
        deviceShieldPixels.suddenKillByVpnRevoked()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_KILLED_VPN_REVOKED_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_KILLED_VPN_REVOKED.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_KILLED.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenPrivacyReportArticleDisplayedThenFireCountPixel() {
        deviceShieldPixels.privacyReportArticleDisplayed()
        deviceShieldPixels.privacyReportArticleDisplayed()

        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_DID_SHOW_PRIVACY_REPORT_ARTICLE.pixelName)
        verify(pixel, times(1)).fire(DeviceShieldPixelNames.ATP_DID_SHOW_PRIVACY_REPORT_ARTICLE_DAILY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenReportUnprotectedAppsBucketCalledThenFirePixels() {
        val bucketSize = 20
        deviceShieldPixels.reportUnprotectedAppsBucket(bucketSize)

        verify(pixel).fire(DeviceShieldPixelNames.ATP_REPORT_UNPROTECTED_APPS_BUCKET.notificationVariant(bucketSize))
        verify(pixel).fire(DeviceShieldPixelNames.ATP_REPORT_UNPROTECTED_APPS_BUCKET_DAILY.notificationVariant(bucketSize))
    }

    private fun DeviceShieldPixelNames.notificationVariant(variant: Int): String {
        return String.format(Locale.US, pixelName, variant)
    }
}
