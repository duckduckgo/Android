/*
 * Copyright (c) 2026 DuckDuckGo
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

/**
 * Handles deletion of individual Duck AI chat conversations from local storage.
 *
 * This is used during single-tab burn to delete the chat data associated with a specific
 * Duck AI chat tab across all relevant domains (duck.ai, duckduckgo.com).
 */
interface DuckChatDeleter {
    /**
     * Deletes the local storage data for a specific chat conversation.
     *
     * @param chatId the unique identifier of the chat to delete
     * @return true if the chat was successfully deleted from all domains, false otherwise
     */
    suspend fun deleteChat(chatId: String): Boolean
}
