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

package com.duckduckgo.app.trackerdetection.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How far the copy of an app.db table into tracker_detection.db has got, keyed by the app.db table name, which is only
 * `web_trackers_blocked`. [lastImportedId] is the app.db id of the last row copied and commits with that batch, so a retry
 * resumes after it. [done] means the rows left in the app.db table must never be imported: either every row was copied,
 * or the user cleared the log first.
 */
@Entity(tableName = "legacy_app_db_imports")
data class LegacyAppDbImport(
    @PrimaryKey val name: String,
    val lastImportedId: Long = 0,
    val done: Boolean = false,
)
