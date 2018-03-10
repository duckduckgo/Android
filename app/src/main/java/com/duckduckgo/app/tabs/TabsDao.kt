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

package com.duckduckgo.app.tabs

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import javax.inject.Singleton

@Dao
@Singleton
interface TabsDao {

    @Query("select count(*) from tabs")
    fun count(): Int

    @Query("select * from tabs")
    fun liveTabs(): LiveData<List<TabEntity>>

    @Query("select * from tabs")
    fun tabs(): List<TabEntity>

    @Query("select * from selected_tab limit 1")
    fun liveSelectedTab(): LiveData<SelectedTabEntity>

    @Query("select * from selected_tab limit 1")
    fun selectedTab(): SelectedTabEntity

    @Query("select * from tabs where tabId = :tabId")
    fun tab(tabId: String): TabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTab(tab: TabEntity)

    @Update
    fun updateTab(tab: TabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun updateSelectedTab(selectedTabEntity: SelectedTabEntity)
}

fun TabsDao.hasTabs(): Boolean {
    return count() > 0
}