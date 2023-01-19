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

package com.duckduckgo.networkprotection.impl.management

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.addClickableLink
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.listitem.TwoLineListItem
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpManagementBinding
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionDetails
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
    private val toggleChangeListener = OnCheckedChangeListener { _, isChecked ->
        binding.netpToggle.isEnabled = false
        viewModel.onNetpToggleClicked(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        bindViews()

        observeViewModel()
        lifecycle.addObserver(viewModel)
    }

    private fun bindViews() {
        setTitle(R.string.netpManagementTitle)
        binding.netpToggle.setOnCheckedChangeListener(toggleChangeListener)

        binding.netpBetaDescription.addClickableLink(
            REPORT_ISSUES_ANNOTATION,
            getText(R.string.netpManagementBetaDescription),
        ) {
            startActivity(Intent(Intent.ACTION_VIEW, FEEDBACK_URL))
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { handleCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        when (viewState.connectionState) {
            ConnectionState.Connecting -> binding.renderConnectingState()
            ConnectionState.Connected -> viewState.connectionDetails?.let { binding.renderConnectedState(it) }
            ConnectionState.Disconnected -> binding.renderDisconnectedState()
            else -> { }
        }
    }

    private fun ActivityNetpManagementBinding.renderConnectedState(connectionDetailsData: ConnectionDetails) {
        netpStatusImage.setImageResource(R.drawable.illustration_vpn_on)
        netpStatusHeader.setText(R.string.netpManagementHeadlineStatusOn)
        netpToggle.quietlySetChecked(true)
        netpToggle.isEnabled = true
        connectionDetailsData.elapsedConnectedTime?.let {
            netpToggle.setSecondaryText(getString(R.string.netpManagementToggleSubtitleConnected, it))
        }
        connectionDetails.root.show()
        if (connectionDetailsData.location.isNullOrEmpty()) {
            connectionDetails.connectionDetailsLocation.gone()
        } else {
            connectionDetails.connectionDetailsLocation.setSecondaryText(connectionDetailsData.location)
        }
        if (connectionDetailsData.ipAddress.isNullOrEmpty()) {
            connectionDetails.connectionDetailsIp.gone()
        } else {
            connectionDetails.connectionDetailsIp.setSecondaryText(connectionDetailsData.ipAddress)
        }
    }

    private fun ActivityNetpManagementBinding.renderDisconnectedState() {
        netpStatusImage.setImageResource(R.drawable.illustration_vpn_off)
        netpStatusHeader.setText(R.string.netpManagementHeadlineStatusOff)
        netpToggle.quietlySetChecked(false)
        netpToggle.setSecondaryText(getString(R.string.netpManagementToggleSubtitleDisconnected))
        netpToggle.isEnabled = true
        connectionDetails.root.gone()
    }

    private fun ActivityNetpManagementBinding.renderConnectingState() {
        netpToggle.setSecondaryText(getString(R.string.netpManagementToggleSubtitleConnecting))
        netpToggle.isEnabled = false
        connectionDetails.root.gone()
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
        private const val REPORT_ISSUES_ANNOTATION = "report_issues_link"
        val FEEDBACK_URL = "https://form.asana.com/?k=_wNLt6YcT5ILpQjDuW0Mxw&d=137249556945".toUri()
        fun intent(context: Context): Intent {
            return Intent(context, NetworkProtectionManagementActivity::class.java)
        }
    }
}
