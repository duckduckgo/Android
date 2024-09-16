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

package com.duckduckgo.remote.messaging.internal.feature

import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.remote.messaging.internal.feature.RMFInternalScreens.InternalSettings
import com.duckduckgo.remotemessaging.internal.databinding.ActivityRmfInternalSettingsBinding
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(InternalSettings::class)
class RMFInternalSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var settings: PluginPoint<RmfSettingPlugin>

    private val binding: ActivityRmfInternalSettingsBinding by viewBinding()

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
                binding.rmfSettingsContent.addView(remoteViewPlugin)
            }
    }
}

sealed class RMFInternalScreens : GlobalActivityStarter.ActivityParams {
    data object InternalSettings : RMFInternalScreens() {
        private fun readResolve(): Any = InternalSettings
    }
}
