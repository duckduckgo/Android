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

package com.duckduckgo.windows.impl.waitlist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.windows.api.WindowsWaitlistState
import com.duckduckgo.windows.impl.WindowsPixelNames.WINDOWS_WAITLIST_SHARE_PRESSED
import com.duckduckgo.windows.impl.waitlist.WindowsWaitlistManager
import com.duckduckgo.windows.impl.waitlist.api.WindowsWaitlistService
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command.CopyInviteToClipboard
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command.ShareInviteCode
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command.ShowErrorMessage
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class WindowsWaitlistViewModel @Inject constructor(
    private val waitlistManager: WindowsWaitlistManager,
    private val waitlistService: WindowsWaitlistService,
    private val workManager: WorkManager,
    private val workRequestBuilder: WindowsWaitlistWorkRequestBuilder,
    private val pixel: Pixel,
) : ViewModel() {

    private val viewStateFlow: MutableStateFlow<ViewState> =
        MutableStateFlow(ViewState(waitlistManager.getState()))
    val viewState: StateFlow<ViewState> = viewStateFlow

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    sealed class Command {
        object ShowErrorMessage : Command()
        data class ShareInviteCode(val inviteCode: String) : Command()
        data class CopyInviteToClipboard(val inviteCode: String, val onlyCode: Boolean) : Command()
    }

    data class ViewState(val waitlist: WindowsWaitlistState)

    fun joinTheWaitlist() {
        viewModelScope.launch {
            runCatching {
                waitlistService.joinWaitlist()
            }.onSuccess {
                val timestamp = it.timestamp
                val token = it.token
                if (timestamp != null && !token.isNullOrBlank()) {
                    joinedWaitlist(timestamp, token)
                } else {
                    commandChannel.send(ShowErrorMessage)
                }
            }.onFailure {
                commandChannel.send(ShowErrorMessage)
            }
        }
    }

    private suspend fun joinedWaitlist(
        timestamp: Int,
        token: String,
    ) {
        waitlistManager.joinWaitlist(timestamp, token)
        workManager.enqueue(workRequestBuilder.waitlistRequestWork(withBigDelay = false))
        viewStateFlow.emit(ViewState(waitlistManager.getState()))
    }

    fun onShareClicked() {
        waitlistManager.getInviteCode()?.let {
            viewModelScope.launch {
                commandChannel.send(ShareInviteCode(it))
                pixel.fire(WINDOWS_WAITLIST_SHARE_PRESSED)
            }
        }
    }

    fun onDialogDismissed() {
        viewModelScope.launch {
            viewStateFlow.emit(ViewState(waitlistManager.getState()))
        }
    }

    fun onCopyToClipboard(onlyCode: Boolean) {
        waitlistManager.getInviteCode()?.let {
            viewModelScope.launch {
                commandChannel.send(CopyInviteToClipboard(it, onlyCode))
            }
        }
    }
}
