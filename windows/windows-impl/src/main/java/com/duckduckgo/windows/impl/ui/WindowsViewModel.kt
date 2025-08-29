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

package com.duckduckgo.windows.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.windows.impl.WindowsDownloadLinkOrigin
import com.duckduckgo.windows.impl.WindowsPixelNames.WINDOWS_WAITLIST_SHARE_PRESSED
import com.duckduckgo.windows.impl.ui.WindowsViewModel.Command.ShareLink
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class WindowsViewModel @Inject constructor(
    private val pixel: Pixel,
    private val windowsDownloadLinkOrigin: WindowsDownloadLinkOrigin,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    sealed class Command {
        data class ShareLink(val originEnabled: Boolean) : Command()
        data object GoToMacClientSettings : Command()
    }

    fun onShareClicked() {
        viewModelScope.launch {
            commandChannel.send(ShareLink(originEnabled = windowsDownloadLinkOrigin.self().isEnabled()))
            pixel.fire(WINDOWS_WAITLIST_SHARE_PRESSED)
        }
    }

    fun onGoToMacClicked() {
        viewModelScope.launch {
            commandChannel.send(Command.GoToMacClientSettings)
        }
    }
}
