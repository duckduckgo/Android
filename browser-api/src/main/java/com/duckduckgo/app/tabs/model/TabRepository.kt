/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.tabs.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.webkit.Profile
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface TabRepository {

    /**
     * @return the tabs that are NOT marked as deletable in the DB
     */
    val liveTabs: LiveData<List<TabEntity>>

    val flowTabs: Flow<List<TabEntity>>

    val childClosedTabs: SharedFlow<String>

    /**
     * @return the tabs that are marked as "deletable" in the DB
     */
    val flowDeletableTabs: Flow<List<TabEntity>>

    val liveSelectedTab: LiveData<TabEntity>

    val flowSelectedTab: Flow<TabEntity?>

    val tabSwitcherData: Flow<TabSwitcherData>

    /**
     * @return tabId of new record
     */
    suspend fun add(
        url: String? = null,
        skipHome: Boolean = false,
        isFireTab: Boolean = false,
    ): String

    suspend fun addDefaultTab(): String

    suspend fun addFromSourceTab(
        url: String? = null,
        skipHome: Boolean = false,
        sourceTabId: String,
        isFireTab: Boolean = false,
    ): String

    suspend fun addNewTabAfterExistingTab(
        url: String? = null,
        tabId: String,
        isFireTab: Boolean = false,
    )

    suspend fun update(
        tabId: String,
        site: Site?,
    )

    suspend fun updateTabPosition(from: Int, to: Int)

    suspend fun updateTabLastAccess(tabId: String)

    /**
     * @return record if it exists, otherwise a new one
     */
    fun retrieveSiteData(tabId: String): MutableLiveData<Site>

    suspend fun delete(tab: TabEntity)

    suspend fun markDeletable(tab: TabEntity)

    suspend fun markDeletable(tabIds: List<String>)

    suspend fun undoDeletable(tab: TabEntity)

    suspend fun undoDeletable(tabIds: List<String>, moveActiveTabToEnd: Boolean = false)

    suspend fun deleteTabs(tabIds: List<String>)

    /**
     * Deletes from the DB all tabs that are marked as "deletable"
     */
    suspend fun purgeDeletableTabs()

    suspend fun getDeletableTabIds(): List<String>

    suspend fun deleteTabAndSelectSource(tabId: String)

    suspend fun deleteAll()

    suspend fun getSelectedTab(): TabEntity?

    suspend fun select(tabId: String)

    suspend fun getTab(tabId: String): TabEntity?

    fun updateTabPreviewImage(
        tabId: String,
        fileName: String?,
    )

    fun updateTabFavicon(
        tabId: String,
        fileName: String?,
    )

    suspend fun selectByUrlOrNewTab(url: String)

    suspend fun getTabId(url: String): String?

    suspend fun setIsUserNew(isUserNew: Boolean)

    suspend fun setTabLayoutType(layoutType: LayoutType)

    suspend fun getTabs(): List<TabEntity>

    fun getOpenTabCount(): Int

    /**
     * Returns the number of tabs, given a range of days within which the tab was last accessed.
     *
     * @param accessOlderThan the minimum number of days (exclusive) since the tab was last accessed
     * @param accessNotMoreThan the maximum number of days (inclusive) since the tab was last accessed (optional)
     * @return the number of tabs that are inactive
     */
    fun countTabsAccessedWithinRange(accessOlderThan: Long, accessNotMoreThan: Long? = null): Int

    fun getTabProfileName(tabId: String): String?

    fun isFireTab(tabId: String): Boolean

    fun clearStaleProfiles()

    fun deleteBrowsingDataForTab(
        tabId: String,
        sites: Set<String>,
    )

    fun deleteBrowsingData()
}
