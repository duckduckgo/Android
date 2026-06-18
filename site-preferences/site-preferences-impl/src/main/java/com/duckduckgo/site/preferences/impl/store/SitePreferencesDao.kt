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

package com.duckduckgo.site.preferences.impl.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SitePreferencesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: SitePreferencesEntity)

    @Query("DELETE FROM site_preferences WHERE domain = :domain")
    fun delete(domain: String)

    @Query("DELETE FROM site_preferences WHERE domain IN (:domains)")
    fun delete(domains: Set<String>)

    @Query("DELETE FROM site_preferences WHERE domain NOT IN (:domains)")
    fun deleteAllExcept(domains: Set<String>)

    @Query("SELECT domain FROM site_preferences WHERE desktopModeEnabled = 1")
    fun desktopModeDomainsFlow(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM site_preferences WHERE domain = :domain AND desktopModeEnabled = 1)")
    fun isDesktopModeEnabled(domain: String): Boolean

    @Query("DELETE FROM site_preferences")
    fun deleteAll()
}
