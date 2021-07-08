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
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceShieldPixels {
    /** This pixel is fired only once, no matter how many times we call this fun */
    fun deviceShieldInstalled()

    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun deviceShieldEnabledOnSearch()
    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun deviceShieldDisabledOnSearch()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun deviceShieldEnabledOnAppLaunch()
    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun deviceShieldDisabledOnAppLaunch()

    /**
     * This fun will fire two pixels
     * unique -> fire only once ever no matter how many times we call this fun
     * daily -> fire only once a day no matter how many times we call this fun
     */
    fun reportEnabled()
    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun reportDisabled()

    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun reportLastDayEnableCount(count: Int)
    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun reportLastDayDisableCount(count: Int)

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
    fun enableFromOnboarding()
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
    fun enableFromSummaryTrackerActivity()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun disableFromQuickSettingsTile()

    fun disableFromSummaryTrackerActivity()

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
    fun didShowSummaryTrackerActivity()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun didShowDetailedTrackerActivity()

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

    /** Will fire a pixel on every call */
    fun privacyReportArticleDisplayed()

    fun vpnTunInterfaceIsDown()

    /** Will fire when the process has gone to the expendable list */
    fun vpnProcessExpendableLow(payload: Map<String, String>)

    /** Will fire when the process is in the middle expendable list */
    fun vpnProcessExpendableModerate(payload: Map<String, String>)

    /** Will fire when the process is near to be killed is no memory is found */
    fun vpnProcessExpendableComplete(payload: Map<String, String>)

    /** Will fire when the system is running moderately low on memory */
    fun vpnMemoryRunningModerate(payload: Map<String, String>)

    /** Will fire when the system is running low on memory */
    fun vpnMemoryRunningLow(payload: Map<String, String>)

    /** Will fire when the system is running extremely low on memory */
    fun vpnMemoryRunningCritical(payload: Map<String, String>)
}

