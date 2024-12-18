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

package com.duckduckgo.app.browser.tabs

import android.os.Message
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.tabs.adapter.TabPagerAdapter

interface TabManager {
    companion object {
        const val MAX_ACTIVE_TABS = 20
    }

    val currentTab: BrowserTabFragment?
    val tabPagerAdapter: TabPagerAdapter

    fun onSelectedTabChanged(tabId: String)
    fun onTabsUpdated(updatedTabIds: List<String>)

    fun openMessageInNewTab(message: Message, sourceTabId: String?)
    fun openExistingTab(tabId: String)
    fun launchNewTab()
    fun openInNewTab(query: String, sourceTabId: String? = null, skipHome: Boolean = false)

    fun clearTabsInMemory()
    fun onCleanup()
}
