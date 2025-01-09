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

package com.duckduckgo.duckchat.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.DuckChatSettingsViewModel.Command.OpenLearnMore
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class DuckChatSettingsViewModel @Inject constructor(
    private val duckChat: DuckChatInternal,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    data class ViewState(
        val showInBrowserMenu: Boolean = false,
    )

    val viewState = duckChat.observeShowInBrowserMenu()
        .map { showInBrowserMenu ->
            ViewState(showInBrowserMenu)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    sealed class Command {
        data class OpenLearnMore(val learnMoreLink: String) : Command()
    }

    fun onShowDuckChatInMenuToggled(checked: Boolean) {
        viewModelScope.launch {
            duckChat.setShowInBrowserMenu(checked)
        }
    }

    fun duckChatLearnMoreClicked() {
        viewModelScope.launch {
            commandChannel.send(OpenLearnMore("https://duckduckgo.com/duckduckgo-help-pages/aichat/"))
        }
    }
}
