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

package com.duckduckgo.dataclearing.api.fire

/**
 * Provider for creating Fire dialog instances.
 * Returns the appropriate dialog variant based on feature flag (Simple or Granular).
 *
 * To receive lifecycle events from the dialog (onShow, onCancel, onClearStarted),
 * use FragmentManager.setFragmentResultListener with the appropriate REQUEST_KEY
 * from GranularFireDialog or NonGranularFireDialog.
 */
interface FireDialogProvider {
    /**
     * Creates a Fire dialog instance.
     *
     * @param origin Where the dialog is being launched from; also carries any per-call destructive scope.
     */
    suspend fun createFireDialog(origin: FireDialogOrigin): FireDialog

    sealed class FireDialogOrigin {
        /** Active browser tab. Operates on the currently selected tab. */
        object Browser : FireDialogOrigin()

        /** Settings screens. Operates on all browsing data. */
        object Settings : FireDialogOrigin()

        /** Tab switcher. Operates on all browsing data. */
        object TabSwitcher : FireDialogOrigin()

        /** Contextual Duck.ai chat sheet. Operates on the active tab's chat data. */
        object DuckAiContextualChat : FireDialogOrigin()

        /**
         * New-tab return Hatch (rendered in BrowserTabFragment or InputScreenFragment).
         * Operates on the specific tab the Hatch represents, which is not necessarily
         * the currently selected tab.
         *
         * When the resolved tab's URL is a Duck.ai chat URL, the dialog renders the
         * Duck.ai-tab strings and click handlers (matching the existing Duck.ai-tab
         * behaviour from BROWSER + Duck.ai URL).
         *
         * @property tabId The id of the tab the Hatch is offering to burn.
         */
        data class Hatch(val tabId: String) : FireDialogOrigin()

        /**
         * Chat history screen — bulk-delete confirmation.
         *
         * @property count Number of chats shown in the title ("Delete N chats?").
         * @property selectedChatUrls Non-null scopes the clear to this subset (Delete-selected);
         *  `null` clears every Duck.ai chat (Fire-all).
         */
        data class ChatHistory(
            val count: Int,
            val selectedChatUrls: Set<String>? = null,
        ) : FireDialogOrigin()
    }
}
