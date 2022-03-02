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
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import dagger.SingleInstanceIn

interface DeviceShieldPixels {
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

    /** Will fire a pixel on every call */
    fun privacyReportArticleDisplayed()

    /**
     * Will fire a pixel on every call
     */
    fun privacyReportOnboardingFAQDisplayed()

    fun vpnEstablishTunInterfaceError()

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

    /** Will fire when the user restores to the default protection list */
    fun restoreDefaultProtectionList()

    /** Will fire when the user restores to the default protection list */
    fun launchAppTPFeedback()

    /** Will fire when the user submits the form that disables protection for an app */
    fun didSubmitManuallyDisableAppProtectionDialog()

    /** Will fire when the user skips the form that disables protection for an app */
    fun didSkipManuallyDisableAppProtectionDialog()

    /** Will fire when the user launches the report issues screen from the tracker activity */
    fun didSubmitReportIssuesFromTrackerActivity()

    /**
     * Will fire when the user reports an app breakage
     */
    fun sendAppBreakageReport(metadata: Map<String, String>)

    fun didShowReportBreakageAppList()

    fun didShowReportBreakageTextForm()

    fun didShowReportBreakageSingleChoiceForm()

    /**
     * Will fire when the user wants to disable the VPN
     */
    fun didShowDisableTrackingProtectionDialog()

    fun didChooseToDisableTrackingProtectionFromDialog()

    fun didChooseToDisableOneAppFromDialog()

    fun didChooseToCancelTrackingProtectionDialog()

    /**
     * Will fire when the user is already connected to a VPN and wants to enable AppTP
     */
    fun didShowVpnConflictDialog()

    fun didChooseToDismissVpnConflicDialog()

    fun didChooseToOpenSettingsFromVpnConflicDialog()

    fun didChooseToContinueFromVpnConflicDialog()

    /**
     * Will fire when the waitlist dialog is showed to the user
     */
    fun didShowWaitlistDialog()

    /**
     * Will fire when the user presses "notify me" CTA in the waitlist dialog
     */
    fun didPressWaitlistDialogNotifyMe()

    /**
     * Will fire when the user presses dismisses the waitlist dialog
     */
    fun didPressWaitlistDialogDismiss()

    /**
     * Will fire when user submits a health monitor report
     */
    fun sendHealthMonitorReport(healthMetrics: Map<String, String>)

    /**
     * Will send a first-in-day pixel for the given alertName
     */
    fun sendHealthMonitorAlert(alertName: String)

    /**
     * Will fire when the VPN receives a packet of unknown protocol
     */
    fun sendUnknownPacketProtocol(protocol: Int)

    /**
     * Will fire when the VPN detected bad health, restarted and fixex the bad health
     */
    fun badHealthResolvedByRestart(data: Map<String, String>)

    /**
     * Will fire when the VPN detected bad health but it resolved itself
     */
    fun badHealthResolvedItself(data: Map<String, String>)

    /**
     * Will fire when the VPN restarted as a result of a bad health mitigation
     */
    fun didRestartVpnOnBadHealth()

