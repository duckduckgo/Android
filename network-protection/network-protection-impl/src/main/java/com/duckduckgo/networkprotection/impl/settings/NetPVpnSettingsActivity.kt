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

package com.duckduckgo.networkprotection.impl.settings

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.launchAlwaysOnSystemSettings
import com.duckduckgo.common.utils.extensions.launchIgnoreBatteryOptimizationSettings
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpVpnSettingsBinding
import com.duckduckgo.networkprotection.impl.settings.NetPVpnSettingsViewModel.RecommendedSettings
import com.duckduckgo.networkprotection.impl.settings.NetPVpnSettingsViewModel.ViewState
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpGeoswitchingScreenNoParams
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetPVpnSettingsScreenNoParams::class)
class NetPVpnSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: ActivityNetpVpnSettingsBinding by viewBinding()
    private val viewModel: NetPVpnSettingsViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        setupUiElements()
        observeViewModel()

        lifecycle.addObserver(viewModel)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(viewModel)
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)
        viewModel.recommendedSettings()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { renderRecommendedSettings(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderRecommendedSettings(state: RecommendedSettings) {
        val batteryTextTitle = if (state.isIgnoringBatteryOptimizations) {
            R.string.netpManageRecentAppsProtectionUnrestrictedBattTitle
        } else {
            R.string.netpManageRecentAppsProtectionAllowUnrestrictedBattTitle
        }
        val batteryTextByline = if (state.isIgnoringBatteryOptimizations) {
            R.string.netpManageRecentAppsProtectionUnrestrictedBattByline
        } else {
            R.string.netpManageRecentAppsProtectionAllowUnrestrictedBattByline
        }
        binding.unrestrictedBatteryUsage.setPrimaryText(getString(batteryTextTitle))
        binding.unrestrictedBatteryUsage.setSecondaryText(getString(batteryTextByline))

        // val alwaysOnLeadingIcon = if (state.alwaysOnState) R.drawable.ic_check_color_24 else R.drawable.ic_alert_color_24
        // binding.alwaysOn.setLeadingIconResource(alwaysOnLeadingIcon)
    }

    private fun renderViewState(viewState: ViewState) {
        val geoSwitchingSubtitle = viewState.preferredLocation ?: getString(R.string.netpVpnSettingGeoswitchingDefault)
        binding.geoswitching.setSecondaryText(geoSwitchingSubtitle)
        binding.excludeLocalNetworks.quietlySetIsChecked(viewState.excludeLocalNetworks) { _, isChecked ->
            viewModel.onExcludeLocalRoutes(isChecked)
        }
        binding.unrestrictedBatteryUsage.setOnClickListener {
            this.launchIgnoreBatteryOptimizationSettings()
        }
    }

    private fun setupUiElements() {
        binding.excludeLocalNetworks.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onExcludeLocalRoutes(isChecked)
        }

        binding.alwaysOn.setOnClickListener {
            this.launchAlwaysOnSystemSettings(appBuildConfig.sdkInt)
        }

        binding.geoswitching.setOnClickListener {
            globalActivityStarter.start(this, NetpGeoswitchingScreenNoParams)
        }
    }
}

internal object NetPVpnSettingsScreenNoParams : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = NetPVpnSettingsScreenNoParams
}
