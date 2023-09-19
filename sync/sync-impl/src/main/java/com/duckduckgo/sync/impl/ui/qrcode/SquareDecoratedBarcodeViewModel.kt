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

package com.duckduckgo.sync.impl.ui.qrcode

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.Command.CheckPermissions
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.Command.RequestPermissions
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.ViewState.PermissionsGranted
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.ViewState.PermissionsNotGranted
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.ViewState.Unknown
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Wrapper for whether permission has already been denied
 */
class PermissionDeniedWrapper @Inject constructor() {

    var permissionAlreadyDenied = false
}

class SquareDecoratedBarcodeViewModel(
    private val permissionDeniedWrapper: PermissionDeniedWrapper,
    private val dispatchers: DispatcherProvider,
) : ViewModel(), MainProcessLifecycleObserver {

    sealed class Command {
        object CheckCameraAvailable : Command()
        object CheckPermissions : Command()
        object OpenSettings : Command()
        object RequestPermissions : Command()
    }

    sealed class ViewState {
        object Unknown : ViewState()
        object PermissionsGranted : ViewState()
        object PermissionsNotGranted : ViewState()
        object CameraUnavailable : ViewState()
    }

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val _viewState: MutableStateFlow<ViewState> = MutableStateFlow(Unknown)
    val viewState: StateFlow<ViewState> get() = _viewState

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        sendCommand(Command.CheckCameraAvailable)
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch(dispatchers.io()) { command.send(newCommand) }
    }

    fun handlePermissions(granted: Boolean) {
        if (!granted && !permissionDeniedWrapper.permissionAlreadyDenied) {
            permissionDeniedWrapper.permissionAlreadyDenied = true
            sendCommand(RequestPermissions)
        } else if (!granted) {
            handlePermissionDenied()
        } else {
            handlePermissionGranted()
        }
    }

    private fun handlePermissionDenied() {
        viewModelScope.launch(dispatchers.io()) {
            _viewState.emit(PermissionsNotGranted)
        }
    }

    private fun handlePermissionGranted() {
        viewModelScope.launch(dispatchers.io()) {
            _viewState.emit(PermissionsGranted)
        }
    }

    fun goToSettings() {
        sendCommand(Command.OpenSettings)
    }

    fun handleCameraAvailability(cameraAvailable: Boolean) {
        if (cameraAvailable) {
            sendCommand(CheckPermissions)
        } else {
            viewModelScope.launch(dispatchers.io()) {
                _viewState.emit(ViewState.CameraUnavailable)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory
    @Inject
    constructor(
        private val permissionDeniedWrapper: PermissionDeniedWrapper,
        private val dispatcherProvider: DispatcherProvider,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return with(modelClass) {
                when {
                    isAssignableFrom(SquareDecoratedBarcodeViewModel::class.java) -> {
                        SquareDecoratedBarcodeViewModel(permissionDeniedWrapper, dispatcherProvider)
                    }
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
        }
    }
}
