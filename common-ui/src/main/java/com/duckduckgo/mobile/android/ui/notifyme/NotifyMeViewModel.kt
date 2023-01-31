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

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
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

class NotifyMeViewModel() : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val visible: Boolean = false,
    )

    sealed class Command {
        object CheckPermissions : Command()
        object OpenSettings : Command()
        object CheckShouldShowRequestPermissionRationale : Command()
        object ShowPermissionRationale : Command()
        object Close : Command()
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
        listener?.visibilityChanged(visible)
        ViewState(visible)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        sendCommand(Command.CheckPermissions)
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
        sendCommand(Command.CheckShouldShowRequestPermissionRationale)
    }

    fun onCloseButtonClicked() {
        viewModelScope.launch {
            command.send(Command.Close)
            listener?.setDismissed()
            dismissCalled.emit(true)
        }
    }

    fun onPermissionRationaleNeeded() {
        sendCommand(Command.ShowPermissionRationale)
    }

    fun onOpenSettings() {
        sendCommand(Command.OpenSettings)
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
    class Factory(
        owner: SavedStateRegistryOwner,
    ) : AbstractSavedStateViewModelFactory(owner, null) {
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle,
        ): T {
            return NotifyMeViewModel() as T
        }
    }
}
