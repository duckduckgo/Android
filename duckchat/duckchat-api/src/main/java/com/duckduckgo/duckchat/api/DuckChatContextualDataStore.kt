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

interface DuckChatContextualDataStore {
    suspend fun persistTabChatUrl(tabId: String, url: String)
    suspend fun getTabChatUrl(tabId: String): String?
    suspend fun persistTabClosedTimestamp(tabId: String, timestampMs: Long)
    suspend fun getTabClosedTimestamp(tabId: String): Long?
    fun clearTabChatUrl(tabId: String)
    fun clearTabClosedTimestamp(tabId: String)
    fun clearAll()
}
