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

package com.duckduckgo.browser.api.ui

import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.Flow

/**
 * Plugin interface for contributing menu items to the browser menu.
 * Implementations are discovered via multibinding and rendered in the browser bottom sheet / popup menu.
 */
interface BrowserMenuPlugin {
    /**
     * Returns a [View] representing this menu item, or null if the item should not be shown.
     */
    fun getMenuItemView(context: Context): View?

    /**
     * Reactive flow indicating whether this plugin wants the browser menu icon to show a highlight dot.
     */
    val menuHighlightFlow: Flow<Boolean>
}
