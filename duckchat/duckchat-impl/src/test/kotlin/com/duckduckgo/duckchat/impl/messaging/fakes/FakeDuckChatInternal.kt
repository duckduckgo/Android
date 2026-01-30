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

package com.duckduckgo.duckchat.impl.messaging.fakes

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fake implementation of [DuckChatInternal] for testing purposes.
 */
class FakeDuckChatInternal(
    private var enabled: Boolean = true,
) : DuckChatInternal {

    private val enableDuckChatUserSetting = MutableStateFlow(enabled)
    private val showInBrowserMenuUserSetting = MutableStateFlow(false)
    private val showInAddressBarUserSetting = MutableStateFlow(false)
    private val showInVoiceSearchUserSetting = MutableStateFlow(false)
    private val _chatState = MutableStateFlow(ChatState.READY)
    private val _inputScreenBottomBarEnabled = MutableStateFlow(false)
    private val _showMainButtonsInInputScreen = MutableStateFlow(false)
    private val inputScreenUserSettingEnabled = MutableStateFlow(false)
    private val cosmeticInputScreenUserSettingEnabled = MutableStateFlow<Boolean?>(null)
    private val automaticContextAttachmentUserSettingEnabled = MutableStateFlow<Boolean>(false)
    var contextualOnboardingCompleted: Boolean = false

    // DuckChat interface methods
    override fun isEnabled(): Boolean = enabled

    override fun openDuckChat() { }

    override fun openDuckChatWithAutoPrompt(query: String) { }

    override fun openDuckChatWithPrefill(query: String) { }

    override fun getDuckChatUrl(query: String, autoPrompt: Boolean, sidebar: Boolean): String {
        return "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
    }

    override fun isDuckChatUrl(uri: Uri): Boolean = false

    override suspend fun wasOpenedBefore(): Boolean = false

    override fun showNewAddressBarOptionChoiceScreen(context: Context, isDarkThemeEnabled: Boolean) { }

    override suspend fun setInputScreenUserSetting(enabled: Boolean) {
        inputScreenUserSettingEnabled.value = enabled
    }

    override suspend fun setCosmeticInputScreenUserSetting(enabled: Boolean) {
        cosmeticInputScreenUserSettingEnabled.value = enabled
    }

    override fun observeInputScreenUserSettingEnabled(): Flow<Boolean> = inputScreenUserSettingEnabled

    override fun observeCosmeticInputScreenUserSettingEnabled(): Flow<Boolean?> = cosmeticInputScreenUserSettingEnabled
    override fun observeAutomaticContextAttachmentUserSettingEnabled(): Flow<Boolean> = automaticContextAttachmentUserSettingEnabled

    override fun showContextualOnboarding(context: Context, onConfirmed: () -> Unit) {
        // No-op for testing
    }

    override suspend fun isContextualOnboardingCompleted(): Boolean = contextualOnboardingCompleted

    override fun isAutomaticContextAttachmentEnabled(): Boolean = automaticContextAttachmentUserSettingEnabled.value

    // DuckChatInternal interface methods
    override suspend fun setEnableDuckChatUserSetting(enabled: Boolean) {
        enableDuckChatUserSetting.value = enabled
    }

    override suspend fun setShowInBrowserMenuUserSetting(showDuckChat: Boolean) {
        showInBrowserMenuUserSetting.value = showDuckChat
    }

    override suspend fun setShowInAddressBarUserSetting(showDuckChat: Boolean) {
        showInAddressBarUserSetting.value = showDuckChat
    }

    override suspend fun setShowInVoiceSearchUserSetting(showToggle: Boolean) {
        showInVoiceSearchUserSetting.value = showToggle
    }

    override suspend fun setAutomaticPageContextUserSetting(isEnabled: Boolean) {
        automaticContextAttachmentUserSettingEnabled.value = isEnabled
    }

    override fun observeEnableDuckChatUserSetting(): Flow<Boolean> = enableDuckChatUserSetting

    override fun observeShowInBrowserMenuUserSetting(): Flow<Boolean> = showInBrowserMenuUserSetting

    override fun observeShowInAddressBarUserSetting(): Flow<Boolean> = showInAddressBarUserSetting

    override fun observeShowInVoiceSearchUserSetting(): Flow<Boolean> = showInVoiceSearchUserSetting

    override fun openDuckChatSettings() { }

    override fun closeDuckChat() { }

    override fun openNewDuckChatSession() { }

    override fun observeCloseEvent(lifecycleOwner: LifecycleOwner, onClose: () -> Unit) { }

    override fun isAddressBarEntryPointEnabled(): Boolean = true

    override fun isVoiceSearchEntryPointEnabled(): Boolean = false

    override fun isDuckChatUserEnabled(): Boolean = enableDuckChatUserSetting.value

    override fun updateChatState(state: ChatState) {
        _chatState.value = state
    }

    override val chatState: StateFlow<ChatState> = _chatState

    override fun isImageUploadEnabled(): Boolean = false

    override fun isStandaloneMigrationEnabled(): Boolean = false

    override fun keepSessionIntervalInMinutes(): Int = 30

    override fun isInputScreenFeatureAvailable(): Boolean = false

    override fun isDuckChatFullScreenModeEnabled(): Boolean = false

    override fun isDuckChatContextualModeEnabled(): Boolean = false

    override fun isDuckChatFeatureEnabled(): Boolean = true

    override fun isChatSyncFeatureEnabled(): Boolean = true

    override fun canHandleOnAiWebView(url: String): Boolean = false

    override val inputScreenBottomBarEnabled: StateFlow<Boolean> = _inputScreenBottomBarEnabled

    override val showMainButtonsInInputScreen: StateFlow<Boolean> = _showMainButtonsInInputScreen

    fun setDuckChatUserEnabled(enabled: Boolean) {
        enableDuckChatUserSetting.value = enabled
    }
}
