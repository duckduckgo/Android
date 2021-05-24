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

package com.duckduckgo.app.onboarding.store

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

const val USER_STAGE_TABLE_NAME = "userStage"

@Entity(tableName = USER_STAGE_TABLE_NAME)
data class UserStage(
    @PrimaryKey val key: Int = 1,
    val appStage: AppStage
)

enum class AppStage {
    NEW,
    DAX_ONBOARDING,
    ESTABLISHED;
}

class StageTypeConverter {

    @TypeConverter
    fun toStage(stage: String): AppStage {
        return try {
            AppStage.valueOf(stage)
        } catch (ex: IllegalArgumentException) {
            AppStage.ESTABLISHED
        }
    }

    @TypeConverter
    fun fromStage(stage: AppStage): String {
        return stage.name
    }
}
