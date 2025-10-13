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

import android.Manifest.permission
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.notifyme.NotifyMeView
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.launchAlwaysOnSystemSettings
import com.duckduckgo.common.utils.extensions.launchApplicationInfoSettings
import com.duckduckgo.common.utils.extensions.launchIgnoreBatteryOptimizationSettings
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpVpnSettingsBinding
import com.duckduckgo.networkprotection.impl.settings.NetPVpnSettingsViewModel.RecommendedSettings
import com.duckduckgo.networkprotection.impl.settings.NetPVpnSettingsViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.Inject
import kotlin.math.absoluteValue

@InjectWith(
    scope = ActivityScope::class,
    delayGeneration = true, // VpnSettingPlugin can be contributed from other modules
)
@ContributeToActivityStarter(NetPVpnSettingsScreenNoParams::class, screenName = "vpn.settings")
class NetPVpnSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var vpnRemoteSettings: PluginPoint<VpnSettingPlugin>

    private val binding: ActivityNetpVpnSettingsBinding by viewBinding()
    private val viewModel: NetPVpnSettingsViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        setupUiElements()
        setupRemoteSettings()
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
    }

    private fun renderViewState(viewState: ViewState) {
        binding.vpnNotifications.quietlySetIsChecked(viewState.vpnNotifications) { _, isChecked ->
            viewModel.onVPNotificationsToggled(isChecked)
        }

        binding.excludeLocalNetworks.quietlySetIsChecked(viewState.excludeLocalNetworks) { _, isChecked ->
            viewModel.onExcludeLocalRoutes(isChecked)
        }

        binding.pauseWhileCalling.quietlySetIsChecked(viewState.pauseDuringWifiCalls) { _, isChecked ->
            if (isChecked && hasPhoneStatePermission()) {
                viewModel.onEnablePauseDuringWifiCalls()
            } else if (isChecked) {
                binding.pauseWhileCalling.setIsChecked(false)
                if (shouldShowRequestPermissionRationale(permission.READ_PHONE_STATE)) {
                    TextAlertDialogBuilder(this)
                        .setTitle(R.string.netpGrantPhonePermissionTitle)
                        .setMessage(R.string.netpGrantPhonePermissionByline)
                        .setPositiveButton(R.string.netpGrantPhonePermissionActionPositive)
                        .setNegativeButton(R.string.netpGrantPhonePermissionActionNegative)
                        .addEventListener(
                            object : TextAlertDialogBuilder.EventListener() {
                                override fun onPositiveButtonClicked() {
                                    // User denied the permission 2+ times
                                    this@NetPVpnSettingsActivity.launchApplicationInfoSettings()
                                }
                            },
                        )
                        .show()
                } else {
                    requestPermissions(arrayOf(permission.READ_PHONE_STATE), permission.READ_PHONE_STATE.hashCode().absoluteValue)
                }
            } else {
                binding.pauseWhileCalling.setIsChecked(false)
                viewModel.onDisablePauseDuringWifiCalls()
            }
        }

        binding.unrestrictedBatteryUsage.setOnClickListener {
            this.launchIgnoreBatteryOptimizationSettings()
        }
    }

    private fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            permission.READ_PHONE_STATE.hashCode().absoluteValue -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                binding.pauseWhileCalling.setIsChecked(granted)
                if (!granted) {
                    logcat { "READ_PHONE_STATE permission denied" }
                }
            }

            else -> {}
        }
    }

    private fun setupUiElements() {
        binding.excludeLocalNetworks.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onExcludeLocalRoutes(isChecked)
        }

        binding.alwaysOn.setOnClickListener {
            this.launchAlwaysOnSystemSettings()
        }

        binding.vpnNotificationSettingsNotifyMe.setOnVisibilityChange(
            object : NotifyMeView.OnVisibilityChangedListener {
                override fun onVisibilityChange(
                    v: View?,
                    isVisible: Boolean,
                ) {
                    // The settings are only interactable when the notifyMe component is not visible
                    binding.vpnNotifications.isEnabled = !isVisible
                }
            },
        )
    }

    private fun setupRemoteSettings() {
        vpnRemoteSettings.getPlugins()
            .map { it.getView(this) }
            .filterNotNull()
            .forEach { remoteViewPlugin ->
                binding.vpnSettingsContent.addView(remoteViewPlugin)
            }
    }
}

internal object NetPVpnSettingsScreenNoParams : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = NetPVpnSettingsScreenNoParams
}
