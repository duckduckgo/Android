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

package com.duckduckgo.app.dev.settings.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.dev.settings.onboarding.PreBrowserOnboardingReplayer
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class ComposeSamplesViewModel @Inject constructor(
    private val replayer: PreBrowserOnboardingReplayer,
) : ViewModel() {

    data class ViewState(val launching: Boolean = false)

    sealed interface Command {
        data object RelaunchIntoOnboarding : Command
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    fun onOnboardingSampleClicked() {
        if (_viewState.value.launching) return
        _viewState.value = ViewState(launching = true)
        viewModelScope.launch {
            replayer.prepareReplay(useComposeDialogs = true)
            _commands.send(Command.RelaunchIntoOnboarding)
        }
    }
}
