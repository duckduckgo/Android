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

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.feature.AppTpTDSPixelsPlugin
import com.duckduckgo.mobile.android.vpn.feature.didFailToDownloadTDS
import com.duckduckgo.mobile.android.vpn.feature.getDisabledProtectionForApp
import com.duckduckgo.mobile.android.vpn.feature.getProtectionDisabledAppFromAll
import com.duckduckgo.mobile.android.vpn.feature.getProtectionDisabledAppFromDetail
import com.duckduckgo.mobile.android.vpn.feature.getSelectedDisableAppProtection
import com.duckduckgo.mobile.android.vpn.feature.getSelectedDisableProtection
import com.duckduckgo.mobile.android.vpn.feature.getSelectedRemoveAppTP
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

    /**
     * Will fire when the there's an error establishing the TUN interface
     */
    fun vpnEstablishTunInterfaceError()

    /**
     * Will fire when the there's an error establishing the null TUN interface
     */
    fun vpnEstablishNullTunInterfaceError()

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

    fun didChooseToDismissVpnConflictDialog()

    fun didChooseToOpenSettingsFromVpnConflictDialog()

    fun didChooseToContinueFromVpnConflictDialog()

    /**
     * Will send CPU usage alert
     */
    fun sendCPUUsageAlert(cpuThresholdPassed: Int)

    /** Will fire when Beta instructions CTA is pressed */

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun didShowExclusionListActivity()

    /**
     * Will fire when the user wants to open the Exclusion List Activity from the Manage Recent Apps Protection Screen
     */
    fun didOpenExclusionListActivityFromManageAppsProtectionScreen()

    /**
     * Will fire when the user opens the Company Trackers Screen
     */
    fun didOpenCompanyTrackersScreen()

    /** Will fire when the user launches the Recent App Settings Screen */
    fun didOpenManageRecentAppSettings()

    fun reportLoopbackDnsError()
    fun reportAnylocalDnsError()
    fun reportGeneralDnsError()

    fun reportBlocklistStats(payload: Map<String, String>)

    /**
     * Will fire when the user is interacting with the Promote Always On Dialog
     */
    fun didShowPromoteAlwaysOnDialog()

    fun didChooseToOpenSettingsFromPromoteAlwaysOnDialog()

    /**
     * Will fire when the user wants to remove the VPN feature all together
     */
    fun didShowRemoveTrackingProtectionFeatureDialog()

    fun didChooseToRemoveTrackingProtectionFeature()

    fun didChooseToCancelRemoveTrakcingProtectionDialog()

    /** Will fire when the user enables protection for a specific app from the detail screen */
    fun didEnableAppProtectionFromDetail()

    /** Will fire when the user disables  protection for a specific app from the detail screen */
    fun didDisableAppProtectionFromDetail()

    /** Will fire when the user enables protection for a specific app from the apps protection screen */
    fun didEnableAppProtectionFromApps()

    /** Will fire when the user disables  protection for a specific app from the apps protection screen */
    fun didDisableAppProtectionFromApps()

    fun reportVpnConnectivityError()
    fun reportDeviceConnectivityError()

    /** Will fire when the user has VPN always-on setting enabled */
    fun reportAlwaysOnEnabledDaily()

    /** Will fire when the user has VPN always-on lockdown setting enabled */
    fun reportAlwaysOnLockdownEnabledDaily()

    fun reportUnprotectedAppsBucket(bucketSize: Int)

    fun didPressOnAppTpEnabledCtaButton()

    fun reportErrorCreatingVpnNetworkStack()

    fun reportTunnelThreadStopTimeout()

    fun reportTunnelThreadAbnormalCrash()

    fun reportVpnAlwaysOnTriggered()

    fun notifyStartFailed()

    fun reportTLSParsingError(errorCode: Int)

    fun reportVpnSnoozedStarted()
    fun reportVpnSnoozedEnded()

    fun reportMotoGFix()

    fun reportVpnStartAttempt()

    fun reportVpnStartAttemptSuccess()

    // New Tab Engagement pixels https://app.asana.com/0/72649045549333/1207667088727866/f
    fun reportNewTabSectionToggled(enabled: Boolean)

    fun reportPproUpsellBannerShown()
    fun reportPproUpsellBannerDismissed()
    fun reportPproUpsellBannerLinkClicked()

    fun reportPproUpsellDisabledInfoShown()
    fun reportPproUpsellDisabledInfoLinkClicked()

    fun reportPproUpsellRevokedInfoShown()
    fun reportPproUpsellRevokedInfoLinkClicked()

    /** Fires when the AppTP experiment blocklist fails to download. */
    fun appTPBlocklistExperimentDownloadFailure(
        statusCode: Int,
        experimentName: String,
        experimentCohort: String,
    )
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDeviceShieldPixels @Inject constructor(
    private val pixel: Pixel,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val appTpTDSPixelsPlugin: AppTpTDSPixelsPlugin,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : DeviceShieldPixels {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            DS_PIXELS_PREF_FILE,
            multiprocess = true,
            migrate = true,
        )
    }

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
        tryToFireMonthlyPixel(DeviceShieldPixelNames.ATP_ENABLE_MONTHLY)
    }

    override fun reportDisabled() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DISABLE_DAILY)
    }

    override fun enableFromReminderNotification() {
        tryToFireUniquePixel(
            DeviceShieldPixelNames.ATP_ENABLE_FROM_REMINDER_NOTIFICATION_UNIQUE,
            tag = FIRST_ENABLE_ENTRY_POINT_TAG,
        )
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_REMINDER_NOTIFICATION_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_REMINDER_NOTIFICATION)
    }

    override fun enableFromOnboarding() {
        tryToFireUniquePixel(
            DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING_UNIQUE,
            tag = FIRST_ENABLE_ENTRY_POINT_TAG,
        )
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_ONBOARDING)
    }

    override fun enableFromQuickSettingsTile() {
        tryToFireUniquePixel(
            DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE_UNIQUE,
            tag = FIRST_ENABLE_ENTRY_POINT_TAG,
        )
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_ENABLE_FROM_SETTINGS_TILE)
    }

    override fun enableFromSummaryTrackerActivity() {
        tryToFireUniquePixel(
            DeviceShieldPixelNames.ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY_UNIQUE,
            tag = FIRST_ENABLE_ENTRY_POINT_TAG,
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
            String.format(Locale.US, DeviceShieldPixelNames.DID_SHOW_DAILY_NOTIFICATION.pixelName, variant),
        )
    }

    override fun didPressOnDailyNotification(variant: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixelNames.DID_PRESS_DAILY_NOTIFICATION.pixelName, variant),
        )
    }

    override fun didShowWeeklyNotification(variant: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixelNames.DID_SHOW_WEEKLY_NOTIFICATION.pixelName, variant),
        )
    }

    override fun didPressOnWeeklyNotification(variant: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixelNames.DID_PRESS_WEEKLY_NOTIFICATION.pixelName, variant),
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
        firePixel(DeviceShieldPixelNames.VPN_START_ATTEMPT_FAILURE)
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
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_PRIVACY_REPORT_ARTICLE_DAILY)
    }

    override fun privacyReportOnboardingFAQDisplayed() {
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_ONBOARDING_FAQ)
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_ONBOARDING_FAQ_DAILY)
    }

    override fun vpnEstablishTunInterfaceError() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ESTABLISH_TUN_INTERFACE_ERROR_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_ESTABLISH_TUN_INTERFACE_ERROR)
    }

    override fun vpnEstablishNullTunInterfaceError() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_ESTABLISH_NULL_TUN_INTERFACE_ERROR_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_ESTABLISH_NULL_TUN_INTERFACE_ERROR)
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
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_APP_BREAKAGE_REPORT_DAILY, metadata)
        tryToFireUniquePixel(DeviceShieldPixelNames.ATP_APP_BREAKAGE_REPORT_UNIQUE, payload = metadata)
    }

    override fun didShowReportBreakageAppList() {
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_REPORT_BREAKAGE_APP_LIST)
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_REPORT_BREAKAGE_APP_LIST_DAILY)
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
        appCoroutineScope.launch(dispatcherProvider.io()) {
            appTpTDSPixelsPlugin.getSelectedDisableProtection()?.getPixelDefinitions()?.forEach {
                firePixel(it.pixelName, it.params)
            }
        }
    }

    override fun didChooseToDisableOneAppFromDialog() {
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_DISABLE_ONE_APP_PROTECTION_DIALOG)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            appTpTDSPixelsPlugin.getSelectedDisableAppProtection()?.getPixelDefinitions()?.forEach {
                firePixel(it.pixelName, it.params)
            }
        }
    }

    override fun didChooseToCancelTrackingProtectionDialog() {
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_CANCEL_APP_PROTECTION_DIALOG)
    }

    override fun didShowVpnConflictDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_VPN_CONFLICT_DIALOG)
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_VPN_CONFLICT_DIALOG)
    }

    override fun didChooseToDismissVpnConflictDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_DISMISS_VPN_CONFLICT_DIALOG_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_DISMISS_VPN_CONFLICT_DIALOG)
    }

    override fun didChooseToOpenSettingsFromVpnConflictDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_OPEN_SETTINGS_VPN_CONFLICT_DIALOG_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_OPEN_SETTINGS_VPN_CONFLICT_DIALOG)
    }

    override fun didChooseToContinueFromVpnConflictDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_CONTINUE_VPN_CONFLICT_DIALOG_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_CONTINUE_VPN_CONFLICT_DIALOG)
    }

    override fun sendCPUUsageAlert(cpuThresholdPassed: Int) {
        firePixel(String.format(DeviceShieldPixelNames.ATP_APP_CPU_MONITOR_REPORT.pixelName, cpuThresholdPassed))
    }

    override fun didShowExclusionListActivity() {
        tryToFireUniquePixel(DeviceShieldPixelNames.ATP_DID_SHOW_EXCLUSION_LIST_ACTIVITY_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_EXCLUSION_LIST_ACTIVITY_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_EXCLUSION_LIST_ACTIVITY)
    }

    override fun didOpenExclusionListActivityFromManageAppsProtectionScreen() {
        tryToFireUniquePixel(
            DeviceShieldPixelNames.ATP_DID_OPEN_EXCLUSION_LIST_ACTIVITY_FROM_MANAGE_APPS_PROTECTION_UNIQUE,
            tag = FIRST_OPEN_ENTRY_POINT_TAG,
        )
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_OPEN_EXCLUSION_LIST_ACTIVITY_FROM_MANAGE_APPS_PROTECTION_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_OPEN_EXCLUSION_LIST_ACTIVITY_FROM_MANAGE_APPS_PROTECTION)
    }

    override fun didOpenCompanyTrackersScreen() {
        tryToFireUniquePixel(DeviceShieldPixelNames.ATP_DID_SHOW_COMPANY_TRACKERS_ACTIVITY_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_COMPANY_TRACKERS_ACTIVITY_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_COMPANY_TRACKERS_ACTIVITY)
    }

    override fun didOpenManageRecentAppSettings() {
        tryToFireUniquePixel(DeviceShieldPixelNames.ATP_DID_SHOW_MANAGE_RECENT_APP_SETTINGS_ACTIVITY_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_MANAGE_RECENT_APP_SETTINGS_ACTIVITY_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_MANAGE_RECENT_APP_SETTINGS_ACTIVITY)
    }

    override fun reportLoopbackDnsError() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_REPORT_LOOPBACK_DNS_SET_ERROR_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_REPORT_LOOPBACK_DNS_SET_ERROR)
    }

    override fun reportAnylocalDnsError() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_REPORT_ANY_LOCAL_ADDR_DNS_SET_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_REPORT_ANY_LOCAL_ADDR_DNS_SET_ERROR)
    }

    override fun reportGeneralDnsError() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_REPORT_DNS_SET_ERROR_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_REPORT_DNS_SET_ERROR)
    }

    override fun reportBlocklistStats(payload: Map<String, String>) {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_REPORT_BLOCKLIST_STATS_DAILY, payload)
    }

    override fun didEnableAppProtectionFromDetail() {
        firePixel(DeviceShieldPixelNames.ATP_DID_ENABLE_APP_PROTECTION_FROM_DETAIL)
    }

    override fun didDisableAppProtectionFromDetail() {
        firePixel(DeviceShieldPixelNames.ATP_DID_DISABLE_APP_PROTECTION_FROM_DETAIL)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            appTpTDSPixelsPlugin.getProtectionDisabledAppFromDetail()?.getPixelDefinitions()?.forEach {
                firePixel(it.pixelName, it.params)
            }
            appTpTDSPixelsPlugin.getDisabledProtectionForApp()?.getPixelDefinitions()?.forEach {
                firePixel(it.pixelName, it.params)
            }
        }
    }

    override fun didEnableAppProtectionFromApps() {
        firePixel(DeviceShieldPixelNames.ATP_DID_ENABLE_APP_PROTECTION_FROM_ALL)
    }

    override fun didDisableAppProtectionFromApps() {
        firePixel(DeviceShieldPixelNames.ATP_DID_DISABLE_APP_PROTECTION_FROM_ALL)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            appTpTDSPixelsPlugin.getProtectionDisabledAppFromAll()?.getPixelDefinitions()?.forEach {
                firePixel(it.pixelName, it.params)
            }
            appTpTDSPixelsPlugin.getDisabledProtectionForApp()?.getPixelDefinitions()?.forEach {
                firePixel(it.pixelName, it.params)
            }
        }
    }

    override fun didShowRemoveTrackingProtectionFeatureDialog() {
        tryToFireUniquePixel(DeviceShieldPixelNames.ATP_DID_SHOW_REMOVE_TRACKING_PROTECTION_FEATURE_DIALOG_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_REMOVE_TRACKING_PROTECTION_FEATURE_DIALOG_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_REMOVE_TRACKING_PROTECTION_FEATURE_DIALOG)
    }

    override fun didChooseToRemoveTrackingProtectionFeature() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_REMOVE_TRACKING_PROTECTION_DIALOG_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_REMOVE_TRACKING_PROTECTION_DIALOG)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            appTpTDSPixelsPlugin.getSelectedRemoveAppTP()?.getPixelDefinitions()?.forEach {
                firePixel(it.pixelName, it.params)
            }
        }
    }

    override fun didChooseToCancelRemoveTrakcingProtectionDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_CANCEL_TRACKING_PROTECTION_DIALOG_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_CANCEL_TRACKING_PROTECTION_DIALOG)
    }

    override fun didShowPromoteAlwaysOnDialog() {
        tryToFireUniquePixel(DeviceShieldPixelNames.ATP_DID_SHOW_PROMOTE_ALWAYS_ON_DIALOG_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_SHOW_PROMOTE_ALWAYS_ON_DIALOG_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_SHOW_PROMOTE_ALWAYS_ON_DIALOG)
    }

    override fun didChooseToOpenSettingsFromPromoteAlwaysOnDialog() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_OPEN_SETTINGS_PROMOTE_ALWAYS_ON_DIALOG_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_DID_CHOOSE_OPEN_SETTINGS_PROMOTE_ALWAYS_ON_DIALOG)
    }

    override fun reportVpnConnectivityError() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_REPORT_VPN_CONNECTIVITY_ERROR_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_REPORT_VPN_CONNECTIVITY_ERROR)
    }

    override fun reportDeviceConnectivityError() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_REPORT_DEVICE_CONNECTIVITY_ERROR_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_REPORT_DEVICE_CONNECTIVITY_ERROR)
    }

    override fun reportVpnSnoozedStarted() {
        tryToFireDailyPixel(DeviceShieldPixelNames.VPN_SNOOZE_STARTED_DAILY)
        firePixel(DeviceShieldPixelNames.VPN_SNOOZE_STARTED)
    }

    override fun reportVpnSnoozedEnded() {
        tryToFireDailyPixel(DeviceShieldPixelNames.VPN_SNOOZE_ENDED_DAILY)
        firePixel(DeviceShieldPixelNames.VPN_SNOOZE_ENDED)
    }

    override fun reportMotoGFix() {
        tryToFireDailyPixel(DeviceShieldPixelNames.VPN_MOTO_G_FIX_DAILY)
    }

    override fun reportVpnStartAttempt() {
        firePixel(DeviceShieldPixelNames.VPN_START_ATTEMPT)
    }

    override fun reportVpnStartAttemptSuccess() {
        firePixel(DeviceShieldPixelNames.VPN_START_ATTEMPT_SUCCESS)
    }

    private fun suddenKill() {
        firePixel(DeviceShieldPixelNames.ATP_KILLED)
    }

    override fun reportAlwaysOnEnabledDaily() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_REPORT_ALWAYS_ON_ENABLED_DAILY)
    }

    override fun reportAlwaysOnLockdownEnabledDaily() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_REPORT_ALWAYS_ON_LOCKDOWN_ENABLED_DAILY)
    }

    override fun reportUnprotectedAppsBucket(bucketSize: Int) {
        tryToFireDailyPixel(
            String.format(Locale.US, DeviceShieldPixelNames.ATP_REPORT_UNPROTECTED_APPS_BUCKET_DAILY.pixelName, bucketSize),
        )
        firePixel(String.format(Locale.US, DeviceShieldPixelNames.ATP_REPORT_UNPROTECTED_APPS_BUCKET.pixelName, bucketSize))
    }

    override fun didPressOnAppTpEnabledCtaButton() {
        firePixel(DeviceShieldPixelNames.ATP_DID_PRESS_APPTP_ENABLED_CTA_BUTTON)
    }

    override fun reportErrorCreatingVpnNetworkStack() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_REPORT_VPN_NETWORK_STACK_CREATE_ERROR_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_REPORT_VPN_NETWORK_STACK_CREATE_ERROR)
    }

    override fun reportTunnelThreadStopTimeout() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_REPORT_TUNNEL_THREAD_STOP_TIMEOUT_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_REPORT_TUNNEL_THREAD_STOP_TIMEOUT)
    }

    override fun reportTunnelThreadAbnormalCrash() {
        tryToFireDailyPixel(DeviceShieldPixelNames.ATP_REPORT_TUNNEL_THREAD_CRASH_DAILY)
        firePixel(DeviceShieldPixelNames.ATP_REPORT_TUNNEL_THREAD_STOP_CRASH)
    }

    override fun reportVpnAlwaysOnTriggered() {
        tryToFireDailyPixel(DeviceShieldPixelNames.REPORT_VPN_ALWAYS_ON_TRIGGERED_DAILY)
        firePixel(DeviceShieldPixelNames.REPORT_VPN_ALWAYS_ON_TRIGGERED)
    }

    override fun notifyStartFailed() {
        tryToFireDailyPixel(DeviceShieldPixelNames.REPORT_NOTIFY_START_FAILURE_DAILY)
        firePixel(DeviceShieldPixelNames.REPORT_NOTIFY_START_FAILURE)
    }

    override fun reportTLSParsingError(errorCode: Int) {
        tryToFireDailyPixel(String.format(Locale.US, DeviceShieldPixelNames.REPORT_TLS_PARSING_ERROR_CODE_DAILY.pixelName, errorCode))
    }

    override fun reportNewTabSectionToggled(enabled: Boolean) {
        if (enabled) {
            firePixel(DeviceShieldPixelNames.NEW_TAB_SECTION_TOGGLED_ON)
        } else {
            firePixel(DeviceShieldPixelNames.NEW_TAB_SECTION_TOGGLED_OFF)
        }
    }

    override fun reportPproUpsellBannerShown() {
        tryToFireUniquePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_ENABLED_BANNER_SHOWN_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_ENABLED_BANNER_SHOWN_DAILY)
        firePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_ENABLED_BANNER_SHOWN)
    }

    override fun reportPproUpsellBannerLinkClicked() {
        tryToFireUniquePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_ENABLED_BANNER_LINK_CLICKED_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_ENABLED_BANNER_LINK_CLICKED_DAILY)
        firePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_ENABLED_BANNER_LINK_CLICKED)
    }

    override fun reportPproUpsellBannerDismissed() {
        firePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_ENABLED_BANNER_DISMISSED)
    }

    override fun reportPproUpsellDisabledInfoShown() {
        tryToFireUniquePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_DISABLED_INFO_SHOWN_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_DISABLED_INFO_SHOWN_DAILY)
        firePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_DISABLED_INFO_SHOWN)
    }

    override fun reportPproUpsellDisabledInfoLinkClicked() {
        tryToFireUniquePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_DISABLED_INFO_LINK_CLICKED_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_DISABLED_INFO_LINK_CLICKED_DAILY)
        firePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_DISABLED_INFO_LINK_CLICKED)
    }

    override fun reportPproUpsellRevokedInfoShown() {
        tryToFireUniquePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_REVOKED_INFO_SHOWN_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_REVOKED_INFO_SHOWN_DAILY)
        firePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_REVOKED_INFO_SHOWN)
    }

    override fun reportPproUpsellRevokedInfoLinkClicked() {
        tryToFireUniquePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_REVOKED_INFO_LINK_CLICKED_UNIQUE)
        tryToFireDailyPixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_REVOKED_INFO_LINK_CLICKED_DAILY)
        firePixel(DeviceShieldPixelNames.APPTP_PPRO_UPSELL_REVOKED_INFO_LINK_CLICKED)
    }

    override fun appTPBlocklistExperimentDownloadFailure(statusCode: Int, experimentName: String, experimentCohort: String) {
        firePixel(
            DeviceShieldPixelNames.ATP_TDS_EXPERIMENT_DOWNLOAD_FAILED,
            mapOf(
                "code" to statusCode.toString(),
                "experimentName" to experimentName,
                "experimentCohort" to experimentCohort,
            ),
        )
        appCoroutineScope.launch(dispatcherProvider.io()) {
            appTpTDSPixelsPlugin.didFailToDownloadTDS()?.getPixelDefinitions()?.forEach {
                firePixel(it.pixelName, it.params)
            }
        }
    }

    private fun firePixel(
        p: DeviceShieldPixelNames,
        payload: Map<String, String> = emptyMap(),
    ) {
        firePixel(p.pixelName, payload, p.enqueue)
    }

    private fun firePixel(
        pixelName: String,
        payload: Map<String, String> = emptyMap(),
        enqueue: Boolean = false,
    ) {
        if (enqueue) {
            pixel.enqueueFire(pixelName, payload)
        } else {
            pixel.fire(pixelName, payload)
        }
    }

    private fun tryToFireDailyPixel(
        pixel: DeviceShieldPixelNames,
        payload: Map<String, String> = emptyMap(),
    ) {
        tryToFireDailyPixel(pixel.pixelName, payload, pixel.enqueue)
    }

    private fun tryToFireDailyPixel(
        pixelName: String,
        payload: Map<String, String> = emptyMap(),
        enqueue: Boolean = false,
    ) {
        val now = getUtcIsoLocalDate()
        val timestamp = preferences.getString(pixelName.appendTimestampSuffix(), null)

        // check if pixel was already sent in the current day
        if (timestamp == null || now > timestamp) {
            if (enqueue) {
                this.pixel.enqueueFire(pixelName, payload)
                    .also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
            } else {
                this.pixel.fire(pixelName, payload)
                    .also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
            }
        }
    }

    private fun tryToFireMonthlyPixel(
        pixel: DeviceShieldPixelNames,
        payload: Map<String, String> = emptyMap(),
    ) {
        tryToFireMonthlyPixel(pixel.pixelName, payload, pixel.enqueue)
    }

    private fun tryToFireMonthlyPixel(
        pixelName: String,
        payload: Map<String, String> = emptyMap(),
        enqueue: Boolean = false,
    ) {
        fun isMoreThan28DaysApart(date1: String, date2: String): Boolean {
            // Parse the strings into LocalDate objects
            val firstDate = LocalDate.parse(date1)
            val secondDate = LocalDate.parse(date2)

            // Calculate the difference in days
            val daysBetween = ChronoUnit.DAYS.between(firstDate, secondDate).absoluteValue

            // Check if the difference is more than 28 days
            return daysBetween > 28
        }

        val now = getUtcIsoLocalDate()
        val timestamp = preferences.getString(pixelName.appendTimestampSuffix(), null)

        // check if pixel was already sent in the current day
        if (timestamp == null || isMoreThan28DaysApart(now, timestamp)) {
            if (enqueue) {
                this.pixel.enqueueFire(pixelName, payload)
                    .also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
            } else {
                this.pixel.fire(pixelName, payload)
                    .also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
            }
        }
    }

    private fun tryToFireUniquePixel(
        pixel: DeviceShieldPixelNames,
        tag: String? = null,
        payload: Map<String, String> = emptyMap(),
    ) {
        val didExecuteAlready = preferences.getBoolean(tag ?: pixel.pixelName, false)

        if (didExecuteAlready) return

        if (pixel.enqueue) {
            this.pixel.enqueueFire(pixel, payload).also { preferences.edit { putBoolean(tag ?: pixel.pixelName, true) } }
        } else {
            this.pixel.fire(pixel, payload).also { preferences.edit { putBoolean(tag ?: pixel.pixelName, true) } }
        }
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
        private const val FIRST_OPEN_ENTRY_POINT_TAG = "FIRST_OPEN_ENTRY_POINT_TAG"

        private const val DS_PIXELS_PREF_FILE = "com.duckduckgo.mobile.android.device.shield.pixels"
    }
}
