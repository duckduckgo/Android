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
     * This fun will fire one pixel
     */
    fun reportLatency(metadata: Map<String, String>)

    /**
     * This fun will fire one pixels
     * daily -> fire only once a day no matter how many times we call this fun
     */
    fun reportPoorLatency()

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
     * This fun will fire two pixels
     * daily -> fire only once a day no matter how many times we call this fun
     * count -> fire a pixel on every call
     *
     * The pixels fire when the waitlist beta is enabled for th user. This is gated by a remote feature flag
     */
    fun waitlistBetaIsEnabled()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealNetworkProtectionPixel @Inject constructor(
    private val pixel: Pixel,
    private val vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
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
        tryToFireDailyPixel(NETP_ENABLE_DAILY)
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

    override fun reportLatency(metadata: Map<String, String>) {
        firePixel(NETP_LATENCY_REPORT, metadata)
    }

    override fun reportPoorLatency() {
        tryToFireDailyPixel(NETP_REPORT_POOR_LATENCY_DAILY)
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
        firePixel(NETP_WAITLIST_BETA_ENABLED)
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
