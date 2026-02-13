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

package com.duckduckgo.dataclearing.api

interface ClearDataAction {
    /**
     * Clears tabs and all browser data (legacy full clear).
     * @param appInForeground whether the app is in foreground
     * @param shouldFireDataClearPixel whether to fire the data clear pixel
     */
    suspend fun clearTabsAndAllDataAsync(
        appInForeground: Boolean,
        shouldFireDataClearPixel: Boolean,
    ): Unit?

    /**
     * Clears tabs and associated data.
     * @param appInForeground whether the app is in foreground
     */
    suspend fun clearTabsAsync(appInForeground: Boolean)

    /**
     * Clears tabs and associated data.
     */
    suspend fun clearTabsOnly()

    /**
     * Clears browser data except tabs and chats.
     * @param shouldFireDataClearPixel whether to fire the data clear pixel
     */
    suspend fun clearBrowserDataOnly(shouldFireDataClearPixel: Boolean)

    /**
     * Clears only DuckAi chats.
     */
    suspend fun clearDuckAiChatsOnly()

    /**
     * Sets the flag indicating whether the app has been used since the last data clear.
     * @param appUsedSinceLastClear true if the app has been used since the last clear, false otherwise
     */
    suspend fun setAppUsedSinceLastClearFlag(appUsedSinceLastClear: Boolean)

    /**
     * Kills the current process.
     */
    fun killProcess()

    /**
     * Kills and restarts the current process.
     * @param notifyDataCleared whether to notify that data has been cleared
     * @param enableTransitionAnimation whether to enable transition animation during restart
     */
    fun killAndRestartProcess(notifyDataCleared: Boolean, enableTransitionAnimation: Boolean = true)
}
