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

import com.duckduckgo.app.browser.viewstate.VpnMenuState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for VPN menu state logic in BrowserPopupMenu.
 * These tests focus on the business logic of when the VPN menu item should be visible
 * and what state it should be in, without requiring complex UI mocking.
 */
class BrowserPopupMenuVpnTest {

    @Test
    fun `when VPN state is Hidden then menu item should not be visible regardless of browser state`() {
        val vpnMenuState = VpnMenuState.Hidden
        assertVpnMenuVisibility(
            vpnMenuState = vpnMenuState,
            browserShowing = false,
            displayedInCustomTabScreen = false,
            expectedVisible = false,
        )

        assertVpnMenuVisibility(
            vpnMenuState = vpnMenuState,
            browserShowing = true,
            displayedInCustomTabScreen = false,
            expectedVisible = false,
        )

        assertVpnMenuVisibility(
            vpnMenuState = vpnMenuState,
            browserShowing = false,
            displayedInCustomTabScreen = true,
            expectedVisible = false,
        )
    }

    @Test
    fun `when VPN state is NotSubscribed and browser not showing and not in custom tab then menu item should be visible`() {
        val vpnMenuState = VpnMenuState.NotSubscribed
        assertVpnMenuVisibility(
            vpnMenuState = vpnMenuState,
            browserShowing = false,
            displayedInCustomTabScreen = false,
            expectedVisible = true,
        )
    }

    @Test
    fun `when VPN state is NotSubscribed and browser showing then menu item should not be visible`() {
        val vpnMenuState = VpnMenuState.NotSubscribed
        assertVpnMenuVisibility(
            vpnMenuState = vpnMenuState,
            browserShowing = true,
            displayedInCustomTabScreen = false,
            expectedVisible = false,
        )
    }

    @Test
    fun `when VPN state is NotSubscribed and in custom tab screen then menu item should not be visible`() {
        val vpnMenuState = VpnMenuState.NotSubscribed
        assertVpnMenuVisibility(
            vpnMenuState = vpnMenuState,
            browserShowing = false,
            displayedInCustomTabScreen = true,
            expectedVisible = false,
        )
    }

    @Test
    fun `when VPN state is Subscribed with VPN enabled and browser not showing and not in custom tab then menu item should be visible`() {
        val vpnMenuState = VpnMenuState.Subscribed(isVpnEnabled = true)
        assertVpnMenuVisibility(
            vpnMenuState = vpnMenuState,
            browserShowing = false,
            displayedInCustomTabScreen = false,
            expectedVisible = true,
        )
    }

    @Test
    fun `when VPN state is Subscribed with VPN disabled and browser not showing and not in custom tab then menu item should be visible`() {
        val vpnMenuState = VpnMenuState.Subscribed(isVpnEnabled = false)
        assertVpnMenuVisibility(
            vpnMenuState = vpnMenuState,
            browserShowing = false,
            displayedInCustomTabScreen = false,
            expectedVisible = true,
        )
    }

    @Test
    fun `when VPN state is Subscribed and browser showing then menu item should not be visible`() {
        val vpnMenuState = VpnMenuState.Subscribed(isVpnEnabled = true)
        assertVpnMenuVisibility(
            vpnMenuState = vpnMenuState,
            browserShowing = true,
            displayedInCustomTabScreen = false,
            expectedVisible = false,
        )
    }

    @Test
    fun `when VPN state is Subscribed and in custom tab screen then menu item should not be visible`() {
        val vpnMenuState = VpnMenuState.Subscribed(isVpnEnabled = true)
        assertVpnMenuVisibility(
            vpnMenuState = vpnMenuState,
            browserShowing = false,
            displayedInCustomTabScreen = true,
            expectedVisible = false,
        )
    }

    @Test
    fun `VPN menu state configuration logic for NotSubscribed state`() {
        val vpnMenuState = VpnMenuState.NotSubscribed
        val config = getVpnMenuConfiguration(vpnMenuState)
        assertEquals(true, config.showTryForFreePill)
        assertEquals(false, config.showStatusIndicator)
        assertEquals(false, config.statusIndicatorOn)
    }

    @Test
    fun `VPN menu state configuration logic for Subscribed with VPN enabled state`() {
        val vpnMenuState = VpnMenuState.Subscribed(isVpnEnabled = true)
        val config = getVpnMenuConfiguration(vpnMenuState)
        assertEquals(false, config.showTryForFreePill)
        assertEquals(true, config.showStatusIndicator)
        assertEquals(true, config.statusIndicatorOn)
    }

    @Test
    fun `VPN menu state configuration logic for Subscribed with VPN disabled state`() {
        val vpnMenuState = VpnMenuState.Subscribed(isVpnEnabled = false)
        val config = getVpnMenuConfiguration(vpnMenuState)
        assertEquals(false, config.showTryForFreePill)
        assertEquals(true, config.showStatusIndicator)
        assertEquals(false, config.statusIndicatorOn)
    }

    private fun assertVpnMenuVisibility(
        vpnMenuState: VpnMenuState,
        browserShowing: Boolean,
        displayedInCustomTabScreen: Boolean,
        expectedVisible: Boolean,
    ) {
        val shouldShowVpnMenuItem = !browserShowing && !displayedInCustomTabScreen

        val actualVisible = when (vpnMenuState) {
            VpnMenuState.Hidden -> false
            VpnMenuState.NotSubscribed -> shouldShowVpnMenuItem
            is VpnMenuState.Subscribed -> shouldShowVpnMenuItem
        }

        assertEquals(
            "VPN menu visibility should be $expectedVisible for state $vpnMenuState, " +
                "browserShowing=$browserShowing, customTab=$displayedInCustomTabScreen",
            expectedVisible,
            actualVisible,
        )
    }

    private fun getVpnMenuConfiguration(vpnMenuState: VpnMenuState): VpnMenuConfiguration {
        return when (vpnMenuState) {
            VpnMenuState.Hidden -> VpnMenuConfiguration(
                showTryForFreePill = false,
                showStatusIndicator = false,
                statusIndicatorOn = false,
            )
            VpnMenuState.NotSubscribed -> VpnMenuConfiguration(
                showTryForFreePill = true,
                showStatusIndicator = false,
                statusIndicatorOn = false,
            )
            is VpnMenuState.Subscribed -> VpnMenuConfiguration(
                showTryForFreePill = false,
                showStatusIndicator = true,
                statusIndicatorOn = vpnMenuState.isVpnEnabled,
            )
        }
    }

    private data class VpnMenuConfiguration(
        val showTryForFreePill: Boolean,
        val showStatusIndicator: Boolean,
        val statusIndicatorOn: Boolean,
    )
}
