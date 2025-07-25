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

package com.duckduckgo.app.tabs.ui.compose

import com.duckduckgo.app.tabs.model.TabSwitcherData
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType

data class ComposeTabSwitcherViewState(
    val selectedTabIndex: Int = 0,
    val tabs: List<ComposeTabSwitcherItem> = emptyList(),
    val layoutType: LayoutType = LayoutType.GRID,
    val mode: Mode = Mode.Normal,
) {

    sealed interface Mode {
        data object Normal : Mode
        data class Selection(
            val selectedTabs: List<String> = emptyList(),
        ) : Mode
    }
}
