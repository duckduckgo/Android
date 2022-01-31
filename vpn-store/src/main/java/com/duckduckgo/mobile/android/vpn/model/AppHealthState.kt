/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter

@Entity(tableName = "app_health_state")
data class AppHealthState(
    // unique ID so that we only have one entry in the DB
    @PrimaryKey val type: HealthEventType,
    val localtime: String = DatabaseDateFormatter.timestamp(),
    val alerts: List<String>,
    val healthDataJsonString: String,
    val restartedAtEpochSeconds: Long?,
) {
    object HealthEventTypeConverter {
        @TypeConverter
        @JvmStatic
        fun toType(stage: String): HealthEventType {
            return HealthEventType.valueOf(stage)
        }

        @TypeConverter
        @JvmStatic
        fun fromType(stage: HealthEventType): String {
            return stage.name
        }
    }
}

enum class HealthEventType {
    BAD_HEALTH,
    GOOD_HEALTH
}
