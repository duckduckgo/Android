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

package com.duckduckgo.browser.api

import com.duckduckgo.common.ui.DuckDuckGoTheme
import java.util.*

interface UserBrowserProperties {
    fun appTheme(): DuckDuckGoTheme
    suspend fun bookmarks(): Long
    suspend fun favorites(): Long

    @Deprecated(
        message = "Use AppBuildConfig.isNewInstall() to check for new installs, " +
            "or PackageInfo.firstInstallTime / lastUpdateTime if you need the actual timestamp.",
    )
    fun daysSinceInstalled(): Long
    suspend fun daysUsedSince(since: Date): Long
    fun defaultBrowser(): Boolean

    /**
     * Returns true if this app has ever been set as the user's default browser.
     * Once true, this value is permanent — it does not revert if the user later
     * changes their default browser to something else.
     */
    suspend fun wasEverDefaultBrowser(): Boolean
    fun emailEnabled(): Boolean
    fun searchCount(): Long
    fun widgetAdded(): Boolean
}
