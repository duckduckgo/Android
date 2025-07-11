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

package com.duckduckgo.savedsites.api.views

import android.content.Context
import android.view.View

/**
 * Provides views for the Saved Sites feature.
 */
interface SavedSitesViewsProvider {

    /**
     * Returns a view for displaying a grid of favorite sites.
     */
    fun getFavoritesGridView(context: Context, config: FavoritesGridConfig? = null): View
}

/**
 * Configuration for the Favorites Grid view.
 *
 * @property isExpandable If true and there are two or more rows, the favorites are collapsed and can be expanded.
 * If false, the favorites are always expanded.
 * @property showPlaceholders Whether to show placeholder with onboarding if favorites list is empty.
 * @property placement The screen on which favorites are displayed.
 */
data class FavoritesGridConfig(
    val isExpandable: Boolean,
    val showPlaceholders: Boolean,
    val placement: FavoritesPlacement,
)

enum class FavoritesPlacement {
    FOCUSED_STATE,
    NEW_TAB_PAGE,
    ;

    companion object {
        fun from(type: Int): FavoritesPlacement {
            return when (type) {
                0 -> FOCUSED_STATE
                1 -> NEW_TAB_PAGE
                else -> FOCUSED_STATE
            }
        }
    }
}
