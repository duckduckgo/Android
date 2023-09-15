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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class NetPWaitlistViewModel @Inject constructor(
    waitlistManager: NetPWaitlistManager,
    private val netPWaitlistManager: NetPWaitlistManager,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    val viewState: Flow<ViewState> = waitlistManager.getState().map { ViewState(it) }.distinctUntilChanged()

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    sealed class Command {
        object EnterInviteCode : Command()
    }

    data class ViewState(val waitlist: NetPWaitlistState)

    fun onJoinWaitlistClicked() {
        viewModelScope.launch(dispatcherProvider.io()) {
            netPWaitlistManager.joinWaitlist()
        }
    }

    fun onCodeRedeemed(resultCode: Int) {
    }
}
