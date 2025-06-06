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

package com.duckduckgo.app.tabs.ui

import com.duckduckgo.app.tabs.model.TabEntity

sealed class TabSwitcherItem(val id: String) {
    sealed class Tab(val tabEntity: TabEntity) : TabSwitcherItem(tabEntity.tabId) {
        data class NormalTab(
            private val entity: TabEntity,
            val isActive: Boolean,
        ) : Tab(entity)

        data class SelectableTab(
            private val entity: TabEntity,
            val isSelected: Boolean,
        ) : Tab(entity)

        val isNewTabPage: Boolean
            get() = tabEntity.url.isNullOrBlank()
    }

    data class TrackerAnimationInfoPanel(val trackerCount: Int) : TabSwitcherItem(TRACKER_ANIMATION_PANEL_ID) {
        companion object {
            const val ANIMATED_TILE_NO_REPLACE_ALPHA = 0.4f
            const val ANIMATED_TILE_DEFAULT_ALPHA = 1f
            const val TRACKER_ANIMATION_PANEL_ID = "TrackerAnimationInfoPanel"
        }
    }
}
