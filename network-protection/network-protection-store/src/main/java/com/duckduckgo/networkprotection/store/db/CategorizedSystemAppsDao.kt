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

package com.duckduckgo.networkprotection.store.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CategorizedSystemAppsDao {
    @Query("SELECT * from netp_system_apps_categories where category = 'COMMUNICATION'")
    fun getCommunicationSystemApps(): List<CategorizedSystemApp>

    @Query("SELECT * from netp_system_apps_categories where category = 'NETWORKING'")
    fun getNetworkingSystemApps(): List<CategorizedSystemApp>

    @Query("SELECT * from netp_system_apps_categories where category = 'MEDIA'")
    fun getMediaSystemApps(): List<CategorizedSystemApp>

    @Transaction
    fun upsertSystemAppCategories(
        systemAppCategories: List<CategorizedSystemApp>,
    ) {
        deleteSystemAppCategories()
        insertSystemAppOverrides(systemAppCategories)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSystemAppOverrides(
        systemAppCategories: List<CategorizedSystemApp>,
    )

    @Query("DELETE from netp_system_apps_categories")
    fun deleteSystemAppCategories()
}

@Entity(tableName = "netp_system_apps_categories")
data class CategorizedSystemApp(
    @PrimaryKey val packageName: String,
    val category: SystemAppCategory,
)

enum class SystemAppCategory {
    COMMUNICATION,
    NETWORKING,
    MEDIA,
    OTHERS,
}
