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

package com.duckduckgo.macos_impl.waitlist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.macos_api.MacOsWaitlistState
import com.duckduckgo.macos_impl.waitlist.MacOsWaitlistManager
import com.duckduckgo.macos_impl.waitlist.api.MacOsWaitlistService
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.Command.CopyInviteToClipboard
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.Command.ShareInviteCode
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.Command.ShowErrorMessage
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.Command.ShowNotificationDialog
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

class MacOsWaitlistViewModel(
    private val waitlistManager: MacOsWaitlistManager,
    private val waitlistService: MacOsWaitlistService,
    private val workManager: WorkManager,
    private val workRequestBuilder: MacOsWaitlistWorkRequestBuilder,
    private val pixel: Pixel
) : ViewModel() {

    private val viewStateFlow: MutableStateFlow<ViewState> =
        MutableStateFlow(ViewState(waitlistManager.getState()))
    val viewState: StateFlow<ViewState> = viewStateFlow

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    sealed class Command {
        object ShowErrorMessage : Command()
        object ShowNotificationDialog : Command()
        data class ShareInviteCode(val inviteCode: String) : Command()
        data class CopyInviteToClipboard(val inviteCode: String, val onlyCode: Boolean) : Command()
    }

    data class ViewState(val waitlist: MacOsWaitlistState)

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

    private fun joinedWaitlist(
        timestamp: Int,
        token: String
    ) {
        viewModelScope.launch {
            waitlistManager.joinWaitlist(timestamp, token)
            commandChannel.send(ShowNotificationDialog)
            workManager.enqueue(workRequestBuilder.waitlistRequestWork(withBigDelay = false))
        }
    }

    fun onShareClicked() {
        waitlistManager.getInviteCode()?.let {
            viewModelScope.launch {
                commandChannel.send(ShareInviteCode(it))
            }
        }
    }

    fun onNotifyMeClicked() {
        viewModelScope.launch {
            waitlistManager.notifyOnJoinedWaitlist()
        }
    }

    fun onNoThanksClicked() {
        viewModelScope.launch {

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

@ContributesMultibinding(AppScope::class)
class AppTPWaitlistViewModelFactory @Inject constructor(
    private val waitlistManager: Provider<MacOsWaitlistManager>,
    private val waitlistService: Provider<MacOsWaitlistService>,
    private val workManager: Provider<WorkManager>,
    private val workRequestBuilder: Provider<MacOsWaitlistWorkRequestBuilder>,
    private val pixel: Provider<Pixel>,
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(MacOsWaitlistViewModel::class.java) -> (
                    MacOsWaitlistViewModel(
                        waitlistManager.get(),
                        waitlistService.get(),
                        workManager.get(),
                        workRequestBuilder.get(),
                        pixel.get(),
                    ) as T
                    )
                else -> null
            }
        }
    }
}
