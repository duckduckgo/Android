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

package com.duckduckgo.app.attributed.metrics.internal.ui

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.attributed.metrics.impl.AttributedMetricsState
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDateUtils
import com.duckduckgo.app.attributed.metrics.store.EventDao
import com.duckduckgo.app.attributed.metrics.store.EventEntity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.attributed.metrics.internal.databinding.ActivityAttributedMetricsDevSettingsBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(MainAttributedMetricsSettings::class)
class AttributedMetricsDevSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityAttributedMetricsDevSettingsBinding by viewBinding()

    @Inject
    lateinit var eventDao: EventDao

    @Inject
    lateinit var dateUtils: AttributedMetricsDateUtils

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var attributedMetricsState: AttributedMetricsState

    @Inject
    lateinit var settingsPlugins: PluginPoint<AttributedMetricsSettingPlugin>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        setupViews()
        setupPlugins()
    }

    private fun setupPlugins() {
        settingsPlugins.getPlugins()
            .mapNotNull { it.getView(this) }
            .forEach { view ->
                binding.settingsContainer.addView(view)
            }
    }

    private fun setupViews() {
        binding.addTestEventsButton.setOnClickListener {
            addTestEvents()
        }
        lifecycleScope.launch {
            binding.clientActive.setSecondaryText(if (attributedMetricsState.isActive()) "Yes" else "No")
            binding.returningUser.setSecondaryText(if (appBuildConfig.isAppReinstall()) "Yes" else "No")
        }
    }

    private fun addTestEvents() {
        lifecycleScope.launch {
            repeat(10) { daysAgo ->
                val date = dateUtils.getDateMinusDays(daysAgo)
                eventDao.insertEvent(EventEntity(eventName = "ddg_search_days", count = 1, day = date))
                eventDao.insertEvent(EventEntity(eventName = "ddg_search", count = 1, day = date))
            }
            Toast.makeText(this@AttributedMetricsDevSettingsActivity, "Test events added", Toast.LENGTH_SHORT).show()
        }
    }
}

data object MainAttributedMetricsSettings : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = MainAttributedMetricsSettings
}
