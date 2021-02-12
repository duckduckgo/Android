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

package com.duckduckgo.app.bookmarks.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * If modify please consider following instructions carefully
 *
 * Every time  we change BookmarkEntity we also need to change check (and probably change)
 * @property com.duckduckgo.app.global.db.MigrationsProvider.BOOKMARKS_DB_ON_CREATE
 * which is located AppDatabase.kt
 */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var title: String?,
    var url: String
)
