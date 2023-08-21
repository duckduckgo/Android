package com.duckduckgo.mobile.android.app.tracking

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.api.InMemorySharedPreferences
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.feature.FakeAppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AppTPVpnConnectivityLossListenerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val networkProtectionState: NetworkProtectionState = mock()
    private val appTrackingProtection: AppTrackingProtection = mock()
    private val context: Context = mock()
    private lateinit var appTpFeatureConfig: AppTpFeatureConfig
    private lateinit var listener: AppTPVpnConnectivityLossListener
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setup() {
        appTpFeatureConfig = FakeAppTpFeatureConfig()
        appTpFeatureConfig.edit().setEnabled(AppTpSetting.RestartOnConnectivityLoss, true)
        sharedPreferences = InMemorySharedPreferences()
        whenever(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences)

        listener = AppTPVpnConnectivityLossListener(
            networkProtectionState,
            appTrackingProtection,
            appTpFeatureConfig,
            coroutinesTestRule.testDispatcherProvider,
            context,
        )
    }

    @Test
    fun onVpnConnectivityLossThenRestartAppTPThirdConsecutiveTime() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)

        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        assertEquals(1, sharedPreferences.getInt("RECONNECT_ATTEMPTS", -1))
        verify(appTrackingProtection, times(1)).restart()

        // first restart
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        assertEquals(2, sharedPreferences.getInt("RECONNECT_ATTEMPTS", -1))
        verify(appTrackingProtection, times(2)).restart()

        // second restart
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        assertEquals(3, sharedPreferences.getInt("RECONNECT_ATTEMPTS", -1))
        verify(appTrackingProtection, times(3)).restart()

        // third restart
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        verify(appTrackingProtection, times(3)).restart()
        verify(appTrackingProtection, times(1)).stop()
        assertEquals(0, sharedPreferences.getInt("RECONNECT_ATTEMPTS", -1))
    }

    @Test
    fun whenRestartOnConnectivityLossIsDisabledThenNoop() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)
        appTpFeatureConfig.edit().setEnabled(AppTpSetting.RestartOnConnectivityLoss, false)

        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)

        verify(appTrackingProtection, never()).restart()
        assertEquals(-1, sharedPreferences.getInt("RECONNECT_ATTEMPTS", -1))
    }

    @Test
    fun onVpnConnectivityLossNetPEnabledThenNoop() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)

        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)

        verify(appTrackingProtection, never()).restart()
        assertEquals(-1, sharedPreferences.getInt("RECONNECT_ATTEMPTS", -1))
    }

    @Test
    fun onVpnConnectivityLossAppTPDisabledThenNoop() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(appTrackingProtection.isEnabled()).thenReturn(false)

        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)

        verify(appTrackingProtection, never()).restart()
        assertEquals(-1, sharedPreferences.getInt("RECONNECT_ATTEMPTS", -1))
    }

    @Test
    fun onVpnConnectedResetsConnectivityLossCounter() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)

        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnected(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)

        verify(appTrackingProtection, never()).restart()
    }

    @Test
    fun onVpnStartedResetsConnectivityLossCounter() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)

        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnStarted(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)

        verify(appTrackingProtection, never()).restart()
        assertEquals(0, sharedPreferences.getInt("RECONNECT_ATTEMPTS", -1))
    }

    @Test
    fun onVpnReconfiguredResetsConnectivityLossCounter() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)

        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnReconfigured(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)

        verify(appTrackingProtection, never()).restart()
    }

    @Test
    fun onVpnStartingDoesNotAffectNormalBehavior() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)

        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnStarting(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)

        verify(appTrackingProtection, times(1)).restart()
    }

    @Test
    fun onVpnStartFailedDoesNotAffectNormalBehavior() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)

        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnStartFailed(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)

        verify(appTrackingProtection, times(1)).restart()
    }

    @Test
    fun onVpnStoppedResetsConnectivityLossCounter() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)

        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnStopped(coroutinesTestRule.testScope, VpnStateMonitor.VpnStopReason.SELF_STOP)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)

        verify(appTrackingProtection, never()).restart()
    }
}
