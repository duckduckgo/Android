/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.fire.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.LocalDateTimeTypeConverter
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSelectionEntity

@Database(
    exportSchema = true,
    version = 1,
    entities = [
        TabEntity::class,
        TabSelectionEntity::class,
    ],
)
@TypeConverters(LocalDateTimeTypeConverter::class)
abstract class FireModeDatabase : RoomDatabase() {

    abstract fun tabsDao(): TabsDao
}
