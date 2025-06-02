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

package com.duckduckgo.autofill.store.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NeverSavedSitesDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(neverSavedSiteEntity: NeverSavedSiteEntity): Long

    @Query("delete from never_saved_sites")
    fun clear()

    @Query("select count(*) from never_saved_sites")
    fun count(): Flow<Int>

    @Query("select count(*) from never_saved_sites where domain = :url")
    fun isInNeverSaveList(url: String): Boolean
}
