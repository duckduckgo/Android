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

package com.duckduckgo.duckchat.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.nativeinput.footer.highusage.HighUsageModelNoticeDismissalStore
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class DuckAiUsageWarningsDevViewModel @Inject constructor(
    private val dismissalStore: HighUsageModelNoticeDismissalStore,
) : ViewModel() {

    data class ViewState(
        val dismissedModelIds: Set<String> = emptySet(),
    )

    sealed class Command {
        data class ShowMessage(val messageResId: Int) : Command()
    }

    val viewState: StateFlow<ViewState> = dismissalStore.dismissedModelIds
        .map { ViewState(dismissedModelIds = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    fun onResetDismissalsClicked() {
        viewModelScope.launch {
            dismissalStore.clear()
            _commands.send(Command.ShowMessage(R.string.devSettingsDuckAiUsageWarningsDismissalsReset))
        }
    }
}
