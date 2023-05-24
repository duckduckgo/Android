/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.macos_impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.macos_impl.MacOsPixelNames.MACOS_WAITLIST_SHARE_PRESSED
import com.duckduckgo.macos_impl.MacOsViewModel.Command.GoToWindowsClientSettings
import com.duckduckgo.macos_impl.MacOsViewModel.Command.ShareLink
import com.duckduckgo.windows.api.WindowsDownloadLinkFeature
import com.duckduckgo.windows.api.WindowsWaitlistFeature
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(AppScope::class)
class MacOsViewModel @Inject constructor(
    private val pixel: Pixel,
    private val windowsWaitlistFeature: WindowsWaitlistFeature,
    private val windowsDownloadLinkFeature: WindowsDownloadLinkFeature,
) : ViewModel() {

    private val viewStateFlow: MutableStateFlow<ViewState> = MutableStateFlow(ViewState(windowsFeatureEnabled = false))
    val viewState: Flow<ViewState> = viewStateFlow.onStart {
        updateViewState()
    }

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    sealed class Command {
        object ShareLink : Command()
        object GoToWindowsWaitlistClientSettings : Command()
        object GoToWindowsClientSettings : Command()
    }

    data class ViewState(val windowsFeatureEnabled: Boolean)

    fun onShareClicked() {
        viewModelScope.launch {
            commandChannel.send(ShareLink)
            pixel.fire(MACOS_WAITLIST_SHARE_PRESSED)
        }
    }

    fun onGoToWindowsClicked() {
        viewModelScope.launch {
            if (windowsDownloadLinkFeature.self().isEnabled()) {
                commandChannel.send(GoToWindowsClientSettings)
            } else {
                commandChannel.send(Command.GoToWindowsWaitlistClientSettings)
            }
        }
    }

    private suspend fun updateViewState() {
        viewStateFlow.emit(
            viewStateFlow.value.copy(
                windowsFeatureEnabled = windowsWaitlistFeature.self().isEnabled() || windowsDownloadLinkFeature.self().isEnabled(),
            ),
        )
    }
}
