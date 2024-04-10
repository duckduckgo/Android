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

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "history_entries", indices = [Index(value = ["url"], unique = true)])
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val query: String?,
    val isSerp: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        return this.url == (other as? HistoryEntryEntity)?.url
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }
}

@Entity(tableName = "visits_list", primaryKeys = ["date", "historyEntryId"])
data class VisitEntity(
    val historyEntryId: Long,
    val date: Long,
)

data class HistoryEntryWithVisits(
    @Embedded val historyEntry: HistoryEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "historyEntryId",
    )
    val visits: List<VisitEntity>,
)
