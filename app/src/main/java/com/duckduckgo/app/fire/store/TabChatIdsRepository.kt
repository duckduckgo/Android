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

package com.duckduckgo.app.fire.store

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Tracks Duck.ai chat IDs associated with browser tabs.
 *
 * Used during single-tab burning to ensure chat data is cleared
 * even if the user navigated away from the Duck.ai chat URL.
 */
interface TabChatIdsRepository {

    /** Records that a Duck.ai chat with [chatId] was opened in the given [tabId]. */
    suspend fun recordChatId(tabId: String, chatId: String)

    /** Returns the set of chat IDs that were opened in the given [tabId]. */
    suspend fun getChatIds(tabId: String): Set<String>

    /** Removes all chat ID records for the given [tabId]. */
    suspend fun clearTab(tabId: String)

    /** Removes all chat ID records across all tabs. */
    suspend fun clearAll()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealTabChatIdsRepository @Inject constructor(
    private val dao: TabChatIdsDao,
) : TabChatIdsRepository {

    override suspend fun recordChatId(tabId: String, chatId: String) {
        dao.insert(TabChatIdEntity(tabId = tabId, chatId = chatId))
    }

    override suspend fun getChatIds(tabId: String): Set<String> {
        return dao.getChatIds(tabId).toSet()
    }

    override suspend fun clearTab(tabId: String) {
        dao.clearTab(tabId)
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }
}
