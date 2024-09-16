/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.common.ui.themepreview.ui.component.listitems

import com.duckduckgo.common.ui.themepreview.ui.component.Component
import com.duckduckgo.common.ui.themepreview.ui.component.Component.MENU_ITEM
import com.duckduckgo.common.ui.themepreview.ui.component.Component.POPUP_MENU_ITEM
import com.duckduckgo.common.ui.themepreview.ui.component.Component.SECTION_HEADER_LIST_ITEM
import com.duckduckgo.common.ui.themepreview.ui.component.Component.SINGLE_LINE_LIST_ITEM
import com.duckduckgo.common.ui.themepreview.ui.component.Component.TWO_LINE_LIST_ITEM
import com.duckduckgo.common.ui.themepreview.ui.component.ComponentFragment

class ComponentListItemsElementsFragment : ComponentFragment() {
    override fun getComponents(): List<Component> {
        return listOf(SECTION_HEADER_LIST_ITEM, SINGLE_LINE_LIST_ITEM, TWO_LINE_LIST_ITEM, MENU_ITEM, POPUP_MENU_ITEM)
    }
}
