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

package com.duckduckgo.app.tabs.model

/**
 * Repository for caching page content extracted by the JS PageContext layer.
 * Content is stored per tab.
 */
interface TabPageContextRepository {

    /**
     * Stores (or replaces) the cached page context for the given tab.
     *
     * @param tabId the tab this content belongs to
     * @param url the URL the content was extracted from
     * @param serializedPageContext the full JSON blob
     */
    suspend fun storePageContext(tabId: String, url: String, serializedPageContext: String)

    /**
     * Returns cached page contexts for the requested tabs.
     *
     * @param tabIds the tab IDs to look up
     * @return a map of tabId → [CachedPageContext] for tabs that have cached content
     */
    suspend fun getPageContexts(tabIds: List<String>): Map<String, CachedPageContext>

    /**
     * Deletes all cached page contexts.
     */
    suspend fun deleteAll()
}

/**
 * Non-Room model representing a cached page context entry.
 */
data class CachedPageContext(
    val tabId: String,
    val url: String,
    val serializedPageContext: String,
    val collectedAt: Long,
)
