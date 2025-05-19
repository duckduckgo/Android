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

package com.duckduckgo.daxprompts.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.daxprompts.impl.ReactivateUsersExperiment
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class DaxPromptDuckPlayerViewModel @Inject constructor(
    private val daxPromptsRepository: DaxPromptsRepository,
    private val reactivateUsersExperiment: ReactivateUsersExperiment,
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onCloseButtonClicked() {
        viewModelScope.launch {
            command.send(Command.CloseScreen)
            reactivateUsersExperiment.fireCloseScreen()
        }
    }

    fun onPrimaryButtonClicked() {
        viewModelScope.launch {
            command.send(Command.TryDuckPlayer(DUCK_PLAYER_DEMO_URL))
            reactivateUsersExperiment.fireDuckPlayerClick()
        }
    }

    fun markDuckPlayerPromptAsShown() {
        viewModelScope.launch {
            daxPromptsRepository.setDaxPromptsShowDuckPlayer(false)
        }
    }

    sealed class Command {
        data object CloseScreen : Command()
        data class TryDuckPlayer(val url: String) : Command()
    }

    companion object {
        internal const val DUCK_PLAYER_DEMO_URL = "https://www.youtube.com/watch?v=yKWIA-Pys4c"
    }
}
