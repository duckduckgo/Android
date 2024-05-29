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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.duckduckgo.newtabpage.impl.settings.db.NewTabUserSection
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface NewTabPageSectionSettingsProvider {
    fun provideSections(): Flow<List<NewTabPageSectionSettings>>
}

@ContributesBinding(scope = AppScope::class)
class RealNewTabPageSectionSettingsProvider @Inject constructor(
    private val newTabSectionsSettingsPlugins: PluginPoint<NewTabPageSectionSettingsPlugin>,
    private val newTabSectionsPlugins: ActivePluginPoint<NewTabPageSectionPlugin>,
    private val userSectionsRepository: NewTabUserSectionsRepository,
    private val dispatcherProvider: DispatcherProvider,
) : NewTabPageSectionSettingsProvider {

    // we only show settings for sections that are enabled via remote config
    override fun provideSections(): Flow<List<NewTabPageSectionSettings>> = flow {
        val plugins = mutableListOf<NewTabPageSectionSettings>()
        val settingsPlugins = newTabSectionsSettingsPlugins.getPlugins()
        newTabSectionsPlugins.getPlugins().onEach { section ->
            val setting = settingsPlugins.find { it.name == section.name }
            // if there isn't a view that implements the settings plugin, we won't show it
            if (setting != null) {
                val userSection = userSectionsRepository.getUserSection(section.name)
                plugins.add(NewTabPageSectionSettings(setting, userSection))
            }
        }
        emit(plugins)
    }
}

data class NewTabPageSectionSettings(
    val plugin: NewTabPageSectionSettingsPlugin,
    val userSection: NewTabUserSection,
)
