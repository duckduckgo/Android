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

package com.duckduckgo.app.beta

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.addRepeatingJob
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.device_shield.DeviceShieldExclusionListActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldEnabledActivity
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingActivity
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import kotlinx.android.synthetic.main.activity_beta_features.*
import kotlinx.android.synthetic.main.include_toolbar.*
import timber.log.Timber

class BetaFeaturesActivity : DuckDuckGoActivity() {

    private val viewModel: BetaFeaturesViewModel by bindViewModel()

    private val deviceShieldToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onDeviceShieldSettingChanged(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_beta_features)
        setupToolbar(toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun observeViewModel() {
        addRepeatingJob(Lifecycle.State.CREATED) {
            viewModel.loadInitialData()
        }

        viewModel.viewState.observe(
            this,
            Observer { viewState ->
                viewState?.let {
                    deviceShieldToggle.isVisible = viewState.deviceShieldOnboardingComplete
                    deviceShieldExcludedAppsText.isVisible = viewState.deviceShieldOnboardingComplete
                    deviceShieldPrivacyReportText.isVisible = viewState.deviceShieldOnboardingComplete
                    deviceShieldEnable.isVisible = !viewState.deviceShieldOnboardingComplete

                    deviceShieldToggle.quietlySetIsChecked(it.deviceShieldEnabled, deviceShieldToggleListener)
                }
            }
        )

        viewModel.command.observe(
            this,
            Observer {
                processCommand(it)
            }
        )
    }

    private fun configureUiEventHandlers() {
        deviceShieldToggle.setOnCheckedChangeListener(deviceShieldToggleListener)
        deviceShieldExcludedAppsText.setOnClickListener { viewModel.onExcludedAppsClicked() }
        deviceShieldPrivacyReportText.setOnClickListener { viewModel.onDeviceShieldPrivacyReportClicked() }
        deviceShieldEnable.setOnClickListener { viewModel.onDeviceShieldOnboardingClicked() }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_DEVICE_SHIELD_ONBOARDING -> handleDeviceShieldOnboardingResult(resultCode)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun handleDeviceShieldOnboardingResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            Timber.i("VPN enabled during device shield onboarding")
            startActivity(DeviceShieldEnabledActivity.intent(this))
        } else {
            Timber.i("VPN NOT enabled during device shield onboarding")
        }
    }

    private fun processCommand(it: BetaFeaturesViewModel.Command) {
        when (it) {
            is BetaFeaturesViewModel.Command.LaunchExcludedAppList -> launchExcludedAppList()
            is BetaFeaturesViewModel.Command.LaunchDeviceShieldPrivacyReport -> launchDeviceShieldPrivacyReport()
            is BetaFeaturesViewModel.Command.LaunchDeviceShieldOnboarding -> launchDeviceShieldOnboarding()
            is BetaFeaturesViewModel.Command.StartDeviceShield -> startVpnIfAllowed()
            is BetaFeaturesViewModel.Command.StopDeviceShield -> stopDeviceShield()
        }
    }

    private fun launchExcludedAppList() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(DeviceShieldExclusionListActivity.intent(this), options)
    }

    private fun launchDeviceShieldPrivacyReport() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(DeviceShieldTrackerActivity.intent(this), options)
    }

    @Suppress("DEPRECATION")
    private fun launchDeviceShieldOnboarding() {
        startActivityForResult(DeviceShieldOnboardingActivity.intent(this), REQUEST_DEVICE_SHIELD_ONBOARDING)
    }

    private fun startVpnIfAllowed() {
        when (val permissionStatus = checkVpnPermission()) {
            is VpnPermissionStatus.Granted -> startDeviceShield()
            is VpnPermissionStatus.Denied -> obtainVpnRequestPermission(permissionStatus.intent)
        }
    }

    private fun checkVpnPermission(): VpnPermissionStatus {
        val intent = VpnService.prepare(this)
        return if (intent == null) {
            VpnPermissionStatus.Granted
        } else {
            VpnPermissionStatus.Denied(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, RC_REQUEST_VPN_PERMISSION)
    }

    private fun startDeviceShield() {
        TrackerBlockingVpnService.startService(this)
    }

    private fun stopDeviceShield() {
        TrackerBlockingVpnService.stopService(this)
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        private const val RC_REQUEST_VPN_PERMISSION = 102
        private const val REQUEST_DEVICE_SHIELD_ONBOARDING = 103

        fun intent(context: Context): Intent {
            return Intent(context, BetaFeaturesActivity::class.java)
        }
    }

}
