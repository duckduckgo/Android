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

package com.duckduckgo.duckchat.api.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DuckChatSharedViewModel() : ViewModel() {

    private val _command = Channel<Command>()
    val command = _command.receiveAsFlow() // Activity will collect this

    fun onFireButtonClicked() {
        viewModelScope.launch {
            _command.send(Command.LaunchFire)
        }
    }

    fun onTabSwitcherCLicked() {
        viewModelScope.launch {
            _command.send(Command.LaunchTabSwitcher)
        }
    }

    fun onSearchRequested(query: String) {
        viewModelScope.launch {
            _command.send(Command.SearchRequested(query))
        }
    }

    fun openExistingTab(tabId: String) {
        viewModelScope.launch {
            _command.send(Command.OpenTab(tabId))
        }
    }

    sealed class Command {
        object LaunchFire : Command()
        object LaunchTabSwitcher : Command()
        data class SearchRequested(val query: String) : Command()
        data class OpenTab(val tabId: String) : Command()
    }
}
