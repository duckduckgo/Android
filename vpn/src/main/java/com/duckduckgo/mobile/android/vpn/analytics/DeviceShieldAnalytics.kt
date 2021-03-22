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

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesBinding
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceShieldAnalytics {
    /** This pixel is fired only once, no matter how many times we call this fun */
    fun deviceShieldInstalled()

    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun deviceShieldEnabledOnSearch()
    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun deviceShieldDisabledOnSearch()
    /**
     * This fun will fire two pixels
     * unique -> fire only once ever no matter how many times we call this fun
     * daily -> fire only once a day no matter how many times we call this fun
     */
    fun reportEnabled()
    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun reportDisabled()

    /**
     * This fun will fire three pixels
     * unique -> fire only once ever no matter how many times we call this fun
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun enableFromNewTab()
    /**
     * This fun will fire three pixels
     * unique -> fire only once ever no matter how many times we call this fun
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun enableFromReminderNotification()
    /**
     * This fun will fire three pixels
     * unique -> fire only once ever no matter how many times we call this fun
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun enableFromSettings()
    /**
     * This fun will fire three pixels
     * unique -> fire only once ever no matter how many times we call this fun
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun enableFromQuickSettingsTile()

    /**
     * This fun will fire three pixels
     * unique -> fire only once ever no matter how many times we call this fun
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun enableFromPrivacyReport()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun disableFromSettings()
    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun disableFromQuickSettingsTile()

    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun didShowDailyNotification(variant: Int)
    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun didPressOnDailyNotification(variant: Int)
    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun didShowWeeklyNotification(variant: Int)
    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun didPressOnWeeklyNotification(variant: Int)
    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun didPressOngoingNotification()
    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun didShowReminderNotification()
    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun didPressReminderNotification()

    /**
     * This fun will fire three pixels
     * unique -> fire only once ever no matter how many times we call this fun
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun didShowNewTabSummary()
    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun didPressNewTabSummary()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun didShowPrivacyReport()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun startError()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun automaticRestart()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun suddenKillBySystem()
    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun suddenKillByVpnRevoked()

    /** This fun will fire a pixel on every call */
    fun trackerBlocked()
}

