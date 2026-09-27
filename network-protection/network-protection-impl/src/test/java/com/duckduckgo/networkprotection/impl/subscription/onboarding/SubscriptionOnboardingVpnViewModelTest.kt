/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.subscription.onboarding

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTED
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTING
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.DISCONNECTED
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.settings.geoswitching.getDisplayableCountry
import com.duckduckgo.networkprotection.impl.subscription.onboarding.SubscriptionOnboardingVpnStepPlugin.Companion.VPN_STEP_ID
import com.duckduckgo.networkprotection.impl.subscription.onboarding.SubscriptionOnboardingVpnViewModel.Command
import com.duckduckgo.networkprotection.impl.subscription.onboarding.SubscriptionOnboardingVpnViewModel.VPNActivationError
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.SKIPPED
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SubscriptionOnboardingVpnViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val controller: SubscriptionOnboardingController = mock()

    @Test
    fun whenConnectionInfoLoadsThenViewStateShowsIpAndFlagFormattedLocation() = runTest {
        val testee = createViewModel(info = ConnectionInfo(ip = "137.220.87.36", city = "Birmingham", country = "GB"))

        testee.viewState().test {
            val state = awaitItem()
            assertEquals("137.220.87.36", state.ipAddress)
            assertEquals("🇬🇧 Birmingham, ${getDisplayableCountry("GB")}", state.location)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenConnectionInfoFailsThenViewStateShowsUnknownPlaceholders() = runTest {
        val testee = createViewModel(info = null)

        testee.viewState().test {
            val state = awaitItem()
            assertEquals("XXX.XXX.XX.XXX", state.ipAddress)
            assertEquals("XX, XX", state.location)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVpnConnectedThenVpnEnabledIsTrue() = runTest {
        val testee = createViewModel(connectionState = CONNECTED)

        testee.viewState().test {
            assertTrue(awaitItem().vpnEnabled == true)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVpnReconnectsAfterConnectedThenStaysOnSuccessAndNoError() = runTest {
        // Once connected, later CONNECTING/DISCONNECTED churn must not rewind the screen or invent a failure.
        val networkProtectionState = mock<NetworkProtectionState>().apply {
            whenever(getConnectionStateFlow()).thenReturn(flowOf(CONNECTED, CONNECTING, DISCONNECTED))
        }
        val testee = SubscriptionOnboardingVpnViewModel(
            controller,
            FakeConnectionService(ConnectionInfo(ip = "137.220.87.36", city = "Birmingham", country = "GB")),
            networkProtectionState,
            mock<WgTunnelConfig>(),
            coroutineRule.testDispatcherProvider,
        )

        testee.viewState().test {
            val state = awaitItem()
            assertTrue(state.vpnEnabled == true)
            assertFalse(state.activating)
            assertNull(state.vpnActivationError)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVpnDisconnectedThenVpnEnabledIsFalse() = runTest {
        val testee = createViewModel(connectionState = DISCONNECTED)

        testee.viewState().test {
            assertFalse(awaitItem().vpnEnabled == true)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVpnConnectingThenActivatingIsTrueAndVpnNotYetEnabled() = runTest {
        val testee = createViewModel(connectionState = CONNECTING)

        testee.viewState().test {
            val state = awaitItem()
            assertTrue(state.activating)
            assertFalse(state.vpnEnabled == true)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVpnPermissionDeniedThenActivationErrorIsPermissionDenied() = runTest {
        val testee = createViewModel(connectionState = DISCONNECTED)

        testee.onVpnPermissionDenied()

        testee.viewState().test {
            assertEquals(VPNActivationError.PERMISSION_DENIED, awaitItem().vpnActivationError)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenActivationFallsBackToDisconnectedThenActivationErrorIsFailed() = runTest {
        val networkProtectionState = mock<NetworkProtectionState>().apply {
            whenever(getConnectionStateFlow()).thenReturn(flowOf(CONNECTING, DISCONNECTED))
        }
        val testee = SubscriptionOnboardingVpnViewModel(
            controller,
            FakeConnectionService(ConnectionInfo(ip = "137.220.87.36", city = "Birmingham", country = "GB")),
            networkProtectionState,
            mock<WgTunnelConfig>(),
            coroutineRule.testDispatcherProvider,
        )

        testee.viewState().test {
            val state = awaitItem()
            assertEquals(VPNActivationError.CONNECTION_FAILED, state.vpnActivationError)
            assertFalse(state.activating)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPermissionDeniedThenGrantedButActivationSilentlyFailsThenConnectionFailedNotStuck() = runTest {
        val networkProtectionState = mock<NetworkProtectionState>().apply {
            whenever(getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        }
        val testee = SubscriptionOnboardingVpnViewModel(
            controller,
            FakeConnectionService(ConnectionInfo(ip = "137.220.87.36", city = "Birmingham", country = "GB")),
            networkProtectionState,
            mock<WgTunnelConfig>(),
            coroutineRule.testDispatcherProvider,
        )

        testee.onVpnPermissionDenied()
        testee.onVpnPermissionGranted()

        verify(networkProtectionState).start()
        testee.viewState().test {
            val state = awaitItem()
            assertEquals(VPNActivationError.CONNECTION_FAILED, state.vpnActivationError)
            assertFalse(state.activating)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenServerLocationWellFormedThenFormattedAsFlagCityCountry() {
        assertEquals(
            "🇳🇱 Amsterdam, ${getDisplayableCountry("NL")}",
            formatVpnServerLocation("Amsterdam, NL"),
        )
    }

    @Test
    fun whenServerLocationMissingOrMalformedThenNull() {
        assertNull(formatVpnServerLocation(null))
        assertNull(formatVpnServerLocation("Amsterdam"))
    }

    @Test
    fun whenPrimaryCtaClickedAndVpnOffThenVpnPermissionRequested() = runTest {
        val testee = createViewModel(connectionState = DISCONNECTED)

        testee.onPrimaryCtaClicked()

        testee.commands.test {
            assertEquals(Command.RequestVpnPermission, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
        verifyNoInteractions(controller)
    }

    @Test
    fun whenPrimaryCtaClickedAndVpnOnThenInfoPageShown() = runTest {
        val testee = createViewModel(connectionState = CONNECTED)

        testee.onPrimaryCtaClicked()

        testee.viewState().test {
            assertTrue(awaitItem().showingVPNInfoBanners)
            cancelAndConsumeRemainingEvents()
        }
        verifyNoInteractions(controller)
    }

    @Test
    fun whenPrimaryCtaClickedOnInfoPageThenStepCompleted() = runTest {
        val testee = createViewModel(connectionState = CONNECTED)

        testee.onPrimaryCtaClicked()
        testee.onPrimaryCtaClicked()

        verify(controller).onStepFinished(VPN_STEP_ID, COMPLETED)
    }

    @Test
    fun whenPrimaryCtaClickedWhileActivatingThenNothingHappens() = runTest {
        val testee = createViewModel(connectionState = CONNECTING)

        testee.onPrimaryCtaClicked()

        testee.commands.test {
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
        verifyNoInteractions(controller)
    }

    @Test
    fun whenSkipClickedThenStepSkipped() = runTest {
        val testee = createViewModel(connectionState = DISCONNECTED)

        testee.onSkipClicked()

        verify(controller).onStepFinished(VPN_STEP_ID, SKIPPED)
    }

    private fun createViewModel(
        info: ConnectionInfo? = ConnectionInfo(ip = "137.220.87.36", city = "Birmingham", country = "GB"),
        connectionState: ConnectionState = DISCONNECTED,
    ): SubscriptionOnboardingVpnViewModel {
        val networkProtectionState = mock<NetworkProtectionState>().apply {
            whenever(getConnectionStateFlow()).thenReturn(flowOf(connectionState))
        }
        return SubscriptionOnboardingVpnViewModel(
            controller,
            FakeConnectionService(info),
            networkProtectionState,
            mock<WgTunnelConfig>(),
            coroutineRule.testDispatcherProvider,
        )
    }

    private class FakeConnectionService(
        private val info: ConnectionInfo?,
    ) : SubscriptionOnboardingConnectionService {
        override suspend fun getConnectionInfo(): ConnectionInfo = info ?: throw RuntimeException("unavailable")
    }
}
