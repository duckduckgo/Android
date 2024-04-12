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
import androidx.annotation.StringRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.listitem.CheckListItem
import com.duckduckgo.common.ui.view.listitem.CheckListItem.CheckItemStatus
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionAccessState
import com.duckduckgo.networkprotection.api.NetworkProtectionAccessState.NetPAccessState
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTED
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTING
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.DISCONNECTED
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_SETTINGS_PRESSED
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.Command.OpenNetPScreen
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Hidden
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Pending
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.ShowState
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
    private val networkProtectionAccessState: NetworkProtectionAccessState,
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(val networkProtectionEntryState: NetPEntryState = Hidden)

    sealed class Command {
        data class OpenNetPScreen(val params: ActivityParams) : Command()
    }

    sealed class NetPEntryState {
        data object Hidden : NetPEntryState()
        data object Pending : NetPEntryState()
        data class ShowState(
            val icon: CheckItemStatus,
            @StringRes val subtitle: Int,
        ) : NetPEntryState()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        viewModelScope.launch {
            combine(networkProtectionAccessState.getStateFlow(), networkProtectionState.getConnectionStateFlow()) { accessState, connectionState ->
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

    private suspend fun getNetworkProtectionEntryState(
        accessState: NetPAccessState,
        networkProtectionConnectionState: ConnectionState,
    ): NetPEntryState {
        return when (accessState) {
            is NetPAccessState.InBeta -> {
                if (accessState.termsAccepted || networkProtectionState.isOnboarded()) {
                    val subtitle = when (networkProtectionConnectionState) {
                        CONNECTED -> R.string.netpSubscriptionSettingsConnected
                        CONNECTING -> R.string.netpSubscriptionSettingsConnecting
                        else -> R.string.netpSubscriptionSettingsDisconnected
                    }

                    val netPItemStatus = if (networkProtectionConnectionState != DISCONNECTED) {
                        CheckListItem.CheckItemStatus.ENABLED
                    } else {
                        CheckListItem.CheckItemStatus.WARNING
                    }

                    ShowState(
                        icon = netPItemStatus,
                        subtitle = subtitle,
                    )
                } else {
                    Pending
                }
            }

            NetPAccessState.NotUnlocked -> Hidden
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val networkProtectionAccessState: NetworkProtectionAccessState,
        private val networkProtectionState: NetworkProtectionState,
        private val dispatcherProvider: DispatcherProvider,
        private val pixel: Pixel,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return with(modelClass) {
                when {
                    isAssignableFrom(ProSettingNetPViewModel::class.java) -> ProSettingNetPViewModel(
                        networkProtectionAccessState,
                        networkProtectionState,
                        dispatcherProvider,
                        pixel,
                    )

                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
        }
    }
}
