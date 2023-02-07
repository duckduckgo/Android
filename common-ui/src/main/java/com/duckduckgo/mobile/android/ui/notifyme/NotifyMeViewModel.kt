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

import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
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

class NotifyMeViewModel(
    private val appBuildConfig: AppBuildConfig,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val visible: Boolean = false,
    )

    sealed class Command {
        object UpdateNotificationsState : Command()
        object UpdateNotificationsStateOnAndroid13Plus : Command()
        object OpenSettings : Command()
        object ShowPermissionRationale : Command()
        object DismissComponent : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    private var listener: NotifyMeListener? = null

    private val notificationsAllowed = MutableStateFlow(true)

    private val dismissCalled = MutableStateFlow(false)

    val viewState: StateFlow<ViewState> = combine(
        flow = notificationsAllowed,
        flow2 = dismissCalled,
    ) { notificationsAllowed, dismissCalled ->
        val visible = !notificationsAllowed && !(dismissCalled || listener?.isDismissed() ?: false)
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
        viewModelScope.launch {
            notificationsAllowed.emit(granted)
        }
    }

    fun onNotifyMeButtonClicked(shouldShowRequestPermissionRationale: Boolean) {
        if (shouldShowRequestPermissionRationale) {
            sendCommand(Command.ShowPermissionRationale)
        } else {
            sendCommand(Command.OpenSettings)
        }
    }

    fun onCloseButtonClicked() {
        viewModelScope.launch {
            command.send(Command.DismissComponent)
            listener?.setDismissed()
            dismissCalled.emit(true)
        }
    }

    fun setNotifyMeListener(listener: NotifyMeListener?) {
        this.listener = listener
    }

    fun removeNotifyMeListener() {
        this.listener = null
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val appBuildConfig: AppBuildConfig,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return with(modelClass) {
                when {
                    isAssignableFrom(NotifyMeViewModel::class.java) -> NotifyMeViewModel(
                        appBuildConfig,
                    )
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
        }
    }
}
