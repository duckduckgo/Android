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

import java.time.format.DateTimeParseException

/**
 * Tracks the days the app is used and is part of the Default Browser Prompts flow.
 */
interface DefaultBrowserPromptsAppUsageRepository {

    suspend fun recordAppUsedNow()

    /**
     * Returns the number of active days the app has been used since the user entered the  Default Browser Prompts flow.
     *
     * Crossing a dateline in local time will not increment the returned count.
     * Only if a given instant crossed dateline in ET timezone, the value will be incremented.
     *
     * @return Count if successful.
     *  [DateTimeParseException] if the enrollment date is malformed.
     */
    suspend fun getActiveDaysUsedSinceEnrollment(): Result<Long>
}