    /**
     * Will fire when the VPN Process is restarted as a result of bad health mitigation
     */
    fun didRestartVpnProcessOnBadHealth()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDeviceShieldPixels @Inject constructor(
    private val context: Context,
    private val pixel: Pixel
) : DeviceShieldPixels {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(DS_PIXELS_PREF_FILE, Context.MODE_MULTI_PROCESS)

    override fun deviceShieldEnabledOnSearch() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ENABLE_UPON_SEARCH_DAILY)
    }

    override fun deviceShieldDisabledOnSearch() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DISABLE_UPON_SEARCH_DAILY)
    }

    override fun deviceShieldEnabledOnAppLaunch() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ENABLE_UPON_APP_LAUNCH_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_ENABLE_UPON_APP_LAUNCH)
    }

    override fun deviceShieldDisabledOnAppLaunch() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DISABLE_UPON_APP_LAUNCH_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DISABLE_UPON_APP_LAUNCH)
    }

    override fun reportEnabled() {
        tryToFireUniquePixel(DeviceShieldPixelNames.ATP_ENABLE_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ENABLE_DAILY)
    }

    override fun reportDisabled() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DISABLE_DAILY)
    }

    override fun reportLastDayEnableCount(count: Int) {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_LAST_DAY_ENABLE_COUNT_DAILY, mapOf("count" to count.toString()))
    }

    override fun reportLastDayDisableCount(count: Int) {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_LAST_DAY_DISABLE_COUNT_DAILY, mapOf("count" to count.toString()))
    }

    override fun enableFromReminderNotification() {
        tryToFireUniquePixel(
            DeviceShieldPixelNames.ATP_ENABLE_FROM_REMINDER_NOTIFICATION_UNIQUE,
            tag = FIRST_ENABLE_ENTRY_POINT_TAG
        )
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_REMINDER_NOTIFICATION_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_REMINDER_NOTIFICATION)
    }

    override fun enableFromOnboarding() {
        tryToFireUniquePixel(
            DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING_UNIQUE,
            tag = FIRST_ENABLE_ENTRY_POINT_TAG
        )
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING)
    }

    override fun enableFromQuickSettingsTile() {
        tryToFireUniquePixel(
            DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE_UNIQUE,
            tag = FIRST_ENABLE_ENTRY_POINT_TAG
        )
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE)
    }

    override fun enableFromSummaryTrackerActivity() {
        tryToFireUniquePixel(
            DeviceShieldPixelNames.ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY_UNIQUE,
            tag = FIRST_ENABLE_ENTRY_POINT_TAG
        )
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY)
    }

    override fun disableFromQuickSettingsTile() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DISABLE_FROM_SETTINGS_TILE_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DISABLE_FROM_SETTINGS_TILE)
    }

    override fun disableFromSummaryTrackerActivity() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DISABLE_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DISABLE_FROM_SUMMARY_TRACKER_ACTIVITY)
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
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_START_ERROR_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_START_ERROR)
    }

    override fun automaticRestart() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_AUTOMATIC_RESTART_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_AUTOMATIC_RESTART)
    }

    override fun suddenKillBySystem() {
        suddenKill()
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_KILLED_BY_SYSTEM_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_KILLED_BY_SYSTEM)
    }

    override fun suddenKillByVpnRevoked() {
        suddenKill()
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_KILLED_VPN_REVOKED_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_KILLED_VPN_REVOKED)
    }

    override fun privacyReportArticleDisplayed() {
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_PRIVACY_REPORT_ARTICLE)
    }

    override fun privacyReportOnboardingFAQDisplayed() {
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_ONBOARDING_FAQ)
    }

    override fun vpnEstablishTunInterfaceError() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ESTABLISH_TUN_INTERFACE_ERROR_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_ESTABLISH_TUN_INTERFACE_ERROR)
    }

    override fun vpnProcessExpendableLow(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_PROCESS_EXPENDABLE_LOW_DAILY, payload)
    }

    override fun vpnProcessExpendableModerate(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_PROCESS_EXPENDABLE_MODERATE_DAILY, payload)
    }

    override fun vpnProcessExpendableComplete(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_PROCESS_EXPENDABLE_COMPLETE_DAILY, payload)
    }

    override fun vpnMemoryRunningLow(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_PROCESS_MEMORY_LOW_DAILY, payload)
    }

    override fun vpnMemoryRunningModerate(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_PROCESS_MEMORY_MODERATE_DAILY, payload)
    }

    override fun vpnMemoryRunningCritical(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_PROCESS_MEMORY_CRITICAL_DAILY, payload)
    }

    override fun restoreDefaultProtectionList() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_RESTORE_APP_PROTECTION_LIST_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_RESTORE_APP_PROTECTION_LIST)
    }

    override fun launchAppTPFeedback() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_LAUNCH_FEEDBACK_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_LAUNCH_FEEDBACK)
    }

    override fun didSubmitManuallyDisableAppProtectionDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SUBMIT_DISABLE_APP_PROTECTION_DIALOG_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_SUBMIT_DISABLE_APP_PROTECTION_DIALOG)
    }

    override fun didSkipManuallyDisableAppProtectionDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SKIP_DISABLE_APP_PROTECTION_DIALOG_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_SKIP_DISABLE_APP_PROTECTION_DIALOG)
    }

    override fun didSubmitReportIssuesFromTrackerActivity() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_REPORT_ISSUES_FROM_TRACKER_ACTIVITY_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_REPORT_ISSUES_FROM_TRACKER_ACTIVITY)
    }

    override fun sendAppBreakageReport(metadata: Map<String, String>) {
        firePixel(DeviceShieldPixelNames.ATP_APP_BREAKAGE_REPORT, metadata)
        tryToFireUniquePixel(DeviceShieldPixelNames.ATP_APP_BREAKAGE_REPORT_UNIQUE, payload = metadata)
    }

    override fun didShowReportBreakageAppList() {
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_REPORT_BREAKAGE_APP_LIST)
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_REPORT_BREAKAGE_APP_LIST_DAILY)
    }

    override fun didShowReportBreakageTextForm() {
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_REPORT_BREAKAGE_TEXT_FORM)
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_REPORT_BREAKAGE_TEXT_FORM_DAILY)
    }

    override fun didShowReportBreakageSingleChoiceForm() {
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_REPORT_BREAKAGE_SINGLE_CHOICE_FORM)
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_REPORT_BREAKAGE_SINGLE_CHOICE_FORM_DAILY)
    }

    override fun didShowDisableTrackingProtectionDialog() {
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_DISABLE_TRACKING_PROTECTION_DIALOG)
    }

    override fun didChooseToDisableTrackingProtectionFromDialog() {
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_DISABLE_TRACKING_PROTECTION_DIALOG)
    }

    override fun didChooseToDisableOneAppFromDialog() {
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_DISABLE_ONE_APP_PROTECTION_DIALOG)
    }

    override fun didChooseToCancelTrackingProtectionDialog() {
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_CANCEL_APP_PROTECTION_DIALOG)
    }

    override fun didShowVpnConflictDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_VPN_CONFLICT_DIALOG)
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_VPN_CONFLICT_DIALOG)
    }

    override fun didChooseToDismissVpnConflicDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_DISMISS_VPN_CONFLICT_DIALOG)
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_DISMISS_VPN_CONFLICT_DIALOG)
    }

    override fun didChooseToOpenSettingsFromVpnConflicDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_OPEN_SETTINGS_VPN_CONFLICT_DIALOG)
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_OPEN_SETTINGS_VPN_CONFLICT_DIALOG)
    }

    override fun didChooseToContinueFromVpnConflicDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_OPEN_SETTINGS_VPN_CONFLICT_DIALOG)
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_OPEN_SETTINGS_VPN_CONFLICT_DIALOG)
    }

    override fun didShowWaitlistDialog() {
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_WAITLIST_DIALOG)
    }

    override fun didPressWaitlistDialogNotifyMe() {
        firePixel(DeviceShieldPixelNames.ATP_DID_PRESS_WAITLIST_DIALOG_NOTIFY_ME)
    }

    override fun didPressWaitlistDialogDismiss() {
        firePixel(DeviceShieldPixelNames.ATP_DID_PRESS_WAITLIST_DIALOG_DISMISS)
    }

    override fun sendHealthMonitorReport(healthMetrics: Map<String, String>) {
        firePixel(DeviceShieldPixelNames.ATP_APP_HEALTH_MONITOR_REPORT, healthMetrics)
    }

    override fun sendHealthMonitorAlert(alertName: String) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixelNames.ATP_APP_HEALTH_ALERT_DAILY.pixelName, alertName)
        )
    }

    override fun sendUnknownPacketProtocol(protocol: Int) {
        tryToFireDailyPixel(String.format(Locale.US, DeviceShieldPixelNames.ATP_RECEIVED_UNKNOWN_PACKET_PROTOCOL_DAILY.pixelName, protocol))
        firePixel(String.format(Locale.US, DeviceShieldPixelNames.ATP_RECEIVED_UNKNOWN_PACKET_PROTOCOL.pixelName, protocol))
    }

    override fun badHealthResolvedByRestart(data: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_APP_BAD_HEALTH_RESOLVED_BY_RESTART_DAILY, data)
        firePixel(DeviceShieldPixelNames.ATP_APP_BAD_HEALTH_RESOLVED_BY_RESTART, data)
    }

    override fun badHealthResolvedItself(data: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_APP_BAD_HEALTH_RESOLVED_ITSELF_DAILY, data)
        firePixel(DeviceShieldPixelNames.ATP_APP_BAD_HEALTH_RESOLVED_ITSELF, data)
    }

    override fun didRestartVpnOnBadHealth() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_RESTART_VPN_ON_BAD_HEALTH_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_RESTART_VPN_ON_BAD_HEALTH)
    }

    override fun didRestartVpnProcessOnBadHealth() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_RESTART_VPN_PROCESS_ON_BAD_HEALTH_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_RESTART_VPN_PROCESS_ON_BAD_HEALTH)
    }

    private fun suddenKill() {
        firePixel(DeviceShieldPixelNames.ATP_KILLED)
    }

    private fun firePixel(
        p: DeviceShieldPixelNames,
        payload: Map<String, String> = emptyMap()
    ) {
        firePixel(p.pixelName, payload)
    }

    private fun firePixel(
        pixelName: String,
        payload: Map<String, String> = emptyMap()
    ) {
        pixel.fire(pixelName, payload)
    }

    private fun tryToFireDailyPixel(
        pixel: DeviceShieldPixelNames,
        payload: Map<String, String> = emptyMap()
    ) {
        tryToFireDailyPixel(pixel.pixelName, payload)
    }

    private fun tryToFireDailyPixel(
        pixelName: String,
        payload: Map<String, String> = emptyMap()
    ) {
        val now = getUtcIsoLocalDate()
        val timestamp = preferences.getString(pixelName.appendTimestampSuffix(), null)

        // check if pixel was already sent in the current day
        if (timestamp == null || now > timestamp) {
            this.pixel.fire(pixelName, payload)
                .also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
        }
    }

    private fun tryToFireUniquePixel(
        pixel: DeviceShieldPixelNames,
        tag: String? = null,
        payload: Map<String, String> = emptyMap()
    ) {
        val didExecuteAlready = preferences.getBoolean(tag ?: pixel.pixelName, false)

        if (didExecuteAlready) return

        this.pixel.fire(pixel, payload).also { preferences.edit { putBoolean(tag ?: pixel.pixelName, true) } }
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
