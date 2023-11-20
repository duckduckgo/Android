/*
 * Copyright (c) 2023 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.networkprotection.impl.settings

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.settings.geoswitching.DisplayablePreferredLocationProvider
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class NetPVpnSettingsViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val displayablePreferredLocationProvider: DisplayablePreferredLocationProvider,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val networkProtectionState: NetworkProtectionState,
) : ViewModel(), DefaultLifecycleObserver {

    private val shouldRestartVpn = AtomicBoolean(false)
    private val _viewState = MutableStateFlow(ViewState())
    internal fun viewState(): Flow<ViewState> = _viewState.asStateFlow()

    internal data class ViewState(
        val preferredLocation: String? = null,
        val excludeLocalNetworks: Boolean = false,
    )

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        viewModelScope.launch(dispatcherProvider.io()) {
            val excludeLocalRoutes = netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().isEnabled()
            val location = displayablePreferredLocationProvider.getDisplayablePreferredLocation()
            _viewState.emit(_viewState.value.copy(preferredLocation = location, excludeLocalNetworks = excludeLocalRoutes))
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        if (shouldRestartVpn.getAndSet(false)) {
            networkProtectionState.restart()
        }
    }

    internal fun onExcludeLocalRoutes(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val oldValue = _viewState.value.excludeLocalNetworks
            netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().setEnabled(Toggle.State(enable = enabled))
            _viewState.emit(_viewState.value.copy(excludeLocalNetworks = enabled))
            shouldRestartVpn.set(enabled != oldValue)
        }
    }
}
