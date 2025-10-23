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

package com.duckduckgo.common.ui.notifyme

import android.annotation.SuppressLint
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.CheckPermissionRationale
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.DismissComponent
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.OpenSettingsOnAndroid8Plus
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.ShowPermissionRationale
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.UpdateNotificationsState
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.UpdateNotificationsStateOnAndroid13Plus
import com.duckduckgo.common.ui.store.notifyme.NotifyMeDataStore
import com.duckduckgo.di.scopes.ViewScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class NotifyMeViewModel @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val notifyMeDataStore: NotifyMeDataStore,
    private val notifyMeListener: NotifyMeListener,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val visible: Boolean = false,
    )

    sealed class Command {
        data object UpdateNotificationsState : Command()
        data object UpdateNotificationsStateOnAndroid13Plus : Command()
        data object OpenSettingsOnAndroid8Plus : Command()
        data object CheckPermissionRationale : Command()
        data object ShowPermissionRationale : Command()
        data object DismissComponent : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    private lateinit var sharedPrefsKeyForDismiss: String

    private val notificationsAllowed = MutableStateFlow(true)

    private val dismissCalled = MutableStateFlow(false)

    val viewState: StateFlow<ViewState> = combine(
        flow = notificationsAllowed,
        flow2 = dismissCalled,
    ) { notificationsAllowed, dismissCalled ->
        val visible = !notificationsAllowed && !(dismissCalled || isDismissed())
        ViewState(visible)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            sendCommand(UpdateNotificationsStateOnAndroid13Plus)
        } else {
            sendCommand(UpdateNotificationsState)
        }
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun updateNotificationsPermissions(granted: Boolean) {
        viewModelScope.launch {
            notificationsAllowed.emit(granted)
        }
    }

    fun onNotifyMeButtonClicked() {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            sendCommand(CheckPermissionRationale)
            viewModelScope.launch {
                notifyMeListener.onNotifyMeButtonClicked()
            }
        } else {
            openSettings()
        }
    }

    fun onCloseButtonClicked() {
        viewModelScope.launch {
            command.send(DismissComponent)
            if (this@NotifyMeViewModel::sharedPrefsKeyForDismiss.isInitialized) {
                notifyMeDataStore.setComponentDismissed(sharedPrefsKeyForDismiss)
            }
            dismissCalled.emit(true)
        }
    }

    fun handleRequestPermissionRationale(shouldShowRationale: Boolean) {
        if (shouldShowRationale) {
            sendCommand(ShowPermissionRationale)
        } else {
            openSettings()
        }
    }

    fun init(sharedPrefsKeyForDismiss: String) {
        this.sharedPrefsKeyForDismiss = sharedPrefsKeyForDismiss
    }

    private fun openSettings() {
        sendCommand(OpenSettingsOnAndroid8Plus)
    }

    private fun isDismissed(): Boolean {
        return if (this@NotifyMeViewModel::sharedPrefsKeyForDismiss.isInitialized) {
            notifyMeDataStore.isComponentDismissed(sharedPrefsKeyForDismiss, false)
        } else {
            false
        }
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }
}
