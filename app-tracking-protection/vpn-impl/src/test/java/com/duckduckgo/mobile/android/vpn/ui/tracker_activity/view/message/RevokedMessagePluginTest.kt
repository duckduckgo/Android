package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RevokedMessagePluginTest {
    private lateinit var plugin: RevokedMessagePlugin
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        plugin = RevokedMessagePlugin()
    }

    @Test
    fun whenVPNIsEnabledThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.ENABLED)) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsEnablinghenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.ENABLING)) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsInvalidThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.INVALID)) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsDisabledWithRevokedReasonThenGetViewReturnsNotNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.DISABLED, stopReason = VpnStopReason.REVOKED)) {}
        assertNotNull(result)
    }

    @Test
    fun whenVPNIsEnabledWithSelfStopReasonThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.DISABLED, stopReason = VpnStopReason.SELF_STOP())) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsEnabledWithErrorReasonThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.DISABLED, stopReason = VpnStopReason.ERROR)) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsEnabledWithRestartReasonThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.DISABLED, stopReason = VpnStopReason.RESTART)) {}
        assertNull(result)
    }

    @Test
    fun whenVPNIsEnabledWithUnknownReasonThenGetViewReturnsNull() {
        val result = plugin.getView(context, VpnState(state = VpnRunningState.DISABLED, stopReason = VpnStopReason.UNKNOWN)) {}
        assertNull(result)
    }
}
