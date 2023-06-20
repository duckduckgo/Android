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

import com.duckduckgo.app.global.api.InMemorySharedPreferences
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealNetworkProtectionPixelTest {
    @Mock
    private lateinit var pixel: Pixel

    @Mock
    private lateinit var vpnSharedPreferencesProvider: VpnSharedPreferencesProvider

    private lateinit var testee: RealNetworkProtectionPixel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        val prefs = InMemorySharedPreferences()
        whenever(
            vpnSharedPreferencesProvider.getSharedPreferences(eq("com.duckduckgo.networkprotection.pixels.v1"), eq(true), eq(false)),
        ).thenReturn(prefs)
        testee = RealNetworkProtectionPixel(pixel, vpnSharedPreferencesProvider)
    }

    @Test
    fun whenReportErrorInRegistrationIsCalledTwiceThenFireCountPixelTwiceAndDailyPixelOnce() {
        testee.reportErrorInRegistration()
        testee.reportErrorInRegistration()

        verify(pixel).enqueueFire("m_netp_ev_backend_api_error_device_registration_failed_d")
        verify(pixel, times(2)).enqueueFire("m_netp_ev_backend_api_error_device_registration_failed_c")
    }

    @Test
    fun whenReportErrorWgInvalidStateIsCalledTwiceThenFireCountPixelTwiceAndDailyPixelOnce() {
        testee.reportErrorWgInvalidState()
        testee.reportErrorWgInvalidState()

        verify(pixel).enqueueFire("m_netp_ev_wireguard_error_invalid_state_d")
        verify(pixel, times(2)).enqueueFire("m_netp_ev_wireguard_error_invalid_state_c")
    }

    @Test
    fun whenReportErrorWgBackendCantStartIsCalledTwiceThenFireCountPixelTwiceAndDailyPixelOnce() {
        testee.reportErrorWgBackendCantStart()
        testee.reportErrorWgBackendCantStart()

        verify(pixel).enqueueFire("m_netp_ev_wireguard_error_cannot_start_wireguard_backend_d")
        verify(pixel, times(2)).enqueueFire("m_netp_ev_wireguard_error_cannot_start_wireguard_backend_c")
    }

    @Test
    fun whenReportEnabledCalledTwiceThenFireDailyPixelOnce() {
        testee.reportEnabled()
        testee.reportEnabled()

        verify(pixel).fire("m_netp_ev_enabled_d")
    }

    @Test
    fun whenReportDisabledCalledTwiceThenFireDailyPixelOnce() {
        testee.reportDisabled()
        testee.reportDisabled()

        verify(pixel).fire("m_netp_ev_disabled_d")
    }

    @Test
    fun whenReportVpnConnectivityLossCalledTwiceThenFireCountPixelTwiceAndDailyPixelOnce() {
        testee.reportVpnConnectivityLoss()
        testee.reportVpnConnectivityLoss()

        verify(pixel).enqueueFire("m_netp_ev_vpn_connectivity_lost_d")
        verify(pixel, times(2)).enqueueFire("m_netp_ev_vpn_connectivity_lost_c")
    }

    @Test
    fun whenReportVpnReconnectFailedCalledTwiceThenFireCountPixelTwiceAndDailyPixelOnce() {
        testee.reportVpnReconnectFailed()
        testee.reportVpnReconnectFailed()

        verify(pixel).enqueueFire("m_netp_ev_vpn_reconnect_failed_d")
        verify(pixel, times(2)).enqueueFire("m_netp_ev_vpn_reconnect_failed_c")
    }

    @Test
    fun whenReportWireguardLibraryLoadFailedCalledTwiceThenFireCountPixelTwiceAndDailyPixelOnce() {
        testee.reportWireguardLibraryLoadFailed()
        testee.reportWireguardLibraryLoadFailed()

        verify(pixel).enqueueFire("m_netp_ev_wireguard_error_unable_to_load_wireguard_library_d")
        verify(pixel, times(2)).enqueueFire("m_netp_ev_wireguard_error_unable_to_load_wireguard_library_c")
    }

    @Test
    fun whenReportRekeyCompletedCalledTwiceThenFireDailyPixelOnce() {
        testee.reportRekeyCompleted()
        testee.reportRekeyCompleted()

        verify(pixel).fire("m_netp_ev_rekey_completed_d")
        verify(pixel, times(2)).fire("m_netp_ev_rekey_completed_c")
    }

    @Test
    fun whenReportVpnConflictDialogShownCalledTwiceThenFireDailyPixelOnce() {
        testee.reportVpnConflictDialogShown()
        testee.reportVpnConflictDialogShown()

        verify(pixel).fire("m_netp_imp_vpn_conflict_dialog_d")
        verify(pixel, times(2)).fire("m_netp_imp_vpn_conflict_dialog_c")
    }

    @Test
    fun whenReportAlwaysOnConflictDialogShownCalledTwiceThenFireDailyPixelOnce() {
        testee.reportAlwaysOnConflictDialogShown()
        testee.reportAlwaysOnConflictDialogShown()

        verify(pixel).fire("m_netp_imp_always_on_conflict_dialog_d")
        verify(pixel, times(2)).fire("m_netp_imp_always_on_conflict_dialog_c")
    }

    @Test
    fun whenReportAlwaysOnPromotionDialogShownCalledTwiceThenFireDailyPixelOnce() {
        testee.reportAlwaysOnPromotionDialogShown()
        testee.reportAlwaysOnPromotionDialogShown()

        verify(pixel).fire("m_netp_imp_always_on_promotion_dialog_d")
        verify(pixel, times(2)).fire("m_netp_imp_always_on_promotion_dialog_c")
    }

    @Test
    fun whenReportAlwaysOnLockdownDialogShownCalledTwiceThenFireDailyPixelOnce() {
        testee.reportAlwaysOnLockdownDialogShown()
        testee.reportAlwaysOnLockdownDialogShown()

        verify(pixel).fire("m_netp_imp_always_on_lockdown_dialog_d")
        verify(pixel, times(2)).fire("m_netp_imp_always_on_lockdown_dialog_c")
    }

    @Test
    fun whenReportOpenSettingsFromAlwaysOnPromotionCalledTwiceThenFireDailyPixelOnce() {
        testee.reportOpenSettingsFromAlwaysOnPromotion()
        testee.reportOpenSettingsFromAlwaysOnPromotion()

        verify(pixel).fire("m_netp_ev_open_settings_from_always_on_promotion_dialog_d")
        verify(pixel, times(2)).fire("m_netp_ev_open_settings_from_always_on_promotion_dialog_c")
    }

    @Test
    fun whenReportOpenSettingsFromAlwaysOnLockdownCalledTwiceThenFireDailyPixelOnce() {
        testee.reportOpenSettingsFromAlwaysOnLockdown()
        testee.reportOpenSettingsFromAlwaysOnLockdown()

        verify(pixel).fire("m_netp_ev_open_settings_from_always_on_lockdown_dialog_d")
        verify(pixel, times(2)).fire("m_netp_ev_open_settings_from_always_on_lockdown_dialog_c")
    }

    @Test
    fun whenReportExclusionListShownCalledTwiceThenFireDailyPixelOnce() {
        testee.reportExclusionListShown()
        testee.reportExclusionListShown()

        verify(pixel).fire("m_netp_imp_exclusion_list_d")
        verify(pixel, times(2)).fire("m_netp_imp_exclusion_list_c")
    }

    @Test
    fun whenReportAppAddedToExclusionListCalledTwiceThenFirePixelTwice() {
        testee.reportAppAddedToExclusionList()
        testee.reportAppAddedToExclusionList()

        verify(pixel, times(2)).fire("m_netp_ev_exclusion_list_app_added_c")
    }

    @Test
    fun whenReportAppRemovedFromExclusionListCalledTwiceThenFirePixelTwice() {
        testee.reportAppRemovedFromExclusionList()
        testee.reportAppRemovedFromExclusionList()

        verify(pixel, times(2)).fire("m_netp_ev_exclusion_list_app_removed_c")
    }

    @Test
    fun whenReportSkippedReportAfterExcludingAppCalledTwiceThenFireDailyPixelOnce() {
        testee.reportSkippedReportAfterExcludingApp()
        testee.reportSkippedReportAfterExcludingApp()

        verify(pixel).fire("m_netp_ev_skip_report_after_excluding_app_d")
        verify(pixel, times(2)).fire("m_netp_ev_skip_report_after_excluding_app_c")
    }

    @Test
    fun whenReportExclusionListRestoreDefaultsCalledTwiceThenFireDailyPixelOnce() {
        testee.reportExclusionListRestoreDefaults()
        testee.reportExclusionListRestoreDefaults()

        verify(pixel).fire("m_netp_ev_exclusion_list_restore_defaults_d")
        verify(pixel, times(2)).fire("m_netp_ev_exclusion_list_restore_defaults_c")
    }

    @Test
    fun whenReportExclusionListLaunchBreakageReportCalledTwiceThenFireDailyPixelOnce() {
        testee.reportExclusionListLaunchBreakageReport()
        testee.reportExclusionListLaunchBreakageReport()

        verify(pixel).fire("m_netp_ev_exclusion_list_launch_breakage_report_d")
        verify(pixel, times(2)).fire("m_netp_ev_exclusion_list_launch_breakage_report_c")
    }
}
