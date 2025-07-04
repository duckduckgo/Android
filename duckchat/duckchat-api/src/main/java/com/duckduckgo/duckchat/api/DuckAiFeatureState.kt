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

package com.duckduckgo.duckchat.api

import kotlinx.coroutines.flow.StateFlow

interface DuckAiFeatureState {

    /**
     * Indicates whether the Duck AI settings should be available from the main settings screen.
     */
    val showSettings: StateFlow<Boolean>

    /**
     * Indicates whether focusing the omnibar should navigate to a new Duck.ai input screen with a Search/AI mode switcher.
     */
    val showInputScreen: StateFlow<Boolean>

    /**
     * Indicates whether the Duck AI shortcut should be shown in the popup menus in the main browser tabs as well as on the tab switcher screen.
     */
    val showPopupMenuShortcut: StateFlow<Boolean>

    /**
     * Indicates whether the Duck AI omnibar shortcut should be shown on the New Tab Page (NTP) and when the omnibar is focused.
     */
    val showOmnibarShortcutOnNtpAndOnFocus: StateFlow<Boolean>

    /**
     * Indicates whether the Duck AI omnibar shortcut should be shown in all states, including when the omnibar is not focused.
     */
    val showOmnibarShortcutInAllStates: StateFlow<Boolean>
}
