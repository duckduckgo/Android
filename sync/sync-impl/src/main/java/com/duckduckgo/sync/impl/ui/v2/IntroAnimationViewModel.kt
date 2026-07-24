/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.Command.PlayIntroAnimation
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.Command.RequestCameraPermission
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class IntroAnimationViewModel @Inject constructor(
    private val cameraAccess: CameraAccess,
) : ViewModel() {
    private val isCameraHardwareAvailable = cameraAccess.isHardwareAvailable()

    private val _viewState = MutableStateFlow(
        ViewState(
            viewMode = if (isCameraHardwareAvailable) ViewMode.Intro else ViewMode.NoCameraAvailable,
        ),
    )
    val viewState = _viewState.asStateFlow()

    private val _command = Channel<Command>(Channel.BUFFERED)
    val commands = _command.receiveAsFlow()

    fun requestAnimationStart() = withCameraHardware {
        val state = viewState.value
        if (state.viewMode == ViewMode.Intro && !state.animationFinished) {
            viewModelScope.launch {
                _command.send(PlayIntroAnimation)
            }
        }
    }

    fun refreshCameraPermissionState() = withCameraHardware {
        if (cameraAccess.isPermissionGranted()) {
            _viewState.update { state ->
                val isReadyForCamera = state.viewMode == ViewMode.NoCameraPermission || (state.viewMode == ViewMode.Intro && state.animationFinished)
                if (isReadyForCamera) {
                    state.copy(viewMode = ViewMode.Camera)
                } else {
                    state
                }
            }
        }
    }

    fun onAnimationFinished() = withCameraHardware {
        _viewState.update {
            it.copy(
                animationFinished = true,
                viewMode = if (cameraAccess.isPermissionGranted()) ViewMode.Camera else it.viewMode,
            )
        }
    }

    fun onScanButtonClicked() = withCameraHardware {
        if (cameraAccess.isPermissionGranted()) {
            _viewState.update { it.copy(animationFinished = true, viewMode = ViewMode.Camera) }
        } else {
            _viewState.update { it.copy(animationFinished = true) }
            viewModelScope.launch {
                _command.send(RequestCameraPermission)
            }
        }
    }

    fun onCameraPermissionResult() = withCameraHardware {
        val isGranted = cameraAccess.isPermissionGranted()
        _viewState.update {
            it.copy(viewMode = if (isGranted) ViewMode.Camera else ViewMode.NoCameraPermission)
        }
    }

    private inline fun withCameraHardware(block: () -> Unit) {
        if (isCameraHardwareAvailable) {
            block()
        }
    }

    data class ViewState(
        val animationFinished: Boolean = false,
        val viewMode: ViewMode = ViewMode.Intro,
    )

    enum class ViewMode {
        Intro,
        Camera,
        NoCameraPermission,
        NoCameraAvailable,
    }

    sealed class Command {
        data object PlayIntroAnimation : Command()
        data object RequestCameraPermission : Command()
    }
}
