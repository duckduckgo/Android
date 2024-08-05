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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import java.time.LocalDateTime

@Dao
interface HistoryDao {
    @Transaction
    @Query("SELECT * FROM history_entries")
    suspend fun getHistoryEntriesWithVisits(): List<HistoryEntryWithVisits>

    @Query("UPDATE history_entries SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Transaction
    suspend fun updateOrInsertVisit(url: String, title: String, query: String?, isSerp: Boolean, date: LocalDateTime) {
        val existingHistoryEntry = getHistoryEntryByUrl(url)

        if (existingHistoryEntry != null) {
            if (title.isNotBlank() && title != existingHistoryEntry.title) {
                updateTitle(existingHistoryEntry.id, title)
            }
            val newVisit = VisitEntity(timestamp = DatabaseDateFormatter.timestamp(date), historyEntryId = existingHistoryEntry.id)
            insertVisit(newVisit)
        } else {
            val newHistoryEntry = HistoryEntryEntity(url = url, title = title, query = query, isSerp = isSerp)
            val historyEntryId = insertHistoryEntry(newHistoryEntry)

            val newVisit = VisitEntity(timestamp = DatabaseDateFormatter.timestamp(date), historyEntryId = historyEntryId)
            insertVisit(newVisit)
        }
    }

    @Query("SELECT * FROM history_entries WHERE url = :url LIMIT 1")
    suspend fun getHistoryEntryByUrl(url: String): HistoryEntryEntity?

    @Query("SELECT * FROM history_entries WHERE `query` = :query LIMIT 1")
    suspend fun getHistoryEntryByQuery(query: String?): HistoryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(historyEntry: HistoryEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: VisitEntity)

    @Query("DELETE FROM visits_list")
    suspend fun deleteAllVisits()

    @Query("DELETE FROM history_entries")
    suspend fun deleteAllHistoryEntries()

    @Transaction
    suspend fun deleteAll() {
        deleteAllVisits()
        deleteAllHistoryEntries()
    }

    @Delete
    suspend fun delete(entryEntity: HistoryEntryEntity)

    @Delete
    suspend fun delete(entities: List<HistoryEntryEntity>)

    @Query("DELETE FROM visits_list WHERE timestamp < :timestamp")
    suspend fun deleteOldVisitsByTimestamp(timestamp: String)

    @Query("DELETE FROM history_entries WHERE id NOT IN (SELECT DISTINCT historyEntryId FROM visits_list)")
    suspend fun deleteEntriesWithNoVisits()

    @Transaction
    suspend fun deleteEntriesOlderThan(dateTime: LocalDateTime) {
        val timestamp = DatabaseDateFormatter.timestamp(dateTime)

        deleteOldVisitsByTimestamp(timestamp)

        deleteEntriesWithNoVisits()
    }
}
