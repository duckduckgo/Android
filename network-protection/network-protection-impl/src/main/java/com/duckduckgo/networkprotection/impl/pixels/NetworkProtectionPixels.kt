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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.duckduckgo.networkprotection.impl.cohort.NetpCohortStore
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.*
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter

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
    fun reportVpnConnectivityLoss()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportVpnReconnectFailed()

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
    fun reportWhatIsAVpnScreenShown()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportFaqsShown()

    /**
     * This fun will fire two pixels when the NetP Terms and Conditions screen is shown
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportTermsShown()

    /**
     * This fun will fire two pixels when the NetP Terms and Conditions screen are accepted
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun reportTermsAccepted()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun waitlistNotificationShown()

    /**
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     */
    fun waitlistNotificationCancelled()

    /**
     * This fun will fire daily -> fire only once a day no matter how many times we call this fun
     *
     * The pixels fire when the waitlist beta is enabled for th user. This is gated by a remote feature flag
     */
    fun waitlistBetaIsEnabled()

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
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealNetworkProtectionPixel @Inject constructor(
    private val pixel: Pixel,
    private val vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
    private val cohortStore: NetpCohortStore,
) : NetworkProtectionPixels {

    private val preferences: SharedPreferences by lazy {
        vpnSharedPreferencesProvider.getSharedPreferences(
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
        tryToFireDailyPixel(NETP_ENABLE_DAILY, mapOf("cohort" to cohortStore.cohortLocalDate?.toString().orEmpty()))
        tryToFireUniquePixel(NETP_ENABLE_UNIQUE, payload = mapOf("cohort" to cohortStore.cohortLocalDate?.toString().orEmpty()))
    }

    override fun reportEnabledOnSearch() {
        tryToFireDailyPixel(NETP_ENABLE_ON_SEARCH_DAILY)
        firePixel(NETP_ENABLE_ON_SEARCH)
    }

    override fun reportDisabled() {
        tryToFireDailyPixel(NETP_DISABLE_DAILY)
    }

    override fun reportVpnConnectivityLoss() {
        tryToFireDailyPixel(NETP_VPN_CONNECTIVITY_LOST_DAILY)
        firePixel(NETP_VPN_CONNECTIVITY_LOST)
    }

    override fun reportVpnReconnectFailed() {
        tryToFireDailyPixel(NETP_VPN_RECONNECT_FAILED_DAILY)
        firePixel(NETP_VPN_RECONNECT_FAILED)
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

    override fun reportWhatIsAVpnScreenShown() {
        tryToFireDailyPixel(NETP_INFO_VPN_SHOWN_DAILY)
        firePixel(NETP_INFO_VPN_SHOWN)
    }

    override fun reportFaqsShown() {
        tryToFireDailyPixel(NETP_FAQS_SHOWN_DAILY)
        firePixel(NETP_FAQS_SHOWN)
    }

    override fun reportTermsShown() {
        tryToFireDailyPixel(NETP_TERMS_SHOWN_DAILY)
        firePixel(NETP_TERMS_SHOWN)
    }

    override fun reportTermsAccepted() {
        tryToFireDailyPixel(NETP_TERMS_ACCEPTED_DAILY)
        firePixel(NETP_TERMS_ACCEPTED)
    }

    override fun waitlistNotificationShown() {
        tryToFireDailyPixel(NETP_WAITLIST_NOTIFICATION_SHOWN_DAILY)
        firePixel(NETP_WAITLIST_NOTIFICATION_SHOWN)
    }

    override fun waitlistNotificationCancelled() {
        tryToFireDailyPixel(NETP_WAITLIST_NOTIFICATION_CANCELLED_DAILY)
        firePixel(NETP_WAITLIST_NOTIFICATION_CANCELLED)
    }

    override fun waitlistBetaIsEnabled() {
        tryToFireDailyPixel(NETP_WAITLIST_BETA_ENABLED_DAILY)
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
            pixel.enqueueFire(pixelName, payload)
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
                this.pixel.enqueueFire(pixelName, payload)
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
        private const val NETP_PIXELS_PREF_FILE = "com.duckduckgo.networkprotection.pixels.v1"
    }
}
