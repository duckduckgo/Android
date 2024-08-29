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

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.text.format.Formatter.formatFileSize
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieDrawable.INFINITE
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.isPrivateDnsStrict
import com.duckduckgo.common.utils.extensions.launchAlwaysOnSystemSettings
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetPAppExclusionListNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenAndEnable
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpManagementBinding
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.None
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowAlwaysOnLockdownEnabled
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowRevoked
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionDetails
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connecting
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Disconnected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.LocationState
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ViewState
import com.duckduckgo.networkprotection.impl.management.alwayson.NetworkProtectionAlwaysOnDialogFragment
import com.duckduckgo.networkprotection.impl.settings.NetPVpnSettingsScreenNoParams
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsScreen
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpGeoswitchingScreenNoParams
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.PrivacyProFeedbackScreenWithParams
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_MANAGEMENT
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetworkProtectionManagementScreenNoParams::class, screenName = "vpn.main")
@ContributeToActivityStarter(NetworkProtectionManagementScreenAndEnable::class, screenName = "vpn.main")
class NetworkProtectionManagementActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

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

    private var previousState: ConnectionState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        bindViews()
        intent.getActivityParams(NetworkProtectionManagementScreenAndEnable::class.java)?.enable?.let { shouldEnable ->
            if (shouldEnable) {
                checkVPNPermission()
            }
        }

        observeViewModel()
        lifecycle.addObserver(viewModel)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(viewModel)
    }

    private fun bindViews() {
        setTitle(R.string.netpManagementTitle)
        binding.netpToggle.setOnCheckedChangeListener(toggleChangeListener)
        binding.netpToggle.setPrimaryText(getString(R.string.netpManagementToggleTitle))

        binding.about.aboutShareFeedback.setClickListener {
            viewModel.onReportIssuesClicked()
        }

        binding.locationDetails.locationItem.setClickListener {
            globalActivityStarter.start(this, NetpGeoswitchingScreenNoParams)
        }

        binding.settings.settingsExclusion.setClickListener {
            globalActivityStarter.start(this, NetPAppExclusionListNoParams)
        }

        binding.settings.settingsVpn.setClickListener {
            globalActivityStarter.start(this, NetPVpnSettingsScreenNoParams)
        }

        binding.about.aboutFaq.setClickListener {
            lifecycleScope.launch(dispatcherProvider.io()) {
                globalActivityStarter.start(
                    this@NetworkProtectionManagementActivity,
                    WebViewActivityWithParams(url = VPN_HELP_CENTER_URL, screenTitle = getString(R.string.netpFaqTitle)),
                )
            }
        }

        binding.connectionDetails.connectionDetailsDns.setClickListener {
            globalActivityStarter.start(this, VpnCustomDnsScreen.Default)
        }
        configureHeaderAnimation()
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
            ShowRevoked -> binding.renderAlertRevoked()
            ShowAlwaysOnLockdownEnabled -> binding.renderAlertLockdownEnabled()
            None -> binding.netPAlert.gone()
        }

        binding.renderLocationState(viewState.locationState)
        if (viewState.excludedAppsCount == 0) {
            binding.settings.settingsExclusion.setSecondaryText(getString(R.string.netpManagementManageItemExclusionSubtitleEmpty))
        } else {
            binding.settings.settingsExclusion.setSecondaryText(
                resources.getQuantityString(
                    R.plurals.netpManagementManageItemExclusionSubtitleAppCount,
                    viewState.excludedAppsCount,
                    viewState.excludedAppsCount,
                ),
            )
        }
    }

    private fun ActivityNetpManagementBinding.renderLocationState(locationState: LocationState?) {
        if (locationState == null || locationState.location.isNullOrEmpty()) {
            locationDetails.locationItem.setDetails(getString(R.string.netpManagementLocationPlaceholder))
            locationDetails.locationItem.setLeadingIcon(R.drawable.ic_location)
            locationDetails.locationItem.status.gone()
        } else {
            locationDetails.locationItem.apply {
                status.show()
                setDetails(locationState.location)
                locationState.icon?.let { setLeadingIcon(it) }
                if (locationState.isCustom) {
                    setStatus(getString(R.string.netpManagementLocationCustom))
                } else {
                    setStatus(getString(R.string.netpManagementLocationNearest))
                }
            }
        }
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
        handleAnimation(Connected)
        netpStatusHeader.setText(R.string.netpManagementHeadlineStatusOn)
        netpStatusDescription.setText(R.string.netpManagementDescriptionOn)
        netpToggle.indicator.setImageDrawable(AppCompatResources.getDrawable(applicationContext, R.drawable.indicator_vpn_connected))
        netpToggle.quietlySetChecked(true)
        netpToggle.isEnabled = true

        locationDetails.locationHeader.setText(R.string.netpManagementLocationHeaderVpnOn)
        connectionDetailsData.elapsedConnectedTime?.let {
            netpToggle.setSecondaryText(getString(R.string.netpManagementToggleSubtitleConnected, it))
        }
        connectionDetails.root.show()

        if (connectionDetailsData.ipAddress.isNullOrEmpty()) {
            connectionDetails.connectionDetailsIp.gone()
        } else {
            connectionDetails.connectionDetailsIp.show()
            connectionDetails.connectionDetailsIp.setSecondaryText(connectionDetailsData.ipAddress)
        }

        connectionDetails.transmittedText.text = formatFileSize(applicationContext, connectionDetailsData.transmittedData)
        connectionDetails.receivedText.text = formatFileSize(applicationContext, connectionDetailsData.receivedData)

        if (connectionDetailsData.customDns.isNullOrEmpty() || this@NetworkProtectionManagementActivity.isPrivateDnsStrict()) {
            connectionDetails.connectionDetailsDns.gone()
        } else {
            connectionDetails.connectionDetailsDns.show()
            connectionDetails.connectionDetailsDns.setSecondaryText(connectionDetailsData.customDns)
        }
    }

    private fun ActivityNetpManagementBinding.renderDisconnectedState() {
        handleAnimation(Disconnected)
        netpStatusHeader.setText(R.string.netpManagementHeadlineStatusOff)
        netpStatusDescription.setText(R.string.netpManagementDescriptionOff)
        netpToggle.indicator.setImageDrawable(AppCompatResources.getDrawable(applicationContext, R.drawable.indicator_vpn_disconnected))
        locationDetails.locationHeader.setText(R.string.netpManagementLocationHeaderVpnOff)
        netpToggle.quietlySetChecked(false)
        netpToggle.setSecondaryText(getString(R.string.netpManagementToggleSubtitleDisconnected))
        netpToggle.isEnabled = true
        connectionDetails.root.gone()
    }

    private fun ActivityNetpManagementBinding.renderConnectingState() {
        handleAnimation(Connecting)
        netpToggle.quietlySetChecked(true)
        netpToggle.indicator.setImageDrawable(AppCompatResources.getDrawable(applicationContext, R.drawable.indicator_vpn_disconnected))
        netpStatusHeader.setText(R.string.netpManagementHeadlineStatusOff)
        netpStatusDescription.setText(R.string.netpManagementDescriptionOff)
        netpToggle.setSecondaryText(getString(R.string.netpManagementToggleSubtitleConnecting))
        locationDetails.locationHeader.setText(R.string.netpManagementLocationHeaderVpnOff)
        netpToggle.isEnabled = false
        connectionDetails.root.gone()
    }

    private fun configureHeaderAnimation() {
        if (appTheme.isLightModeEnabled()) {
            binding.netpStatusImage.setAnimation(R.raw.vpn_header)
        } else {
            binding.netpStatusImage.setAnimation(R.raw.vpn_header_dark)
        }
    }

    private fun handleAnimation(newState: ConnectionState) {
        if (newState == previousState) {
            return
        }

        binding.netpStatusImage.removeAllAnimatorListeners()
        if (previousState == null) {
            // This is not a transition state so skip transition
            if (newState == Connected) {
                binding.netpStatusImage.setMinAndMaxProgress(0.35f, 1f)
                binding.netpStatusImage.progress = 0.35f
                binding.netpStatusImage.repeatCount = INFINITE
                binding.netpStatusImage.playAnimation()
            } else {
                binding.netpStatusImage.setMinAndMaxProgress(0f, 0f)
                binding.netpStatusImage.progress = 0f
                binding.netpStatusImage.repeatCount = 0
                binding.netpStatusImage.playAnimation()
            }
        } else {
            if (newState == Connected) {
                binding.netpStatusImage.setMinAndMaxProgress(0f, 1f)
                binding.netpStatusImage.progress = 0f
                binding.netpStatusImage.speed = 1f
                binding.netpStatusImage.addAnimatorListener(
                    object : AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            binding.netpStatusImage.setMinAndMaxProgress(0.35f, 1f)
                            binding.netpStatusImage.progress = 0.35f
                            binding.netpStatusImage.repeatCount = INFINITE
                            binding.netpStatusImage.removeAllAnimatorListeners()
                            binding.netpStatusImage.playAnimation()
                        }

                        override fun onAnimationCancel(animation: Animator) {
                        }

                        override fun onAnimationRepeat(animation: Animator) {
                        }
                    },
                )
                binding.netpStatusImage.playAnimation()
            } else if (previousState != Disconnected) {
                binding.netpStatusImage.setMinAndMaxProgress(0f, 0f)
                binding.netpStatusImage.progress = 0f
                binding.netpStatusImage.repeatCount = 0
                binding.netpStatusImage.playAnimation()
            }
        }

        previousState = newState
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
            is Command.ShowUnifiedFeedback -> globalActivityStarter.start(
                this,
                PrivacyProFeedbackScreenWithParams(feedbackSource = VPN_MANAGEMENT),
            )
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

    private fun openVPNSettings() {
        this.launchAlwaysOnSystemSettings()
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

    private fun VpnToggle.quietlySetChecked(isChecked: Boolean) {
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
        private const val VPN_HELP_CENTER_URL = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/vpn/"
    }
}
