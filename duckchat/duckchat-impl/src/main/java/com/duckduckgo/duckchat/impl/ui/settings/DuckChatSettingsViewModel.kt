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
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnel
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelParameters
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.pixel.fireCountAndDaily
import com.duckduckgo.duckchat.impl.store.DefaultTogglePosition
import com.duckduckgo.duckchat.impl.store.SearchAssistVisibility
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLink
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLinkInNewTab
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenShortcutSettings
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.ShowSearchAssistDialog
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.settings.api.SerpSettingsDataProvider
import com.duckduckgo.settings.api.SettingsPageFeature
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DuckChatSettingsViewModel @AssistedInject constructor(
    @Assisted duckChatActivityParams: GlobalActivityStarter.ActivityParams,
    private val duckChat: DuckChatInternal,
    private val pixel: Pixel,
    private val inputScreenDiscoveryFunnel: InputScreenDiscoveryFunnel,
    private val settingsPageFeature: SettingsPageFeature,
    private val duckChatPixels: DuckChatPixels,
    private val dispatcherProvider: DispatcherProvider,
    private val duckChatFeature: DuckChatFeature,
    private val serpSettingsDataProvider: SerpSettingsDataProvider,
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
        val isAutomaticContextVisible: Boolean = false,
        val isAutomaticContextEnabled: Boolean = false,
        val isDefaultTogglePositionVisible: Boolean = false,
        val defaultTogglePosition: DefaultTogglePosition = DefaultTogglePosition.SEARCH,
        val isNativeControlsEnabled: Boolean = false,
        val searchAssistVisibility: SearchAssistVisibility = DEFAULT_SEARCH_ASSIST_VISIBILITY,
    )

    private data class FeatureState(
        val isDuckChatUserEnabled: Boolean,
        val isCosmeticInputScreenEnabled: Boolean?,
        val isInputScreenEnabled: Boolean,
        val isAutomaticContextEnabled: Boolean,
    )

    private data class FeatureVisibility(
        val isHideGeneratedImagesOptionVisible: Boolean,
        val isRememberTogglePositionVisible: Boolean,
        val isNativeControlsEnabled: Boolean,
    )

    private val featureState =
        combine(
            duckChat.observeEnableDuckChatUserSetting(),
            duckChat.observeCosmeticInputScreenUserSettingEnabled(),
            duckChat.observeInputScreenUserSettingEnabled(),
            duckChat.observeAutomaticContextAttachmentUserSettingEnabled(),
        ) { isDuckChatUserEnabled, cosmeticInputScreenEnabled, isInputScreenEnabled, isAutomaticPageContextEnabled ->
            FeatureState(
                isDuckChatUserEnabled = isDuckChatUserEnabled,
                isCosmeticInputScreenEnabled = cosmeticInputScreenEnabled,
                isInputScreenEnabled = isInputScreenEnabled,
                isAutomaticContextEnabled = isAutomaticPageContextEnabled,
            )
        }

    private val featureVisibility =
        flow {
            emit(
                FeatureVisibility(
                    isHideGeneratedImagesOptionVisible = duckChatFeature.showHideAiGeneratedImages().isEnabled(),
                    isRememberTogglePositionVisible = duckChatFeature.rememberTogglePosition().isEnabled(),
                    isNativeControlsEnabled = duckChatFeature.aiFeaturesNativeControls().isEnabled(),
                ),
            )
        }.flowOn(dispatcherProvider.io())

    val viewState =
        combine(
            featureState,
            featureVisibility,
            duckChat.observeDefaultTogglePosition(),
            observeSearchAssistVisibility(),
        ) { featureState, featureVisibility, defaultTogglePosition, searchAssistVisibility ->
            val isDuckChatUserEnabled = featureState.isDuckChatUserEnabled
            val isInputScreenEnabled = featureState.isCosmeticInputScreenEnabled ?: featureState.isInputScreenEnabled
            ViewState(
                isDuckChatUserEnabled = isDuckChatUserEnabled,
                isInputScreenEnabled = isInputScreenEnabled,
                shouldShowShortcuts = isDuckChatUserEnabled,
                shouldShowInputScreenToggle = isDuckChatUserEnabled && duckChat.isInputScreenFeatureAvailable(),
                isSearchSectionVisible = isSearchSectionVisible(duckChatActivityParams),
                isHideGeneratedImagesOptionVisible = featureVisibility.isHideGeneratedImagesOptionVisible,
                isAutomaticContextEnabled = featureState.isAutomaticContextEnabled,
                isAutomaticContextVisible = isDuckChatUserEnabled && duckChatFeature.automaticContextAttachment().isEnabled(),
                isDefaultTogglePositionVisible = isDuckChatUserEnabled && isInputScreenEnabled &&
                    duckChat.isInputScreenFeatureAvailable() && featureVisibility.isRememberTogglePositionVisible,
                defaultTogglePosition = defaultTogglePosition,
                isNativeControlsEnabled = featureVisibility.isNativeControlsEnabled,
                searchAssistVisibility = searchAssistVisibility ?: DEFAULT_SEARCH_ASSIST_VISIBILITY,
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

        data class ShowDefaultTogglePositionDialog(
            val currentPosition: DefaultTogglePosition,
        ) : Command()

        data class ShowSearchAssistDialog(
            val currentVisibility: SearchAssistVisibility,
        ) : Command()
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

    fun onAutomaticContextAttachmentToggled(checked: Boolean) {
        viewModelScope.launch {
            duckChat.setAutomaticPageContextUserSetting(checked)
        }
        duckChatPixels.reportContextualSettingAutomaticPageContentToggled(checked)
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

    fun duckChatLearnMoreClicked() {
        viewModelScope.launch {
            commandChannel.send(OpenLink(DUCK_CHAT_LEARN_MORE_LINK, R.string.duck_chat_title))
        }
    }

    fun duckChatSearchAISettingsClicked() {
        viewModelScope.launch {
            val (nativeControlsEnabled, embeddedSettingsEnabled, showHideAiGeneratedImages) = withContext(dispatcherProvider.io()) {
                Triple(
                    duckChatFeature.aiFeaturesNativeControls().isEnabled(),
                    settingsPageFeature.embeddedSettingsWebView().isEnabled(),
                    duckChatFeature.showHideAiGeneratedImages().isEnabled(),
                )
            }

            if (nativeControlsEnabled) {
                commandChannel.send(ShowSearchAssistDialog(viewState.value.searchAssistVisibility))
            } else if (embeddedSettingsEnabled) {
                commandChannel.send(
                    OpenLink(
                        link = if (showHideAiGeneratedImages) {
                            DUCK_CHAT_SEARCH_AI_SETTINGS_LINK_EMBEDDED
                        } else {
                            LEGACY_DUCK_CHAT_SEARCH_AI_SETTINGS_LINK_EMBEDDED
                        },
                        titleRes = if (showHideAiGeneratedImages) {
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
            pixel.fire(DuckChatPixelName.SERP_SETTINGS_OPEN_HIDE_AI_GENERATED_IMAGES)
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

    fun onDefaultTogglePositionClicked() {
        viewModelScope.launch {
            commandChannel.send(
                Command.ShowDefaultTogglePositionDialog(
                    currentPosition = viewState.value.defaultTogglePosition,
                ),
            )
        }
    }

    fun onDefaultTogglePositionSelected(position: DefaultTogglePosition) {
        viewModelScope.launch {
            duckChat.setDefaultTogglePosition(position)
        }
        pixel.fireCountAndDaily(
            countPixel = DuckChatPixelName.DUCK_CHAT_SETTINGS_DEFAULT_TOGGLE_POSITION_CHANGED_COUNT,
            dailyPixel = DuckChatPixelName.DUCK_CHAT_SETTINGS_DEFAULT_TOGGLE_POSITION_CHANGED_DAILY,
            parameters = mapOf(DuckChatPixelParameters.DEFAULT_TOGGLE_POSITION_VALUE to position.pixelValue),
        )
    }

    fun onSearchAssistVisibilitySelected(visibility: SearchAssistVisibility) {
        viewModelScope.launch {
            // The SERP blob is the single source of truth, so the web reflects this on its next getNativeSettings.
            serpSettingsDataProvider.setSetting(SearchAssistVisibility.SERP_SETTINGS_KEY, visibility.serpCode)
        }
    }

    // Emits null until the user (or SERP) has provided a value; callers default it.
    private fun observeSearchAssistVisibility(): Flow<SearchAssistVisibility?> =
        serpSettingsDataProvider.observeSetting(SearchAssistVisibility.SERP_SETTINGS_KEY)
            .map { SearchAssistVisibility.fromSerpCode(it) }

    fun duckAiInputScreenShareFeedbackClicked() {
        viewModelScope.launch {
            commandChannel.send(Command.LaunchFeedback)
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
        // Shown when no Search Assist value has been synced from the SERP yet.
        private val DEFAULT_SEARCH_ASSIST_VISIBILITY = SearchAssistVisibility.SOMETIMES
        const val DUCK_CHAT_LEARN_MORE_LINK = "https://duckduckgo.com/duckduckgo-help-pages/aichat/"
        const val DUCK_CHAT_SEARCH_AI_SETTINGS_LINK = "https://duckduckgo.com/settings?ko=-1#aifeatures"
        const val LEGACY_DUCK_CHAT_SEARCH_AI_SETTINGS_LINK_EMBEDDED = "https://duckduckgo.com/settings?ko=-1&embedded=1&highlight=kbe#aifeatures"
        const val DUCK_CHAT_SEARCH_AI_SETTINGS_LINK_EMBEDDED =
            "https://duckduckgo.com/settings?ko=-1&embedded=1&highlight=kbe&hideduckai=1#aifeatures"
        const val DUCK_CHAT_HIDE_GENERATED_IMAGES_LINK_EMBEDDED =
            "https://duckduckgo.com/settings?ko=-1&embedded=1&highlight=kbj&hideduckai=1#aifeatures"
    }
}
