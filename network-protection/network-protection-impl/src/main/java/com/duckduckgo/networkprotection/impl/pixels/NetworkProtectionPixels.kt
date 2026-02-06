/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.pixels

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.cohort.NetpCohortStore
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.*
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Qualifier

interface NetworkProtectionPixels {
    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportErrorInRegistration()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportErrorWgInvalidState()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportErrorWgBackendCantStart()

    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun reportEnabled()
    fun reportEnabledOnSearch()

    /** This pixel will be unique on a given day, no matter how many times we call this fun */
    fun reportDisabled()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportWireguardLibraryLoadFailed()

    /**
     * This fun will fire one pixel when the latency running average is terrible
     */
    fun reportTerribleLatency()

    /**
     * This fun will fire one pixel when the latency running average is poor
     */
    fun reportPoorLatency()

    /**
     * This fun will fire one pixel when the latency running average is moderate
     */
    fun reportModerateLatency()

    /**
     * This fun will fire one pixel when the latency running average is good
     */
    fun reportGoodLatency()

    /**
     * This fun will fire one pixel when the latency running average is excellent
     */
    fun reportExcellentLatency()

    /**
     * This fun will fire one pixel
     * daily -> fire only once a day no matter how many times we call this fun
     */
    fun reportLatencyMeasurementError()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportRekeyCompleted()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportVpnConflictDialogShown()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportAlwaysOnConflictDialogShown()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportAlwaysOnPromotionDialogShown()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportOpenSettingsFromAlwaysOnPromotion()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportAlwaysOnLockdownDialogShown()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportOpenSettingsFromAlwaysOnLockdown()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportExclusionListShown()

    /**
     * This fun will fire one pixel
     */
    fun reportAppAddedToExclusionList()

    /**
     * This fun will fire one pixel
     */
    fun reportAppRemovedFromExclusionList()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportSkippedReportAfterExcludingApp()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportExclusionListRestoreDefaults()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportExclusionListLaunchBreakageReport()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportFaqsShown()

    /**
     * This fun will fire one pixel
     */
    fun reportGeoswitchingScreenShown()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     *
     * The pixels fire whenever the user resets the preferred location to the default nearest available
     */
    fun reportPreferredLocationSetToNearest()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     *
     * The pixels fire whenever the user sets the preferred location to any other location than the default.
     */
    fun reportPreferredLocationSetToCustom()

    /**
     * This fun will fire one pixel
     */
    fun reportGeoswitchingNoLocations()

    /**
     * This fun will fire just daily pixel whenever private DNS is set by the user
     */
    fun reportPrivateDnsSet()

    /**
     * This fun will fire just daily pixel whenever private DNS is set by the user and VPN start fails
     */
    fun reportPrivateDnsSetOnVpnStartFail()

    /**
     * Fires count pixel every time the VPN is attempting to get enabled
     */
    fun reportEnableAttempt()

    /**
     * Fires count pixel upon VPN enable attempt success
     */
    fun reportEnableAttemptSuccess()

    /**
     * Fires count pixel upon VPN enable attempt failure
     */
    fun reportEnableAttemptFailure()

    /**
     * Fires daily and count pixels when a tunnel failure (handshake with egress) happens
     */
    fun reportTunnelFailure()

    /**
     * Fires count pixel when a tunnel failure (handshake with egress) is recovered on its own before VPN is disabled
     */
    fun reportTunnelFailureRecovered()
    fun reportVpnSnoozedCanceled()

    fun reportFailureRecoveryStarted()
    fun reportFailureRecoveryFailed()
    fun reportFailureRecoveryCompletedWithServerHealthy()
    fun reportFailureRecoveryCompletedWithServerUnhealthy()
    fun reportFailureRecoveryCompletedWithDifferentTunnelAddress()

    fun reportAccessRevokedDialogShown()
    fun reportPrivacyProPromotionDialogShown()
    fun reportVpnBetaStoppedWhenPrivacyProUpdatedAndEnabled()

    fun reportVpnEnabledFromQuickSettingsTile()
    fun reportVpnDisabledFromQuickSettingsTile()

    fun reportVpnScreenShown()

    fun reportVpnSettingsShown()

    fun reportEnabledPauseDuringCalls()

    fun reportDisabledPauseDuringCalls()

    fun reportExcludeSystemAppsEnabledForCategory(category: String)
    fun reportExcludeSystemAppsDisabledForCategory(category: String)

