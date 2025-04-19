package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingEnabledMessagePluginTest {
    private lateinit var plugin: OnboardingEnabledMessagePlugin
    private lateinit var vpnStore: FakeVPNStore
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        vpnStore = FakeVPNStore()
        plugin = OnboardingEnabledMessagePlugin(vpnStore)
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
    fun whenVPNIsEnabledAndIsNotOnboardingThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.ENABLED)) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsEnabledAndIsOnboardingThenGetViewReturnsView() {
        vpnStore.getAndSetOnboardingSession()
        val result = plugin.getView(context, VpnState(state = VpnRunningState.ENABLED)) {}
        assertNotNull(result)
    }
}
