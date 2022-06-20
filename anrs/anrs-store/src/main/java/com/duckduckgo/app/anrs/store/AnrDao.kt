/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.anrs.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnrDao {
    @Query("SELECT * FROM anr_entity")
    fun getAnrs(): List<AnrEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(anr: AnrEntity)

    @Query("SELECT EXISTS(SELECT * FROM anr_entity WHERE hash = :hash)")
    fun anrExists(hash: String): Boolean

    @Query("DELETE FROM anr_entity WHERE hash = :hash")
    fun deleteAnr(hash: String)

    @Query("SELECT * FROM anr_entity ORDER BY timestamp DESC LIMIT 1")
    fun latestAnr(): AnrEntity?
}
