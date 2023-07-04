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
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_ALWAYSON_CONFLICT_SHOWN
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_ALWAYSON_CONFLICT_SHOWN_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_ALWAYSON_LOCKDOWN_OPEN_SETTINGS
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_ALWAYSON_LOCKDOWN_OPEN_SETTINGS_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_ALWAYSON_LOCKDOWN_SHOWN
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_ALWAYSON_LOCKDOWN_SHOWN_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_ALWAYSON_PROMOTION_OPEN_SETTINGS
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_ALWAYSON_PROMOTION_OPEN_SETTINGS_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_ALWAYSON_PROMOTION_SHOWN
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_ALWAYSON_PROMOTION_SHOWN_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_BACKEND_API_ERROR_DEVICE_REGISTRATION_FAILED
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_BACKEND_API_ERROR_DEVICE_REGISTRATION_FAILED_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_DISABLE_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_ENABLE_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_EXCLUSION_LIST_APP_ADDED
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_EXCLUSION_LIST_APP_REMOVED
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_EXCLUSION_LIST_LAUNCH_BREAKAGE_REPORT
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_EXCLUSION_LIST_LAUNCH_BREAKAGE_REPORT_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_EXCLUSION_LIST_RESTORE_DEFAULTS
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_EXCLUSION_LIST_RESTORE_DEFAULTS_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_EXCLUSION_LIST_SHOWN
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_EXCLUSION_LIST_SHOWN_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_EXCLUSION_LIST_SKIP_REPORT_AFTER_EXCLUDING
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_EXCLUSION_LIST_SKIP_REPORT_AFTER_EXCLUDING_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_LATENCY_REPORT
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REKEY_COMPLETED
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REKEY_COMPLETED_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_VPN_CONFLICT_SHOWN
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_VPN_CONFLICT_SHOWN_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_VPN_CONNECTIVITY_LOST
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_VPN_CONNECTIVITY_LOST_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_VPN_RECONNECT_FAILED
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_VPN_RECONNECT_FAILED_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_WG_ERROR_CANT_START_WG_BACKEND
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_WG_ERROR_CANT_START_WG_BACKEND_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_WG_ERROR_FAILED_TO_LOAD_WG_LIBRARY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_WG_ERROR_FAILED_TO_LOAD_WG_LIBRARY_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_WG_ERROR_INVALID_STATE
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_WG_ERROR_INVALID_STATE_DAILY
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
