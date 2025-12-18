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

package com.duckduckgo.app.fire

/**
 * Interface for automatic data clearing operations triggered by app lifecycle events.
 */
interface AutomaticDataClearing {
    /**
     * Clears data automatically based on auto-clear settings.
     * @param killProcessIfNeeded whether to kill the app process after clearing data and
     *
     * @return true if process should be restarted later, false otherwise
     */
    suspend fun clearDataUsingAutomaticFireOptions(killProcessIfNeeded: Boolean = true): Boolean

    /**
     * Determines whether data should be cleared based on auto-clear settings.
     * @param isFreshAppLaunch true if the app has been freshly launched, false otherwise
     * @param appUsedSinceLastClear true if the app has been used since the last data clear, false otherwise
     * @param appIconChanged true if the app icon has changed since the last
     *
     * @return true if data should be cleared automatically, false otherwise
     */
    suspend fun shouldClearDataAutomatically(
        isFreshAppLaunch: Boolean,
        appUsedSinceLastClear: Boolean,
        appIconChanged: Boolean,
    ): Boolean

    /**
     * Checks if the user has selected any automatic data clearing option.
     *
     * @return true if process should be killed on exit, false otherwise
     */
    suspend fun isAutomaticDataClearingOptionSelected(): Boolean
}
