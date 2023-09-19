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

package com.duckduckgo.mobile.android.ui.notifyme

import android.annotation.SuppressLint
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.mobile.android.ui.store.notifyme.NotifyMeDataStore
import javax.inject.Inject
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

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
class NotifyMeViewModel(
    private val appBuildConfig: AppBuildConfig,
    private val notifyMeDataStore: NotifyMeDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val visible: Boolean = false,
    )

    sealed class Command {
        object UpdateNotificationsState : Command()
        object UpdateNotificationsStateOnAndroid13Plus : Command()
        object OpenSettingsOnAndroid8Plus : Command()
        object OpenSettings : Command()
        object CheckPermissionRationale : Command()
        object ShowPermissionRationale : Command()
        object DismissComponent : Command()
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
            sendCommand(Command.UpdateNotificationsStateOnAndroid13Plus)
        } else {
            sendCommand(Command.UpdateNotificationsState)
        }
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun updateNotificationsPermissions(granted: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            notificationsAllowed.emit(granted)
        }
    }

    fun onNotifyMeButtonClicked() {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            sendCommand(Command.CheckPermissionRationale)
        } else {
            openSettings()
        }
    }

    fun onCloseButtonClicked() {
        viewModelScope.launch(dispatcherProvider.io()) {
            command.send(Command.DismissComponent)
            if (this@NotifyMeViewModel::sharedPrefsKeyForDismiss.isInitialized) {
                notifyMeDataStore.setComponentDismissed(sharedPrefsKeyForDismiss)
            }
            dismissCalled.emit(true)
        }
    }

    fun handleRequestPermissionRationale(shouldShowRationale: Boolean) {
        if (shouldShowRationale) {
            sendCommand(Command.ShowPermissionRationale)
        } else {
            openSettings()
        }
    }

    fun init(sharedPrefsKeyForDismiss: String) {
        this.sharedPrefsKeyForDismiss = sharedPrefsKeyForDismiss
    }

    private fun openSettings() {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.O) {
            sendCommand(Command.OpenSettingsOnAndroid8Plus)
        } else {
            sendCommand(Command.OpenSettings)
        }
    }

    private fun isDismissed(): Boolean {
        return if (this@NotifyMeViewModel::sharedPrefsKeyForDismiss.isInitialized) {
            notifyMeDataStore.isComponentDismissed(sharedPrefsKeyForDismiss, false)
        } else {
            false
        }
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch(dispatcherProvider.io()) {
            command.send(newCommand)
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val appBuildConfig: AppBuildConfig,
        private val notifyMeDataStore: NotifyMeDataStore,
        private val dispatcherProvider: DispatcherProvider,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return with(modelClass) {
                when {
                    isAssignableFrom(NotifyMeViewModel::class.java) -> NotifyMeViewModel(
                        appBuildConfig,
                        notifyMeDataStore,
                        dispatcherProvider,
                    )
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
        }
    }
}
