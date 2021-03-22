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

package com.duckduckgo.mobile.android.vpn.analytics

import android.content.Context.MODE_PRIVATE
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.mobile.android.vpn.analytics.RealDeviceShieldAnalytics.Companion.DS_ANALYTICS_PREF_FILE
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import org.junit.Before
import org.junit.Test
import java.util.*

class RealDeviceShieldAnalyticsTest {

    private val pixel = mock<Pixel>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val deviceShieldAnalytics: DeviceShieldAnalytics = RealDeviceShieldAnalytics(context, pixel)

    @Before
    fun setup() {
        context.getSharedPreferences(DS_ANALYTICS_PREF_FILE, MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun whenDeviceShieldInstalledIsCalledThenFireUniquePixel() {
        deviceShieldAnalytics.deviceShieldInstalled()
        deviceShieldAnalytics.deviceShieldInstalled()

        verify(pixel).fire(DeviceShieldPixels.DS_INSTALLED_UNIQUE)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDeviceShieldEnabledOnSearchThenFireDailyPixel() {
        deviceShieldAnalytics.deviceShieldEnabledOnSearch()
        deviceShieldAnalytics.deviceShieldEnabledOnSearch()

        verify(pixel).fire(DeviceShieldPixels.DS_ENABLE_UPON_SEARCH_DAILY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDeviceShieldDisabledOnSearchThenFireDailyPixel() {
        deviceShieldAnalytics.deviceShieldDisabledOnSearch()
        deviceShieldAnalytics.deviceShieldDisabledOnSearch()

        verify(pixel).fire(DeviceShieldPixels.DS_DISABLE_UPON_SEARCH_DAILY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenReportEnableThenFireUniqueAndDailyPixel() {
        deviceShieldAnalytics.reportEnabled()
        deviceShieldAnalytics.reportEnabled()

        verify(pixel).fire(DeviceShieldPixels.DS_ENABLE_UNIQUE)
        verify(pixel).fire(DeviceShieldPixels.DS_ENABLE_DAILY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenReporDisableThenFireDailyPixel() {
        deviceShieldAnalytics.reportDisabled()
        deviceShieldAnalytics.reportDisabled()

        verify(pixel).fire(DeviceShieldPixels.DS_DISABLE_DAILY.pixelName)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromNewTabThenFireUniqueDailyAndCountPixels() {
        deviceShieldAnalytics.enableFromNewTab()
        deviceShieldAnalytics.enableFromNewTab()

        verify(pixel).fire(DeviceShieldPixels.ENABLE_DS_FROM_NEW_TAB_UNIQUE)
        verify(pixel).fire(DeviceShieldPixels.ENABLE_DS_FROM_NEW_TAB_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.ENABLE_DS_FROM_NEW_TAB)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromReminderNotificationThenFireUniqueDailyAndCountPixels() {
        deviceShieldAnalytics.enableFromReminderNotification()
        deviceShieldAnalytics.enableFromReminderNotification()

        verify(pixel).fire(DeviceShieldPixels.ENABLE_DS_FROM_REMINDER_NOTIFICATION_UNIQUE)
        verify(pixel).fire(DeviceShieldPixels.ENABLE_DS_FROM_REMINDER_NOTIFICATION_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.ENABLE_DS_FROM_REMINDER_NOTIFICATION)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromSettingsThenFireUniqueDailyAndCountPixels() {
        deviceShieldAnalytics.enableFromSettings()
        deviceShieldAnalytics.enableFromSettings()

        verify(pixel).fire(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS_UNIQUE)
        verify(pixel).fire(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromSettingsTileThenFireUniqueDailyAndCountPixels() {
        deviceShieldAnalytics.enableFromQuickSettingsTile()
        deviceShieldAnalytics.enableFromQuickSettingsTile()

        verify(pixel).fire(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS_TILE_UNIQUE)
        verify(pixel).fire(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS_TILE_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS_TILE)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenEnableFromPrivacyReportThenFireUniqueDailyAndCountPixels() {
        deviceShieldAnalytics.enableFromPrivacyReport()
        deviceShieldAnalytics.enableFromPrivacyReport()

        verify(pixel).fire(DeviceShieldPixels.ENABLE_DS_FROM_PRIVACY_REPORT_UNIQUE)
        verify(pixel).fire(DeviceShieldPixels.ENABLE_DS_FROM_PRIVACY_REPORT_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.ENABLE_DS_FROM_PRIVACY_REPORT)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDisableFromSettingsThenFireDailyAndCountPixels() {
        deviceShieldAnalytics.disableFromSettings()
        deviceShieldAnalytics.disableFromSettings()

        verify(pixel).fire(DeviceShieldPixels.DISABLE_DS_FROM_SETTINGS_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DISABLE_DS_FROM_SETTINGS)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDisableFromSettingsTileThenFireDailyAndCountPixels() {
        deviceShieldAnalytics.disableFromQuickSettingsTile()
        deviceShieldAnalytics.disableFromQuickSettingsTile()

        verify(pixel).fire(DeviceShieldPixels.DISABLE_DS_FROM_SETTINGS_TILE_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DISABLE_DS_FROM_SETTINGS_TILE)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowDailyNotificationThenFireDailyPixel() {
        deviceShieldAnalytics.didShowDailyNotification(0)
        deviceShieldAnalytics.didShowDailyNotification(1)

        verify(pixel).fire(DeviceShieldPixels.DID_SHOW_DAILY_NOTIFICATION.notificationVariant(0))
        verify(pixel).fire(DeviceShieldPixels.DID_SHOW_DAILY_NOTIFICATION.notificationVariant(1))
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressDailyNotificationThenFireDailyPixel() {
        deviceShieldAnalytics.didPressOnDailyNotification(0)
        deviceShieldAnalytics.didPressOnDailyNotification(1)

        verify(pixel).fire(DeviceShieldPixels.DID_PRESS_DAILY_NOTIFICATION.notificationVariant(0))
        verify(pixel).fire(DeviceShieldPixels.DID_PRESS_DAILY_NOTIFICATION.notificationVariant(1))
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowWeeklyNotificationThenFireDailyPixel() {
        deviceShieldAnalytics.didShowWeeklyNotification(0)
        deviceShieldAnalytics.didShowWeeklyNotification(1)

        verify(pixel).fire(DeviceShieldPixels.DID_SHOW_WEEKLY_NOTIFICATION.notificationVariant(0))
        verify(pixel).fire(DeviceShieldPixels.DID_SHOW_WEEKLY_NOTIFICATION.notificationVariant(1))
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressWeeklyNotificationThenFireDailyPixel() {
        deviceShieldAnalytics.didPressOnWeeklyNotification(0)
        deviceShieldAnalytics.didPressOnWeeklyNotification(1)

        verify(pixel).fire(DeviceShieldPixels.DID_PRESS_WEEKLY_NOTIFICATION.notificationVariant(0))
        verify(pixel).fire(DeviceShieldPixels.DID_PRESS_WEEKLY_NOTIFICATION.notificationVariant(1))
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressOngoingNotificationThenFireDailyAndCountPixels() {
        deviceShieldAnalytics.didPressOngoingNotification()
        deviceShieldAnalytics.didPressOngoingNotification()

        verify(pixel).fire(DeviceShieldPixels.DID_PRESS_ONGOING_NOTIFICATION_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DID_PRESS_ONGOING_NOTIFICATION)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowReminderNotificationThenFireDailyAndCountPixels() {
        deviceShieldAnalytics.didShowReminderNotification()
        deviceShieldAnalytics.didShowReminderNotification()

        verify(pixel).fire(DeviceShieldPixels.DID_SHOW_REMINDER_NOTIFICATION_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DID_SHOW_REMINDER_NOTIFICATION)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressReminderNotificationThenFireDailyAndCountPixels() {
        deviceShieldAnalytics.didPressReminderNotification()
        deviceShieldAnalytics.didPressReminderNotification()

        verify(pixel).fire(DeviceShieldPixels.DID_PRESS_REMINDER_NOTIFICATION_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DID_PRESS_REMINDER_NOTIFICATION)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowNewTabSummaryThenFireUniqueDailyAndCountPixels() {
        deviceShieldAnalytics.didShowNewTabSummary()
        deviceShieldAnalytics.didShowNewTabSummary()

        verify(pixel).fire(DeviceShieldPixels.DID_SHOW_NEW_TAB_SUMMARY_UNIQUE)
        verify(pixel).fire(DeviceShieldPixels.DID_SHOW_NEW_TAB_SUMMARY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DID_SHOW_NEW_TAB_SUMMARY)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidPressNewTabSummaryThenFireDailyAndCountPixels() {
        deviceShieldAnalytics.didPressNewTabSummary()
        deviceShieldAnalytics.didPressNewTabSummary()

        verify(pixel).fire(DeviceShieldPixels.DID_PRESS_NEW_TAB_SUMMARY_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DID_PRESS_NEW_TAB_SUMMARY)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDidShowPrivacyReportThenFireUniqueDailyAndCountPixels() {
        deviceShieldAnalytics.didShowPrivacyReport()
        deviceShieldAnalytics.didShowPrivacyReport()

        verify(pixel).fire(DeviceShieldPixels.DID_SHOW_PRIVACY_REPORT_UNIQUE)
        verify(pixel).fire(DeviceShieldPixels.DID_SHOW_PRIVACY_REPORT_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DID_SHOW_PRIVACY_REPORT)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenStartErrorThenFireDailyAndCountPixels() {
        deviceShieldAnalytics.startError()
        deviceShieldAnalytics.startError()

        verify(pixel).fire(DeviceShieldPixels.DS_START_ERROR_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DS_START_ERROR)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenAutomaticRestartThenFireDailyAndCountPixels() {
        deviceShieldAnalytics.automaticRestart()
        deviceShieldAnalytics.automaticRestart()

        verify(pixel).fire(DeviceShieldPixels.DS_AUTOMATIC_RESTART_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DS_AUTOMATIC_RESTART)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenSuddenKillBySystemThenFireDailyAndCountPixels() {
        deviceShieldAnalytics.suddenKillBySystem()
        deviceShieldAnalytics.suddenKillBySystem()

        verify(pixel).fire(DeviceShieldPixels.DS_KILLED_BY_SYSTEM_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DS_KILLED_BY_SYSTEM)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DS_KILLED)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenSuddenKillByVpnRevokedThenFireDailyAndCountPixels() {
        deviceShieldAnalytics.suddenKillByVpnRevoked()
        deviceShieldAnalytics.suddenKillByVpnRevoked()

        verify(pixel).fire(DeviceShieldPixels.DS_KILLED_VPN_REVOKED_DAILY.pixelName)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DS_KILLED_VPN_REVOKED)
        verify(pixel, times(2)).fire(DeviceShieldPixels.DS_KILLED)
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenTrackersBlockedThenFireCountPixel() {
        deviceShieldAnalytics.trackerBlocked()
        deviceShieldAnalytics.trackerBlocked()

        verify(pixel, times(2)).fire(DeviceShieldPixels.DS_TRACKER_BLOCKED)
        verifyNoMoreInteractions(pixel)
    }

    private fun DeviceShieldPixels.notificationVariant(variant: Int): String {
        return String.format(Locale.US, pixelName, variant)
    }
}
