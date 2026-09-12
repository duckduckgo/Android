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

package com.duckduckgo.app.anr.internal.feature

import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.anr.internal.databinding.ActivityCrasnAnrDevBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject
import kotlin.collections.forEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(CrashANRsInternalScreens.InternalCrashSettings::class)
class CrashAnrDevActivity : DuckDuckGoActivity() {

    @Inject lateinit var plugins: PluginPoint<CrashAnrDevCapabilityPlugin>

    private val binding: ActivityCrasnAnrDevBinding by viewBinding()
    private val pluginItems = mutableListOf<Pair<CrashAnrDevCapabilityPlugin, TwoLineListItem>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        plugins.getPlugins().forEach { plugin ->
            val item = TwoLineListItem(this)
            item.setPrimaryText(plugin.title())
            item.setSecondaryText(plugin.subtitle())
            item.setOnClickListener {
                plugin.onCapabilityClicked(this)
                item.setSecondaryText(plugin.subtitle())
            }
            binding.capabilitiesContainer.addView(item)
            pluginItems += plugin to item
        }
    }

    override fun onResume() {
        super.onResume()
        pluginItems.forEach { (plugin, item) -> item.setSecondaryText(plugin.subtitle()) }
    }
}

sealed class CrashANRsInternalScreens : GlobalActivityStarter.ActivityParams {
    data object InternalCrashSettings : CrashANRsInternalScreens() {
        private fun readResolve(): Any = InternalCrashSettings
    }
}
