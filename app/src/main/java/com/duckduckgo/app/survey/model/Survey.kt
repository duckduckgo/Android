/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.survey.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.io.Serializable

@Entity(
    tableName = "survey"
)
data class Survey(
    @PrimaryKey val surveyId: String,
    val url: String?,
    val daysInstalled: Int?,
    var status: Status
) : Serializable {

    enum class Status {
        NOT_ALLOCATED,
        SCHEDULED,
        CANCELLED,
        DONE
    }

    class StatusTypeConverter {

        @TypeConverter
        fun toStatus(value: String): Status {
            return Status.valueOf(value)
        }

        @TypeConverter
        fun fromStatus(value: Status): String {
            return value.name
        }
    }
}
