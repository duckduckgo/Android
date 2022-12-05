/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.entry

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.listitem.TwoLineListItem
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpManagementBinding
import com.duckduckgo.networkprotection.impl.entry.NetworkProtectionManagementViewModel.Command
import com.duckduckgo.networkprotection.impl.entry.NetworkProtectionManagementViewModel.ConnectionState
import com.duckduckgo.networkprotection.impl.entry.NetworkProtectionManagementViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
class NetworkProtectionManagementActivity : DuckDuckGoActivity() {

    private val binding: ActivityNetpManagementBinding by viewBinding()
    private val viewModel: NetworkProtectionManagementViewModel by bindViewModel()
    private val vpnPermissionRequestActivityResult =
        registerForActivityResult(StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                viewModel.onStartVpn()
            }
        }
    private val toggleChangeListener = OnCheckedChangeListener { _, isChecked -> viewModel.onNetpToggleClicked(isChecked) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        bindViews()

        observeViewModel()
    }

    private fun bindViews() {
        binding.netpToggle.setOnCheckedChangeListener(toggleChangeListener)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.viewState()
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { renderViewState(it) }
        }

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { handleCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        if (viewState.connectionState == ConnectionState.Connected) {
            binding.netpToggle.quietlySetChecked(true)
            binding.netpToggle.setSecondaryText("Connected")
        } else {
            binding.netpToggle.quietlySetChecked(false)
            binding.netpToggle.setSecondaryText("Disconnected")
        }
    }

    private fun handleCommand(command: Command) {
        when (command) {
            is Command.CheckVPNPermission -> checkVPNPermission()
            is Command.RequestVPNPermission -> requestVPNPermission(command.vpnIntent)
        }
    }

    private fun checkVPNPermission() {
        when (val permissionStatus = checkVpnPermissionStatus()) {
            is VpnPermissionStatus.Granted -> {
                viewModel.onStartVpn()
            }
            is VpnPermissionStatus.Denied -> {
                binding.netpToggle.quietlySetChecked(false)
                viewModel.onRequiredPermissionNotGranted(permissionStatus.intent)
            }
        }
    }

    private fun checkVpnPermissionStatus(): VpnPermissionStatus {
        val intent = VpnService.prepare(applicationContext)
        return if (intent == null) {
            VpnPermissionStatus.Granted
        } else {
            VpnPermissionStatus.Denied(intent)
        }
    }

    private fun requestVPNPermission(intent: Intent) {
        vpnPermissionRequestActivityResult.launch(intent)
    }

    private fun TwoLineListItem.quietlySetChecked(isChecked: Boolean) {
        setOnCheckedChangeListener { _, _ -> }
        setIsChecked(isChecked)
        setOnCheckedChangeListener(toggleChangeListener)
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, NetworkProtectionManagementActivity::class.java)
        }
    }
}
