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

package com.duckduckgo.duckchat.impl.contextual

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

data class PageContextData(
    val tabId: String,
    val serializedPageData: String,
    val collectedAtMs: Long,
    val isCleared: Boolean = false,
)

interface PageContextRepository {
    suspend fun update(tabId: String, serializedPageData: String)
    suspend fun clear(tabId: String)
    fun getPageContext(tabId: String): Flow<PageContextData?>
}

@ContributesBinding(AppScope::class)
class RealPageContextRepository @Inject constructor(
    private val dataStore: DuckChatDataStore,
) : PageContextRepository {

    override suspend fun update(tabId: String, serializedPageData: String) {
        dataStore.setDuckChatPageContext(tabId, serializedPageData)
    }

    override suspend fun clear(tabId: String) {
        dataStore.clearDuckChatPageContext(tabId)
    }

    override fun getPageContext(tabId: String): Flow<PageContextData?> =
        dataStore.observeDuckChatPageContext()
}
