/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.email.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.email.ui.EmailWebViewViewModel.Command.EmailSignEvent
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class EmailWebViewViewModel @Inject constructor(
    emailManager: EmailManager
) : ViewModel() {

    sealed class Command {
        object EmailSignEvent : Command()
    }

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    init {
        emailManager.signedInFlow().onEach {
            commandChannel.send(EmailSignEvent)
        }.launchIn(viewModelScope)
    }
}
