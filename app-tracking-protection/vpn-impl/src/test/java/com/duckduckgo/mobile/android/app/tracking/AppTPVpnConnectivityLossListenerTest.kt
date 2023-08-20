package com.duckduckgo.mobile.android.app.tracking

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.feature.FakeAppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class AppTPVpnConnectivityLossListenerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val networkProtectionState: NetworkProtectionState = mock()
    private val appTrackingProtection: AppTrackingProtection = mock()
    private lateinit var appTpFeatureConfig: AppTpFeatureConfig
    private lateinit var listener: AppTPVpnConnectivityLossListener

    @Before
    fun setup() {
        appTpFeatureConfig = FakeAppTpFeatureConfig()
        appTpFeatureConfig.edit().setEnabled(AppTpSetting.RestartOnConnectivityLoss, true)

        listener = AppTPVpnConnectivityLossListener(
            networkProtectionState,
            appTrackingProtection,
            appTpFeatureConfig,
            coroutinesTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun onVpnConnectivityLossThenRestartAppTPThirdConsecutiveTime() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(appTrackingProtection.isEnabled()).thenReturn(true)

        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)
        listener.onVpnConnectivityLoss(coroutinesTestRule.testScope)

        verify(appTrackingProtection, times(2)).restart()
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
