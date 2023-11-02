package com.duckduckgo.networkprotection.impl.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.settings.geoswitching.DisplayablePreferredLocationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NetPVpnSettingsViewModelTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val networkProtectionState = mock<NetworkProtectionState>()
    private lateinit var locationProvider: FakeDisplayablePreferredLocationProvider
    private lateinit var netPSettingsLocalConfig: NetPSettingsLocalConfig

    private lateinit var viewModel: NetPVpnSettingsViewModel

    @Before
    fun setup() {
        netPSettingsLocalConfig = FakeNetPSettingsLocalConfigFactory.create()
        locationProvider = FakeDisplayablePreferredLocationProvider()

        viewModel = NetPVpnSettingsViewModel(
            coroutineRule.testDispatcherProvider,
            locationProvider,
            netPSettingsLocalConfig,
            networkProtectionState,
        )
    }

    @Test
    fun onStartEmitDefaultState() = runTest {
        viewModel.viewState().test {
            viewModel.onStart(mock())

            assertEquals(NetPVpnSettingsViewModel.ViewState(null, false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onStartEmitCorrectState() = runTest {
        viewModel.viewState().test {
            locationProvider.location = "location"
            netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().setEnabled(Toggle.State(remoteEnableState = true))

            viewModel.onStart(mock())

            assertEquals(NetPVpnSettingsViewModel.ViewState(null, false), awaitItem())
            assertEquals(NetPVpnSettingsViewModel.ViewState(locationProvider.location, true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onExcludeLocalRoutesEmitsCorrectState() = runTest {
        viewModel.viewState().test {
            locationProvider.location = "location"

            viewModel.onExcludeLocalRoutes(false)
            assertEquals(NetPVpnSettingsViewModel.ViewState(null, false), awaitItem())

            viewModel.onExcludeLocalRoutes(true)
            assertEquals(NetPVpnSettingsViewModel.ViewState(null, true), awaitItem())
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