@ContributesBinding(AppObjectGraph::class)
@Singleton
class RealDeviceShieldAnalytics @Inject constructor(
    private val context: Context,
    private val pixel: Pixel
) : DeviceShieldAnalytics {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(DS_ANALYTICS_PREF_FILE, Context.MODE_PRIVATE)

    override fun deviceShieldInstalled() {
        tryToFireUniquePixel(DeviceShieldPixels.DS_INSTALLED_UNIQUE)
    }

    override fun deviceShieldEnabledOnSearch() {
        tryToFireDailyPixel(DeviceShieldPixels.DS_ENABLE_UPON_SEARCH_DAILY)
    }

    override fun deviceShieldDisabledOnSearch() {
        tryToFireDailyPixel(DeviceShieldPixels.DS_DISABLE_UPON_SEARCH_DAILY)
    }

    override fun reportEnabled() {
        tryToFireUniquePixel(DeviceShieldPixels.DS_ENABLE_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixels.DS_ENABLE_DAILY)
    }

    override fun reportDisabled() {
        tryToFireDailyPixel(DeviceShieldPixels.DS_DISABLE_DAILY)
    }

    override fun enableFromNewTab() {
        tryToFireUniquePixel(DeviceShieldPixels.ENABLE_DS_FROM_NEW_TAB_UNIQUE, tag = FIRST_ENABLE_ENTRY_POINT_TAG)
        tryToFireDailyPixel(DeviceShieldPixels.ENABLE_DS_FROM_NEW_TAB_DAILY)
        firePixel(DeviceShieldPixels.ENABLE_DS_FROM_NEW_TAB)
    }

    override fun enableFromReminderNotification() {
        tryToFireUniquePixel(DeviceShieldPixels.ENABLE_DS_FROM_REMINDER_NOTIFICATION_UNIQUE, tag = FIRST_ENABLE_ENTRY_POINT_TAG)
        tryToFireDailyPixel(DeviceShieldPixels.ENABLE_DS_FROM_REMINDER_NOTIFICATION_DAILY)
        firePixel(DeviceShieldPixels.ENABLE_DS_FROM_REMINDER_NOTIFICATION)
    }

    override fun enableFromSettings() {
        tryToFireUniquePixel(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS_UNIQUE, tag = FIRST_ENABLE_ENTRY_POINT_TAG)
        tryToFireDailyPixel(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS_DAILY)
        firePixel(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS)
    }

    override fun enableFromQuickSettingsTile() {
        tryToFireUniquePixel(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS_TILE_UNIQUE, tag = FIRST_ENABLE_ENTRY_POINT_TAG)
        tryToFireDailyPixel(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS_TILE_DAILY)
        firePixel(DeviceShieldPixels.ENABLE_DS_FROM_SETTINGS_TILE)
    }

    override fun enableFromPrivacyReport() {
        tryToFireUniquePixel(DeviceShieldPixels.ENABLE_DS_FROM_PRIVACY_REPORT_UNIQUE, tag = FIRST_ENABLE_ENTRY_POINT_TAG)
        tryToFireDailyPixel(DeviceShieldPixels.ENABLE_DS_FROM_PRIVACY_REPORT_DAILY)
        firePixel(DeviceShieldPixels.ENABLE_DS_FROM_PRIVACY_REPORT)
    }

    override fun disableFromSettings() {
        tryToFireDailyPixel(DeviceShieldPixels.DISABLE_DS_FROM_SETTINGS_DAILY)
        firePixel(DeviceShieldPixels.DISABLE_DS_FROM_SETTINGS)
    }

    override fun disableFromQuickSettingsTile() {
        tryToFireDailyPixel(DeviceShieldPixels.DISABLE_DS_FROM_SETTINGS_TILE_DAILY)
        firePixel(DeviceShieldPixels.DISABLE_DS_FROM_SETTINGS_TILE)
    }

    override fun didShowDailyNotification(variant: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixels.DID_SHOW_DAILY_NOTIFICATION.pixelName, variant)
        )
    }

    override fun didPressOnDailyNotification(variant: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixels.DID_PRESS_DAILY_NOTIFICATION.pixelName, variant)
        )
    }

    override fun didShowWeeklyNotification(variant: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixels.DID_SHOW_WEEKLY_NOTIFICATION.pixelName, variant)
        )
    }

    override fun didPressOnWeeklyNotification(variant: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixels.DID_PRESS_WEEKLY_NOTIFICATION.pixelName, variant)
        )
    }

    override fun didPressOngoingNotification() {
        tryToFireDailyPixel(DeviceShieldPixels.DID_PRESS_ONGOING_NOTIFICATION_DAILY)
        firePixel(DeviceShieldPixels.DID_PRESS_ONGOING_NOTIFICATION)
    }

    override fun didShowReminderNotification() {
        tryToFireDailyPixel(DeviceShieldPixels.DID_SHOW_REMINDER_NOTIFICATION_DAILY)
        firePixel(DeviceShieldPixels.DID_SHOW_REMINDER_NOTIFICATION)
    }

    override fun didPressReminderNotification() {
        tryToFireDailyPixel(DeviceShieldPixels.DID_PRESS_REMINDER_NOTIFICATION_DAILY)
        firePixel(DeviceShieldPixels.DID_PRESS_REMINDER_NOTIFICATION)
    }

    override fun didShowNewTabSummary() {
        tryToFireUniquePixel(DeviceShieldPixels.DID_SHOW_NEW_TAB_SUMMARY_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixels.DID_SHOW_NEW_TAB_SUMMARY_DAILY)
        firePixel(DeviceShieldPixels.DID_SHOW_NEW_TAB_SUMMARY)
    }

    override fun didPressNewTabSummary() {
        tryToFireDailyPixel(DeviceShieldPixels.DID_PRESS_NEW_TAB_SUMMARY_DAILY)
        firePixel(DeviceShieldPixels.DID_PRESS_NEW_TAB_SUMMARY)
    }

    override fun didShowPrivacyReport() {
        tryToFireUniquePixel(DeviceShieldPixels.DID_SHOW_PRIVACY_REPORT_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixels.DID_SHOW_PRIVACY_REPORT_DAILY)
        firePixel(DeviceShieldPixels.DID_SHOW_PRIVACY_REPORT)
    }

    override fun startError() {
        tryToFireDailyPixel(DeviceShieldPixels.DS_START_ERROR_DAILY)
        firePixel(DeviceShieldPixels.DS_START_ERROR)
    }

    override fun automaticRestart() {
        tryToFireDailyPixel(DeviceShieldPixels.DS_AUTOMATIC_RESTART_DAILY)
        firePixel(DeviceShieldPixels.DS_AUTOMATIC_RESTART)
    }

    override fun suddenKillBySystem() {
        suddenKill()
        tryToFireDailyPixel(DeviceShieldPixels.DS_KILLED_BY_SYSTEM_DAILY)
        firePixel(DeviceShieldPixels.DS_KILLED_BY_SYSTEM)
    }

    override fun suddenKillByVpnRevoked() {
        suddenKill()
        tryToFireDailyPixel(DeviceShieldPixels.DS_KILLED_VPN_REVOKED_DAILY)
        firePixel(DeviceShieldPixels.DS_KILLED_VPN_REVOKED)
    }

    override fun trackerBlocked() {
        firePixel(DeviceShieldPixels.DS_TRACKER_BLOCKED)
    }

    private fun suddenKill() {
        firePixel(DeviceShieldPixels.DS_KILLED)
    }

    private fun firePixel(p: DeviceShieldPixels) {
        Timber.v("Device shield pixel ${p.pixelName} fired")
        pixel.fire(p)
    }

    private fun tryToFireDailyPixel(pixel: DeviceShieldPixels) {
        tryToFireDailyPixel(pixel.pixelName)
    }

    private fun tryToFireDailyPixel(pixelName: String) {
        val now = getUtcIsoLocalDate()
        val timestamp = preferences.getString(pixelName.appendTimestampSuffix(), null)

        // check if pixel was already sent in the current day
        if (timestamp == null || now > timestamp) {
            Timber.v("Device shield daily pixel $pixelName fired")
            this.pixel.fire(pixelName).also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
        }
    }

    private fun tryToFireUniquePixel(pixel: DeviceShieldPixels, tag: String? = null) {
        val didExecuteAlready = preferences.getBoolean(tag ?: pixel.pixelName, false)

        if (didExecuteAlready) return

        Timber.v("Device shield unique pixel ${pixel.pixelName} fired")
        this.pixel.fire(pixel).also { preferences.edit { putBoolean(tag ?: pixel.pixelName, true) } }
    }

    private fun String.appendTimestampSuffix(): String {
        return "${this}_timestamp"
    }

    private fun getUtcIsoLocalDate(): String {
        // returns YYYY-MM-dd
        return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    companion object {
        private const val FIRST_ENABLE_ENTRY_POINT_TAG = "FIRST_ENABLE_ENTRY_POINT_TAG"
        @VisibleForTesting
        const val DS_ANALYTICS_PREF_FILE = "com.duckduckgo.mobile.android.device.shield.analytics"
    }
}
