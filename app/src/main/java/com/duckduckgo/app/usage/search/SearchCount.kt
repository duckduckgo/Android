/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.usage.search

import androidx.room.*
import javax.inject.Singleton

@Dao
@Singleton
abstract class SearchCountDao {

    @Query("SELECT count FROM search_count LIMIT 1")
    abstract fun getSearchesMade(): Long

    @Query("UPDATE search_count SET count = count + 1")
    abstract fun incrementSearchCountIfExists(): Int

    @Insert
    abstract fun initialiseValue(entity: SearchCountEntity)

    /**
     * We need to increment the value in the DB, but the row might not exist yet.
     * We therefore update if the row exists, or insert a new value if it doesn't.
     */
    @Transaction
    open fun incrementSearchCount() {
        val changedRows = incrementSearchCountIfExists()
        if (changedRows == 0) {
            initialiseValue(SearchCountEntity(count = 1))
        }
    }
}

@Entity(tableName = "search_count")
data class SearchCountEntity(
    @PrimaryKey val key: String = SINGLETON_KEY,
    val count: Long
) {

    companion object {
        const val SINGLETON_KEY = "SINGLETON_KEY"
    }
}
