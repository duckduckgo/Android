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

package com.duckduckgo.app.browser

import app.cash.turbine.test
import com.duckduckgo.app.browser.menu.VpnMenuStateProvider
import com.duckduckgo.app.browser.viewstate.VpnMenuState
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BrowserTabViewModelVpnMenuTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Mock
    private val mockVpnMenuStateProvider: VpnMenuStateProvider = mock()

    @Test
    fun `when VPN menu state changes to NotSubscribed then flow emits NotSubscribed`() = runTest {
        val vpnMenuStateFlow = flowOf(VpnMenuState.NotSubscribed)
        whenever(mockVpnMenuStateProvider.getVpnMenuState()).thenReturn(vpnMenuStateFlow)

        mockVpnMenuStateProvider.getVpnMenuState().test {
            val state = awaitItem()
            assertEquals(VpnMenuState.NotSubscribed, state)
            awaitComplete()
        }
    }

    @Test
    fun `when VPN menu state changes to Subscribed with VPN enabled then flow emits correct state`() = runTest {
        val vpnMenuStateFlow = flowOf(VpnMenuState.Subscribed(isVpnEnabled = true))
        whenever(mockVpnMenuStateProvider.getVpnMenuState()).thenReturn(vpnMenuStateFlow)

        mockVpnMenuStateProvider.getVpnMenuState().test {
            val state = awaitItem()
            assertEquals(VpnMenuState.Subscribed(isVpnEnabled = true), state)
            awaitComplete()
        }
    }

    @Test
    fun `when VPN menu state changes to Subscribed with VPN disabled then flow emits correct state`() = runTest {
        val vpnMenuStateFlow = flowOf(VpnMenuState.Subscribed(isVpnEnabled = false))
        whenever(mockVpnMenuStateProvider.getVpnMenuState()).thenReturn(vpnMenuStateFlow)

        mockVpnMenuStateProvider.getVpnMenuState().test {
            val state = awaitItem()
            assertEquals(VpnMenuState.Subscribed(isVpnEnabled = false), state)
            awaitComplete()
        }
    }

    @Test
    fun `when VPN menu state changes to Hidden then flow emits Hidden`() = runTest {
        val vpnMenuStateFlow = flowOf(VpnMenuState.Hidden)
        whenever(mockVpnMenuStateProvider.getVpnMenuState()).thenReturn(vpnMenuStateFlow)

        mockVpnMenuStateProvider.getVpnMenuState().test {
            val state = awaitItem()
            assertEquals(VpnMenuState.Hidden, state)
            awaitComplete()
        }
    }

    @Test
    fun `when VPN menu state provider is called then it returns the expected flow`() = runTest {
        val vpnMenuStateFlow = flowOf(VpnMenuState.Hidden)
        whenever(mockVpnMenuStateProvider.getVpnMenuState()).thenReturn(vpnMenuStateFlow)

        mockVpnMenuStateProvider.getVpnMenuState().test {
            val state = awaitItem()
            assertEquals(VpnMenuState.Hidden, state)
            awaitComplete()
        }
    }
}
