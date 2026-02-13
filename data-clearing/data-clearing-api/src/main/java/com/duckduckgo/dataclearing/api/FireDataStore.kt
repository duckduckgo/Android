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

package com.duckduckgo.dataclearing.api

import kotlinx.coroutines.flow.Flow

/**
 * DataStore for managing Fire clearing preferences.
 * Stores two separate sets of clearing options:
 * - Manual options: Options for manual Fire button clearing
 * - Automatic options: Options used for automatic clearing on app exit
 */
interface FireDataStore {
    /**
     * Gets the flow of manual clear options for manual Fire actions.
     */
    fun getManualClearOptionsFlow(): Flow<Set<FireClearOption>>

    /**
     * Gets the manual clear options for manual Fire actions.
     */
    suspend fun getManualClearOptions(): Set<FireClearOption>

    /**
     * Sets the manual clear options for manual Fire actions.
     * @param options Set of options to clear. Can be any combination of TABS, DATA, and DUCKAI_CHATS.
     */
    suspend fun setManualClearOptions(options: Set<FireClearOption>)

    /**
     * Adds a clear option to the manual selection.
     */
    suspend fun addManualClearOption(option: FireClearOption)

    /**
     * Removes a clear option from the manual selection.
     */
    suspend fun removeManualClearOption(option: FireClearOption)

    /**
     * Checks if a specific option is in the manual selection.
     */
    suspend fun isManualClearOptionSelected(option: FireClearOption): Boolean

    /**
     * Gets the flow of automatic clear options.
     */
    fun getAutomaticClearOptionsFlow(): Flow<Set<FireClearOption>>

    /**
     * Gets the automatic clear options.
     */
    suspend fun getAutomaticClearOptions(): Set<FireClearOption>

    /**
     * Sets the automatic clear options.
     * @param options Set of options to clear automatically. Can be any combination of TABS, DATA, and DUCKAI_CHATS.
     */
    suspend fun setAutomaticClearOptions(options: Set<FireClearOption>)

    /**
     * Adds a clear option to the automatic selection.
     */
    suspend fun addAutomaticClearOption(option: FireClearOption)

    /**
     * Removes a clear option from the automatic selection.
     */
    suspend fun removeAutomaticClearOption(option: FireClearOption)

    /**
     * Checks if a specific option is in the automatic selection.
     */
    suspend fun isAutomaticClearOptionSelected(option: FireClearOption): Boolean

    /**
     * Gets the flow of automatic clear when option.
     */
    fun getAutomaticallyClearWhenOptionFlow(): Flow<ClearWhenOption>

    /**
     * Gets the automatic clear when option.
     */
    suspend fun getAutomaticallyClearWhenOption(): ClearWhenOption

    /**
     * Sets the automatic clear when option.
     * @param option The option determining when to automatically clear data.
     */
    suspend fun setAutomaticallyClearWhenOption(option: ClearWhenOption)
}
