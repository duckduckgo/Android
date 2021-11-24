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
import com.duckduckgo.di.scopes.AppObjectGraph
import kotlinx.coroutines.flow.Flow
import dagger.SingleIn

@Dao
@SingleIn(AppObjectGraph::class)
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
    abstract fun flowTabs(): Flow<List<TabEntity>>

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

    @Query("delete from tabs where deletable is 1")
    abstract fun deleteTabsMarkedAsDeletable()

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
    open fun undoDeletableTab(tab: TabEntity) {
        // ensure the tab is in the DB
        val dbTab = tab(tab.tabId)
        dbTab?.let {
            updateTab(dbTab.copy(deletable = false))
        }
    }

    @Transaction
    open fun purgeDeletableTabsAndUpdateSelection() {
        deleteTabsMarkedAsDeletable()
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
    open fun addAndSelectTab(tab: TabEntity) {
        deleteBlankTabs()
        insertTab(tab)
        insertTabSelection(TabSelectionEntity(tabId = tab.tabId))
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
    open fun deleteTabAndUpdateSelection(tab: TabEntity, newSelectedTab: TabEntity? = null) {
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

    @Query("update tabs set url=:url, title=:title, viewed=:viewed where tabId=:tabId")
    abstract fun updateUrlAndTitle(tabId: String, url: String?, title: String?, viewed: Boolean)

}
