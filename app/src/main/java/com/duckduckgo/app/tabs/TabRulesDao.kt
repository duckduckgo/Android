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

package com.duckduckgo.app.tabs

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
@SingleInstanceIn(AppScope::class)
interface TabRulesDao {

    @Query("SELECT * FROM tab_rules")
    fun tabRules(): Flow<List<TabRuleEntity>>

    @Query("SELECT * FROM tab_rules WHERE id = :id")
    suspend fun getTabRule(id: String): TabRuleEntity?

    @Query("SELECT * FROM tab_rules WHERE url = :url")
    suspend fun getTabRuleByUrl(url: String): TabRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTabRule(tabRule: TabRuleEntity)

    @Query("DELETE FROM tab_rules WHERE id = :id")
    suspend fun deleteTabRule(id: Long)

    @Query("DELETE FROM tab_rules WHERE url = :url")
    suspend fun deleteTabRule(url: String)

    @Query("UPDATE tab_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateTabRuleEnabled(id: Long, isEnabled: Boolean)

    @Query("DELETE FROM tab_rules")
    suspend fun deleteAllTabRules()
}


@Entity(tableName = "tab_rules")
data class TabRuleEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String?,
    val isEnabled: Boolean,
    val createdAt: LocalDateTime,
)
