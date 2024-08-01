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

package com.duckduckgo.networkprotection.impl.management

import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.vpn.network.ExternalVpnDetector
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.AlwaysOnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLING
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.UNKNOWN
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.OpenVpnBreakageCategoryWithBrokenApp
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.NetpRemoteFeature
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.None
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowAlwaysOnLockdownEnabled
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowRevoked
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.CheckVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.OpenVPNSettings
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.RequestVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ResetToggle
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowAlwaysOnLockdownDialog
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowAlwaysOnPromotionDialog
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowIssueReportingPage
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowUnifiedFeedback
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowVpnAlwaysOnConflictDialog
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowVpnConflictDialog
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionDetails
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connecting
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Disconnected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.LocationState
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ViewState
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.settings.NetpVpnSettingsDataStore
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.volume.NetpDataVolumeStore
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository.UserPreferredLocation
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NetworkProtectionManagementViewModelTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var vpnStateMonitor: VpnStateMonitor

    @Mock
    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    @Mock
    private lateinit var wgTunnelConfig: WgTunnelConfig

    @Mock
    private lateinit var externalVpnDetector: ExternalVpnDetector

    @Mock
    private lateinit var networkProtectionPixels: NetworkProtectionPixels

    @Mock
    private lateinit var networkProtectionState: NetworkProtectionState

    @Mock
    private lateinit var netPGeoswitchingRepository: NetPGeoswitchingRepository

    @Mock
    private lateinit var netpDataVolumeStore: NetpDataVolumeStore

    @Mock
    private lateinit var netPExclusionListRepository: NetPExclusionListRepository

    @Mock
    private lateinit var lifecycleOwner: LifecycleOwner

    @Mock
    private lateinit var netpVpnSettingsDataStore: NetpVpnSettingsDataStore

    private var remoteFeature = FakeFeatureToggleFactory.create(NetpRemoteFeature::class.java)

    private val wgQuickConfig = """
        [Interface]
        Address = 10.237.97.63/32
        DNS = 1.2.3.4
        MTU = 1280
        PrivateKey = yD1fKxCG/HFbxOy4YfR6zG86YQ1nOswlsv8n7uypb14=
        
        [Peer]
        AllowedIPs = 0.0.0.0/0
        Endpoint = 10.10.10.10:443
        Name = euw.1
        Location = Stockholm, SE
        PublicKey = u4geRTVQHaZYwsQzb/LsJqEDpxU8Fqzb5VjxGeIHslM=
    """.trimIndent()
    private val wgConfig: Config = Config.parse(BufferedReader(StringReader(wgQuickConfig)))

    private lateinit var testee: NetworkProtectionManagementViewModel
    private val testbreakageCategories = listOf(AppBreakageCategory("test", "test description"))

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        runTest {
            whenever(vpnStateMonitor.isAlwaysOnEnabled()).thenReturn(false)
            whenever(vpnStateMonitor.vpnLastDisabledByAndroid()).thenReturn(false)
        }

        testee = NetworkProtectionManagementViewModel(
            vpnStateMonitor,
            networkProtectionRepository,
            wgTunnelConfig,
            coroutineRule.testDispatcherProvider,
            externalVpnDetector,
            networkProtectionPixels,
            testbreakageCategories,
            networkProtectionState,
            netPGeoswitchingRepository,
            netpDataVolumeStore,
            netPExclusionListRepository,
            netpVpnSettingsDataStore,
            remoteFeature,
        )
    }

    @Test
    fun whenOnNetpToggleClickedToEnabledThenEmitCheckVPNPermissionCommand() = runTest {
        whenever(externalVpnDetector.isExternalVpnDetected()).thenReturn(false)

        testee.commands().test {
            testee.onNetpToggleClicked(true)

            assertEquals(CheckVPNPermission, this.awaitItem())
        }
    }

    @Test
    fun whenOnStartVpnThenRegisterFeature() = runTest {
        testee.onStartVpn()

        verify(networkProtectionState).start()
    }

    @Test
    fun whenOnNetpToggleClickedToDisabledThenUnregisterFeature() = runTest {
        testee.onNetpToggleClicked(false)

        verify(networkProtectionState).clearVPNConfigurationAndStop()
    }

    @Test
    fun whenExternalVPNDetectedAndOnNetpToggleClickedTrueThenEmitShowVpnConflictDialog() = runTest {
        whenever(externalVpnDetector.isExternalVpnDetected()).thenReturn(true)

        testee.commands().test {
            testee.onNetpToggleClicked(true)
            assertEquals(ShowVpnConflictDialog, this.awaitItem())
            verify(networkProtectionPixels).reportVpnConflictDialogShown()
        }
    }

    @Test
    fun whenNoExternalVPNDetectedAndOnNetpToggleClickedTrueThenEmitCheckVPNPermission() = runTest {
        whenever(externalVpnDetector.isExternalVpnDetected()).thenReturn(false)

        testee.commands().test {
            testee.onNetpToggleClicked(true)
            assertEquals(CheckVPNPermission, this.awaitItem())
        }
    }

    @Test
    fun wheOnVPNPermissionRejectedWithTimeToLastVPNRequestDiffLessThan500ThenEmitShowVpnAlwaysOnConflictDialog() = runTest {
        testee.commands().test {
            val intent = Intent()
            testee.onRequiredPermissionNotGranted(intent, 600)
            testee.onVPNPermissionRejected(1000)
            assertEquals(RequestVPNPermission(intent), this.awaitItem())
            assertEquals(ResetToggle, this.awaitItem())
            assertEquals(ShowVpnAlwaysOnConflictDialog, this.awaitItem())
            verify(networkProtectionPixels).reportAlwaysOnConflictDialogShown()
        }
    }

    @Test
    fun whenOnVPNPermissionRejectedWithTimeToLastVPNRequestDiffGreaterThan500ThenDoNotShowAlwaysOnConflictDialog() = runTest {
        testee.commands().test {
            val intent = Intent()
            testee.onRequiredPermissionNotGranted(intent, 600)
            testee.onVPNPermissionRejected(1200)
            assertEquals(RequestVPNPermission(intent), this.awaitItem())
            assertEquals(ResetToggle, this.awaitItem())
            this.ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenOnRequiredPermissionNotGrantedThenEmitRequestVPNPermission() = runTest {
        testee.commands().test {
            val intent = Intent()
            testee.onRequiredPermissionNotGranted(intent, 1000)
            assertEquals(RequestVPNPermission(intent), this.awaitItem())
        }
    }

    @Test
    fun whenVpnStateIsEnablingThenViewStateEmitsConnecting() = runTest {
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = ENABLING,
                ),
            ),
        )
        whenever(netPGeoswitchingRepository.getUserPreferredLocation()).thenReturn(UserPreferredLocation())

        testee.onStartVpn()

        testee.viewState().test {
            assertEquals(
                ViewState(
                    connectionState = Connecting,
                    alertState = None,
                    locationState = LocationState(
                        location = null,
                        icon = null,
                        isCustom = false,
                    ),
                ),
                this.expectMostRecentItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVpnStateIsDisabledThenViewStateEmitsDisconnected() = runTest {
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = DISABLED,
                ),
            ),
        )
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(wgConfig)
        whenever(netPGeoswitchingRepository.getUserPreferredLocation()).thenReturn(
            UserPreferredLocation(
                countryCode = "ES",
                cityName = "Madrid",
            ),
        )

        testee.viewState().test {
            assertEquals(
                ViewState(
                    connectionState = Disconnected,
                    locationState = LocationState(
                        location = "Madrid, Spain",
                        icon = "🇪🇸",
                        isCustom = true,
                    ),
                ),
                this.awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenEnabledAndServerDetailsAvailableThenEmitViewStateConnectedWithDetails() = runTest {
        whenever(networkProtectionRepository.enabledTimeInMillis).thenReturn(-1)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(wgConfig)
        whenever(netpVpnSettingsDataStore.customDns).thenReturn("1.1.1.1")
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = ENABLED,
                ),
            ),
        )
        whenever(netPGeoswitchingRepository.getUserPreferredLocation()).thenReturn(UserPreferredLocation())

        testee.viewState().distinctUntilChanged().test {
            assertEquals(
                ViewState(
                    connectionState = Connected,
                    connectionDetails = ConnectionDetails(
                        location = "Stockholm, SE",
                        ipAddress = "10.10.10.10",
                        elapsedConnectedTime = null,
                        customDns = "1.1.1.1",
                    ),
                    locationState = LocationState(
                        location = "Stockholm, Sweden",
                        icon = "🇸🇪",
                        isCustom = false,
                    ),
                ),
                this.expectMostRecentItem(),
            )
            testee.onStop(mock())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnResumeThenReturnViewStateExcludeAppCount() = runTest {
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = ENABLING,
                ),
            ),
        )
        whenever(netPGeoswitchingRepository.getUserPreferredLocation()).thenReturn(UserPreferredLocation())
        whenever(netPExclusionListRepository.getExcludedAppPackages()).thenReturn(listOf("app1"))

        testee.onResume(lifecycleOwner)

        testee.viewState().test {
            assertEquals(
                ViewState(
                    connectionState = Connecting,
                    alertState = None,
                    locationState = LocationState(
                        location = null,
                        icon = null,
                        isCustom = false,
                    ),
                    excludedAppsCount = 1,
                ),
                this.expectMostRecentItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenTimeDifferenceIs0ThenShowStartingTimeString() {
        assertEquals("00:00:00", 0L.toDisplayableTimerText())
    }

    @Test
    fun whenTimeDifferenceHasHoursOnlyThenSetMinsAndSecondsToDefault() {
        assertEquals("27:00:00", 97_200_000L.toDisplayableTimerText())
    }

    @Test
    fun whenTimeDifferenceHasMinsOnlyThenSetHoursAndSecondsToDefault() {
        assertEquals("00:38:00", 2_280_000L.toDisplayableTimerText())
    }

    @Test
    fun whenTimeDifferenceHasSecondsOnlyThenSetHoursAndMinutesToDefault() {
        assertEquals("00:00:32", 32_000L.toDisplayableTimerText())
    }

    @Test
    fun whenTimeDifferenceThenSetHoursAndMinutesToDefault() {
        assertEquals("27:38:32", 99_512_000L.toDisplayableTimerText())
    }

    @Test
    fun whenOnStartVpnThenResetValuesInRepository() {
        testee.onStartVpn()

        verify(networkProtectionRepository).enabledTimeInMillis = -1L
    }

    @Test
    fun whenVpnStateIsDisabledAndNullStopReasonThenNone() {
        assertEquals(None, testee.getAlertState(DISABLED, null, AlwaysOnState.DEFAULT))
    }

    @Test
    fun whenVpnStateIsDisabledAndUnknownStopReasonThenNone() {
        assertEquals(None, testee.getAlertState(DISABLED, UNKNOWN, AlwaysOnState.DEFAULT))
    }

    @Test
    fun whenVpnStateIsEnabledAndNullStopReasonThenNone() {
        assertEquals(None, testee.getAlertState(ENABLED, null, AlwaysOnState.DEFAULT))
    }

    @Test
    fun whenVpnStateIsEnablingAndNoneStopReasonThenNone() {
        assertEquals(None, testee.getAlertState(ENABLING, null, AlwaysOnState.DEFAULT))
    }

    @Test
    fun whenVpnStateIsEnabledAndAlwaysOnStateIsLockdownThenAlertStateIsShowAlwaysOnLockdownEnabled() {
        assertEquals(ShowAlwaysOnLockdownEnabled, testee.getAlertState(ENABLED, null, AlwaysOnState.ALWAYS_ON_LOCKED_DOWN))
    }

    @Test
    fun whenNotReconnectingThenAlertStateIsNone() {
        assertEquals(None, testee.getAlertState(DISABLED, UNKNOWN, AlwaysOnState.DEFAULT))
    }

    @Test
    fun whenStopReasonIsRevokedAndNotReconnectingThenAlertStateIsShowRevoked() {
        assertEquals(ShowRevoked, testee.getAlertState(DISABLED, REVOKED, AlwaysOnState.DEFAULT))
    }

    @Test
    fun whenOnAlwaysOnOpenSettingsClickedFromPromotionThenEmitOpenVPNSettingsCommandAndEmitPixels() = runTest {
        testee.commands().test {
            testee.onOpenSettingsFromAlwaysOnPromotionClicked()
            assertEquals(OpenVPNSettings, this.awaitItem())
            verify(networkProtectionPixels).reportOpenSettingsFromAlwaysOnPromotion()
        }
    }

    @Test
    fun whenOnAlwaysOnOpenSettingsClickedFromLockdownThenEmitOpenVPNSettingsCommandAndEmitPixels() = runTest {
        testee.commands().test {
            testee.onOpenSettingsFromAlwaysOnLockdownClicked()
            assertEquals(OpenVPNSettings, this.awaitItem())
            verify(networkProtectionPixels).reportOpenSettingsFromAlwaysOnLockdown()
        }
    }

    @Test
    fun whenOnStartVpnWithAlwaysOnOFFAndVPNLastDisabledByAndroidThenEmitShowAlwaysOnPromotionDialogCommand() = runTest {
        whenever(vpnStateMonitor.isAlwaysOnEnabled()).thenReturn(false)
        whenever(vpnStateMonitor.vpnLastDisabledByAndroid()).thenReturn(true)

        testee.commands().test {
            testee.onStartVpn()
            assertEquals(ShowAlwaysOnPromotionDialog, this.awaitItem())
            verify(networkProtectionPixels).reportAlwaysOnPromotionDialogShown()
        }
    }

    @Test
    fun whenOnStartVpnWithAlwaysOnEnabledThenDoNotEmitShowAlwaysOnPromotionDialogCommand() = runTest {
        whenever(vpnStateMonitor.isAlwaysOnEnabled()).thenReturn(true)
        whenever(vpnStateMonitor.vpnLastDisabledByAndroid()).thenReturn(true)

        testee.commands().test {
            testee.onStartVpn()
            this.ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenOnStartVpnWithAlwaysOnOffButVPNNotKilledByAndroidThenDoNotEmitShowAlwaysOnPromotionDialogCommand() = runTest {
        whenever(vpnStateMonitor.isAlwaysOnEnabled()).thenReturn(false)
        whenever(vpnStateMonitor.vpnLastDisabledByAndroid()).thenReturn(false)

        testee.commands().test {
            testee.onStartVpn()
            this.ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenOnStartWithAlwaysOnLockdownThenDoNotEmitShowAlwaysOnLockdownDialogCommand() = runTest {
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = ENABLED,
                    alwaysOnState = AlwaysOnState.ALWAYS_ON_LOCKED_DOWN,
                ),
            ),
        )

        testee.commands().test {
            testee.onStart(mock())
            assertEquals(ShowAlwaysOnLockdownDialog, this.awaitItem())
            verify(networkProtectionPixels).reportAlwaysOnLockdownDialogShown()
        }
    }

    @Test
    fun whenOnStartWithoutAlwaysOnLockdowmThenDoNotEmitShowAlwaysOnLockdownDialogCommand() = runTest {
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = ENABLED,
                    alwaysOnState = AlwaysOnState.ALWAYS_ON_ENABLED,
                ),
            ),
        )

        testee.commands().test {
            testee.onStart(mock())
            this.ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenOnReportIssuesClickedThenEmitShowIssueReportingPageCommand() = runTest {
        remoteFeature.useUnifiedFeedback().setEnabled(Toggle.State(enable = false))
        testee.onReportIssuesClicked()

        testee.commands().test {
            assertEquals(
                ShowIssueReportingPage(
                    OpenVpnBreakageCategoryWithBrokenApp(
                        launchFrom = "netp",
                        appName = "",
                        appPackageId = "",
                        breakageCategories = testbreakageCategories,
                    ),
                ),
                this.awaitItem(),
            )
            this.ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenOnReportIssuesClickedWithUnifiedFeedbackEnabledThenEmitShowUnifiedFeedback() = runTest {
        remoteFeature.useUnifiedFeedback().setEnabled(Toggle.State(enable = true))
        testee.onReportIssuesClicked()

        testee.commands().test {
            assertEquals(
                ShowUnifiedFeedback,
                this.awaitItem(),
            )
            this.ensureAllEventsConsumed()
        }
    }
}
