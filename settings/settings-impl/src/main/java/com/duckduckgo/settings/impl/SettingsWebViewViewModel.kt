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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.settings.api.SettingsConstants
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SettingsWebViewViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {
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

    fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            when (featureName) {
                SettingsConstants.FEATURE_SERP_SETTINGS ->
                    when (method) {
                        SettingsConstants.METHOD_OPEN_NATIVE_SETTINGS -> {
                            val returnParam = data?.optString(SettingsConstants.PARAM_RETURN)
                            openNativeSettings(returnParam)
                        }
                    }
            }
        }
    }

    private fun openNativeSettings(returnParam: String?) {
        when (returnParam) {
            SettingsConstants.ID_AI_FEATURES,
            SettingsConstants.ID_PRIVATE_SEARCH,
            -> {
                sendCommand(Command.Exit)
            }
            else -> {
                logcat(LogPriority.WARN) { "Unknown settings return value: $returnParam" }
            }
        }
    }

    private fun sendCommand(command: Command) {
        viewModelScope.launch {
            commandChannel.send(command)
        }
    }
}
