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
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset

@Entity(
    tableName = "sync_attempts",
)
data class SyncAttempt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: String = DatabaseDateFormatter.iso8601(),
    val state: SyncAttemptState,
    val meta: String = "",
) {
    fun today(): Boolean {
        return DatabaseDateFormatter.iso8601Date(this.timestamp).isEqual(LocalDate.now(ZoneOffset.UTC))
    }
}

enum class SyncAttemptState {
    IN_PROGRESS,
    SUCCESS,
    FAIL,
}
