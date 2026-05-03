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

import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.savedsites.api.SavedSitesRepository

enum class TestScenario(val key: String) {
    NATIVE_INPUT_FAVORITES_3("native_input_favorites_3"),
    NATIVE_INPUT_BOOKMARKS_2("native_input_bookmarks_2"),
    NATIVE_INPUT_OMNIBAR_TOP("native_input_omnibar_top"),
    NATIVE_INPUT_OMNIBAR_BOTTOM("native_input_omnibar_bottom"),
    NATIVE_INPUT_DUCK_AI_ENABLED("native_input_duck_ai_enabled"),
    NATIVE_INPUT_DUCK_AI_DISABLED("native_input_duck_ai_disabled");

    // Caller is responsible for providing an IO dispatcher context (e.g. withContext(dispatchers.io())).
    suspend fun seed(
        savedSitesRepository: SavedSitesRepository,
        settingsDataStore: SettingsDataStore,
        duckChatDataStore: DuckChatDataStore,
    ) {
        when (this) {
            NATIVE_INPUT_FAVORITES_3 -> {
                savedSitesRepository.insertFavorite(url = "https://example.com", title = "Example")
                savedSitesRepository.insertFavorite(url = "https://duckduckgo.com", title = "DuckDuckGo")
                savedSitesRepository.insertFavorite(url = "https://privacy-test-pages.site", title = "Privacy Test Pages")
            }
            NATIVE_INPUT_BOOKMARKS_2 -> {
                savedSitesRepository.insertBookmark(url = "https://example.com", title = "Example")
                savedSitesRepository.insertBookmark(url = "https://duckduckgo.com", title = "DuckDuckGo")
            }
            NATIVE_INPUT_OMNIBAR_TOP -> {
                settingsDataStore.omnibarType = OmnibarType.SINGLE_TOP
            }
            NATIVE_INPUT_OMNIBAR_BOTTOM -> {
                settingsDataStore.omnibarType = OmnibarType.SINGLE_BOTTOM
            }
            NATIVE_INPUT_DUCK_AI_ENABLED -> {
                duckChatDataStore.setDuckChatUserEnabled(true)
            }
            NATIVE_INPUT_DUCK_AI_DISABLED -> {
                duckChatDataStore.setDuckChatUserEnabled(false)
            }
        }
    }

    companion object {
        fun fromKey(key: String): TestScenario? = entries.firstOrNull { it.key == key }
    }
}
