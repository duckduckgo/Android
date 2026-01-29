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

package com.duckduckgo.app.tabs.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.duckduckgo.app.tabs.model.TabGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabGroupsDao {

    @Query("SELECT * FROM tab_groups")
    fun getAllGroups(): List<TabGroupEntity>

    @Query("SELECT * FROM tab_groups")
    fun flowAllGroups(): Flow<List<TabGroupEntity>>

    @Query("SELECT * FROM tab_groups WHERE groupId = :groupId")
    fun getGroup(groupId: String): TabGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGroup(group: TabGroupEntity)

    @Update
    fun updateGroup(group: TabGroupEntity)

    @Delete
    fun deleteGroup(group: TabGroupEntity)

    @Query("DELETE FROM tab_groups WHERE groupId = :groupId")
    fun deleteGroupById(groupId: String)

    @Query("DELETE FROM tab_groups WHERE groupId NOT IN (SELECT DISTINCT groupId FROM tabs WHERE groupId IS NOT NULL)")
    fun deleteEmptyGroups()
}
