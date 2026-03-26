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

package com.duckduckgo.duckchat.impl.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class DuckAiShortcutSettingsViewModel @Inject constructor(
    private val duckChat: DuckChatInternal,
    duckAiFeatureState: DuckAiFeatureState,
) : ViewModel() {

    data class ViewState(
        val showInBrowserMenu: Boolean = false,
        val showInAddressBar: Boolean = false,
        val showInVoiceSearch: Boolean = false,
        val shouldShowAddressBarToggle: Boolean = false,
        val shouldShowVoiceSearchToggle: Boolean = false,
    )

    val viewState = combine(
        duckChat.observeShowInBrowserMenuUserSetting(),
        duckChat.observeShowInAddressBarUserSetting(),
        duckChat.observeShowInVoiceSearchUserSetting(),
        duckAiFeatureState.showVoiceSearchToggle,
    ) { showInBrowserMenu, showInAddressBar, showInVoiceSearch, showVoiceSearchToggle ->
        ViewState(
            showInBrowserMenu = showInBrowserMenu,
            showInAddressBar = showInAddressBar,
            showInVoiceSearch = showInVoiceSearch,
            shouldShowAddressBarToggle = duckChat.isAddressBarEntryPointEnabled(),
            shouldShowVoiceSearchToggle = duckChat.isVoiceSearchEntryPointEnabled(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    fun onShowDuckChatInMenuToggled(checked: Boolean) {
        viewModelScope.launch {
            duckChat.setShowInBrowserMenuUserSetting(checked)
        }
    }

    fun onShowDuckChatInAddressBarToggled(checked: Boolean) {
        viewModelScope.launch {
            duckChat.setShowInAddressBarUserSetting(checked)
        }
    }

    fun onShowDuckChatInVoiceSearchToggled(checked: Boolean) {
        viewModelScope.launch {
            duckChat.setShowInVoiceSearchUserSetting(checked)
        }
    }
}
