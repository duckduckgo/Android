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

package com.duckduckgo.dataclearing.impl.toggles

import com.duckduckgo.dataclearing.api.toggles.DataClearingModularization
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.APP_CACHE
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.CONTEXTUAL_CHATS
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.COOKIES
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.DUCK_AI_WEB_STORAGE
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.NAVIGATION_HISTORY
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.SAVED_SITES_PRUNE
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.SITE_PERMISSIONS
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.SITE_PREFERENCES
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.TABS
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.TRACKER_STATS
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.WEB_STORAGE
import com.duckduckgo.dataclearing.api.toggles.DataClearingSource.WEB_STORAGE_SINGLE_TAB
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDataClearingModularization @Inject constructor(
    private val feature: DataClearingModularizationFeature,
) : DataClearingModularization {

    override fun isPluginEnabled(source: DataClearingSource): Boolean {
        if (!feature.self().isEnabled()) return false
        return when (source) {
            NAVIGATION_HISTORY -> feature.navigationHistory()
            SAVED_SITES_PRUNE -> feature.savedSitesPrune()
            APP_CACHE -> feature.appCache()
            CONTEXTUAL_CHATS -> feature.contextualChats()
            SITE_PERMISSIONS -> feature.sitePermissions()
            SITE_PREFERENCES -> feature.sitePreferences()
            COOKIES -> feature.cookies()
            TABS -> feature.tabs()
            WEB_STORAGE -> feature.webStorage()
            WEB_STORAGE_SINGLE_TAB -> feature.webStorageSingleTab()
            DUCK_AI_WEB_STORAGE -> feature.duckAiWebStorage()
            TRACKER_STATS -> feature.trackerStats()
        }.isEnabled()
    }
}
