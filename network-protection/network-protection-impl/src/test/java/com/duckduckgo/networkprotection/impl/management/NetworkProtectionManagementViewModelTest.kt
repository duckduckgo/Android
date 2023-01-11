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
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLING
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.CheckVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.RequestVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionDetails
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connecting
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Disconnected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ViewState
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
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
    private lateinit var testee: NetworkProtectionManagementViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = NetworkProtectionManagementViewModel(
            vpnStateMonitor,
            vpnFeaturesRegistry,
            networkProtectionRepository,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenOnNetpToggleClickedToEnabledThenEmitCheckVPNPermissionCommand() = runTest {
        testee.commands().test {
            testee.onNetpToggleClicked(true)

            assertEquals(this.awaitItem(), CheckVPNPermission)
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
    fun whenOnRequiredPermissionNotGrantedThenEmitRequestVPNPermission() = runTest {
        testee.commands().test {
            val intent = Intent()
            testee.onRequiredPermissionNotGranted(intent)
            assertEquals(this.awaitItem(), RequestVPNPermission(intent))
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
                this.awaitItem(),
                ViewState(
                    connectionState = Connecting,
                ),
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
                ipAddress = "10.10.10.10",
                location = "Stockholm, Sweden",
            ),
        )

        testee.viewState().test {
            assertEquals(
                this.awaitItem(),
                ViewState(
                    connectionState = Disconnected,
                ),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenEnabledAndServerDetailsAvailableThenEmitViewStateConnectedWithDetails() = runTest {
        whenever(networkProtectionRepository.enabledTimeInMillis).thenReturn(-1)
        whenever(networkProtectionRepository.serverDetails).thenReturn(
            ServerDetails(
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
                this.expectMostRecentItem(),
                ViewState(
                    connectionState = Connected,
                    connectionDetails = ConnectionDetails(
                        location = "Stockholm, Sweden",
                        ipAddress = "10.10.10.10",
                        elapsedConnectedTime = null,
                    ),
                ),
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
}
