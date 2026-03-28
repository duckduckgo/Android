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

package com.duckduckgo.duckchat.internal

import android.os.Bundle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.internal.databinding.ActivityDuckAiDevBinding
import com.duckduckgo.duckchat.internalapi.DuckAiDevCapabilityPlugin
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class DuckAiDevActivity : DuckDuckGoActivity() {

    @Inject lateinit var plugins: PluginPoint<DuckAiDevCapabilityPlugin>

    private val binding: ActivityDuckAiDevBinding by viewBinding()

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
        }
    }
}
