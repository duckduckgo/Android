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

import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.NewTabSettingsScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.duckduckgo.newtabpage.impl.databinding.ActivityNewTabSettingsBinding
import logcat.logcat
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NewTabSettingsScreenNoParams::class, screenName = "newtabsettings")
class NewTabSettingsActivity: DuckDuckGoActivity() {

    @Inject
    lateinit var newTabSectionsSettingsPlugins: PluginPoint<NewTabPageSectionSettingsPlugin>

    private val binding: ActivityNewTabSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        addSections()
    }

    private fun addSections(){
        newTabSectionsSettingsPlugins.getPlugins().forEach { feature ->
            logcat { "New Tab: Settings - Add Section $feature" }
            val sectionView = feature.getView(this)
            binding.newTabSettingSectionsLayout.addDragView(sectionView, sectionView)
        }
    }

}

@ContributesPluginPoint(
    scope = AppScope::class,
    boundType = NewTabPageSectionSettingsPlugin::class,
)
private interface NewTabPageSectionSettingsPluginPointTrigger

