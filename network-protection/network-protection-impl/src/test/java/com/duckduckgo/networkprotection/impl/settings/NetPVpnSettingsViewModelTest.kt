package com.duckduckgo.networkprotection.impl.settings

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.snooze.VpnDisableOnCall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class NetPVpnSettingsViewModelTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val networkProtectionState = mock<NetworkProtectionState>()
    private val vpnDisableOnCall = mock<VpnDisableOnCall>()
    private val networkProtectionPixels = mock<NetworkProtectionPixels>()
    private lateinit var netPSettingsLocalConfig: NetPSettingsLocalConfig
    private var isIgnoringBatteryOptimizations: Boolean = false

    private lateinit var viewModel: NetPVpnSettingsViewModel

    @Before
    fun setup() {
        isIgnoringBatteryOptimizations = false
        netPSettingsLocalConfig = FakeNetPSettingsLocalConfigFactory.create()

        viewModel = NetPVpnSettingsViewModel(
            coroutineRule.testDispatcherProvider,
            netPSettingsLocalConfig,
            networkProtectionState,
            vpnDisableOnCall,
            networkProtectionPixels,
        ) { isIgnoringBatteryOptimizations }
    }

    @Test
    fun whenVpnSettingsScreenShownThenEmitImpressionPixels() {
        viewModel.onCreate(mock())

        verify(networkProtectionPixels).reportVpnSettingsShown()
    }

    @Test
    fun whenIgnoringBatteryOptimizationsFalseThenRecommendedSettingsAreCorrect() = runTest {
        viewModel.recommendedSettings().test {
            isIgnoringBatteryOptimizations = false
            assertEquals(NetPVpnSettingsViewModel.RecommendedSettings(false), awaitItem())
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenIgnoringBatteryOptimizationsTrueThenRecommendedSettingsAreCorrect() = runTest {
        viewModel.recommendedSettings().test {
            isIgnoringBatteryOptimizations = true
            assertEquals(NetPVpnSettingsViewModel.RecommendedSettings(false), awaitItem())
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun onStartEmitDefaultState() = runTest {
        whenever(vpnDisableOnCall.isEnabled()).thenReturn(false)
        viewModel.viewState().test {
            viewModel.onStart(mock())

            assertEquals(
                NetPVpnSettingsViewModel.ViewState(
                    excludeLocalNetworks = false,
                    pauseDuringWifiCalls = false,
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun onStartEmitCorrectState() = runTest {
        whenever(vpnDisableOnCall.isEnabled()).thenReturn(true)
        viewModel.viewState().test {
            netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().setRawStoredState(Toggle.State(remoteEnableState = true))
            netPSettingsLocalConfig.vpnNotificationAlerts().setRawStoredState(Toggle.State(remoteEnableState = false))

            viewModel.onStart(mock())

            assertEquals(
                NetPVpnSettingsViewModel.ViewState(excludeLocalNetworks = false, pauseDuringWifiCalls = false, vpnNotifications = false),
                awaitItem(),
            )
            assertEquals(
                NetPVpnSettingsViewModel.ViewState(excludeLocalNetworks = true, pauseDuringWifiCalls = true, vpnNotifications = false),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onExcludeLocalRoutesEmitsCorrectState() = runTest {
        viewModel.viewState().test {
            viewModel.onExcludeLocalRoutes(false)
            assertEquals(NetPVpnSettingsViewModel.ViewState(false), awaitItem())

            viewModel.onExcludeLocalRoutes(true)
            assertEquals(NetPVpnSettingsViewModel.ViewState(true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onPauseDoNotRestartVpn() = runTest {
        whenever(vpnDisableOnCall.isEnabled()).thenReturn(false)
        viewModel.onStart(mock())
        verify(networkProtectionState, never()).restart()
    }

    @Test
    fun onPauseDoNotRestartVpnWhenNothingChanges() = runTest {
        viewModel.onExcludeLocalRoutes(false)

        viewModel.onPause(mock())
        verify(networkProtectionState, never()).restart()

        viewModel.onExcludeLocalRoutes(true)
        viewModel.onPause(mock())
        verify(networkProtectionState).restart()
    }

    @Test
    fun onPauseRestartVpnWhenSettingChanged() = runTest {
        viewModel.onExcludeLocalRoutes(true)
        viewModel.onPause(mock())
        verify(networkProtectionState).restart()
    }

    @Test
    fun whenOnEnablePauseDuringWifiCallsThenEnableFeature() {
        viewModel.onEnablePauseDuringWifiCalls()

        verify(vpnDisableOnCall).enable()
    }

    @Test
    fun whenOnDisablePauseDuringWifiCallsThenDisableFeature() {
        viewModel.onDisablePauseDuringWifiCalls()

        verify(vpnDisableOnCall).disable()
    }
}
