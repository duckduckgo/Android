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

package com.duckduckgo.duckchat.api

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * DuckChat interface provides a set of methods for interacting and controlling DuckChat.
 */
interface DuckChat {
    /**
     * Checks whether DuckChat is enabled based on remote config flag and user preference.
     * Uses a cached value - does not perform disk I/O.
     *
     * @return true if DuckChat is enabled, false otherwise.
     */
    fun isEnabled(): Boolean

    /**
     * Opens the DuckChat WebView with optional pre-filled [String] query.
     */
    fun openDuckChat()

    /**
     * Auto-prompts the DuckChat WebView with the provided [String] query.
     */
    fun openDuckChatWithAutoPrompt(query: String)

    /**
     * Opens Duck Chat with a prefilled [String] query.
     */
    fun openDuckChatWithPrefill(query: String)

    /**
     * Returns the Duck Chat URL to be used
     */
    fun getDuckChatUrl(query: String, autoPrompt: Boolean, sidebar: Boolean = false): String

    /**
     * Determines whether a given [Uri] is a DuckChat URL.
     * There are two Duck Chat URLs
     * Legacy: https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5
     * After Migration: https://duck.ai/chat?q=DuckDuckGo+AI+Chat&duckai=5&atb=v520-1ru&ko=-1&t=ddg_android&migration=native-import
     * https://app.asana.com/1/137249556945/task/1210497696306780
     * @return true if it is a DuckChat URL, false otherwise.
     */
    fun isDuckChatUrl(uri: Uri): Boolean

    /**
     * Returns `true` if Duck Chat was ever opened before.
     */
    suspend fun wasOpenedBefore(): Boolean

    /**
     * Displays the new address bar option choice screen.
     */
    fun showNewAddressBarOptionChoiceScreen(context: Context, isDarkThemeEnabled: Boolean)

    /**
     * Set user setting to determine whether dedicated Duck.ai input screen with a mode switch should be used.
     */
    suspend fun setInputScreenUserSetting(enabled: Boolean)

    /**
     * Cosmetically sets the input screen user setting.
     */
    suspend fun setCosmeticInputScreenUserSetting(enabled: Boolean)

    /**
     * Observes whether Duck.ai input screen with a mode switch is enabled or disabled.
     */
    fun observeInputScreenUserSettingEnabled(): Flow<Boolean>

    /**
     * Observes the cosmetic value for the input screen user setting.
     * Returns null if the cosmetic value has not been set before.
     */
    fun observeCosmeticInputScreenUserSettingEnabled(): Flow<Boolean?>

    /**
     * Observes the value for the automatic context attachment for Contextual Mode
     */
    fun observeAutomaticContextAttachmentUserSettingEnabled(): Flow<Boolean>

    /**
     * Shows the contextual onboarding bottom sheet dialog.
     * @param context The context to show the dialog in.
     * @param onConfirmed Callback invoked when the user confirms the dialog.
     */
    fun showContextualOnboarding(context: Context, onConfirmed: () -> Unit)

    /**
     * Checks if the contextual onboarding has been completed.
     * @return true if the onboarding was completed, false otherwise.
     */
    suspend fun isContextualOnboardingCompleted(): Boolean
}
