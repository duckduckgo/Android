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

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckChatNativeSettingsNoParams
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnel
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLink
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLinkInNewTab
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenShortcutSettings
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.settings.api.SettingsPageFeature
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DuckChatSettingsViewModel @AssistedInject constructor(
    @Assisted duckChatActivityParams: GlobalActivityStarter.ActivityParams,
    private val duckChat: DuckChatInternal,
    private val pixel: Pixel,
    private val inputScreenDiscoveryFunnel: InputScreenDiscoveryFunnel,
    private val settingsPageFeature: SettingsPageFeature,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {
    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    data class ViewState(
        val isDuckChatUserEnabled: Boolean = false,
        val isInputScreenEnabled: Boolean = false,
        val shouldShowShortcuts: Boolean = false,
        val shouldShowInputScreenToggle: Boolean = false,
        val isSearchSectionVisible: Boolean = true,
        val isHideGeneratedImagesOptionVisible: Boolean = false,
        val shouldShowFullScreenModeToggle: Boolean = false,
        val isFullScreenModeEnabled: Boolean = false,
    )

    val viewState =
        combine(
            duckChat.observeEnableDuckChatUserSetting(),
            duckChat.observeInputScreenUserSettingEnabled(),
            duckChat.observeFullscreenModeUserSetting(),
            flowOf(settingsPageFeature.hideAiGeneratedImagesOption().isEnabled()).flowOn(dispatcherProvider.io()),
        ) { isDuckChatUserEnabled, isInputScreenEnabled, isFullScreenModeEnabled, isHideAiGeneratedImagesOptionVisible ->
            ViewState(
                isDuckChatUserEnabled = isDuckChatUserEnabled,
                isInputScreenEnabled = isInputScreenEnabled,
                shouldShowShortcuts = isDuckChatUserEnabled,
                shouldShowInputScreenToggle = isDuckChatUserEnabled && duckChat.isInputScreenFeatureAvailable(),
                isSearchSectionVisible = isSearchSectionVisible(duckChatActivityParams),
                isHideGeneratedImagesOptionVisible = isHideAiGeneratedImagesOptionVisible,
                shouldShowFullScreenModeToggle = duckChat.isDuckChatFeatureEnabled(),
                isFullScreenModeEnabled = isFullScreenModeEnabled,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    sealed class Command {
        data class OpenLink(
            val link: String,
            @StringRes val titleRes: Int,
        ) : Command()

        data class OpenLinkInNewTab(
            val link: String,
        ) : Command()

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
            commandChannel.send(OpenLink(DUCK_CHAT_LEARN_MORE_LINK, R.string.duck_chat_title))
        }
    }

    fun duckChatSearchAISettingsClicked() {
        viewModelScope.launch {
            if (settingsPageFeature.embeddedSettingsWebView().isEnabled()) {
                commandChannel.send(
                    OpenLink(
                        link = DUCK_CHAT_SEARCH_AI_SETTINGS_LINK_EMBEDDED,
                        titleRes = if (settingsPageFeature.hideAiGeneratedImagesOption().isEnabled()) {
                            R.string.duckAiSerpSettingsTitle
                        } else {
                            R.string.duck_chat_assist_settings_title
                        },
                    ),
                )
            } else {
                commandChannel.send(OpenLinkInNewTab(DUCK_CHAT_SEARCH_AI_SETTINGS_LINK))
            }
            pixel.fire(DuckChatPixelName.DUCK_CHAT_SEARCH_ASSIST_SETTINGS_BUTTON_CLICKED)
        }
    }

    fun onDuckAiHideAiGeneratedImagesClicked() {
        viewModelScope.launch {
            commandChannel.send(
                OpenLink(
                    link = DUCK_CHAT_HIDE_GENERATED_IMAGES_LINK_EMBEDDED,
                    titleRes = R.string.duckAiSerpSettingsTitle,
                ),
            )
            pixel.fire(DuckChatPixelName.DUCK_CHAT_HIDE_AI_GENERATED_IMAGES_BUTTON_CLICKED)
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

    fun onDuckChatFullscreenModeToggled(checked: Boolean) {
        viewModelScope.launch {
            duckChat.setFullScreenModeUserSetting(checked)
        }
    }

    private fun isSearchSectionVisible(duckChatActivityParams: GlobalActivityStarter.ActivityParams): Boolean = when (duckChatActivityParams) {
        is DuckChatSettingsNoParams -> true
        is DuckChatNativeSettingsNoParams -> false
        else -> throw IllegalArgumentException("Unknown params type: $duckChatActivityParams")
    }

    @AssistedFactory
    interface DuckChatSettingsViewModelFactory {
        fun create(duckChatActivityParams: GlobalActivityStarter.ActivityParams): DuckChatSettingsViewModel
    }

    companion object {
        const val DUCK_CHAT_LEARN_MORE_LINK = "https://duckduckgo.com/duckduckgo-help-pages/aichat/"
        const val DUCK_CHAT_SEARCH_AI_SETTINGS_LINK = "https://duckduckgo.com/settings?ko=-1#aifeatures"
        const val DUCK_CHAT_SEARCH_AI_SETTINGS_LINK_EMBEDDED = "https://duckduckgo.com/settings?ko=-1&embedded=1&highlight=kbe#aifeatures"
        const val DUCK_CHAT_HIDE_GENERATED_IMAGES_LINK_EMBEDDED = "https://duckduckgo.com/settings?ko=-1&embedded=1&highlight=kbj#aifeatures"
    }
}
