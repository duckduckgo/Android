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

package com.duckduckgo.networkprotection.internal.feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.MenuRes
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.internal.network.NetPInternalMtuProvider
import java.lang.IllegalStateException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import om.duckduckgo.networkprotection.internal.R
import om.duckduckgo.networkprotection.internal.databinding.ActivityNetpInternalSettingsBinding

@Suppress("NoHardcodedCoroutineDispatcher")
@InjectWith(ActivityScope::class)
class NetPInternalSettingsActivity : DuckDuckGoActivity() {

    @Inject lateinit var netPFeatureConfig: NetPFeatureConfig

    @Inject lateinit var vpnStateMonitor: VpnStateMonitor

    @Inject lateinit var netPInternalMtuProvider: NetPInternalMtuProvider

    @Inject lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    private val binding: ActivityNetpInternalSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        setupUiElementState()
        setupConfigSection()
    }

    private fun setupUiElementState() {
        vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .map { it.state == VpnStateMonitor.VpnRunningState.ENABLED }
            .onEach { isEnabled ->
                binding.excludeSystemAppsToggle.isEnabled = isEnabled
                binding.overrideMtuSelector.isEnabled = isEnabled
                binding.overrideMtuSelector.setSecondaryText("MTU size: ${netPInternalMtuProvider.getMtu()}")
            }
            .launchIn(lifecycleScope)
    }

    private fun setupConfigSection() {
        with(NetPSetting.ExcludeSystemApps) {
            binding.excludeSystemAppsToggle.setIsChecked(netPFeatureConfig.isEnabled(this))
            binding.excludeSystemAppsToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sendBroadcast(VpnRemoteFeatureReceiver.enableIntent(this))
                } else {
                    sendBroadcast(VpnRemoteFeatureReceiver.disableIntent(this))
                }
            }
        }

        binding.overrideMtuSelector.setOnClickListener { showMtuSelectorMenu() }
    }

    private fun showMtuSelectorMenu(@MenuRes popupMenu: Int = R.menu.mtu_size_menu) {
        val popup = PopupMenu(this, binding.overrideMtuSelector)
        popup.menuInflater.inflate(popupMenu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            val mtuSize = when (menuItem.itemId) {
                R.id.mtuDefault -> null
                R.id.mtu1500 -> 1500
                R.id.mtu1420 -> 1420
                R.id.mtu1300 -> 1300
                R.id.mtu1280 -> 1280
                R.id.mtu1100 -> 1100
                R.id.mtu1000 -> 1000
                R.id.mtu500 -> 500
                R.id.mtu200 -> 200
                R.id.mtu100 -> 100
                else -> throw IllegalStateException()
            }
            netPInternalMtuProvider.setMtu(mtuSize)
            binding.overrideMtuSelector.setSecondaryText("MTU size: ${netPInternalMtuProvider.getMtu()}")
            lifecycleScope.launch(Dispatchers.Default) {
                vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
            }

            true
        }
        popup.setOnDismissListener { }
        popup.show()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, NetPInternalSettingsActivity::class.java)
        }
    }
}
