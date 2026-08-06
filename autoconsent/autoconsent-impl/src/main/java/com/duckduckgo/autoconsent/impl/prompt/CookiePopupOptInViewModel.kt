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

package com.duckduckgo.autoconsent.impl.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class CookiePopupOptInViewModel @Inject constructor() : ViewModel() {

    enum class Choice { MAX, KEEP_CURRENT }

    data class ViewState(val selected: Choice)

    sealed class Command {
        data object Close : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    private val viewStateFlow = MutableStateFlow(ViewState(selected = Choice.MAX))
    val viewState: StateFlow<ViewState> = viewStateFlow

    fun commands(): Flow<Command> = command.receiveAsFlow()

    fun onOptionSelected(choice: Choice) {
        viewStateFlow.value = ViewState(selected = choice)
    }

    fun onConfirmClicked() {
        viewModelScope.launch { command.send(Command.Close) }
    }
}
