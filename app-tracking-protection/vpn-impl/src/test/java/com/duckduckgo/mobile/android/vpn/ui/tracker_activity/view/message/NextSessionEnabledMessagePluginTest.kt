package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class NextSessionEnabledMessagePluginTest {
    private lateinit var plugin: NextSessionEnabledMessagePlugin
    private lateinit var vpnStore: FakeVPNStore
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val deviceShieldPixels: DeviceShieldPixels = mock()

    @Before
    fun setUp() {
        vpnStore = FakeVPNStore()
        plugin = NextSessionEnabledMessagePlugin(vpnStore, deviceShieldPixels)
    }

    @Test
    fun whenVPNIsDisabledThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.DISABLED)) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsEnablingThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.ENABLING)) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsEnabledAndIsNotOnboardingThenGetViewReturnsView() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.ENABLED)) {}
        assertNotNull(result)
    }

    @Test
    fun whenVPNIsEnabledAndIsOnboardingThenGetViewReturnsNull() {
        vpnStore.getAndSetOnboardingSession()
        val result = plugin.getView(context, VpnState(state = VpnRunningState.ENABLED)) {}
        assertNull(result)
    }
}
