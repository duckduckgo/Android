/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.tabs

import com.duckduckgo.codegen.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "tabManager",
)
interface TabManagerFeatureFlags {
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    @Toggle.InternalAlwaysEnabled
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun tabInsertionFixes(): Toggle

    /**
     * Kill switch for fetching the page title and favicon of tabs opened in the background, before they are loaded.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun backgroundTabPrefetch(): Toggle

    /**
     * Reports the furthest a user went back in the tab activation order, so that the effect of
     * lowering the retained fragment limit can be sized before it is changed.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    @Toggle.InternalAlwaysEnabled
    fun tabMaxReuseDistancePixel(): Toggle

    /**
     * The Duck.ai button opens the chats menu (New Chat, recent chats, View all) instead of opening a new Duck.ai tab.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun duckAiChatsMenu(): Toggle

    /**
     * The Duck.ai "+" menu shows a "New Tab" header, drops the "New" prefix from its items, renames New Tab to Search
     * (opening on the Search input in the current browser mode) and removes New Fire Tab.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun duckAiPlusMenuUpdate(): Toggle

    /**
     * Shows Duck.ai tabs in the tab switcher grid as a rich card instead of the tab preview.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun richDuckAiGridTabItem(): Toggle
}
