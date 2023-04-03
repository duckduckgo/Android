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
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.network.ExternalVpnDetector
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.AlwaysOnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLING
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.UNKNOWN
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.alerts.reconnect.NetPReconnectNotifications
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.None
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowReconnecting
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowReconnectingFailed
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowRevoked
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.CheckVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.OpenVPNSettings
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.RequestVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ResetToggle
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowAlwaysOnLockdownDialog
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowAlwaysOnPromotionDialog
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowVpnAlwaysOnConflictDialog
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowVpnConflictDialog
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionDetails
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connecting
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Disconnected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ViewState
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.NotReconnecting
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.Reconnecting
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.ReconnectingFailed
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ServerDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkProtectionManagementViewModelTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var vpnStateMonitor: VpnStateMonitor

    @Mock
    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Mock
    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    @Mock
    private lateinit var reconnectNotifications: NetPReconnectNotifications

    @Mock
    private lateinit var externalVpnDetector: ExternalVpnDetector

    @Mock
    private lateinit var networkProtectionPixels: NetworkProtectionPixels
    private lateinit var testee: NetworkProtectionManagementViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(networkProtectionRepository.reconnectStatus).thenReturn(NotReconnecting)
        testee = NetworkProtectionManagementViewModel(
            vpnStateMonitor,
            vpnFeaturesRegistry,
            networkProtectionRepository,
            coroutineRule.testDispatcherProvider,
            reconnectNotifications,
            externalVpnDetector,
            networkProtectionPixels,
        )
    }

    @Test
    fun whenOnNetpToggleClickedToEnabledThenEmitCheckVPNPermissionCommand() = runTest {
        testee.commands().test {
            testee.onNetpToggleClicked(true)

            assertEquals(CheckVPNPermission, this.awaitItem())
        }
    }

    @Test
    fun whenOnStartVpnThenRegisterFeature() {
        testee.onStartVpn()

        verify(vpnFeaturesRegistry).registerFeature(NetPVpnFeature.NETP_VPN)
    }

    @Test
    fun whenOnNetpToggleClickedToDisabledThenUnregisterFeature() {
        testee.onNetpToggleClicked(false)

        verify(vpnFeaturesRegistry).unregisterFeature(NetPVpnFeature.NETP_VPN)
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

        testee.onStartVpn()

        testee.viewState().test {
            assertEquals(
                ViewState(
                    connectionState = Connecting,
                    alertState = None,
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
        whenever(networkProtectionRepository.serverDetails).thenReturn(
            ServerDetails(
                serverName = "euw.1",
                ipAddress = "10.10.10.10",
                location = "Stockholm, Sweden",
            ),
        )

        testee.viewState().test {
            assertEquals(
                ViewState(
                    connectionState = Disconnected,
                ),
                this.awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenEnabledAndServerDetailsAvailableThenEmitViewStateConnectedWithDetails() = runTest {
        whenever(networkProtectionRepository.enabledTimeInMillis).thenReturn(-1)
        whenever(networkProtectionRepository.serverDetails).thenReturn(
            ServerDetails(
                serverName = "euw.1",
                ipAddress = "10.10.10.10",
                location = "Stockholm, Sweden",
            ),
        )
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = ENABLED,
                ),
            ),
        )

        testee.viewState().distinctUntilChanged().test {
            assertEquals(
                ViewState(
                    connectionState = Connected,
                    connectionDetails = ConnectionDetails(
                        location = "Stockholm, Sweden",
                        ipAddress = "10.10.10.10",
                        elapsedConnectedTime = null,
                    ),
                ),
                this.expectMostRecentItem(),
            )
            testee.onDestroy(mock())
            cancelAndIgnoreRemainingEvents()
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
    fun whenReconnectingAndVpnEnabledThenConnectionStateShouldBeConnectingAndAlertShouldShowReconnecting() = runTest {
        whenever(networkProtectionRepository.reconnectStatus).thenReturn(Reconnecting)

        testee = NetworkProtectionManagementViewModel(
            vpnStateMonitor,
            vpnFeaturesRegistry,
            networkProtectionRepository,
            coroutineRule.testDispatcherProvider,
            reconnectNotifications,
            externalVpnDetector,
            networkProtectionPixels,
        )

        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = ENABLED,
                ),
            ),
        )

        testee.viewState().distinctUntilChanged().test {
            assertEquals(
                ViewState(
                    connectionState = Connecting,
                    alertState = ShowReconnecting,
                ),
                this.expectMostRecentItem(),
            )
            testee.onDestroy(mock())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenReconnectingAndVpnDisabledThenConnectionStateShouldBeDisconnectedAndAlertShouldShowReconnectingFailed() = runTest {
        whenever(networkProtectionRepository.reconnectStatus).thenReturn(Reconnecting)

        testee = NetworkProtectionManagementViewModel(
            vpnStateMonitor,
            vpnFeaturesRegistry,
            networkProtectionRepository,
            coroutineRule.testDispatcherProvider,
            reconnectNotifications,
            externalVpnDetector,
            networkProtectionPixels,
        )

        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = DISABLED,
                ),
            ),
        )

        testee.viewState().distinctUntilChanged().test {
            assertEquals(
                ViewState(
                    connectionState = Disconnected,
                    alertState = ShowReconnectingFailed,
                ),
                this.expectMostRecentItem(),
            )
            testee.onDestroy(mock())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenReconnectingAndVpnEnablingThenConnectionStateShouldBeConnectingAndAlertShouldShowReconnecting() = runTest {
        whenever(networkProtectionRepository.reconnectStatus).thenReturn(Reconnecting)

        testee = NetworkProtectionManagementViewModel(
            vpnStateMonitor,
            vpnFeaturesRegistry,
            networkProtectionRepository,
            coroutineRule.testDispatcherProvider,
            reconnectNotifications,
            externalVpnDetector,
            networkProtectionPixels,
        )

        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = ENABLING,
                ),
            ),
        )

        testee.viewState().distinctUntilChanged().test {
            assertEquals(
                ViewState(
                    connectionState = Connecting,
                    alertState = ShowReconnecting,
                ),
                this.expectMostRecentItem(),
            )
            testee.onDestroy(mock())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnStartVpnThenResetValuesInRepository() {
        testee.onStartVpn()

        verify(reconnectNotifications).clearNotifications()
        verify(networkProtectionRepository).reconnectStatus = NotReconnecting
        verify(networkProtectionRepository).enabledTimeInMillis = -1L
    }

    @Test
    fun whenOnNetpToggleClickedToDisabledThenResetReconnectState() {
        testee.onNetpToggleClicked(false)

        verify(reconnectNotifications).clearNotifications()
    }

    @Test
    fun whenVpnStateIsDisabledAndReconnectingThenAlertStateIsShowReconnectingFailed() {
        assertEquals(ShowReconnectingFailed, testee.getAlertState(DISABLED, Reconnecting, null))
    }

    @Test
    fun whenVpnStateIsDisabledAndReconnectingFailedThenAlertStateIsShowReconnectingFailed() {
        assertEquals(ShowReconnectingFailed, testee.getAlertState(DISABLED, ReconnectingFailed, UNKNOWN))
    }

    @Test
    fun whenVpnStateIsEnabledAndReconnectingThenAlertStateIsShowReconnecting() {
        assertEquals(ShowReconnecting, testee.getAlertState(ENABLED, Reconnecting, null))
    }

    @Test
    fun whenVpnStateIsEnablingAndReconnectingThenAlertStateIsShowReconnecting() {
        assertEquals(ShowReconnecting, testee.getAlertState(ENABLING, Reconnecting, null))
    }

    @Test
    fun whenNotReconnectingThenAlertStateIsNone() {
        assertEquals(None, testee.getAlertState(DISABLED, NotReconnecting, UNKNOWN))
    }

    @Test
    fun whenStopReasonIsRevokedAndNotReconnectingThenAlertStateIsShowRevoked() {
        assertEquals(ShowRevoked, testee.getAlertState(DISABLED, NotReconnecting, REVOKED))
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
            assertEquals(ShowAlwaysOnPromotionDialog, this.expectMostRecentItem())
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
    fun whenOnCreateWithAlwaysOnLockdownThenDoNotEmitShowAlwaysOnLockdownDialogCommand() = runTest {
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = ENABLED,
                    alwaysOnState = AlwaysOnState.ALWAYS_ON_LOCKED_DOWN,
                ),
            ),
        )

        testee.commands().test {
            testee.onCreate(mock())
            assertEquals(ShowAlwaysOnLockdownDialog, this.awaitItem())
            verify(networkProtectionPixels).reportAlwaysOnLockdownDialogShown()
        }
    }

    @Test
    fun whenOnCreateWithoutAlwaysOnLockdowmThenDoNotEmitShowAlwaysOnLockdownDialogCommand() = runTest {
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(
                    state = ENABLED,
                    alwaysOnState = AlwaysOnState.ALWAYS_ON_ENABLED,
                ),
            ),
        )

        testee.commands().test {
            testee.onCreate(mock())
            this.ensureAllEventsConsumed()
        }
    }
}
