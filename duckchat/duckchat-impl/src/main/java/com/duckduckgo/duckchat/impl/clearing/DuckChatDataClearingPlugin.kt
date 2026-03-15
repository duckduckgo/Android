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

package com.duckduckgo.duckchat.impl.clearing

import com.duckduckgo.dataclearing.api.plugin.ClearResult
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.dataclearing.api.plugin.DataType
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.store.DuckChatContextualDataStore
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DuckChatDataClearingPlugin @Inject constructor(
    private val duckChat: DuckChatInternal,
    private val contextualDataStore: DuckChatContextualDataStore,
) : DataClearingPlugin {

    override suspend fun onClearData(types: Set<DataType>): ClearResult {
        for (type in types) {
            when (type) {
                is DataType.Tabs.Single -> {
                    contextualDataStore.clearTabChatUrl(type.tabId)
                }
                is DataType.DuckChats.Contextual -> {
                    contextualDataStore.getTabChatUrl(type.tabId)?.let { contextualChatUrl ->
                        duckChat.deleteChat(contextualChatUrl)
                    }
                }
                is DataType.Tabs.All -> {
                    contextualDataStore.clearAll()
                }
                is DataType.DuckChats.Single -> {
                    duckChat.deleteChat(type.chatUrl)
                }
                is DataType.DuckChats.All -> {
                    // Full chat content is cleared via WebDataManager (BrowserData path).
                    // Contextual metadata is cleaned up via Tabs.All.
                }
                else -> { /* not handled by this plugin */ }
            }
        }
        return ClearResult.Success
    }
}
