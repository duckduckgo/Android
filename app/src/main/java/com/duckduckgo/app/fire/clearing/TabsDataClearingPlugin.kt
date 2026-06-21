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

package com.duckduckgo.app.fire.clearing

import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import javax.inject.Inject

/** Clears Fire-mode tabs (and a single Fire tab for the single-tab burn). */
@ContributesMultibinding(AppScope::class)
class TabsDataClearingPlugin @Inject constructor(
    private val tabRepositoryProvider: BrowserModeDataProvider<TabRepository>,
) : DataClearingPlugin {

    override suspend fun onClearData(types: Set<ClearableData>) {
        types.forEach { type ->
            when (type) {
                is ClearableData.Tabs.All -> performDelete(BrowserMode.FIRE)
                is ClearableData.Tabs.AllForMode -> if (type.mode == BrowserMode.FIRE) performDelete(type.mode)
                is ClearableData.Tabs.SingleForMode -> if (type.mode == BrowserMode.FIRE) {
                    tabRepositoryProvider.forMode(type.mode).deleteTabs(listOf(type.tabId))
                }
                else -> { /* not handled */ }
            }
        }
    }

    private suspend fun performDelete(browserMode: BrowserMode) {
        logcat { "Clearing all $browserMode tabs" }
        tabRepositoryProvider.forMode(browserMode).deleteAll()
    }
}
