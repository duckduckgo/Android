/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.subscription.settings

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionAccessState
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_SETTINGS_PRESSED
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState.Hidden
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState.Visible.Activating
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState.Visible.Expired
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState.Visible.Subscribed
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.Command.OpenNetPScreen
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.logcat

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
class ProSettingNetPViewModel(
    private val networkProtectionSettingsState: NetworkProtectionSettingsState,
    private val networkProtectionState: NetworkProtectionState,
    private val networkProtectionAccessState: NetworkProtectionAccessState,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(val networkProtectionEntryState: NetPEntryState = NetPEntryState.Hidden)

    sealed class Command {
        data class OpenNetPScreen(val params: ActivityParams) : Command()
    }

    sealed class NetPEntryState {
        data object Hidden : NetPEntryState()
        data class Subscribed(val isActive: Boolean) : NetPEntryState()
        data object Expired : NetPEntryState()
        data object Activating : NetPEntryState()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        viewModelScope.launch {
            combine(
                networkProtectionSettingsState.getNetPSettingsStateFlow(),
                networkProtectionState.getConnectionStateFlow(),
            ) { accessState, connectionState ->
                _viewState.emit(
                    viewState.value.copy(
                        networkProtectionEntryState = getNetworkProtectionEntryState(accessState, connectionState),
                    ),
                )
            }.flowOn(dispatcherProvider.main()).launchIn(viewModelScope)
        }
    }

    fun onNetPSettingClicked() {
        viewModelScope.launch {
            val screen = networkProtectionAccessState.getScreenForCurrentState()
            screen?.let {
                command.send(OpenNetPScreen(screen))
                pixel.fire(NETP_SETTINGS_PRESSED)
            } ?: logcat { "Get screen for current NetP state is null" }
        }
    }

    private fun getNetworkProtectionEntryState(
        settingsState: NetPSettingsState,
        networkProtectionConnectionState: ConnectionState,
    ): NetPEntryState =
        when (settingsState) {
            Hidden -> NetPEntryState.Hidden
            Subscribed -> NetPEntryState.Subscribed(isActive = networkProtectionConnectionState.isConnected())
            Activating -> NetPEntryState.Activating
            Expired -> NetPEntryState.Expired
        }

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val networkProtectionSettingsState: NetworkProtectionSettingsState,
        private val networkProtectionState: NetworkProtectionState,
        private val networkProtectionAccessState: NetworkProtectionAccessState,
        private val dispatcherProvider: DispatcherProvider,
        private val pixel: Pixel,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return with(modelClass) {
                when {
                    isAssignableFrom(ProSettingNetPViewModel::class.java) -> ProSettingNetPViewModel(
                        networkProtectionSettingsState,
                        networkProtectionState,
                        networkProtectionAccessState,
                        dispatcherProvider,
                        pixel,
                    )

                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
        }
    }
}
