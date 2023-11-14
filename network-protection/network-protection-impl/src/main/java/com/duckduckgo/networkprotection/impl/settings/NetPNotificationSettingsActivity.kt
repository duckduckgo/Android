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
import android.view.View
import android.widget.CompoundButton
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.notifyme.NotifyMeView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpNotificationSettingsBinding
import javax.inject.Inject
import kotlinx.coroutines.launch
import logcat.logcat

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetPNotificationSettingsScreenNoParams::class)
class NetPNotificationSettingsActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var netPSettingsLocalConfig: NetPSettingsLocalConfig

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val toggleNotificationAlertListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        logcat { "VPN alert notification settings set to $isChecked" }
        netPSettingsLocalConfig.vpnNotificationAlerts().setEnabled(Toggle.State(enable = isChecked))
    }

    private val binding: ActivityNetpNotificationSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        setupUiElements()
    }

    private fun setupUiElements() {
        lifecycleScope.launch(dispatcherProvider.io()) {
            binding.vpnNotificationAlerts.quietlySetIsChecked(
                netPSettingsLocalConfig.vpnNotificationAlerts().isEnabled(),
                toggleNotificationAlertListener,
            )
        }
        binding.vpnNotificationAlerts.setOnCheckedChangeListener(toggleNotificationAlertListener)
        binding.vpnNotificationSettingsNotifyMe.setOnVisibilityChange(
            object : NotifyMeView.OnVisibilityChangedListener {
                override fun onVisibilityChange(v: View?, isVisible: Boolean) {
                    // The settings are only interactable when the notifyMe component is not visible
                    binding.vpnNotificationAlerts.isEnabled = !isVisible
                }
            },
        )
    }
}

internal object NetPNotificationSettingsScreenNoParams : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = NetPNotificationSettingsScreenNoParams
}