@ContributesBinding(AppObjectGraph::class)
@Singleton
class RealDeviceShieldPixels @Inject constructor(
    private val context: Context,
    private val pixel: Pixel
) : DeviceShieldPixels {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(DS_PIXELS_PREF_FILE, Context.MODE_MULTI_PROCESS)

    override fun deviceShieldInstalled() {
        tryToFireUniquePixel(DeviceShieldPixelNames.DS_INSTALLED_UNIQUE)
    }

    override fun deviceShieldEnabledOnSearch() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_ENABLE_UPON_SEARCH_DAILY)
    }

    override fun deviceShieldDisabledOnSearch() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_DISABLE_UPON_SEARCH_DAILY)
    }

    override fun deviceShieldEnabledOnAppLaunch() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_ENABLE_UPON_APP_LAUNCH_DAILY)
        firePixel(DeviceShieldPixelNames.DS_ENABLE_UPON_APP_LAUNCH)
    }

    override fun deviceShieldDisabledOnAppLaunch() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_DISABLE_UPON_APP_LAUNCH_DAILY)
        firePixel(DeviceShieldPixelNames.DS_DISABLE_UPON_APP_LAUNCH)
    }

    override fun reportEnabled() {
        tryToFireUniquePixel(DeviceShieldPixelNames.DS_ENABLE_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_ENABLE_DAILY)
    }

    override fun reportDisabled() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_DISABLE_DAILY)
    }

    override fun reportLastDayEnableCount(count: Int) {
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_LAST_DAY_ENABLE_COUNT_DAILY, mapOf("count" to count.toString()))
    }

    override fun reportLastDayDisableCount(count: Int) {
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_LAST_DAY_DISABLE_COUNT_DAILY, mapOf("count" to count.toString()))
    }

    override fun enableFromReminderNotification() {
        tryToFireUniquePixel(DeviceShieldPixelNames.ENABLE_DS_FROM_REMINDER_NOTIFICATION_UNIQUE, tag = FIRST_ENABLE_ENTRY_POINT_TAG)
        tryToFireDailyPixel(DeviceShieldPixelNames.ENABLE_DS_FROM_REMINDER_NOTIFICATION_DAILY)
        firePixel(DeviceShieldPixelNames.ENABLE_DS_FROM_REMINDER_NOTIFICATION)
    }

    override fun enableFromOnboarding() {
        tryToFireUniquePixel(DeviceShieldPixelNames.ENABLE_DS_FROM_ONBOARDING_UNIQUE, tag = FIRST_ENABLE_ENTRY_POINT_TAG)
        tryToFireDailyPixel(DeviceShieldPixelNames.ENABLE_DS_FROM_ONBOARDING_DAILY)
        firePixel(DeviceShieldPixelNames.ENABLE_DS_FROM_ONBOARDING)
    }

    override fun enableFromQuickSettingsTile() {
        tryToFireUniquePixel(DeviceShieldPixelNames.ENABLE_DS_FROM_SETTINGS_TILE_UNIQUE, tag = FIRST_ENABLE_ENTRY_POINT_TAG)
        tryToFireDailyPixel(DeviceShieldPixelNames.ENABLE_DS_FROM_SETTINGS_TILE_DAILY)
        firePixel(DeviceShieldPixelNames.ENABLE_DS_FROM_SETTINGS_TILE)
    }

    override fun enableFromSummaryTrackerActivity() {
        tryToFireUniquePixel(DeviceShieldPixelNames.ENABLE_DS_FROM_SUMMARY_TRACKER_ACTIVITY_UNIQUE, tag = FIRST_ENABLE_ENTRY_POINT_TAG)
        tryToFireDailyPixel(DeviceShieldPixelNames.ENABLE_DS_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY)
        firePixel(DeviceShieldPixelNames.ENABLE_DS_FROM_SUMMARY_TRACKER_ACTIVITY)
    }

    override fun disableFromQuickSettingsTile() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DISABLE_DS_FROM_SETTINGS_TILE_DAILY)
        firePixel(DeviceShieldPixelNames.DISABLE_DS_FROM_SETTINGS_TILE)
    }

    override fun disableFromSummaryTrackerActivity() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DISABLE_DS_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY)
        firePixel(DeviceShieldPixelNames.DISABLE_DS_FROM_SUMMARY_TRACKER_ACTIVITY)
    }

    override fun didShowDailyNotification(variant: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixelNames.DID_SHOW_DAILY_NOTIFICATION.pixelName, variant)
        )
    }

    override fun didPressOnDailyNotification(variant: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixelNames.DID_PRESS_DAILY_NOTIFICATION.pixelName, variant)
        )
    }

    override fun didShowWeeklyNotification(variant: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixelNames.DID_SHOW_WEEKLY_NOTIFICATION.pixelName, variant)
        )
    }

    override fun didPressOnWeeklyNotification(variant: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixelNames.DID_PRESS_WEEKLY_NOTIFICATION.pixelName, variant)
        )
    }

    override fun didPressOngoingNotification() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DID_PRESS_ONGOING_NOTIFICATION_DAILY)
        firePixel(DeviceShieldPixelNames.DID_PRESS_ONGOING_NOTIFICATION)
    }

    override fun didShowReminderNotification() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DID_SHOW_REMINDER_NOTIFICATION_DAILY)
        firePixel(DeviceShieldPixelNames.DID_SHOW_REMINDER_NOTIFICATION)
    }

    override fun didPressReminderNotification() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DID_PRESS_REMINDER_NOTIFICATION_DAILY)
        firePixel(DeviceShieldPixelNames.DID_PRESS_REMINDER_NOTIFICATION)
    }

    override fun didShowNewTabSummary() {
        tryToFireUniquePixel(DeviceShieldPixelNames.DID_SHOW_NEW_TAB_SUMMARY_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.DID_SHOW_NEW_TAB_SUMMARY_DAILY)
        firePixel(DeviceShieldPixelNames.DID_SHOW_NEW_TAB_SUMMARY)
    }

    override fun didPressNewTabSummary() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DID_PRESS_NEW_TAB_SUMMARY_DAILY)
        firePixel(DeviceShieldPixelNames.DID_PRESS_NEW_TAB_SUMMARY)
    }

    override fun didShowSummaryTrackerActivity() {
        tryToFireUniquePixel(DeviceShieldPixelNames.DID_SHOW_SUMMARY_TRACKER_ACTIVITY_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.DID_SHOW_SUMMARY_TRACKER_ACTIVITY_DAILY)
        firePixel(DeviceShieldPixelNames.DID_SHOW_SUMMARY_TRACKER_ACTIVITY)
    }

    override fun didShowDetailedTrackerActivity() {
        tryToFireUniquePixel(DeviceShieldPixelNames.DID_SHOW_DETAILED_TRACKER_ACTIVITY_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.DID_SHOW_DETAILED_TRACKER_ACTIVITY_DAILY)
        firePixel(DeviceShieldPixelNames.DID_SHOW_DETAILED_TRACKER_ACTIVITY)
    }

    override fun startError() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_START_ERROR_DAILY)
        firePixel(DeviceShieldPixelNames.DS_START_ERROR)
    }

    override fun automaticRestart() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_AUTOMATIC_RESTART_DAILY)
        firePixel(DeviceShieldPixelNames.DS_AUTOMATIC_RESTART)
    }

    override fun suddenKillBySystem() {
        suddenKill()
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_KILLED_BY_SYSTEM_DAILY)
        firePixel(DeviceShieldPixelNames.DS_KILLED_BY_SYSTEM)
    }

    override fun suddenKillByVpnRevoked() {
        suddenKill()
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_KILLED_VPN_REVOKED_DAILY)
        firePixel(DeviceShieldPixelNames.DS_KILLED_VPN_REVOKED)
    }

    override fun trackerBlocked() {
        firePixel(DeviceShieldPixelNames.DS_TRACKER_BLOCKED)
    }

    override fun privacyReportArticleDisplayed() {
        firePixel(DeviceShieldPixelNames.DS_PRIVACY_REPORT_ARTICLE_SHOWED)
    }

    override fun vpnTunInterfaceIsDown() {
        tryToFireDailyPixel(DeviceShieldPixelNames.DS_TUN_INTERFACE_DOWN_DAILY)
        firePixel(DeviceShieldPixelNames.DS_TUN_INTERFACE_DOWN)
    }

    override fun vpnProcessExpendableLow(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.VPN_PROCESS_EXPENDABLE_LOW_DAILY, payload)
        firePixel(DeviceShieldPixelNames.VPN_PROCESS_EXPENDABLE_LOW, payload)
    }

    override fun vpnProcessExpendableModerate(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.VPN_PROCESS_EXPENDABLE_MODERATE_DAILY, payload)
        firePixel(DeviceShieldPixelNames.VPN_PROCESS_EXPENDABLE_MODERATE, payload)
    }

    override fun vpnProcessExpendableComplete(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.VPN_PROCESS_EXPENDABLE_COMPLETE_DAILY, payload)
        firePixel(DeviceShieldPixelNames.VPN_PROCESS_EXPENDABLE_COMPLETE, payload)
    }

    override fun vpnMemoryRunningLow(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.VPN_PROCESS_MEMORY_LOW_DAILY, payload)
        firePixel(DeviceShieldPixelNames.VPN_PROCESS_MEMORY_LOW, payload)
    }

    override fun vpnMemoryRunningModerate(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.VPN_PROCESS_MEMORY_MODERATE_DAILY, payload)
        firePixel(DeviceShieldPixelNames.VPN_PROCESS_MEMORY_MODERATE, payload)
    }

    override fun vpnMemoryRunningCritical(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.VPN_PROCESS_MEMORY_CRITICAL_DAILY, payload)
        firePixel(DeviceShieldPixelNames.VPN_PROCESS_MEMORY_CRITICAL, payload)
    }

    private fun suddenKill() {
        firePixel(DeviceShieldPixelNames.DS_KILLED)
    }

    private fun firePixel(p: DeviceShieldPixelNames, payload: Map<String, String> = emptyMap()) {
        pixel.fire(p, payload)
    }

    private fun tryToFireDailyPixel(pixel: DeviceShieldPixelNames, payload: Map<String, String> = emptyMap()) {
        tryToFireDailyPixel(pixel.pixelName, payload)
    }

    private fun tryToFireDailyPixel(pixelName: String, payload: Map<String, String> = emptyMap()) {
        val now = getUtcIsoLocalDate()
        val timestamp = preferences.getString(pixelName.appendTimestampSuffix(), null)

        // check if pixel was already sent in the current day
        if (timestamp == null || now > timestamp) {
            this.pixel.fire(pixelName, payload).also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
        }
    }

    private fun tryToFireUniquePixel(pixel: DeviceShieldPixelNames, tag: String? = null) {
        val didExecuteAlready = preferences.getBoolean(tag ?: pixel.pixelName, false)

        if (didExecuteAlready) return

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
        const val DS_PIXELS_PREF_FILE = "com.duckduckgo.mobile.android.device.shield.pixels"
    }
}
