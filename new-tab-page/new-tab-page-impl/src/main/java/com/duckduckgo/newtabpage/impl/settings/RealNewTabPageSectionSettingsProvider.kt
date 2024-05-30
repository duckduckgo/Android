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

package com.duckduckgo.newtabpage.impl.settings

import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import logcat.logcat

interface NewTabPageSectionSettingsProvider {
    fun provideSections(): Flow<List<NewTabPageSectionSettingsPlugin>>
}

@ContributesBinding(scope = AppScope::class)
class RealNewTabPageSectionSettingsProvider @Inject constructor(
    private val newTabSectionsSettingsPlugins: PluginPoint<NewTabPageSectionSettingsPlugin>,
    private val newTabSectionsPlugins: ActivePluginPoint<NewTabPageSectionPlugin>,
    private val newTabSettingsStore: NewTabSettingsStore,
) : NewTabPageSectionSettingsProvider {

    // we only show settings for sections that are enabled via remote config
    override fun provideSections(): Flow<List<NewTabPageSectionSettingsPlugin>> = flow {
        val plugins = mutableListOf<NewTabPageSectionSettingsPlugin>()

        val settingsPlugins = newTabSectionsSettingsPlugins.getPlugins()
        // store can be empty the first time we check it, so we make sure the content is initialised
        if (settingsPlugins.isNotEmpty()) {
            if (newTabSettingsStore.settings.isEmpty()) {
                val userSections = settingsPlugins.map { it.name }
                logcat { "New Tab: User Sections initialised to $userSections" }
                newTabSettingsStore.settings = userSections
            }
        }

        // we need to take the user settings order into account
        newTabSectionsPlugins.getPlugins().onEach { section ->
            val setting = settingsPlugins.find { it.name == section.name }
            // if there isn't a view that implements the settings plugin, we won't show it
            if (setting != null) {
                plugins.add(setting)
            }
        }
        emit(plugins)
    }
}
