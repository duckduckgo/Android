/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.subscription.settings

import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionAccessState
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTED
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.DISCONNECTED
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_SETTINGS_PRESSED
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.Command
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Disabled
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Enabled
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Hidden
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProSettingNetPViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val networkProtectionState: NetworkProtectionState = mock()
    private val networkProtectionAccessState: NetworkProtectionAccessState = mock()
    private val subscriptions: Subscriptions = mock()
    private lateinit var proSettingNetPViewModel: ProSettingNetPViewModel

    @Before
    fun before() {
        proSettingNetPViewModel = ProSettingNetPViewModel(
            networkProtectionState,
            networkProtectionAccessState,
            subscriptions,
            coroutineTestRule.testDispatcherProvider,
            pixel,
        )
    }

    @Test
    fun `when NetP setting clicked and not onboarded then return screen for current state and send pixel`() = runTest {
        val testScreen = object : ActivityParams {}
        whenever(networkProtectionAccessState.getScreenForCurrentState()).thenReturn(testScreen)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        proSettingNetPViewModel.commands().test {
            proSettingNetPViewModel.onNetPSettingClicked()

            assertEquals(Command.OpenNetPScreen(testScreen), awaitItem())
            verify(pixel).fire(NETP_SETTINGS_PRESSED, parameters = mapOf("was_used_before" to "0"))

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when NetP setting clicked and onboarded then return screen for current state and send pixel`() = runTest {
        val testScreen = object : ActivityParams {}
        whenever(networkProtectionAccessState.getScreenForCurrentState()).thenReturn(testScreen)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)

        proSettingNetPViewModel.commands().test {
            proSettingNetPViewModel.onNetPSettingClicked()

            assertEquals(Command.OpenNetPScreen(testScreen), awaitItem())
            verify(pixel).fire(NETP_SETTINGS_PRESSED, parameters = mapOf("was_used_before" to "1"))

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when subscription state is unknown then NetpEntryState is hidden`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(UNKNOWN)

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is unknown and vpn is enabled then vpn is stopped`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(UNKNOWN)

        proSettingNetPViewModel.onStart(mock())

        verify(networkProtectionState).stop()
    }

    @Test
    fun `when subscription state is inactive and no netp product available then NetpEntryState is hidden`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(INACTIVE)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is inactive and netp product available then NetpEntryState is disabled`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(INACTIVE)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.NetP))

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is inactive and vpn is enabled then vpn is stopped`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(INACTIVE)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        proSettingNetPViewModel.onStart(mock())

        verify(networkProtectionState).stop()
    }

    @Test
    fun `when subscription state is expired and no netp product available then NetpEntryState is hidden`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(EXPIRED)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is expired and netp product available then NetpEntryState is disabled`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(EXPIRED)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.NetP))

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is expired and vpn is enabled then vpn is stopped`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(EXPIRED)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        proSettingNetPViewModel.onStart(mock())

        verify(networkProtectionState).stop()
    }

    @Test
    fun `when subscription state is waiting and no netp product available then NetpEntryState is hidden`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(WAITING)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is waiting and netp product available then NetpEntryState is disabled`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(WAITING)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.NetP))

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is waiting and vpn is enabled then vpn is stopped`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(WAITING)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        proSettingNetPViewModel.onStart(mock())

        verify(networkProtectionState).stop()
    }

    @Test
    fun `when subscription state is auto renewable and entitled then NetpEntryState is enabled`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.NetP)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Enabled(isActive = false),
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is auto renewable and entitled and connection is active then NetpEntryState is enabled and active`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(CONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.NetP)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Enabled(isActive = true),
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is auto renewable and not entitled then NetpEntryState is hidden`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is auto renewable and not entitled then vpn is stopped`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        proSettingNetPViewModel.onStart(mock())

        verify(networkProtectionState).stop()
    }

    @Test
    fun `when subscription state is not auto renewable and entitled then NetpEntryState is enabled`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.NetP)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(NOT_AUTO_RENEWABLE)

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Enabled(isActive = false),
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is not auto renewable and entitled and connection is active then NetpEntryState is enabled and active`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(CONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.NetP)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(NOT_AUTO_RENEWABLE)

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Enabled(isActive = true),
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is not auto renewable and not entitled then NetpEntryState is hidden`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(NOT_AUTO_RENEWABLE)

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is not auto renewable and not entitled then vpn is stopped`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(NOT_AUTO_RENEWABLE)

        proSettingNetPViewModel.onStart(mock())

        verify(networkProtectionState).stop()
    }

    @Test
    fun `when subscription state is grace period and entitled then NetpEntryState is enabled`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.NetP)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(GRACE_PERIOD)

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Enabled(isActive = false),
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is grace period and entitled and connection is active then NetpEntryState is enabled and active`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(CONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.NetP)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(GRACE_PERIOD)

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Enabled(isActive = true),
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is grace period and not entitled then NetpEntryState is hidden`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(GRACE_PERIOD)

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().netPEntryState,
            )
        }
    }

    @Test
    fun `when subscription state is grace period and not entitled then vpn is stopped`() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(GRACE_PERIOD)

        proSettingNetPViewModel.onStart(mock())

        verify(networkProtectionState).stop()
    }
}
