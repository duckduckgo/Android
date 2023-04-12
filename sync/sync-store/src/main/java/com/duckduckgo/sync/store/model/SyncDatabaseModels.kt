/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.store.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter

@Entity(
    tableName = "sync_attempts",
)
data class SyncAttempt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: String = getUtcIsoLocalDate(),
    val state: SyncState,
    val meta: String
) {
    companion object {
        private fun getUtcIsoLocalDate(): String {
            return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
        }
    }
}

enum class SyncState {
    IN_PROGRESS,
    SUCCESS,
    FAIL
}

private fun getUtcIsoLocalDate(): String {
    // returns YYYY-MM-dd
    return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
}
