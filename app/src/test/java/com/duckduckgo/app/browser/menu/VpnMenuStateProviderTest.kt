/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.menu

import app.cash.turbine.test
import com.duckduckgo.app.browser.viewstate.VpnMenuState
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState
import com.duckduckgo.subscriptions.api.Product.NetP
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VpnMenuStateProviderTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private val subscriptions: Subscriptions = mock()

    @Mock
    private val networkProtectionState: NetworkProtectionState = mock()

    @Mock
    private val connectionState: ConnectionState = mock()

    @Mock
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()

    @Mock
    private val vpnMenuStore: VpnMenuStore = mock()

    @Mock
    private val featureToggle: Toggle = mock()

    private lateinit var testee: VpnMenuStateProviderImpl

    @Before
    fun setUp() {
        whenever(androidBrowserConfigFeature.vpnMenuItem()).thenReturn(featureToggle)
        whenever(featureToggle.isEnabled()).thenReturn(true)
        whenever(vpnMenuStore.canShowVpnMenuForNotSubscribed()).thenReturn(true)
        testee = VpnMenuStateProviderImpl(
            subscriptions,
            networkProtectionState,
            androidBrowserConfigFeature,
            vpnMenuStore,
            coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when user has active subscription with NetP entitlement and VPN connected then return Subscribed with VPN enabled`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(NetP)))
            whenever(connectionState.isConnected()).thenReturn(true)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))

            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.Subscribed(isVpnEnabled = true), state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user has active subscription with NetP entitlement and VPN disconnected then return Subscribed with VPN disabled`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(NetP)))
            whenever(connectionState.isConnected()).thenReturn(false)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))

            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.Subscribed(isVpnEnabled = false), state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user has NOT_AUTO_RENEWABLE subscription with NetP entitlement and VPN connected then return Subscribed with VPN enabled`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.NOT_AUTO_RENEWABLE))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(NetP)))
            whenever(connectionState.isConnected()).thenReturn(true)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))
            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.Subscribed(isVpnEnabled = true), state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user has GRACE_PERIOD subscription with NetP entitlement and VPN connected then return Subscribed with VPN enabled`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.GRACE_PERIOD))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(NetP)))
            whenever(connectionState.isConnected()).thenReturn(true)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))
            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.Subscribed(isVpnEnabled = true), state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user has active subscription but no NetP entitlement then return Hidden`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
            whenever(connectionState.isConnected()).thenReturn(true)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))
            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.Hidden, state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user has no active subscription then return NotSubscribed`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.INACTIVE))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
            whenever(connectionState.isConnected()).thenReturn(false)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))
            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.NotSubscribed, state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user has EXPIRED subscription then return NotSubscribed`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.EXPIRED))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
            whenever(connectionState.isConnected()).thenReturn(false)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))
            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.NotSubscribed, state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user has UNKNOWN subscription status then return NotSubscribed`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.UNKNOWN))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
            whenever(connectionState.isConnected()).thenReturn(false)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))
            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.NotSubscribed, state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user has WAITING subscription status then return NotSubscribed`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.WAITING))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
            whenever(connectionState.isConnected()).thenReturn(false)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))
            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.NotSubscribed, state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when VPN connection state is disconnected then state shows VPN disabled`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(NetP)))
            whenever(connectionState.isConnected()).thenReturn(false)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))

            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.Subscribed(isVpnEnabled = false), state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when subscription status is active with entitlements then state shows subscribed`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(NetP)))
            whenever(connectionState.isConnected()).thenReturn(true)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))

            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.Subscribed(isVpnEnabled = true), state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when feature flag is disabled then return Hidden regardless of subscription status`() =
        runTest {
            whenever(featureToggle.isEnabled()).thenReturn(false)
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(NetP)))
            whenever(connectionState.isConnected()).thenReturn(true)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))

            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.Hidden, state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when feature flag is disabled and user not subscribed then return Hidden`() =
        runTest {
            whenever(featureToggle.isEnabled()).thenReturn(false)
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.INACTIVE))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
            whenever(connectionState.isConnected()).thenReturn(false)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))

            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.Hidden, state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user not subscribed and frequency cap not reached then return NotSubscribed`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.INACTIVE))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
            whenever(connectionState.isConnected()).thenReturn(false)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))
            whenever(vpnMenuStore.canShowVpnMenuForNotSubscribed()).thenReturn(true)

            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.NotSubscribed, state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user not subscribed and frequency cap reached then return NotSubscribedNoPill`() =
        runTest {
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.INACTIVE))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
            whenever(connectionState.isConnected()).thenReturn(false)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(connectionState))
            whenever(vpnMenuStore.canShowVpnMenuForNotSubscribed()).thenReturn(false)

            testee.getVpnMenuState().test {
                val state = awaitItem()
                assertEquals(VpnMenuState.NotSubscribedNoPill, state)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
