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

import android.annotation.SuppressLint
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.addClickableLink
import com.duckduckgo.mobile.android.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.listitem.TwoLineListItem
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetPAppExclusionListNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpManagementBinding
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.None
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowAlwaysOnLockdownEnabled
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowReconnecting
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowReconnectingFailed
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowRevoked
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionDetails
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ViewState
import com.duckduckgo.networkprotection.impl.management.alwayson.NetworkProtectionAlwaysOnDialogFragment
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetworkProtectionManagementScreenNoParams::class)
class NetworkProtectionManagementActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: ActivityNetpManagementBinding by viewBinding()
    private val viewModel: NetworkProtectionManagementViewModel by bindViewModel()
    private val vpnPermissionRequestActivityResult =
        registerForActivityResult(StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                viewModel.onStartVpn()
            } else {
                viewModel.onVPNPermissionRejected(System.currentTimeMillis())
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
            viewModel.onReportIssuesClicked()
        }

        binding.settings.settingsExclusion.setClickListener {
            globalActivityStarter.start(this, NetPAppExclusionListNoParams)
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
            else -> {}
        }

        when (viewState.alertState) {
            ShowReconnecting -> binding.renderAlertReconnecting()
            ShowReconnectingFailed -> binding.renderAlertReconnectingFailed()
            ShowRevoked -> binding.renderAlertRevoked()
            ShowAlwaysOnLockdownEnabled -> binding.renderAlertLockdownEnabled()
            None -> binding.netPAlert.gone()
        }
    }

    private fun ActivityNetpManagementBinding.renderAlertReconnecting() {
        netPAlert.setText(getString(R.string.netpBannerReconnecting))
        netPAlert.show()
    }

    private fun ActivityNetpManagementBinding.renderAlertReconnectingFailed() {
        netPAlert.setText(getString(R.string.netpBannerReconnectionFailed))
        netPAlert.show()
    }

    private fun ActivityNetpManagementBinding.renderAlertRevoked() {
        netPAlert.setClickableLink("", getText(R.string.netpBannerVpnRevoked)) {}
        netPAlert.show()
    }

    private fun ActivityNetpManagementBinding.renderAlertLockdownEnabled() {
        netPAlert.setClickableLink(OPEN_SETTINGS_ANNOTATION, getText(R.string.netpBannerAlwaysOnLockDownEnabled)) {
            openVPNSettings()
        }
        netPAlert.show()
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
        netpStatusImage.setImageResource(R.drawable.illustration_vpn_off)
        netpStatusHeader.setText(R.string.netpManagementHeadlineStatusOff)
        netpToggle.setSecondaryText(getString(R.string.netpManagementToggleSubtitleConnecting))
        netpToggle.isEnabled = false
        connectionDetails.root.gone()
    }

    private fun handleCommand(command: Command) {
        when (command) {
            is Command.CheckVPNPermission -> checkVPNPermission()
            is Command.RequestVPNPermission -> requestVPNPermission(command.vpnIntent)
            is Command.ShowVpnAlwaysOnConflictDialog -> showAlwaysOnConflictDialog()
            is Command.ShowVpnConflictDialog -> showVpnConflictDialog()
            is Command.ResetToggle -> resetToggle()
            is Command.ShowAlwaysOnPromotionDialog -> showAlwaysOnPromotionDialog()
            is Command.ShowAlwaysOnLockdownDialog -> showAlwaysOnLockdownDialog()
            is Command.OpenVPNSettings -> openVPNSettings()
            is Command.ShowIssueReportingPage -> globalActivityStarter.start(this, command.params)
        }
    }

    private fun checkVPNPermission() {
        when (val permissionStatus = checkVpnPermissionStatus()) {
            is VpnPermissionStatus.Granted -> {
                viewModel.onStartVpn()
            }
            is VpnPermissionStatus.Denied -> {
                binding.netpToggle.quietlySetChecked(false)
                viewModel.onRequiredPermissionNotGranted(permissionStatus.intent, System.currentTimeMillis())
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

    private fun showAlwaysOnConflictDialog() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.netpAlwaysOnVpnConflictTitle)
            .setMessage(R.string.netpAlwaysOnVpnConflictMessage)
            .setPositiveButton(R.string.netpActionOpenSettings)
            .setNegativeButton(R.string.netpActionCancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onVpnConflictDialogGoToSettings()
                    }

                    override fun onNegativeButtonClicked() {}
                },
            )
            .show()
    }

    private fun showVpnConflictDialog() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.netpVpnConflictTitle)
            .setMessage(R.string.netpVpnConflictMessage)
            .setPositiveButton(R.string.netpActionGotIt)
            .setNegativeButton(R.string.netpActionCancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onVpnConflictDialogContinue()
                    }

                    override fun onNegativeButtonClicked() {
                        resetToggle()
                    }
                },
            )
            .show()
    }

    private fun onVpnConflictDialogContinue() {
        checkVPNPermission()
    }

    fun onVpnConflictDialogGoToSettings() {
        openVPNSettings()
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

    private fun resetToggle() {
        binding.netpToggle.isEnabled = true
        binding.netpToggle.quietlySetChecked(false)
    }

    private fun showAlwaysOnPromotionDialog() {
        dismissAlwaysOnDialog()

        NetworkProtectionAlwaysOnDialogFragment.newPromotionDialog(
            object : NetworkProtectionAlwaysOnDialogFragment.Listener {
                override fun onGoToSettingsClicked() {
                    viewModel.onOpenSettingsFromAlwaysOnPromotionClicked()
                }

                override fun onCanceled() {}
            },
        ).show(supportFragmentManager, TAG_ALWAYS_ON_DIALOG)
    }

    private fun showAlwaysOnLockdownDialog() {
        dismissAlwaysOnDialog()

        NetworkProtectionAlwaysOnDialogFragment.newLockdownDialog(
            object : NetworkProtectionAlwaysOnDialogFragment.Listener {
                override fun onGoToSettingsClicked() {
                    viewModel.onOpenSettingsFromAlwaysOnLockdownClicked()
                }

                override fun onCanceled() {}
            },
        ).show(supportFragmentManager, TAG_ALWAYS_ON_DIALOG)
    }

    private fun dismissAlwaysOnDialog() {
        (supportFragmentManager.findFragmentByTag(TAG_ALWAYS_ON_DIALOG) as? DialogFragment)?.dismiss()
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
        private const val OPEN_SETTINGS_ANNOTATION = "open_settings_link"
        private const val TAG_ALWAYS_ON_DIALOG = "NETP_ALWAYS_ON_DIALOG"
    }
}
