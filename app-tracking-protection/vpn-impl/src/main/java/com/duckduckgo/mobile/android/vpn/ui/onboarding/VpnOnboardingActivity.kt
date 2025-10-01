/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.onboarding

import android.annotation.SuppressLint
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.launchAlwaysOnSystemSettings
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerOnboardingActivityWithEmptyParamsParams
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.R.string
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.databinding.ActivityVpnOnboardingBinding
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.CheckVPNPermission
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.LaunchVPN
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.RequestVPNPermission
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.ShowVpnAlwaysOnConflictDialog
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.ShowVpnConflictDialog
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AppTrackerOnboardingActivityWithEmptyParamsParams::class)
class VpnOnboardingActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: VpnOnboardingViewModel by bindViewModel()
    private val binding: ActivityVpnOnboardingBinding by viewBinding()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        configureUI()
        observeViewModel()
    }

    private fun configureUI() {
        binding.onboardingPager.adapter = DeviceShieldOnboardingAdapter(viewModel.pages) {
            launchFAQ()
        }
        binding.onboardingPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    showOnboardingPage(position)
                    super.onPageSelected(position)
                }
            },
        )

        binding.onboardingClose.setOnClickListener {
            close()
        }

        overrideStatusBarColor()
    }

    private fun overrideStatusBarColor() {
        window.statusBarColor = getColorFromAttr(com.duckduckgo.mobile.android.R.attr.appTPHeaderBackground)
    }

    private fun observeViewModel() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun launchFAQ() {
        globalActivityStarter.start(
            this,
            WebViewActivityWithParams(
                url = FAQ_WEBSITE,
                screenTitle = getString(string.atp_FAQActivityTitle),
            ),
        )
    }

    private fun showOnboardingPage(position: Int) {
        when (position) {
            0 -> {
                showNextPageCTA()
            }

            1 -> {
                showNextPageCTA()
            }

            2 -> {
                showEnableCTA()
            }

            else -> {} // no-op
        }
    }

    private fun showNextPageCTA() {
        with(binding.onboardingNextCta) {
            setText(R.string.atp_OnboardingContinue)
            setOnClickListener {
                showNextPage()
            }
        }
    }

    private fun showNextPage() {
        binding.onboardingPager.currentItem = binding.onboardingPager.currentItem + 1
    }

    private fun showEnableCTA() {
        with(binding.onboardingNextCta) {
            setText(R.string.atp_OnboardingLogoDescription)
            setOnClickListener {
                viewModel.onTurnAppTpOffOn()
            }
        }
    }

    private fun launchDeviceShieldTrackerActivity() {
        startActivity(DeviceShieldTrackerActivity.intent(this))
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // go back to previous screen or get out if first page
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        close()
        return true
    }

    private fun close() {
        finish()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        if (requestCode == REQUEST_ASK_VPN_PERMISSION) {
            viewModel.onVPNPermissionResult(resultCode)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is LaunchVPN -> startVpn()
            is ShowVpnConflictDialog -> showVpnConflictDialog()
            is ShowVpnAlwaysOnConflictDialog -> showAlwaysOnConflictDialog()
            is CheckVPNPermission -> checkVPNPermission()
            is RequestVPNPermission -> obtainVpnRequestPermission(command.vpnIntent)
        }
    }

    private fun checkVPNPermission() {
        when (val permissionStatus = checkVpnPermissionStatus()) {
            is VpnPermissionStatus.Granted -> {
                startVpn()
            }

            is VpnPermissionStatus.Denied -> {
                viewModel.onVPNPermissionNeeded(permissionStatus.intent)
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

    @Suppress("DEPRECATION")
    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, REQUEST_ASK_VPN_PERMISSION)
    }

    private fun startVpn() {
        lifecycleScope.launch(dispatcherProvider.io()) {
            vpnFeaturesRegistry.registerFeature(AppTpVpnFeature.APPTP_VPN)
            launchDeviceShieldTrackerActivity()
            viewModel.onAppTpEnabled()
        }
    }

    private fun showVpnConflictDialog() {
        deviceShieldPixels.didShowVpnConflictDialog()
        TextAlertDialogBuilder(this)
            .setTitle(R.string.atp_VpnConflictDialogTitle)
            .setMessage(R.string.atp_VpnConflictDialogMessage)
            .setPositiveButton(R.string.atp_VpnConflictDialogGotIt)
            .setNegativeButton(R.string.atp_VpnConflictDialogCancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onVpnConflictDialogContinue()
                    }

                    override fun onNegativeButtonClicked() {
                        onVpnConflictDialogDismiss()
                    }
                },
            )
            .show()
    }

    private fun showAlwaysOnConflictDialog() {
        deviceShieldPixels.didShowVpnConflictDialog()
        TextAlertDialogBuilder(this)
            .setTitle(R.string.atp_VpnConflictAlwaysOnDialogTitle)
            .setMessage(R.string.atp_VpnConflictDialogAlwaysOnMessage)
            .setPositiveButton(R.string.atp_VpnConflictDialogOpenSettings)
            .setNegativeButton(R.string.atp_VpnConflictDialogCancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onVpnConflictDialogGoToSettings()
                    }

                    override fun onNegativeButtonClicked() {
                        onVpnConflictDialogDismiss()
                    }
                },
            )
            .show()
    }

    fun onVpnConflictDialogDismiss() {
        deviceShieldPixels.didChooseToDismissVpnConflictDialog()
    }

    @SuppressLint("InlinedApi")
    fun onVpnConflictDialogGoToSettings() {
        deviceShieldPixels.didChooseToOpenSettingsFromVpnConflictDialog()

        this.launchAlwaysOnSystemSettings()
    }

    fun onVpnConflictDialogContinue() {
        deviceShieldPixels.didChooseToContinueFromVpnConflictDialog()
        checkVPNPermission()
    }

    private sealed class VpnPermissionStatus {
        data object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        private const val REQUEST_ASK_VPN_PERMISSION = 101
        private const val FAQ_WEBSITE = "https://help.duckduckgo.com/duckduckgo-help-pages/p-app-tracking-protection/what-is-app-tracking-protection/"
    }
}
