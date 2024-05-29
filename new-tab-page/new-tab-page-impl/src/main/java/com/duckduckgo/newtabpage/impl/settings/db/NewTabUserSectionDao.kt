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

package com.duckduckgo.newtabpage.impl.settings.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Dao
interface NewTabUserSectionDao {
    @Query("select * from new_tab_sections")
    fun get(): List<NewTabUserSection>

    @Query("select * from new_tab_sections where name = :name")
    fun getBy(name: String): NewTabUserSection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(section: NewTabUserSection)
}

@Entity(tableName = "new_tab_sections")
data class NewTabUserSection(
    @PrimaryKey val name: String,
    val enabled: Boolean,
)
