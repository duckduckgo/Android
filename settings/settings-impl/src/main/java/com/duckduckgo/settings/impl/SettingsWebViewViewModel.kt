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

package com.duckduckgo.settings.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SettingsWebViewViewModel @Inject constructor() : ViewModel() {
    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    sealed class Command {
        data class LoadUrl(
            val url: String,
        ) : Command()

        data object Exit : Command()
    }

    fun onStart(url: String?) {
        if (url != null) {
            sendCommand(Command.LoadUrl(url))
        } else {
            logcat(LogPriority.ERROR) { "No URL provided to WebViewActivity" }
            sendCommand(Command.Exit)
        }
    }

    private fun sendCommand(command: Command) {
        viewModelScope.launch {
            commandChannel.send(command)
        }
    }
}
