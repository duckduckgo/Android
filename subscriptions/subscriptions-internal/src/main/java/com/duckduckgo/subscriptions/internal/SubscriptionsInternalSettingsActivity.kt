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

package com.duckduckgo.subscriptions.internal

import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.edgetoedge.api.EdgeToEdge
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.internal.SubsInternalScreens.InternalSettings
import com.duckduckgo.subscriptions.internal.databinding.ActivitySubsInternalSettingsBinding
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(InternalSettings::class)
class SubscriptionsInternalSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var settings: PluginPoint<SubsSettingPlugin>

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var edgeToEdge: EdgeToEdge

    private val binding: ActivitySubsInternalSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge.enableIfToggled(this)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        setupEdgeToEdge()
        setupUiElementState()
    }

    private fun setupEdgeToEdge() {
        if (!edgeToEdge.isEnabled()) return
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            binding.appBar.updatePadding(top = insets.top)
            binding.scrollView.updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupUiElementState() {
        settings.getPlugins()
            .mapNotNull { it.getView(this) }
            .forEach { remoteViewPlugin ->
                binding.subsSettingsContent.addView(remoteViewPlugin)
            }
    }
}

sealed class SubsInternalScreens : GlobalActivityStarter.ActivityParams {
    data object InternalSettings : SubsInternalScreens() {
        private fun readResolve(): Any = InternalSettings
    }
}
