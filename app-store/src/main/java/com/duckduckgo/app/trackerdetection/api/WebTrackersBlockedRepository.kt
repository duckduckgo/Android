/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.app.trackerdetection.db.DatabaseDateFormatter
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

interface WebTrackersBlockedRepository {

    fun get(
        startTime: () -> String,
        endTime: String = noEndDate(),
    ): Flow<List<WebTrackerBlocked>>

    suspend fun deleteAll()

    private fun noEndDate(): String {
        return DatabaseDateFormatter.timestamp(LocalDateTime.of(9999, 1, 1, 0, 0))
    }
}
