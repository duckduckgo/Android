package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.AlwaysOnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionRequiredDisabledMessagePluginTest {
    private lateinit var plugin: ActionRequiredDisabledMessagePlugin
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        plugin = ActionRequiredDisabledMessagePlugin()
    }

    @Test
    fun whenVPNIsDisabledThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.DISABLED, alwaysOnState = AlwaysOnState.ALWAYS_ON_LOCKED_DOWN)) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsEnablingThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.ENABLING, alwaysOnState = AlwaysOnState.ALWAYS_ON_LOCKED_DOWN)) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsEnabledAndAlwaysOnOnlyEnabledThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.ENABLED, alwaysOnState = AlwaysOnState.ALWAYS_ON_ENABLED)) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsEnabledAndLockdownEnabledThenGetViewReturnsView() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.ENABLED, alwaysOnState = AlwaysOnState.ALWAYS_ON_LOCKED_DOWN)) {}
        assertNotNull(result)
    }
}
