/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultbrowsing.prompts.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class DefaultBrowserPromptsAppUsageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(defaultBrowserPromptsAppUsageEntity: DefaultBrowserPromptsAppUsageEntity)

    @Query("SELECT COUNT(*) from default_browser_prompts_app_usage WHERE isoDateET > :isoDateET")
    abstract fun getNumberOfDaysAppUsedSinceDateET(isoDateET: String): Long

    @Query("SELECT isoDateET FROM default_browser_prompts_app_usage ORDER BY isoDateET ASC LIMIT 1")
    abstract fun getFirstDay(): String?
}
