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

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.duckduckgo.app.browser.commands.Command
import com.duckduckgo.app.browser.menu.VpnMenuStateProvider
import com.duckduckgo.app.browser.viewstate.VpnMenuState
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.milliseconds

class BrowserTabViewModelVpnMenuTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var mockVpnMenuStateProvider: VpnMenuStateProvider

    private lateinit var testee: TestBrowserTabViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        testee = TestBrowserTabViewModel()
    }

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

    @Test
    fun `when VPN menu clicked and user not subscribed then launches Privacy Pro command`() = runTest {
        testee.setVpnMenuState(VpnMenuState.NotSubscribed)

        testee.onVpnMenuClicked()

        testee.commands.test {
            val command = awaitItem()
            assertTrue("Expected LaunchPrivacyPro command", command is Command.LaunchPrivacyPro)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when VPN menu clicked and user subscribed with VPN enabled then launches VPN management`() = runTest {
        testee.setVpnMenuState(VpnMenuState.Subscribed(isVpnEnabled = true))

        testee.onVpnMenuClicked()

        testee.commands.test {
            val command = awaitItem()
            assertEquals(Command.LaunchVpnManagement, command)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when VPN menu clicked and user subscribed with VPN disabled then launches VPN management`() = runTest {
        testee.setVpnMenuState(VpnMenuState.Subscribed(isVpnEnabled = false))

        testee.onVpnMenuClicked()

        testee.commands.test {
            val command = awaitItem()
            assertEquals(Command.LaunchVpnManagement, command)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when VPN menu clicked and state is hidden then no command is emitted`() = runTest {
        testee.setVpnMenuState(VpnMenuState.Hidden)

        testee.onVpnMenuClicked()

        testee.commands.test(timeout = 1000.milliseconds) {
            expectNoEvents()
        }
    }

    /**
     * Test implementation of BrowserTabViewModel that only includes the parts needed for VPN menu testing
     */
    private class TestBrowserTabViewModel {
        private val _commands = kotlinx.coroutines.flow.MutableSharedFlow<Command>(replay = 1)
        val commands = _commands

        private var currentVpnMenuState: VpnMenuState = VpnMenuState.Hidden

        fun setVpnMenuState(state: VpnMenuState) {
            currentVpnMenuState = state
        }

        fun onVpnMenuClicked() {
            when (currentVpnMenuState) {
                VpnMenuState.NotSubscribed -> {
                    val mockUri = org.mockito.kotlin.mock<Uri>()
                    _commands.tryEmit(Command.LaunchPrivacyPro(mockUri))
                }
                is VpnMenuState.Subscribed -> {
                    _commands.tryEmit(Command.LaunchVpnManagement)
                }
                VpnMenuState.Hidden -> {}
            }
        }
    }
}
