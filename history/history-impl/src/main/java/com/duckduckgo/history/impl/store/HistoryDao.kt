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

package com.duckduckgo.history.impl.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface HistoryDao {
    @Transaction
    @Query("SELECT * FROM history_entries")
    fun getHistoryEntriesWithVisits(): List<HistoryEntryWithVisits>

    @Transaction
    fun updateOrInsertVisit(url: String, title: String, query: String?, isSerp: Boolean, date: Long) {
        val existingHistoryEntry = getHistoryEntryByUrl(url)

        if (existingHistoryEntry != null) {
            val newVisit = VisitEntity(date = date, historyEntryId = existingHistoryEntry.id)
            insertVisit(newVisit)
        } else {
            val newHistoryEntry = HistoryEntryEntity(url = url, title = title, query = query, isSerp = isSerp)
            val historyEntryId = insertHistoryEntry(newHistoryEntry)

            val newVisit = VisitEntity(date = date, historyEntryId = historyEntryId)
            insertVisit(newVisit)
        }
    }

    @Query("SELECT * FROM history_entries WHERE url = :url LIMIT 1")
    fun getHistoryEntryByUrl(url: String): HistoryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHistoryEntry(historyEntry: HistoryEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertVisit(visit: VisitEntity)
}
