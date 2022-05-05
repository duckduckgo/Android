/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingVpnPermissionBinding
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.ContinueVpnConflictDialog
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.DismissVpnConflictDialog
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.OpenSettingVpnConflictDialog
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.VpnPermissionDenied
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.VpnPermissionGranted
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.VpnPermissionResult
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Command.CheckVPNPermission
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Command.LeaveVpnPermission
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Command.OpenVpnFAQ
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Command.OpenVpnSettings
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Command.RequestVpnPermission
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Command.ShowVpnAlwaysOnConflictDialog
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Command.ShowVpnConflictDialog
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Command.StartVpn
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.VpnPermissionStatus
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.addClickableLink
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldFAQActivity
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPVpnConflictDialog
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPVpnConflictDialog.Listener
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.content_onboarding_default_browser.*
import kotlinx.android.synthetic.main.include_default_browser_buttons.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class VpnPermissionPage : OnboardingPageFragment(), Listener {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: ContentOnboardingVpnPermissionBinding by viewBinding()

    private val viewModel: VpnPagesViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(VpnPagesViewModel::class.java)
    }

    override fun layoutResource(): Int = R.layout.content_onboarding_vpn_permission

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQUEST_ASK_VPN_PERMISSION) {
            viewModel.onAction(VpnPermissionResult(resultCode))
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
            viewModel.onAction(Action.PermissionPageBecameVisible)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        observeViewModel()
        setButtonsBehaviour()
        binding.onboardingPageText.addClickableLink(
            "learn_more_link",
            getText(com.duckduckgo.mobile.android.vpn.R.string.atp_daxOnboardingPermissionMessage)
        ) {
            viewModel.onAction(Action.LearnMore)
        }
    }

    private fun observeViewModel() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: VpnPagesViewModel.Command) {
        when (command) {
            is ShowVpnConflictDialog -> launchVPNConflictDialog(false)
            is ShowVpnAlwaysOnConflictDialog -> launchVPNConflictDialog(true)
            is LeaveVpnPermission -> onOnboardingDone()
            is OpenVpnFAQ -> openFAQ()
            is OpenVpnSettings -> openVpnSettings()
            is CheckVPNPermission -> checkVPNPermission()
            is StartVpn -> startVpn()
            is RequestVpnPermission -> obtainVpnRequestPermission(command.intent)
        }
    }

    private fun setButtonsBehaviour() {
        binding.onboardingMaybeLater.setOnClickListener {
            viewModel.onAction(Action.LeaveVpnPermission)
        }
        binding.onboardingNextCta.setOnClickListener {
            viewModel.onAction(Action.EnableVPN)
        }
    }

    private fun openFAQ() {
        startActivity(DeviceShieldFAQActivity.intent(requireContext()))
    }

    private fun openVpnSettings() {
        val intent = Intent(Settings.ACTION_VPN_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun launchVPNConflictDialog(isAlwaysOn: Boolean) {
        val dialog = AppTPVpnConflictDialog.instance(this, isAlwaysOn)
        dialog.show(
            requireActivity().supportFragmentManager,
            AppTPVpnConflictDialog.TAG_VPN_CONFLICT_DIALOG
        )
    }

    private fun checkVPNPermission() {
        when (val permissionStatus = checkVpnPermissionStatus()) {
            is VpnPermissionStatus.Granted -> {
                viewModel.onAction(VpnPermissionGranted)
            }
            is VpnPermissionStatus.Denied -> {
                viewModel.onAction(VpnPermissionDenied(permissionStatus.intent))
            }
        }
    }

    private fun checkVpnPermissionStatus(): VpnPermissionStatus {
        val intent = VpnService.prepare(requireContext())
        return if (intent == null) {
            VpnPermissionStatus.Granted
        } else {
            VpnPermissionStatus.Denied(intent)
        }
    }

    private fun startVpn() {
        TrackerBlockingVpnService.startService(requireContext())
        onContinuePressed()
    }

    @Suppress("DEPRECATION")
    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, REQUEST_ASK_VPN_PERMISSION)
    }

    companion object {
        private const val REQUEST_ASK_VPN_PERMISSION = 101
    }

    override fun onVpnConflictDialogDismiss() {
        viewModel.onAction(DismissVpnConflictDialog)
    }

    override fun onVpnConflictDialogGoToSettings() {
        viewModel.onAction(OpenSettingVpnConflictDialog)
    }

    override fun onVpnConflictDialogContinue() {
        viewModel.onAction(ContinueVpnConflictDialog)
    }
}
