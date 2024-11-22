package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PProUpsellBannerPluginTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val browserNav: BrowserNav = mock()
    private val subscriptions: Subscriptions = mock()
    private val deviceShieldPixels: DeviceShieldPixels = mock()
    private lateinit var vpnStore: FakeVPNStore
    private lateinit var plugin: PProUpsellBannerPlugin

    @Before
    fun setUp() {
        vpnStore = FakeVPNStore(pproUpsellBannerDismissed = false)
        plugin = PProUpsellBannerPlugin(subscriptions, browserNav, vpnStore, deviceShieldPixels)
    }

    @Test
    fun whenVPNIsDisabledAndUserNotEligibleToPProThenGetViewReturnsNull() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(false)
        whenever(subscriptions.isSignedIn()).thenReturn(false)

        val result = plugin.getView(context, VpnState(state = DISABLED)) {}

        assertNull(result)
    }

    @Test
    fun whenVPNIsEnabledAndUserIsSubscriberToPProThenGetViewReturnsNull() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.isSignedIn()).thenReturn(true)

        val result = plugin.getView(context, VpnState(state = ENABLED)) {}

        assertNull(result)
    }

    @Test
    fun whenBannerDismissedThenGetViewReturnsNull() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.isSignedIn()).thenReturn(false)
        vpnStore.dismissPproUpsellBanner()

        val result = plugin.getView(context, VpnState(state = ENABLED)) {}

        assertNull(result)
    }
}
