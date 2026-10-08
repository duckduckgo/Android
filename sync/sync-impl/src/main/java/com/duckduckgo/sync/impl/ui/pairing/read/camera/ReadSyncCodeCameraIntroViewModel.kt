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

package com.duckduckgo.sync.impl.ui.pairing.read.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.pairing.read.camera.ReadSyncCodeCameraIntroViewModel.Command.ExpandScannerCutout
import com.duckduckgo.sync.impl.ui.pairing.read.camera.ReadSyncCodeCameraIntroViewModel.Command.OpenPermissionSettings
import com.duckduckgo.sync.impl.ui.pairing.read.camera.ReadSyncCodeCameraIntroViewModel.Command.PlayIntroAnimation
import com.duckduckgo.sync.impl.ui.pairing.read.camera.ReadSyncCodeCameraIntroViewModel.Command.RequestCameraPermission
import com.duckduckgo.sync.impl.ui.pairing.read.camera.ReadSyncCodeCameraIntroViewModel.Command.ResumeCamera
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class ReadSyncCodeCameraIntroViewModel @Inject constructor(
    private val cameraAccess: CameraAccess,
    private val syncPixels: SyncPixels,
    private val syncFeature: SyncFeature,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val isCameraHardwareAvailable = cameraAccess.isHardwareAvailable()
    private val isCameraGrantedOnInit = cameraAccess.isPermissionGranted()
    private var shouldReportCameraPermission = true
    private var shouldAutoRequestCameraPermission = false

    private val _viewState = MutableStateFlow(
        ViewState(
            viewMode = if (isCameraHardwareAvailable) ViewMode.Loading else ViewMode.NoCameraAvailable,
        ),
    )
    val viewState = _viewState.asStateFlow()

    private val _command = Channel<Command>(Channel.BUFFERED)
    val commands = _command.receiveAsFlow()

    private val initializationJob = viewModelScope.launch {
        val isImprovedSyncFlow = withContext(dispatchers.io()) { syncFeature.canUseImprovedSyncFlow().isEnabled() }
        val viewMode = when {
            !isCameraHardwareAvailable -> ViewMode.NoCameraAvailable
            isImprovedSyncFlow && !cameraAccess.isPermissionGranted() -> {
                shouldAutoRequestCameraPermission = true
                ViewMode.NoCameraPermission
            }
            else -> ViewMode.Intro
        }
        _viewState.update { current ->
            current.copy(viewMode = viewMode, isImprovedSyncEnabled = isImprovedSyncFlow)
        }
    }

    fun requestAnimationStart() = withCameraHardware {
        val state = viewState.value
        if (state.viewMode == ViewMode.Intro && !state.animationFinished) {
            _command.send(PlayIntroAnimation)
        }
    }

    fun refreshCameraPermissionState() = withCameraHardware {
        val isPermissionGranted = cameraAccess.isPermissionGranted()
        when {
            isPermissionGranted -> {
                _viewState.update { state ->
                    when {
                        state.viewMode == ViewMode.NoCameraPermission -> state.copy(viewMode = state.viewModeAfterGrant())
                        state.viewMode == ViewMode.Intro && state.animationFinished -> state.copy(viewMode = ViewMode.Camera)
                        else -> state
                    }
                }
                requestCameraActivation()
            }

            shouldAutoRequestCameraPermission && !isPermissionGranted -> {
                shouldAutoRequestCameraPermission = false
                _command.send(RequestCameraPermission)
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
        requestCameraActivation()
    }

    fun onScanButtonClicked() = withCameraHardware {
        if (cameraAccess.isPermissionGranted()) {
            _viewState.update { it.copy(animationFinished = true, viewMode = ViewMode.Camera) }
            requestCameraActivation()
        } else {
            _viewState.update { it.copy(animationFinished = true) }
            _command.send(RequestCameraPermission)
        }
    }

    fun onCameraPermissionResult() = withCameraHardware {
        val isGranted = cameraAccess.isPermissionGranted()
        reportCameraPermissionState(isGranted = isGranted)
        _viewState.update {
            it.copy(viewMode = if (isGranted) it.viewModeAfterGrant() else ViewMode.NoCameraPermission)
        }
        if (isGranted) {
            requestCameraActivation()
        }
    }

    fun onGoToPermissionSettingsClicked() = withCameraHardware {
        // The user may grant the permission in the system settings, so we allow to capture the pixel again.
        shouldReportCameraPermission = true
        _command.send(OpenPermissionSettings)
    }

    private suspend fun requestCameraActivation() {
        if (viewState.value.viewMode == ViewMode.Camera) {
            // An active camera means the permission is granted. This is where we report grants
            // that skip the permission dialog: a permission granted before this screen opened or
            // one granted from the system settings.
            reportCameraPermissionState(isGranted = true)
            _command.send(ResumeCamera)
            _command.send(ExpandScannerCutout)
        }
    }

    // The improved flow asks for the permission before the intro has played, so a grant leads into the intro, which
    // hands over to the camera once it finishes. The old flow only asks after the intro, so a grant goes straight to the camera.
    private fun ViewState.viewModeAfterGrant(): ViewMode {
        return if (isImprovedSyncEnabled && !animationFinished) ViewMode.Intro else ViewMode.Camera
    }

    private fun reportCameraPermissionState(isGranted: Boolean) {
        if (!shouldReportCameraPermission) return
        shouldReportCameraPermission = false
        syncPixels.fireScannerCameraPermissionState(
            beforeRequesting = isCameraGrantedOnInit,
            afterRequesting = isGranted,
        )
    }

    private fun withCameraHardware(block: suspend () -> Unit) {
        if (!isCameraHardwareAvailable) return
        viewModelScope.launch {
            initializationJob.join()
            block()
        }
    }

    data class ViewState(
        val animationFinished: Boolean = false,
        val isImprovedSyncEnabled: Boolean = false,
        val viewMode: ViewMode = ViewMode.Intro,
    )

    enum class ViewMode {
        Loading,
        Intro,
        Camera,
        NoCameraPermission,
        NoCameraAvailable,
    }

    sealed class Command {
        data object PlayIntroAnimation : Command()
        data object RequestCameraPermission : Command()
        data object OpenPermissionSettings : Command()
        data object ResumeCamera : Command()
        data object ExpandScannerCutout : Command()
    }
}
