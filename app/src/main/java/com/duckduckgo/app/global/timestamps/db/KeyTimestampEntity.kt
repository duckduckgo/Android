/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.global.timestamps.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

const val KEY_TIMESTAMPS_TABLE_NAME = "keyTimestamps"

@Entity(tableName = KEY_TIMESTAMPS_TABLE_NAME)
data class KeyTimestampEntity(
    @PrimaryKey val id: TimestampKey,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TimestampKey {
    USE_OUR_APP_SHORTCUT_ADDED
}

class TimestampKeyTypeConverter {

    @TypeConverter
    fun toKey(stage: String): TimestampKey {
        return TimestampKey.valueOf(stage)
    }

    @TypeConverter
    fun fromKey(stage: TimestampKey): String {
        return stage.name
    }
}
