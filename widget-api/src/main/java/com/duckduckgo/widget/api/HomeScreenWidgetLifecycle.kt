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

package com.duckduckgo.widget.api

/**
 * Lifecycle hooks every home-screen widget provider reports to, so app-wide widget metrics stay consistent.
 */
interface HomeScreenWidgetLifecycle {
    /**
     * Called from AppWidgetProvider.onEnabled, when the first instance of a widget type is placed.
     */
    fun onWidgetEnabled()

    /**
     * Called from AppWidgetProvider.onDisabled, when the last instance of a widget type is removed.
     */
    fun onWidgetDisabled()
}
