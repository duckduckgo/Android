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

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSelectionEntity
import javax.inject.Singleton

@Dao
@Singleton
abstract class TabsDao {

    @Query("select * from tabs limit 1")
    abstract fun firstTab(): TabEntity?

    @Query("select tabs.tabId, url, title from tabs inner join tab_selection ON tabs.tabId = tab_selection.tabId limit 1")
    abstract fun selectedTab(): TabEntity?

    @Query("select tabs.tabId, url, title from tabs inner join tab_selection ON tabs.tabId = tab_selection.tabId limit 1")
    abstract fun liveSelectedTab(): LiveData<TabEntity>

    @Query("select * from tabs")
    abstract fun tabs(): List<TabEntity>

    @Query("select * from tabs")
    abstract fun liveTabs(): LiveData<List<TabEntity>>

    @Query("select * from tabs where tabId = :tabId")
    abstract fun tab(tabId: String): TabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertTab(tab: TabEntity)

    @Update
    abstract fun updateTab(tab: TabEntity)

    @Delete
    abstract fun deleteTab(tab: TabEntity)

    @Query("delete from tabs")
    abstract fun deleteAllTabs()

    @Transaction
    open fun addAndSelectTab(tab: TabEntity) {
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertTabSelection(tabSelectionEntity: TabSelectionEntity)
}
