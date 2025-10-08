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
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.isIgnoringBatteryOptimizations
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.snooze.VpnDisableOnCall
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Qualifier

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class NetPVpnSettingsViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val networkProtectionState: NetworkProtectionState,
    private val vpnDisableOnCall: VpnDisableOnCall,
    private val networkProtectionPixels: NetworkProtectionPixels,
    @InternalApi private val isIgnoringBatteryOptimizations: () -> Boolean,
) : ViewModel(), DefaultLifecycleObserver {

    private val shouldRestartVpn = AtomicBoolean(false)
    private val _viewState = MutableStateFlow(ViewState())

    // A state flow behaves identically to a shared flow when it is created with the following parameters
    // See https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/
    // See also https://github.com/Kotlin/kotlinx.coroutines/issues/2515
    //
    // WARNING: only use _state to emit values, for anything else use getState()
    private val _recommendedSettingsState: MutableSharedFlow<RecommendedSettings> = MutableSharedFlow(
        replay = 1,
        onBufferOverflow = DROP_OLDEST,
    )

    internal fun viewState(): Flow<ViewState> = _viewState.asStateFlow()

    internal data class ViewState(
        val excludeLocalNetworks: Boolean = false,
        val pauseDuringWifiCalls: Boolean = false,
        val vpnNotifications: Boolean = false,
    )

    init {
        viewModelScope.launch(dispatcherProvider.io()) {
            _recommendedSettingsState.tryEmit(RecommendedSettings(isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations()))
        }
    }

    internal fun recommendedSettings(): Flow<RecommendedSettings> {
        return _recommendedSettingsState.distinctUntilChanged()
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        networkProtectionPixels.reportVpnSettingsShown()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        viewModelScope.launch(dispatcherProvider.io()) {
            val excludeLocalRoutes = netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().isEnabled()
            _viewState.emit(
                _viewState.value.copy(
                    excludeLocalNetworks = excludeLocalRoutes,
                    pauseDuringWifiCalls = vpnDisableOnCall.isEnabled(),
                    vpnNotifications = netPSettingsLocalConfig.vpnNotificationAlerts().isEnabled(),
                ),
            )
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        fun updateRecommendedSettings() {
            owner.lifecycleScope.launch(dispatcherProvider.io()) {
                _recommendedSettingsState.tryEmit(
                    RecommendedSettings(isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations()),
                )
            }
        }

        updateRecommendedSettings()
    }

    override fun onPause(owner: LifecycleOwner) {
        if (shouldRestartVpn.getAndSet(false)) {
            networkProtectionState.restart()
        }
    }

    internal fun onExcludeLocalRoutes(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val oldValue = _viewState.value.excludeLocalNetworks
            netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().setRawStoredState(Toggle.State(enable = enabled))
            _viewState.emit(_viewState.value.copy(excludeLocalNetworks = enabled))
            shouldRestartVpn.set(enabled != oldValue)
        }
    }

    internal fun onEnablePauseDuringWifiCalls() {
        networkProtectionPixels.reportEnabledPauseDuringCalls()
        vpnDisableOnCall.enable()
    }

    internal fun onDisablePauseDuringWifiCalls() {
        networkProtectionPixels.reportDisabledPauseDuringCalls()
        vpnDisableOnCall.disable()
    }

    fun onVPNotificationsToggled(checked: Boolean) {
        logcat { "VPN alert notification settings set to $checked" }
        netPSettingsLocalConfig.vpnNotificationAlerts().setRawStoredState(Toggle.State(enable = checked))
    }

    data class RecommendedSettings(val isIgnoringBatteryOptimizations: Boolean)
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class InternalApi

@Module
@ContributesTo(ActivityScope::class)
class IgnoringBatteryOptimizationsModule {
    @Provides
    @InternalApi
    fun providesIsIgnoringBatteryOptimizations(context: Context): () -> Boolean {
        return { context.isIgnoringBatteryOptimizations() }
    }
}
