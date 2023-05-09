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

package com.duckduckgo.networkprotection.impl.waitlist

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class NetPWaitlistViewModel @Inject constructor(
    waitlistManager: NetPWaitlistManager,
) : ViewModel() {

    private val viewStateFlow: MutableStateFlow<ViewState> =
        MutableStateFlow(ViewState(waitlistManager.getState()))
    val viewState: StateFlow<ViewState> = viewStateFlow

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    sealed class Command {
        object EnterInviteCode : Command()
        object OpenNetP : Command()
    }

    data class ViewState(val waitlist: NetPWaitlistState)

    fun haveAnInviteCode() {
        viewModelScope.launch {
            commandChannel.send(Command.EnterInviteCode)
        }
    }

    fun onCodeRedeemed(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            viewModelScope.launch {
                viewStateFlow.emit(ViewState(NetPWaitlistState.CodeRedeemed))
            }
        }
    }

    fun getStarted() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenNetP)
        }
    }
}
