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

package com.duckduckgo.mobile.android.voice.impl.listeningmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.mobile.android.voice.impl.listeningmode.OnDeviceSpeechRecognizer.Event.PartialResultReceived
import com.duckduckgo.mobile.android.voice.impl.listeningmode.OnDeviceSpeechRecognizer.Event.RecognitionSuccess
import com.duckduckgo.mobile.android.voice.impl.listeningmode.OnDeviceSpeechRecognizer.Event.VolumeUpdateReceived
import com.duckduckgo.mobile.android.voice.impl.listeningmode.VoiceSearchViewModel.Command.HandleSpeechRecognitionSuccess
import com.duckduckgo.mobile.android.voice.impl.listeningmode.VoiceSearchViewModel.Command.UpdateVoiceIndicator
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.voice.impl.listeningmode.OnDeviceSpeechRecognizer.Event.RecognitionTimedOut
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

class VoiceSearchViewModel constructor(
    private val speechRecognizer: OnDeviceSpeechRecognizer
) : ViewModel() {
    data class ViewState(
        val result: String = "",
        val unsentResult: String = ""
    )

    sealed class Command {
        data class UpdateVoiceIndicator(val volume: Float) : Command()
        data class HandleSpeechRecognitionSuccess(val result: String) : Command()
        object TerminateVoiceSearch : Command()
    }

    private val viewState = MutableStateFlow(ViewState())

    private val command = Channel<Command>(1, DROP_OLDEST)

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun start() {
        if (viewState.value.result.isNotEmpty()) {
            viewModelScope.launch {
                viewState.emit(
                    viewState.value.copy(unsentResult = viewState.value.result)
                )
            }
        }

        speechRecognizer.start {
            when (it) {
                is PartialResultReceived -> showRecognizedSpeech(it.partialResult)
                is RecognitionSuccess -> handleSuccess(it.result)
                is VolumeUpdateReceived -> sendCommand(UpdateVoiceIndicator(it.normalizedVolume))
                is RecognitionTimedOut -> handleTimeOut()
            }
        }
    }

    private fun handleTimeOut() {
        if (viewState.value.result.isEmpty()) {
            viewModelScope.launch {
                command.send(Command.TerminateVoiceSearch)
            }
        } else {
            handleSuccess(viewState.value.result)
        }
    }

    fun stop() {
        speechRecognizer.stop()
    }

    private fun sendCommand(commandToSend: Command) {
        viewModelScope.launch {
            command.send(commandToSend)
        }
    }

    private fun handleSuccess(result: String) {
        sendCommand(
            HandleSpeechRecognitionSuccess(
                getFullResult(
                    result,
                    viewState.value.unsentResult
                )
            )
        )
    }

    private fun showRecognizedSpeech(result: String) {
        viewModelScope.launch {
            viewState.emit(
                viewState.value.copy(
                    result = getFullResult(result, viewState.value.unsentResult)
                )
            )
        }
        if (result.hasReachedWordLimit()) {
            handleSuccess(result)
        }
    }

    private fun String.hasReachedWordLimit(): Boolean {
        return this.isNotEmpty() && this.split(" ").size > 30
    }

    private fun getFullResult(
        result: String,
        unsentResult: String
    ): String {
        return if (unsentResult.isNotEmpty()) {
            "$unsentResult $result"
        } else {
            result
        }
    }

    fun userInitiatesSearchComplete() {
        sendCommand(
            HandleSpeechRecognitionSuccess(
                getFullResult(
                    viewState.value.result,
                    viewState.value.unsentResult
                )
            )
        )
    }
}

@ContributesMultibinding(AppScope::class)
class VoiceSearchViewModelFactory @Inject constructor(
    private val speechRecognizer: Provider<OnDeviceSpeechRecognizer>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(VoiceSearchViewModel::class.java) -> (
                    VoiceSearchViewModel(speechRecognizer.get()) as T
                    )
                else -> null
            }
        }
    }
}
