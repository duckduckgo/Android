/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.subscriptions.api.Subscriptions
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

@ContributesViewModel(FragmentScope::class)
class DuckChatWebViewViewModel @Inject constructor(
    private val subscriptions: Subscriptions,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    sealed class Command {
        data object SendSubscriptionAuthUpdateEvent : Command()
    }

    init {
        observeSubscriptionChanges()
    }

    private fun observeSubscriptionChanges() {
        subscriptions.getSubscriptionStatusFlow()
            .distinctUntilChanged()
            .onEach { _ ->
                commandChannel.trySend(Command.SendSubscriptionAuthUpdateEvent)
            }.launchIn(viewModelScope)
    }
}
