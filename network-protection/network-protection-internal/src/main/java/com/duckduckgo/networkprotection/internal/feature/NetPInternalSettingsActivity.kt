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
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.internal.feature.system_apps.NetPSystemAppsExclusionListActivity
import com.duckduckgo.networkprotection.internal.network.NetPInternalIPProvider
import com.duckduckgo.networkprotection.internal.network.NetPInternalMtuProvider
import com.duckduckgo.networkprotection.store.remote_config.NetPServerRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import om.duckduckgo.networkprotection.internal.databinding.ActivityNetpInternalSettingsBinding

@Suppress("NoHardcodedCoroutineDispatcher")
@InjectWith(ActivityScope::class)
class NetPInternalSettingsActivity : DuckDuckGoActivity() {

    @Inject lateinit var netPInternalFeatureToggles: NetPInternalFeatureToggles

    @Inject lateinit var vpnStateMonitor: VpnStateMonitor

    @Inject lateinit var netPInternalMtuProvider: NetPInternalMtuProvider

    @Inject lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject lateinit var serverRepository: NetPServerRepository

    @Inject lateinit var internalIPProvider: NetPInternalIPProvider

    private val binding: ActivityNetpInternalSettingsBinding by viewBinding()

    private val mtuSizes: List<Int?> = listOf(1000, 1100, 1280, 1400, 1500, null)

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
                binding.systemAppsItem.isEnabled = isEnabled && !netPInternalFeatureToggles.excludeSystemApps().isEnabled()
                binding.overrideMtuSelector.isEnabled = isEnabled
                binding.overrideMtuSelector.setSecondaryText("MTU size: ${netPInternalMtuProvider.getMtu()}")
                binding.overrideServerBackendSelector.isEnabled = isEnabled
                binding.overrideServerBackendSelector.setSecondaryText("${serverRepository.getSelectedServer()?.name ?: AUTOMATIC}")
                if (isEnabled) {
                    internalIPProvider.internalIP?.let {
                        binding.internalIp.show()
                        binding.internalIp.setSecondaryText(it)
                    } ?: binding.internalIp.gone()
                } else {
                    binding.internalIp.gone()
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun setupConfigSection() {
        with(netPInternalFeatureToggles.excludeSystemApps()) {
            binding.excludeSystemAppsToggle.setIsChecked(this.isEnabled())
            binding.excludeSystemAppsToggle.setOnCheckedChangeListener { _, isChecked ->
                this.setEnabled(Toggle.State(enable = isChecked))
                lifecycleScope.launch {
                    vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
                }
            }
        }

        binding.overrideMtuSelector.setOnClickListener { showMtuSelectorMenu() }
        binding.overrideServerBackendSelector.setOnClickListener {
            lifecycleScope.launch {
                showServerSelectorMenu()
            }
        }
        binding.systemAppsItem.setOnClickListener {
            startActivity(NetPSystemAppsExclusionListActivity.intent(this))
        }
    }

    private fun showMtuSelectorMenu() {
        PopupMenu(this, binding.overrideMtuSelector).apply {
            mtuSizes.forEach {
                menu.add(it?.toString() ?: AUTOMATIC)
            }
            setOnMenuItemClickListener { menuItem ->
                val mtuSize = kotlin.runCatching { menuItem.title.toString().toInt() }.getOrNull()
                netPInternalMtuProvider.setMtu(mtuSize)
                binding.overrideMtuSelector.setSecondaryText("MTU size: ${netPInternalMtuProvider.getMtu()}")
                lifecycleScope.launch(Dispatchers.Default) {
                    vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
                }

                true
            }
            setOnDismissListener { }
        }.show()
    }

    private suspend fun showServerSelectorMenu() {
        fun MenuItem.serverName(): String? {
            return if (title == AUTOMATIC) null else title.toString()
        }
        val hostnames = serverRepository.getServerNames()
        PopupMenu(this, binding.overrideServerBackendSelector).apply {
            (hostnames + AUTOMATIC).forEach { hostname ->
                menu.add(hostname)
            }

            setOnMenuItemClickListener {
                this@NetPInternalSettingsActivity.lifecycleScope.launch {
                    serverRepository.setSelectedServer(it.serverName())
                    binding.overrideServerBackendSelector.setSecondaryText("${serverRepository.getSelectedServer()?.name ?: AUTOMATIC}")
                    vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
                }
                true
            }

            setOnDismissListener { }
        }.show()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, NetPInternalSettingsActivity::class.java)
        }

        private const val AUTOMATIC = "Automatic"
    }
}
