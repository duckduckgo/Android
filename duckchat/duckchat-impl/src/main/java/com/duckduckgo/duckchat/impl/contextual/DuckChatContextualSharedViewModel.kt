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

package com.duckduckgo.duckchat.impl.contextual

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class DuckChatContextualSharedViewModel() : ViewModel() {

    private val _command = Channel<Command>()
    val commands = _command.receiveAsFlow() // Activity will collect this

    fun onPageContextReceived(tabId: String, pageContext: String) {
        _command.trySend(Command.PageContextAttached(tabId, pageContext))
    }

    fun onOpenRequested() {
        _command.trySend(Command.OpenSheet)
    }

    sealed class Command {
        data class PageContextAttached(
            val tabId: String,
            val pageContext: String,
        ) : Command()

        data object OpenSheet : Command()
    }
}
