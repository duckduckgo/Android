/*
 * Copyright (c) 2023 DuckDuckGo
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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.anr.internal.databinding.ActivityCrashAnrInternalSettingsBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class CrashANRsInternalSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var settings: PluginPoint<CrashANRsSettingPlugin>

    private val binding: ActivityCrashAnrInternalSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        setupUiElementState()
    }

    private fun setupUiElementState() {
        settings.getPlugins()
            .mapNotNull { it.getView(this) }
            .forEach { remoteViewPlugin ->
                binding.settingsContent.addView(remoteViewPlugin)
            }
    }
}

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class CrashAnrLogsCapabilityPlugin @Inject constructor() : CrashAnrDevCapabilityPlugin {

    override fun title(): String = "Recent Crash & ANR"

    override fun subtitle(): String = "List of recent Crash and ANRs"

    override fun onCapabilityClicked(activityContext: Context) {
        activityContext.startActivity(Intent(activityContext, CrashANRsInternalSettingsActivity::class.java))
    }
}
