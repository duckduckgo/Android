/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.tabs.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSelectionEntity
import com.duckduckgo.common.utils.swap
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import java.time.LocalDateTime
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import logcat.logcat

@Dao
@SingleInstanceIn(AppScope::class)
abstract class TabsDao {

    @Query("select * from tabs where deletable is 0 order by position limit 1")
    abstract fun firstTab(): TabEntity?

    @Query("select * from tabs inner join tab_selection on tabs.tabId = tab_selection.tabId order by position limit 1")
    abstract fun selectedTab(): TabEntity?

    @Query("select * from tabs inner join tab_selection on tabs.tabId = tab_selection.tabId order by position limit 1")
    abstract fun liveSelectedTab(): LiveData<TabEntity>

    @Query("select * from tabs where deletable is 0 order by position")
    abstract fun tabs(): List<TabEntity>

    @Query("select * from tabs where deletable is 0 order by position")
    abstract fun liveTabs(): LiveData<List<TabEntity>>

    @Query("select * from tabs where deletable is 1 order by position")
    abstract fun flowDeletableTabs(): Flow<List<TabEntity>>

    @Query("select * from tabs where tabId = :tabId")
    abstract fun tab(tabId: String): TabEntity?

    @Query("select tabId from tabs where url LIKE :query")
    abstract suspend fun selectTabByUrl(query: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertTab(tab: TabEntity)

    @Update
    abstract fun updateTab(tab: TabEntity)

    @Delete
    abstract fun deleteTab(tab: TabEntity)

    @Query("delete from tabs where tabId in (:tabIds)")
    abstract fun deleteTabs(tabIds: List<String>)

    @Query("delete from tabs where deletable is 1")
    abstract fun deleteTabsMarkedAsDeletable()

    @Query("select tabId from tabs where deletable is 1")
    abstract fun getDeletableTabIds(): List<String>

    @Transaction
    open fun markTabAsDeletable(tab: TabEntity) {
        // requirement: only one tab can be marked as deletable
        deleteTabsMarkedAsDeletable()
        // ensure the tab is in the DB
        val dbTab = tab(tab.tabId)
        dbTab?.let {
            updateTab(dbTab.copy(deletable = true))
        }
    }

    @Transaction
    open fun markTabsAsDeletable(tabIds: List<String>) {
        tabIds.forEach { tabId ->
            tab(tabId)?.let { tab ->
                updateTab(tab.copy(deletable = true))
            }
        }
    }

    @Transaction
    open fun undoDeletableTab(tab: TabEntity) {
        // ensure the tab is in the DB
        val dbTab = tab(tab.tabId)
        dbTab?.let {
            updateTab(dbTab.copy(deletable = false))
        }
    }

    @Transaction
    open fun undoDeletableTabs(tabIds: List<String>, moveActiveTabToEnd: Boolean) {
        // ensure the tab is in the DB
        var lastTabPosition = selectedTab()?.position ?: 0
        tabIds.forEach { tabId ->
            tab(tabId)?.let { tab ->
                updateTab(tab.copy(deletable = false))
                lastTabPosition = max(lastTabPosition, tab.position)
            }
        }

        if (moveActiveTabToEnd) {
            selectedTab()?.let { activeTab ->
                updateTab(activeTab.copy(position = lastTabPosition + 1))
            }
        }
    }

    @Transaction
    open fun purgeDeletableTabsAndUpdateSelection() {
        deleteTabsMarkedAsDeletable()
        if (selectedTab() != null) {
            return
        }
        lastTab()?.let {
            insertTabSelection(TabSelectionEntity(tabId = it.tabId))
        }
    }

    @Transaction
    open fun deleteTabsAndUpdateSelection(tabIds: List<String>) {
        deleteTabs(tabIds)

        if (selectedTab() != null) {
            return
        }

        firstTab()?.let {
            insertTabSelection(TabSelectionEntity(tabId = it.tabId))
        }
    }

    @Query("delete from tabs")
    abstract fun deleteAllTabs()

    @Query("delete from tabs where url is null")
    abstract fun deleteBlankTabs()

    @Query("update tabs set position = position + 1 where position >= :position")
    abstract fun incrementPositionStartingAt(position: Int)

    @Transaction
    open fun addAndSelectTab(tab: TabEntity, updateIfBlankParent: Boolean = false) {
        try {
            val parent = tab.sourceTabId?.let { tab(it) }
            val newTab = if (updateIfBlankParent && parent != null && parent.url == null) {
                /*
                 * If the parent tab is blank, we need to update the parent tab with the previous
                 * one. Otherwise, foreign key constrains won't be met
                 */
                tab.copy(sourceTabId = parent.sourceTabId, position = parent.position)
            } else {
                tab
            }
            deleteBlankTabs()
            insertTab(newTab)
            insertTabSelection(TabSelectionEntity(tabId = newTab.tabId))
        } catch (e: Exception) {
            logcat { "Error adding and selecting tab: $e" }
        }
    }

    @Transaction
    open fun deleteTabAndUpdateSelection(tab: TabEntity) {
        deleteTab(tab)

        if (selectedTab() != null) {
            return
        }

        firstTab()?.let {
            insertTabSelection(TabSelectionEntity(tabId = it.tabId))
        }
    }

    @Transaction
    open fun deleteTabAndUpdateSelection(
        tab: TabEntity,
        newSelectedTab: TabEntity? = null,
    ) {
        deleteTab(tab)

        if (newSelectedTab != null) {
            insertTabSelection(TabSelectionEntity(tabId = newSelectedTab.tabId))
            return
        }

        if (selectedTab() != null) {
            return
        }

        firstTab()?.let {
            insertTabSelection(TabSelectionEntity(tabId = it.tabId))
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertTabSelection(tabSelectionEntity: TabSelectionEntity)

    @Transaction
    open fun insertTabAtPosition(tab: TabEntity) {
        incrementPositionStartingAt(tab.position)
        insertTab(tab)
    }

    fun lastTab(): TabEntity? {
        return tabs().lastOrNull()
    }

    @Query("update tabs set lastAccessTime=:lastAccessTime where tabId=:tabId")
    abstract fun updateTabLastAccess(
        tabId: String,
        lastAccessTime: LocalDateTime,
    )

    @Query("update tabs set url=:url, title=:title, viewed=:viewed where tabId=:tabId")
    abstract fun updateUrlAndTitle(
        tabId: String,
        url: String?,
        title: String?,
        viewed: Boolean,
    )

    @Transaction
    open fun updateTabsOrder(from: Int, to: Int) {
        if (from != to) {
            val newTabs = tabs().swap(from, to)
            newTabs.forEachIndexed { index, tabEntity ->
                updateTab(tabEntity.copy(position = index))
            }
        }
    }
}