    fun reportServerMigrationAttempt()
    fun reportServerMigrationAttemptSuccess()
    fun reportServerMigrationAttemptFailed()

    fun reportCustomDnsSet()

    fun reportDefaultDnsSet()

    fun reportExcludePromptShown()
    fun reportExcludePromptExcludeAppClicked()
    fun reportExcludePromptDisableVpnClicked()
    fun reportExcludePromptDontAskAgainClicked()

    fun reportAutoExcludePromptShownInVPNScreen()
    fun reportAutoExcludePromptShownInExclusionList()
    fun reportAutoExcludePromptExcludeApps()
    fun reportAutoExcludePromptNoAction()
    fun reportAutoExcludePromptEnable()
    fun reportAutoExcludeEnableViaExclusionList()
    fun reportAutoExcludeDisableViaExclusionList()
    fun reportNotificationLaunched(pixelName: String)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealNetworkProtectionPixel @Inject constructor(
    private val pixel: Pixel,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val cohortStore: NetpCohortStore,
    private val etTimestamp: ETTimestamp,
) : NetworkProtectionPixels {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            NETP_PIXELS_PREF_FILE,
            multiprocess = true,
            migrate = false,
        )
    }

    override fun reportErrorInRegistration() {
        tryToFireDailyPixel(NETP_BACKEND_API_ERROR_DEVICE_REGISTRATION_FAILED_DAILY)
        firePixel(NETP_BACKEND_API_ERROR_DEVICE_REGISTRATION_FAILED)
    }

    override fun reportErrorWgInvalidState() {
        tryToFireDailyPixel(NETP_WG_ERROR_INVALID_STATE_DAILY)
        firePixel(NETP_WG_ERROR_INVALID_STATE)
    }

    override fun reportErrorWgBackendCantStart() {
        tryToFireDailyPixel(NETP_WG_ERROR_CANT_START_WG_BACKEND_DAILY)
        firePixel(NETP_WG_ERROR_CANT_START_WG_BACKEND)
    }

    override fun reportEnabled() {
        fun LocalDate?.asWeeklyCohortDate(): String {
            val baseDate = LocalDate.of(2023, 1, 1)
            return this?.let { cohortLocalDate ->
                // do we need to coalesce
                // I know cohortLocalDate is in ET timezone and we're comparing with LocalDate.now() but the error should be ok
                val weeksSinceCohortAssigned = ChronoUnit.WEEKS.between(cohortLocalDate, LocalDate.now())
                return@let if (weeksSinceCohortAssigned > WEEKS_TO_COALESCE_COHORT) {
                    // coalesce to no cohort
                    ""
                } else {
                    "week-${ChronoUnit.WEEKS.between(baseDate, cohortLocalDate) + 1}"
                }
            } ?: ""
        }
        tryToFireDailyPixel(NETP_ENABLE_DAILY, mapOf("cohort" to cohortStore.cohortLocalDate.asWeeklyCohortDate()))
        tryToFireUniquePixel(NETP_ENABLE_UNIQUE, payload = mapOf("cohort" to cohortStore.cohortLocalDate.asWeeklyCohortDate()))
    }

    override fun reportEnabledOnSearch() {
        tryToFireDailyPixel(NETP_ENABLE_ON_SEARCH_DAILY)
        firePixel(NETP_ENABLE_ON_SEARCH)
    }

    override fun reportDisabled() {
        tryToFireDailyPixel(NETP_DISABLE_DAILY)
    }

    override fun reportWireguardLibraryLoadFailed() {
        tryToFireDailyPixel(NETP_WG_ERROR_FAILED_TO_LOAD_WG_LIBRARY_DAILY)
        firePixel(NETP_WG_ERROR_FAILED_TO_LOAD_WG_LIBRARY)
    }

    override fun reportTerribleLatency() {
        firePixel(NETP_REPORT_TERRIBLE_LATENCY)
    }

    override fun reportPoorLatency() {
        firePixel(NETP_REPORT_POOR_LATENCY)
    }

    override fun reportModerateLatency() {
        firePixel(NETP_REPORT_MODERATE_LATENCY)
    }

    override fun reportGoodLatency() {
        firePixel(NETP_REPORT_GOOD_LATENCY)
    }

    override fun reportExcellentLatency() {
        firePixel(NETP_REPORT_EXCELLENT_LATENCY)
    }

    override fun reportLatencyMeasurementError() {
        tryToFireDailyPixel(NETP_REPORT_LATENCY_ERROR_DAILY)
    }

    override fun reportRekeyCompleted() {
        tryToFireDailyPixel(NETP_REKEY_COMPLETED_DAILY)
        firePixel(NETP_REKEY_COMPLETED)
    }

    override fun reportVpnConflictDialogShown() {
        tryToFireDailyPixel(NETP_VPN_CONFLICT_SHOWN_DAILY)
        firePixel(NETP_VPN_CONFLICT_SHOWN)
    }

    override fun reportAlwaysOnConflictDialogShown() {
        tryToFireDailyPixel(NETP_ALWAYSON_CONFLICT_SHOWN_DAILY)
        firePixel(NETP_ALWAYSON_CONFLICT_SHOWN)
    }

    override fun reportAlwaysOnPromotionDialogShown() {
        tryToFireDailyPixel(NETP_ALWAYSON_PROMOTION_SHOWN_DAILY)
        firePixel(NETP_ALWAYSON_PROMOTION_SHOWN)
    }

    override fun reportOpenSettingsFromAlwaysOnPromotion() {
        tryToFireDailyPixel(NETP_ALWAYSON_PROMOTION_OPEN_SETTINGS_DAILY)
        firePixel(NETP_ALWAYSON_PROMOTION_OPEN_SETTINGS)
    }

    override fun reportAlwaysOnLockdownDialogShown() {
        tryToFireDailyPixel(NETP_ALWAYSON_LOCKDOWN_SHOWN_DAILY)
        firePixel(NETP_ALWAYSON_LOCKDOWN_SHOWN)
    }

    override fun reportOpenSettingsFromAlwaysOnLockdown() {
        tryToFireDailyPixel(NETP_ALWAYSON_LOCKDOWN_OPEN_SETTINGS_DAILY)
        firePixel(NETP_ALWAYSON_LOCKDOWN_OPEN_SETTINGS)
    }

    override fun reportExclusionListShown() {
        tryToFireDailyPixel(NETP_EXCLUSION_LIST_SHOWN_DAILY)
        firePixel(NETP_EXCLUSION_LIST_SHOWN)
    }

    override fun reportAppAddedToExclusionList() {
        firePixel(NETP_EXCLUSION_LIST_APP_ADDED)
    }

    override fun reportAppRemovedFromExclusionList() {
        firePixel(NETP_EXCLUSION_LIST_APP_REMOVED)
    }

    override fun reportSkippedReportAfterExcludingApp() {
        tryToFireDailyPixel(NETP_EXCLUSION_LIST_SKIP_REPORT_AFTER_EXCLUDING_DAILY)
        firePixel(NETP_EXCLUSION_LIST_SKIP_REPORT_AFTER_EXCLUDING)
    }

    override fun reportExclusionListRestoreDefaults() {
        tryToFireDailyPixel(NETP_EXCLUSION_LIST_RESTORE_DEFAULTS_DAILY)
        firePixel(NETP_EXCLUSION_LIST_RESTORE_DEFAULTS)
    }

    override fun reportExclusionListLaunchBreakageReport() {
        tryToFireDailyPixel(NETP_EXCLUSION_LIST_LAUNCH_BREAKAGE_REPORT_DAILY)
        firePixel(NETP_EXCLUSION_LIST_LAUNCH_BREAKAGE_REPORT)
    }

    override fun reportFaqsShown() {
        tryToFireDailyPixel(NETP_FAQS_SHOWN_DAILY)
        firePixel(NETP_FAQS_SHOWN)
    }

    override fun reportGeoswitchingScreenShown() {
        firePixel(NETP_GEOSWITCHING_PAGE_SHOWN)
    }

    override fun reportPreferredLocationSetToNearest() {
        tryToFireDailyPixel(NETP_GEOSWITCHING_SET_NEAREST_DAILY)
        firePixel(NETP_GEOSWITCHING_SET_NEAREST)
    }

    override fun reportPreferredLocationSetToCustom() {
        tryToFireDailyPixel(NETP_GEOSWITCHING_SET_CUSTOM_DAILY)
        firePixel(NETP_GEOSWITCHING_SET_CUSTOM)
    }

    override fun reportGeoswitchingNoLocations() {
        tryToFireDailyPixel(NETP_GEOSWITCHING_NO_AVAILABLE_LOCATIONS_DAILY)
        firePixel(NETP_GEOSWITCHING_NO_AVAILABLE_LOCATIONS)
    }

    override fun reportPrivateDnsSet() {
        tryToFireDailyPixel(NETP_PRIVATE_DNS_SET_DAILY)
    }

    override fun reportPrivateDnsSetOnVpnStartFail() {
        tryToFireDailyPixel(NETP_PRIVATE_DNS_SET_VPN_START_FAILED_DAILY)
    }

    override fun reportEnableAttempt() {
        firePixel(NETP_ENABLE_ATTEMPT)
    }

    override fun reportEnableAttemptSuccess() {
        firePixel(NETP_ENABLE_ATTEMPT_SUCCESS)
    }

    override fun reportEnableAttemptFailure() {
        firePixel(NETP_ENABLE_ATTEMPT_FAILURE)
    }

    override fun reportTunnelFailure() {
        firePixel(NETP_TUNNEL_FAILURE)
        tryToFireDailyPixel(NETP_TUNNEL_FAILURE_DAILY)
    }

    override fun reportTunnelFailureRecovered() {
        firePixel(NETP_TUNNEL_FAILURE_RECOVERED)
    }

    override fun reportVpnSnoozedCanceled() {
        tryToFireDailyPixel(VPN_SNOOZE_CANCELED_DAILY)
        firePixel(VPN_SNOOZE_CANCELED)
    }

    override fun reportFailureRecoveryStarted() {
        firePixel(NETP_FAILURE_RECOVERY_STARTED)
        tryToFireDailyPixel(NETP_FAILURE_RECOVERY_STARTED_DAILY)
    }

    override fun reportFailureRecoveryFailed() {
        firePixel(NETP_FAILURE_RECOVERY_FAILED)
        tryToFireDailyPixel(NETP_FAILURE_RECOVERY_FAILED_DAILY)
    }

    override fun reportFailureRecoveryCompletedWithServerHealthy() {
        firePixel(NETP_FAILURE_RECOVERY_COMPLETED_SERVER_HEALTHY)
        tryToFireDailyPixel(NETP_FAILURE_RECOVERY_COMPLETED_SERVER_HEALTHY_DAILY)
    }

    override fun reportFailureRecoveryCompletedWithServerUnhealthy() {
        firePixel(NETP_FAILURE_RECOVERY_COMPLETED_SERVER_UNHEALTHY)
        tryToFireDailyPixel(NETP_FAILURE_RECOVERY_COMPLETED_SERVER_UNHEALTHY_DAILY)
    }

    override fun reportFailureRecoveryCompletedWithDifferentTunnelAddress() {
        firePixel(NETP_FAILURE_RECOVERY_COMPLETED_SERVER_HEALTHY_NEW_TUN_ADDRESS)
        tryToFireDailyPixel(NETP_FAILURE_RECOVERY_COMPLETED_SERVER_HEALTHY_NEW_TUN_ADDRESS_DAILY)
    }

    override fun reportAccessRevokedDialogShown() {
        firePixel(NETP_ACCESS_REVOKED_DIALOG_SHOWN)
        tryToFireDailyPixel(NETP_ACCESS_REVOKED_DIALOG_SHOWN_DAILY)
    }

    override fun reportPrivacyProPromotionDialogShown() {
        firePixel(NETP_PRIVACY_PRO_PROMOTION_DIALOG_SHOWN)
        tryToFireDailyPixel(NETP_PRIVACY_PRO_PROMOTION_DIALOG_SHOWN_DAILY)
    }

    override fun reportVpnBetaStoppedWhenPrivacyProUpdatedAndEnabled() {
        firePixel(NETP_BETA_STOPPED_WHEN_PRIVACY_PRO_UPDATED_AND_ENABLED)
        tryToFireDailyPixel(NETP_BETA_STOPPED_WHEN_PRIVACY_PRO_UPDATED_AND_ENABLED_DAILY)
    }

    override fun reportVpnEnabledFromQuickSettingsTile() {
        firePixel(NETP_ENABLE_FROM_SETTINGS_TILE)
        tryToFireUniquePixel(NETP_ENABLE_FROM_SETTINGS_TILE_UNIQUE)
        tryToFireDailyPixel(NETP_ENABLE_FROM_SETTINGS_TILE_DAILY)
    }

    override fun reportVpnDisabledFromQuickSettingsTile() {
        firePixel(NETP_DISABLE_FROM_SETTINGS_TILE)
        tryToFireDailyPixel(NETP_DISABLE_FROM_SETTINGS_TILE_DAILY)
    }

    override fun reportVpnScreenShown() {
        firePixel(NETP_VPN_SCREEN_SHOWN)
        tryToFireDailyPixel(NETP_VPN_SCREEN_SHOWN_DAILY)
    }

    override fun reportVpnSettingsShown() {
        firePixel(NETP_VPN_SETTINGS_SHOWN)
        tryToFireDailyPixel(NETP_VPN_SETTINGS_SHOWN_DAILY)
    }

    override fun reportEnabledPauseDuringCalls() {
        firePixel(NETP_PAUSE_ON_CALL_ENABLED)
        tryToFireDailyPixel(NETP_PAUSE_ON_CALL_ENABLED_DAILY)
    }

    override fun reportDisabledPauseDuringCalls() {
        firePixel(NETP_PAUSE_ON_CALL_DISABLED)
        tryToFireDailyPixel(NETP_PAUSE_ON_CALL_ENABLED_DAILY)
    }

    override fun reportExcludeSystemAppsEnabledForCategory(category: String) {
        firePixel(NETP_EXCLUDE_SYSTEM_APPS_ENABLED, mapOf("category" to category))
        tryToFireDailyPixel(NETP_EXCLUDE_SYSTEM_APPS_ENABLED_DAILY, mapOf("category" to category))
    }

    override fun reportExcludeSystemAppsDisabledForCategory(category: String) {
        firePixel(NETP_EXCLUDE_SYSTEM_APPS_DISABLED, mapOf("category" to category))
        tryToFireDailyPixel(NETP_EXCLUDE_SYSTEM_APPS_DISABLED_DAILY, mapOf("category" to category))
    }

    override fun reportServerMigrationAttempt() {
        firePixel(NETP_SERVER_MIGRATION_ATTEMPT)
        tryToFireDailyPixel(NETP_SERVER_MIGRATION_ATTEMPT_DAILY)
    }

    override fun reportServerMigrationAttemptSuccess() {
        firePixel(NETP_SERVER_MIGRATION_ATTEMPT_SUCCESS)
        tryToFireDailyPixel(NETP_SERVER_MIGRATION_ATTEMPT_SUCCESS_DAILY)
    }

    override fun reportServerMigrationAttemptFailed() {
        firePixel(NETP_SERVER_MIGRATION_ATTEMPT_FAILED)
        tryToFireDailyPixel(NETP_SERVER_MIGRATION_ATTEMPT_FAILED_DAILY)
    }

    override fun reportCustomDnsSet() {
        firePixel(NETP_UPDATE_CUSTOM_DNS)
        tryToFireDailyPixel(NETP_UPDATE_CUSTOM_DNS_DAILY)
    }

    override fun reportDefaultDnsSet() {
        firePixel(NETP_UPDATE_DEFAULT_DNS)
        tryToFireDailyPixel(NETP_UPDATE_DEFAULT_DNS_DAILY)
    }

    override fun reportExcludePromptShown() {
        firePixel(NETP_EXCLUDE_PROMPT_SHOWN)
        tryToFireDailyPixel(NETP_EXCLUDE_PROMPT_SHOWN_DAILY)
    }

    override fun reportExcludePromptExcludeAppClicked() {
        firePixel(NETP_EXCLUDE_PROMPT_EXCLUDE_APP_CLICKED)
        tryToFireDailyPixel(NETP_EXCLUDE_PROMPT_EXCLUDE_APP_CLICKED_DAILY)
    }

    override fun reportExcludePromptDisableVpnClicked() {
        firePixel(NETP_EXCLUDE_PROMPT_DISABLE_VPN_CLICKED)
        tryToFireDailyPixel(NETP_EXCLUDE_PROMPT_DISABLE_VPN_CLICKED_DAILY)
    }

    override fun reportExcludePromptDontAskAgainClicked() {
        firePixel(NETP_EXCLUDE_PROMPT_DONT_ASK_AGAIN_CLICKED)
    }

    override fun reportAutoExcludePromptShownInVPNScreen() {
        firePixel(NETP_AUTO_EXCLUDE_PROMPT_SHOWN_VPN_SCREEN)
        tryToFireDailyPixel(NETP_AUTO_EXCLUDE_PROMPT_SHOWN_VPN_SCREEN_DAILY)
    }

    override fun reportAutoExcludePromptShownInExclusionList() {
        firePixel(NETP_AUTO_EXCLUDE_PROMPT_SHOWN_EXCLUSION_SCREEN)
        tryToFireDailyPixel(NETP_AUTO_EXCLUDE_PROMPT_SHOWN_EXCLUSION_SCREEN_DAILY)
    }

    override fun reportAutoExcludePromptExcludeApps() {
        firePixel(NETP_AUTO_EXCLUDE_PROMPT_EXCLUDE_APPS)
        tryToFireDailyPixel(NETP_AUTO_EXCLUDE_PROMPT_EXCLUDE_APPS_DAILY)
    }

    override fun reportAutoExcludePromptNoAction() {
        firePixel(NETP_AUTO_EXCLUDE_PROMPT_NO_ACTION)
        tryToFireDailyPixel(NETP_AUTO_EXCLUDE_PROMPT_NO_ACTION_DAILY)
    }

    override fun reportAutoExcludePromptEnable() {
        firePixel(NETP_AUTO_EXCLUDE_PROMPT_ENABLED)
        tryToFireDailyPixel(NETP_AUTO_EXCLUDE_PROMPT_ENABLED_DAILY)
    }

    override fun reportAutoExcludeEnableViaExclusionList() {
        firePixel(NETP_AUTO_EXCLUDE_ENABLED_VIA_EXCLUSION_LIST)
        tryToFireDailyPixel(NETP_AUTO_EXCLUDE_ENABLED_VIA_EXCLUSION_LIST_DAILY)
    }

    override fun reportAutoExcludeDisableViaExclusionList() {
        firePixel(NETP_AUTO_EXCLUDE_DISABLED_VIA_EXCLUSION_LIST)
        tryToFireDailyPixel(NETP_AUTO_EXCLUDE_DISABLED_VIA_EXCLUSION_LIST_DAILY)
    }

    override fun reportNotificationLaunched(pixelName: String) {
        firePixel(pixelName)
    }

    private fun firePixel(
        p: NetworkProtectionPixelNames,
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
            pixel.enqueueFire(pixelName, payload.addTimestampAtZoneET())
        } else {
            pixel.fire(pixelName, payload)
        }
    }

    private fun tryToFireDailyPixel(
        pixel: NetworkProtectionPixelNames,
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
                this.pixel.enqueueFire(pixelName, payload.addTimestampAtZoneET())
                    .also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
            } else {
                this.pixel.fire(pixelName, payload)
                    .also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
            }
        }
    }

    private fun tryToFireUniquePixel(
        pixel: NetworkProtectionPixelNames,
        tag: String? = null,
        payload: Map<String, String> = emptyMap(),
    ) {
        val didExecuteAlready = preferences.getBoolean(tag ?: pixel.pixelName, false)

        if (didExecuteAlready) return

        if (pixel.enqueue) {
            this.pixel.enqueueFire(pixel, payload.addTimestampAtZoneET()).also { preferences.edit { putBoolean(tag ?: pixel.pixelName, true) } }
        } else {
            this.pixel.fire(pixel, payload).also { preferences.edit { putBoolean(tag ?: pixel.pixelName, true) } }
        }
    }

    private fun String.appendTimestampSuffix(): String {
        return "${this}_timestamp"
    }

    private fun Map<String, String>.addTimestampAtZoneET(): Map<String, String> {
        return this.toMutableMap().apply {
            put(TIMESTAMP_ET_PARAM, etTimestamp.formattedTimestamp())
        }
    }

    private fun getUtcIsoLocalDate(): String {
        // returns YYYY-MM-dd
        return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    companion object {
        private const val NETP_PIXELS_PREF_FILE = "com.duckduckgo.networkprotection.pixels.v1"
        private const val TIMESTAMP_ET_PARAM = "ts"
        private const val WEEKS_TO_COALESCE_COHORT = 6
    }
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class InternalApi

// This class is here for testing purposes
@InternalApi
open class ETTimestamp @Inject constructor() {
    open fun formattedTimestamp(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        return Instant.now().atZone(ZoneId.of("America/New_York")).format(formatter)
    }
}
