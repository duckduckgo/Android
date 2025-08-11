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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.experiments.visual.store.ExperimentalThemingDataStore
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLink
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLinkInNewTab
import com.duckduckgo.subscriptions.api.SubscriptionRebrandingFeatureToggle
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class DuckAiShortcutSettingsViewModel @Inject constructor(
    private val duckChat: DuckChatInternal,
) : ViewModel() {

    data class ViewState(
        val showInBrowserMenu: Boolean = false,
        val showInAddressBar: Boolean = false,
        val shouldShowBrowserMenuToggle: Boolean = false,
        val shouldShowAddressBarToggle: Boolean = false,
    )

    val viewState = combine(
        duckChat.observeShowInBrowserMenuUserSetting(),
        duckChat.observeShowInAddressBarUserSetting(),
    ) { showInBrowserMenu, showInAddressBar ->
        ViewState(
            showInBrowserMenu = showInBrowserMenu,
            showInAddressBar = showInAddressBar,
            shouldShowBrowserMenuToggle = true,
            shouldShowAddressBarToggle = duckChat.isAddressBarEntryPointEnabled(),
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
}
