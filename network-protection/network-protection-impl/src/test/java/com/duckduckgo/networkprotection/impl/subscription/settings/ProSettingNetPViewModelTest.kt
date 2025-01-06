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
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTING
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.DISCONNECTED
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_SETTINGS_PRESSED
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.Command
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Activating
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Expired
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Hidden
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Subscribed
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
    private val networkProtectionSettingsState: NetworkProtectionSettingsState = mock()
    private lateinit var proSettingNetPViewModel: ProSettingNetPViewModel

    @Before
    fun before() {
        proSettingNetPViewModel = ProSettingNetPViewModel(
            networkProtectionSettingsState,
            networkProtectionState,
            networkProtectionAccessState,
            coroutineTestRule.testDispatcherProvider,
            pixel,
        )
    }

    @Test
    fun whenNetPSettingClickedThenNetPScreenOpened() = runTest {
        val testScreen = object : ActivityParams {}
        whenever(networkProtectionAccessState.getScreenForCurrentState()).thenReturn(testScreen)

        proSettingNetPViewModel.commands().test {
            proSettingNetPViewModel.onNetPSettingClicked()

            assertEquals(Command.OpenNetPScreen(testScreen), awaitItem())
            verify(pixel).fire(NETP_SETTINGS_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNetPVisibilityStateIsHiddenThenNetPEntryStateIsHidden() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionSettingsState.getNetPSettingsStateFlow()).thenReturn(flowOf(NetPSettingsState.Hidden))

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPVisibilityStateIsActivatingThenNetPEntryStateIsActivating() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionSettingsState.getNetPSettingsStateFlow()).thenReturn(flowOf(NetPSettingsState.Visible.Activating))

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Activating,
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPVisibilityStateConnectedAndAccessStateIsSubscribedThenNetPEntryStateIsSubscribedAndActive() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(CONNECTED))
        whenever(networkProtectionSettingsState.getNetPSettingsStateFlow()).thenReturn(flowOf(NetPSettingsState.Visible.Subscribed))

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Subscribed(isActive = true),
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPVisibilityStateDisconnectedAndAccessStateIsSubscribedThenNetPEntryStateIsSubscribedAndInactive() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionSettingsState.getNetPSettingsStateFlow()).thenReturn(flowOf(NetPSettingsState.Visible.Subscribed))

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Subscribed(isActive = false),
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPVisibilityStateIsConnectingThenNetPEntryStateIsSubscribedAndNotActive() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(CONNECTING))
        whenever(networkProtectionSettingsState.getNetPSettingsStateFlow()).thenReturn(flowOf(NetPSettingsState.Visible.Subscribed))

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Subscribed(isActive = false),
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPVisibilityStateIsExpiredThenNetPEntryStateIsExpired() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionSettingsState.getNetPSettingsStateFlow()).thenReturn(flowOf(NetPSettingsState.Visible.Expired))

        proSettingNetPViewModel.onStart(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Expired,
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }
}
