package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLING
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PproUpsellRevokedMessagePluginTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val browserNav: BrowserNav = mock()
    private val subscriptions: Subscriptions = mock()
    private val deviceShieldPixels: DeviceShieldPixels = mock()
    private val appTPStateMessageToggle: AppTPStateMessageToggle = mock()
    private lateinit var plugin: PproUpsellRevokedMessagePlugin

    private val mockDisabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }

    @Before
    fun setUp() = runTest {
        whenever(subscriptions.isFreeTrialEligible()).thenReturn(false)
        whenever(appTPStateMessageToggle.freeTrialCopy()).thenReturn(mockDisabledToggle)
        plugin = PproUpsellRevokedMessagePlugin(subscriptions, browserNav, deviceShieldPixels, appTPStateMessageToggle)
    }

    @Test
    fun whenVPNIsDisabledBy3rdPartyAndUserNotEligibleToPProThenGetViewReturnsNull() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(false)
        whenever(subscriptions.isSignedIn()).thenReturn(false)

        val result = plugin.getView(context, VpnState(state = DISABLED, stopReason = REVOKED)) {}

        assertNull(result)
    }

    @Test
    fun whenVPNIsDisabledBy3rdPartyOnAndUserIsSubscriberToPProThenGetViewReturnsNull() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.isSignedIn()).thenReturn(true)

        val result = plugin.getView(context, VpnState(state = DISABLED, stopReason = REVOKED)) {}

        assertNull(result)
    }

    @Test
    fun whenVPNIsDisabledBy3rdPartyOnAndUserIsEligibleToPproThenGetViewReturnsNotNull() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.isSignedIn()).thenReturn(false)

        val result = plugin.getView(context, VpnState(state = DISABLED, stopReason = REVOKED)) {}

        assertNotNull(result)
    }

    @Test
    fun whenVPNIsDisabledByUserAndUserIsEligibleToPproThenGetViewReturnsNull() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.isSignedIn()).thenReturn(false)

        val result = plugin.getView(context, VpnState(state = DISABLED, stopReason = SELF_STOP())) {}

        assertNull(result)
    }

    @Test
    fun whenVPNIsEnablingAndUserIsEligibleToPproThenGetViewReturnsNull() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.isSignedIn()).thenReturn(false)

        val result = plugin.getView(context, VpnState(state = ENABLING)) {}

        assertNull(result)
    }

    @Test
    fun whenVPNIsEnabledAndUserIsEligibleToPproThenGetViewReturnsNull() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.isSignedIn()).thenReturn(false)

        val result = plugin.getView(context, VpnState(state = ENABLED)) {}

        assertNull(result)
    }
}
