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
import com.duckduckgo.duckchat.api.DuckChat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake implementation of [DuckChat] for testing purposes.
 */
class FakeDuckChat(
    private var enabled: Boolean = true,
) : DuckChat {

    private val openDuckChatCalls = mutableListOf<Unit>()
    private val openDuckChatWithAutoPromptCalls = mutableListOf<String>()
    private val openDuckChatWithPrefillCalls = mutableListOf<String>()
    private var wasOpenedBeforeValue: Boolean = false
    private val inputScreenUserSettingEnabled = MutableStateFlow<Boolean>(false)
    private val cosmeticInputScreenUserSettingEnabled = MutableStateFlow<Boolean?>(null)
    private val automaticContextAttachmentUserSettingEnabled = MutableStateFlow<Boolean>(false)
    var contextualOnboardingCompleted: Boolean = false

    override fun isEnabled(): Boolean = enabled

    override fun openDuckChat() {
        openDuckChatCalls.add(Unit)
    }

    override fun openDuckChatWithAutoPrompt(query: String) {
        openDuckChatWithAutoPromptCalls.add(query)
    }

    override fun openDuckChatWithPrefill(query: String) {
        openDuckChatWithPrefillCalls.add(query)
    }

    override fun getDuckChatUrl(
        query: String,
        autoPrompt: Boolean,
        sidebar: Boolean,
    ): String {
        return "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
    }

    override fun isDuckChatUrl(uri: Uri): Boolean {
        return uri.toString().contains("duckchat")
    }

    override suspend fun wasOpenedBefore(): Boolean {
        return wasOpenedBeforeValue
    }

    override fun showNewAddressBarOptionChoiceScreen(context: Context, isDarkThemeEnabled: Boolean) {
        // No-op for testing
    }

    override suspend fun setInputScreenUserSetting(enabled: Boolean) {
        inputScreenUserSettingEnabled.value = enabled
    }

    override suspend fun setCosmeticInputScreenUserSetting(enabled: Boolean) {
        cosmeticInputScreenUserSettingEnabled.value = enabled
    }

    override fun observeInputScreenUserSettingEnabled(): Flow<Boolean> {
        return inputScreenUserSettingEnabled
    }

    override fun observeCosmeticInputScreenUserSettingEnabled(): Flow<Boolean?> {
        return cosmeticInputScreenUserSettingEnabled
    }

    override fun observeAutomaticContextAttachmentUserSettingEnabled(): Flow<Boolean> {
        return automaticContextAttachmentUserSettingEnabled
    }

    override fun showContextualOnboarding(context: Context, onConfirmed: () -> Unit) {
        // No-op for testing
    }

    override suspend fun isContextualOnboardingCompleted(): Boolean {
        return contextualOnboardingCompleted
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
