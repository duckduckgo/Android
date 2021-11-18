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

import android.content.Context.MODE_PRIVATE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.mobile.android.vpn.pixels.RealDeviceShieldPixels.Companion.DS_PIXELS_PREF_FILE
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class RealDeviceShieldPixelsTest {

    private val pixel = mock<Pixel>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val deviceShieldPixels: DeviceShieldPixels = RealDeviceShieldPixels(context, pixel)

    @Before
    fun setup() {
        context.getSharedPreferences(DS_PIXELS_PREF_FILE, MODE_PRIVATE).edit().clear().commit()
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
    fun whenReportEnableThenFireUniqueAndDailyPixel() {
        deviceShieldPixels.reportEnabled()
        deviceShieldPixels.reportEnabled()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_DAILY.pixelName)
        verifyNoMoreInteractions(pixel)
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
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_REMINDER_NOTIFICATION)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromSettingsThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.enableFromOnboarding()
        deviceShieldPixels.enableFromOnboarding()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromSettingsTileThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.enableFromQuickSettingsTile()
        deviceShieldPixels.enableFromQuickSettingsTile()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromPrivacyReportThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.enableFromSummaryTrackerActivity()
        deviceShieldPixels.enableFromSummaryTrackerActivity()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDisableFromSettingsTileThenFireDailyAndCountPixels() {
        deviceShieldPixels.disableFromQuickSettingsTile()
        deviceShieldPixels.disableFromQuickSettingsTile()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_DISABLE_FROM_SETTINGS_TILE_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_DISABLE_FROM_SETTINGS_TILE)
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
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_PRESS_ONGOING_NOTIFICATION)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowReminderNotificationThenFireDailyAndCountPixels() {
        deviceShieldPixels.didShowReminderNotification()
        deviceShieldPixels.didShowReminderNotification()

        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_REMINDER_NOTIFICATION_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_SHOW_REMINDER_NOTIFICATION)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressReminderNotificationThenFireDailyAndCountPixels() {
        deviceShieldPixels.didPressReminderNotification()
        deviceShieldPixels.didPressReminderNotification()

        verify(pixel).fire(DeviceShieldPixelNames.DID_PRESS_REMINDER_NOTIFICATION_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_PRESS_REMINDER_NOTIFICATION)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowNewTabSummaryThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.didShowNewTabSummary()
        deviceShieldPixels.didShowNewTabSummary()

        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_NEW_TAB_SUMMARY_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_NEW_TAB_SUMMARY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_SHOW_NEW_TAB_SUMMARY)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressNewTabSummaryThenFireDailyAndCountPixels() {
        deviceShieldPixels.didPressNewTabSummary()
        deviceShieldPixels.didPressNewTabSummary()

        verify(pixel).fire(DeviceShieldPixelNames.DID_PRESS_NEW_TAB_SUMMARY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_PRESS_NEW_TAB_SUMMARY)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowSummaryTrackerActivityThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.didShowSummaryTrackerActivity()
        deviceShieldPixels.didShowSummaryTrackerActivity()

        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_SUMMARY_TRACKER_ACTIVITY_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_SUMMARY_TRACKER_ACTIVITY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_SHOW_SUMMARY_TRACKER_ACTIVITY)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowDetailedTrackerActivityThenFireUniqueDailyAndCountPixels() {
        deviceShieldPixels.didShowDetailedTrackerActivity()
        deviceShieldPixels.didShowDetailedTrackerActivity()

        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_DETAILED_TRACKER_ACTIVITY_UNIQUE)
        verify(pixel).fire(DeviceShieldPixelNames.DID_SHOW_DETAILED_TRACKER_ACTIVITY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.DID_SHOW_DETAILED_TRACKER_ACTIVITY)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenStartErrorThenFireDailyAndCountPixels() {
        deviceShieldPixels.startError()
        deviceShieldPixels.startError()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_START_ERROR_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_START_ERROR)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenAutomaticRestartThenFireDailyAndCountPixels() {
        deviceShieldPixels.automaticRestart()
        deviceShieldPixels.automaticRestart()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_AUTOMATIC_RESTART_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_AUTOMATIC_RESTART)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenSuddenKillBySystemThenFireDailyAndCountPixels() {
        deviceShieldPixels.suddenKillBySystem()
        deviceShieldPixels.suddenKillBySystem()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_KILLED_BY_SYSTEM_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_KILLED_BY_SYSTEM)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_KILLED)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenSuddenKillByVpnRevokedThenFireDailyAndCountPixels() {
        deviceShieldPixels.suddenKillByVpnRevoked()
        deviceShieldPixels.suddenKillByVpnRevoked()

        verify(pixel).fire(DeviceShieldPixelNames.ATP_KILLED_VPN_REVOKED_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_KILLED_VPN_REVOKED)
        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_KILLED)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenPrivacyReportArticleDisplayedThenFireCountPixel() {
        deviceShieldPixels.privacyReportArticleDisplayed()
        deviceShieldPixels.privacyReportArticleDisplayed()

        verify(pixel, times(2)).fire(DeviceShieldPixelNames.ATP_DID_SHOW_PRIVACY_REPORT_ARTICLE)
        verifyNoMoreInteractions(pixel)
    }

    private fun DeviceShieldPixelNames.notificationVariant(variant: Int): String {
        return String.format(Locale.US, pixelName, variant)
    }
}
