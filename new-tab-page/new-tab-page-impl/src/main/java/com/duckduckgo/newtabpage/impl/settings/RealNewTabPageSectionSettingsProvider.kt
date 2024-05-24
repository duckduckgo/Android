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

import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsProvider
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat

@ContributesBinding(
    scope = AppScope::class,
)
class RealNewTabPageSectionSettingsProvider(
    private val sectionSettings: PluginPoint<NewTabPageSectionSettingsPlugin>,
) : NewTabPageSectionSettingsProvider {
    override fun provideSettings(): List<NewTabPageSectionSettingsPlugin> {
        logcat { "New Tab: ${sectionSettings.getPlugins()}" }
        return sectionSettings.getPlugins().map { it }
    }
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = NewTabPageSectionSettingsPlugin::class,
)
private interface NewTabPageSectionSettingsPluginPointTrigger
