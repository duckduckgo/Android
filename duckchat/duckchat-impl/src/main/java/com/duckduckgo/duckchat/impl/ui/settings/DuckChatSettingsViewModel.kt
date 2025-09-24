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
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnel
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLink
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLinkInNewTab
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenShortcutSettings
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.subscriptions.api.SubscriptionRebrandingFeatureToggle
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class DuckChatSettingsViewModel @Inject constructor(
    private val duckChat: DuckChatInternal,
    private val pixel: Pixel,
    private val rebrandingAiFeaturesEnabled: SubscriptionRebrandingFeatureToggle,
    private val inputScreenDiscoveryFunnel: InputScreenDiscoveryFunnel,
    private val settingsPageFeature: SettingsPageFeature,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    data class ViewState(
        val isDuckChatUserEnabled: Boolean = false,
        val isInputScreenEnabled: Boolean = false,
        val shouldShowShortcuts: Boolean = false,
        val shouldShowInputScreenToggle: Boolean = false,
        val isRebrandingAiFeaturesEnabled: Boolean = false,
    )

    val viewState = combine(
        duckChat.observeEnableDuckChatUserSetting(),
        duckChat.observeInputScreenUserSettingEnabled(),
    ) { isDuckChatUserEnabled, isInputScreenEnabled ->
        ViewState(
            isDuckChatUserEnabled = isDuckChatUserEnabled,
            isInputScreenEnabled = isInputScreenEnabled,
            shouldShowShortcuts = isDuckChatUserEnabled,
            shouldShowInputScreenToggle = isDuckChatUserEnabled && duckChat.isInputScreenFeatureAvailable(),
            isRebrandingAiFeaturesEnabled = rebrandingAiFeaturesEnabled.isAIFeaturesRebrandingEnabled(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    sealed class Command {
        data class OpenLink(val link: String) : Command()
        data class OpenLinkInNewTab(val link: String) : Command()
        data object OpenShortcutSettings : Command()
        data object LaunchFeedback : Command()
    }

    fun onDuckChatUserEnabledToggled(checked: Boolean) {
        viewModelScope.launch {
            if (checked) {
                pixel.fire(DuckChatPixelName.DUCK_CHAT_USER_ENABLED)
            } else {
                pixel.fire(DuckChatPixelName.DUCK_CHAT_USER_DISABLED)
            }
            duckChat.setEnableDuckChatUserSetting(checked)
        }
    }

    fun onShowDuckChatInMenuToggled(checked: Boolean) {
        viewModelScope.launch {
            if (checked) {
                pixel.fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_ON)
            } else {
                pixel.fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_OFF)
            }
            duckChat.setShowInBrowserMenuUserSetting(checked)
        }
    }

    fun onShowDuckChatInAddressBarToggled(checked: Boolean) {
        viewModelScope.launch {
            if (checked) {
                pixel.fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_SETTING_ON)
            } else {
                pixel.fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_SETTING_OFF)
            }
            duckChat.setShowInAddressBarUserSetting(checked)
        }
    }

    fun duckChatLearnMoreClicked() {
        viewModelScope.launch {
            commandChannel.send(OpenLink(DUCK_CHAT_LEARN_MORE_LINK))
        }
    }

    fun duckChatSearchAISettingsClicked() {
        viewModelScope.launch {
            val settingsLink = if (settingsPageFeature.saveAndExitSerpSettings().isEnabled()) {
                DUCK_CHAT_SEARCH_AI_SETTINGS_LINK_WITH_RETURN_PARAM
            } else {
                DUCK_CHAT_SEARCH_AI_SETTINGS_LINK
            }
            commandChannel.send(OpenLinkInNewTab(settingsLink))
            pixel.fire(DuckChatPixelName.DUCK_CHAT_SEARCH_ASSIST_SETTINGS_BUTTON_CLICKED)
        }
    }

    fun onDuckAiShortcutsClicked() {
        viewModelScope.launch {
            commandChannel.send(OpenShortcutSettings)
        }
    }

    fun onDuckAiInputScreenWithoutAiSelected() {
        viewModelScope.launch {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_OFF)
            inputScreenDiscoveryFunnel.onInputScreenDisabled()
            duckChat.setInputScreenUserSetting(enabled = false)
        }
    }

    fun onDuckAiInputScreenWithAiSelected() {
        viewModelScope.launch {
            pixel.fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_ON)
            inputScreenDiscoveryFunnel.onInputScreenEnabled()
            duckChat.setInputScreenUserSetting(enabled = true)
        }
    }

    fun duckAiInputScreenShareFeedbackClicked() {
        viewModelScope.launch {
            commandChannel.send(Command.LaunchFeedback)
        }
    }

    companion object {
        const val DUCK_CHAT_LEARN_MORE_LINK = "https://duckduckgo.com/duckduckgo-help-pages/aichat/"
        const val DUCK_CHAT_SEARCH_AI_SETTINGS_LINK = "https://duckduckgo.com/settings?ko=-1#aifeatures"
        const val DUCK_CHAT_SEARCH_AI_SETTINGS_LINK_WITH_RETURN_PARAM = "https://duckduckgo.com/settings?ko=-1&return=aiFeatures#aifeatures"
    }
}
