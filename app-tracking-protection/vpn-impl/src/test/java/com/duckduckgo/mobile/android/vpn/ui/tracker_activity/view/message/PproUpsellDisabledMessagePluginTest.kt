package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.mobile.android.vpn.network.ExternalVpnDetector
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PproUpsellDisabledMessagePluginTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val browserNav: BrowserNav = mock()
    private val subscriptions: Subscriptions = mock()
    private val deviceShieldPixels: DeviceShieldPixels = mock()
    private val vpnDetector: ExternalVpnDetector = mock()
    private lateinit var plugin: PproUpsellDisabledMessagePlugin

    @Before
    fun setUp() {
        plugin = PproUpsellDisabledMessagePlugin(subscriptions, vpnDetector, browserNav, deviceShieldPixels)
    }

    @Test
    fun whenVPNIsDisabledWith3rdPartyOnAndUserNotEligibleToPProThenGetViewReturnsNull() = runTest {
        whenever(vpnDetector.isExternalVpnDetected()).thenReturn(true)
        whenever(subscriptions.isEligible()).thenReturn(false)

        val result = plugin.getView(context, VpnState(state = DISABLED, stopReason = SELF_STOP())) {}

        assertNull(result)
    }

    @Test
    fun whenVPNIsDisabledWith3rdPartyOnAndUserIsSubscriberToPProThenGetViewReturnsNull() = runTest {
        whenever(vpnDetector.isExternalVpnDetected()).thenReturn(true)
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getAccessToken()).thenReturn("123")

        val result = plugin.getView(context, VpnState(state = DISABLED, stopReason = SELF_STOP())) {}

        assertNull(result)
    }

    @Test
    fun whenVPNIsDisabledWith3rdPartyOnAndUserIsEligibleToPproThenGetViewReturnsNotNull() = runTest {
        whenever(vpnDetector.isExternalVpnDetected()).thenReturn(true)
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getAccessToken()).thenReturn(null)

        val result = plugin.getView(context, VpnState(state = DISABLED, stopReason = SELF_STOP())) {}

        assertNotNull(result)
    }

    @Test
    fun whenVPNIsDisabledBy3rdPartyOnAndUserIsEligibleToPproThenGetViewReturnsNull() = runTest {
        whenever(vpnDetector.isExternalVpnDetected()).thenReturn(true)
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getAccessToken()).thenReturn(null)

        val result = plugin.getView(context, VpnState(state = DISABLED, stopReason = REVOKED)) {}

        assertNull(result)
    }

    @Test
    fun whenVPNIsEnablingWith3rdPartyOnAndUserIsEligibleToPproThenGetViewReturnsNull() = runTest {
        whenever(vpnDetector.isExternalVpnDetected()).thenReturn(true)
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getAccessToken()).thenReturn(null)

        val result = plugin.getView(context, VpnState(state = ENABLING)) {}

        assertNull(result)
    }

    @Test
    fun whenVPNIsEnabledAndUserIsEligibleToPproThenGetViewReturnsNull() = runTest {
        whenever(vpnDetector.isExternalVpnDetected()).thenReturn(true)
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getAccessToken()).thenReturn(null)

        val result = plugin.getView(context, VpnState(state = ENABLED)) {}

        assertNull(result)
    }
}
