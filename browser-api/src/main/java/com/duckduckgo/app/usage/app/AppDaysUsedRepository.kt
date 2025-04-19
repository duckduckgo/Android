/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.usage.app

import java.util.*

/**
 * Repository for storing and retrieving the number of days the app has been used
 */
interface AppDaysUsedRepository {

    /**
     * Get the number of days the app has been used
     */
    suspend fun getNumberOfDaysAppUsed(): Long

    /**
     * Record that the app has been used today
     */
    suspend fun recordAppUsedToday()

    /**
     * Get the number of days the app has been used since a given date.
     *
     * The provided [date] is compared against records of app usage collected by day-truncated local date at a time of capture.
     * It might not be precise enough for all applications.
     */
    suspend fun getNumberOfDaysAppUsedSinceDate(date: Date): Long

    /**
     * Get the last day the app was used
     */
    suspend fun getLastActiveDay(): String

    /**
     * Get the previous active day
     */
    suspend fun getPreviousActiveDay(): String?
}
