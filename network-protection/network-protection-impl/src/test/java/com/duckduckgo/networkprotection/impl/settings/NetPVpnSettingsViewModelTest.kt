package com.duckduckgo.networkprotection.impl.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.settings.geoswitching.DisplayablePreferredLocationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class NetPVpnSettingsViewModelTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val networkProtectionState = mock<NetworkProtectionState>()
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
        ) { isIgnoringBatteryOptimizations }
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
        viewModel.viewState().test {
            viewModel.onStart(mock())

            assertEquals(NetPVpnSettingsViewModel.ViewState(false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onStartEmitCorrectState() = runTest {
        viewModel.viewState().test {
            netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().setEnabled(Toggle.State(remoteEnableState = true))

            viewModel.onStart(mock())

            assertEquals(NetPVpnSettingsViewModel.ViewState(false), awaitItem())
            assertEquals(NetPVpnSettingsViewModel.ViewState(true), awaitItem())
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
}

private class FakeDisplayablePreferredLocationProvider : DisplayablePreferredLocationProvider {
    var location: String? = null
    override suspend fun getDisplayablePreferredLocation(): String? {
        return location
    }
}
