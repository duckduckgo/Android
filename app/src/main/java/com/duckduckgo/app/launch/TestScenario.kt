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

package com.duckduckgo.app.launch

import com.duckduckgo.savedsites.api.SavedSitesRepository

enum class TestScenario(val key: String) {
    FAVORITES_3("favorites_3"),
    BOOKMARKS_2("bookmarks_2");

    // Caller is responsible for providing an IO dispatcher context (e.g. withContext(dispatchers.io())).
    suspend fun seed(savedSitesRepository: SavedSitesRepository) {
        when (this) {
            FAVORITES_3 -> {
                savedSitesRepository.insertFavorite(url = "https://example.com", title = "Example")
                savedSitesRepository.insertFavorite(url = "https://duckduckgo.com", title = "DuckDuckGo")
                savedSitesRepository.insertFavorite(url = "https://privacy-test-pages.site", title = "Privacy Test Pages")
            }
            BOOKMARKS_2 -> {
                savedSitesRepository.insertBookmark(url = "https://example.com", title = "Example")
                savedSitesRepository.insertBookmark(url = "https://duckduckgo.com", title = "DuckDuckGo")
            }
        }
    }

    companion object {
        fun fromKey(key: String): TestScenario? = entries.firstOrNull { it.key == key }
    }
}
