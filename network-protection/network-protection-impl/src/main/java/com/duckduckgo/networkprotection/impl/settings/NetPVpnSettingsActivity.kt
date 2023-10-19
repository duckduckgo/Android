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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpVpnSettingsBinding
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
    }

    private fun renderViewState(viewState: ViewState) {
        val geoSwitchingSubtitle = viewState.preferredLocation ?: getString(R.string.netpVpnSettingGeoswitchingDefault)
        binding.geoswitching.setSecondaryText(geoSwitchingSubtitle)
    }

    private fun setupUiElements() {
        binding.excludeLocalNetworks.quietlySetIsChecked(true, null)
        binding.excludeLocalNetworks.showSwitch()
        binding.excludeLocalNetworks.setSwitchEnabled(false)

        binding.alwaysOn.setOnClickListener {
            openVPNSettings()
        }

        binding.geoswitching.setOnClickListener {
            globalActivityStarter.start(this, NetpGeoswitchingScreenNoParams)
        }
    }

    @SuppressLint("InlinedApi")
    private fun openVPNSettings() {
        val intent = if (appBuildConfig.sdkInt >= Build.VERSION_CODES.N) {
            Intent(Settings.ACTION_VPN_SETTINGS)
        } else {
            Intent("android.net.vpn.SETTINGS")
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
}

internal object NetPVpnSettingsScreenNoParams : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = NetPVpnSettingsScreenNoParams
}
