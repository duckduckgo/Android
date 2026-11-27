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

package com.duckduckgo.app.widget

import android.content.Context

/**
 * Launches the system pin-widget prompt where supported, otherwise manual add-widget instructions.
 * The manual instructions need an Activity behind [context] (an Activity or a View's context); without one they are not shown
 * in release builds and crash internal/debug builds.
 */
interface AddWidgetLauncher {
    fun launchAddWidget(
        context: Context,
        widgetType: WidgetType = WidgetType.SEARCH,
    )
}

/**
 * Home screen widgets the pin prompt can offer. [value] is the identifier used in remote config (e.g. the RMF `addWidget` action);
 * [fromValue] falls back to [SEARCH] for empty or unknown values, so new types can be added without breaking older clients.
 */
enum class WidgetType(val value: String) {
    SEARCH("search"),
    SEARCH_ONLY("searchOnly"),
    SEARCH_AND_FAVORITES("searchAndFavorites"),
    ;

    companion object {
        fun fromValue(value: String?): WidgetType = entries.firstOrNull { it.value == value } ?: SEARCH
    }
}
