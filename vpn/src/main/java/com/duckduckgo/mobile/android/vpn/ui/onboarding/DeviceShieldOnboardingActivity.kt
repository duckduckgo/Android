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

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.CheckVPNPermission
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.LaunchVPN
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.RequestVPNPermission
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.ShowVpnAlwaysOnConflictDialog
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.ShowVpnConflictDialog
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPVpnConflictDialog
import dagger.android.AndroidInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class DeviceShieldOnboardingActivity : AppCompatActivity(R.layout.activity_device_shield_onboarding), AppTPVpnConflictDialog.Listener {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    private lateinit var viewPager: ViewPager2

    private lateinit var nextOnboardingPageCta: ImageButton
    private lateinit var enableDeviceShieldLayout: View
    private lateinit var onboardingFAQCta: Button
    private lateinit var onboardingClose: ImageButton
    private lateinit var enableDeviceShieldToggle: View

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }

    private val viewModel: DeviceShieldOnboardingViewModel by bindViewModel()

    private var timeElapsed: Long = -1
    private var startTime: Long = -1

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidInjection.inject(this)

        bindViews()
        configureUI()
        observeViewModel()
    }

    override fun onResume() {
        startTime = SystemClock.elapsedRealtime()
        super.onResume()
    }

    override fun onPause() {
        val onScreenTime = SystemClock.elapsedRealtime() - startTime
        timeElapsed += onScreenTime
        super.onPause()
    }

    override fun onDestroy() {
        deviceShieldPixels.didSpendTimeOnOnboardingActivity(timeElapsed)
        super.onDestroy()
    }

    private fun bindViews() {
        onboardingClose = findViewById(R.id.onboarding_close)
        enableDeviceShieldToggle = findViewById(R.id.onboarding_switch_layout)
        viewPager = findViewById(R.id.onboarding_pager)
        enableDeviceShieldLayout = findViewById(R.id.onboarding_cta_layout)
        onboardingFAQCta = findViewById(R.id.onboarding_faq_cta)
        nextOnboardingPageCta = findViewById(R.id.onboarding_next_cta)
    }

    private fun configureUI() {
        viewPager.adapter = DeviceShieldOnboardingAdapter(viewModel.pages)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                showOnboardingPage(position)
                super.onPageSelected(position)
            }
        })

        onboardingFAQCta.setOnClickListener {
            DeviceShieldFAQActivity.intent(this).also {
                startActivity(it)
            }
        }

        nextOnboardingPageCta.setOnClickListener {
            viewPager.currentItem = viewPager.currentItem + 1
        }

        onboardingClose.setOnClickListener {
            close()
        }

        enableDeviceShieldToggle.setOnClickListener {
            viewModel.onTurnAppTpOffOn()
        }
    }

    private fun observeViewModel() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
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
        }
    }

    private fun showEnableCTA() {
        nextOnboardingPageCta.isGone = true
        enableDeviceShieldLayout.isVisible = true
    }

    private fun showNextPageCTA() {
        nextOnboardingPageCta.isVisible = true
        enableDeviceShieldLayout.isGone = true
    }

    override fun onBackPressed() {
        // go back to previous screen or get out if first page
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        close()
        return true
    }

    private fun close() {
        viewModel.onClose()
        finish()
    }

    override fun onStart() {
        super.onStart()
        viewModel.onStart()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQUEST_ASK_VPN_PERMISSION) {
            viewModel.onVPNPermissionResult(resultCode)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is LaunchVPN -> startVpn()
            is ShowVpnConflictDialog -> launchVPNConflictDialog(false)
            is ShowVpnAlwaysOnConflictDialog -> launchVPNConflictDialog(true)
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

    private fun checkVpnPermission(): VpnPermissionStatus {
        val intent = VpnService.prepare(this)
        return if (intent == null) {
            VpnPermissionStatus.Granted
        } else {
            VpnPermissionStatus.Denied(intent)
        }
    }

    private fun startVpn() {
        TrackerBlockingVpnService.startService(this)
        viewModel.onAppTpEnabled()
        startActivity(DeviceShieldEnabledActivity.intent(this))
        finish()
    }

    private fun launchVPNConflictDialog(isAlwaysOn: Boolean) {
        deviceShieldPixels.didShowVpnConflictDialog()
        val dialog = AppTPVpnConflictDialog.instance(this, isAlwaysOn)
        dialog.show(
            supportFragmentManager,
            AppTPVpnConflictDialog.TAG_VPN_CONFLICT_DIALOG
        )
    }

    override fun onDismissConflictDialog() {
        deviceShieldPixels.didChooseToDismissVpnConflicDialog()
    }

    override fun onOpenSettings() {
        deviceShieldPixels.didChooseToOpenSettingsFromVpnConflicDialog()

        val intent = Intent(Settings.ACTION_VPN_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onContinue() {
        deviceShieldPixels.didChooseToContinueFromVpnConflicDialog()
        checkVPNPermission()
    }
    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        private const val REQUEST_ASK_VPN_PERMISSION = 101

        fun intent(context: Context): Intent {
            return Intent(context, DeviceShieldOnboardingActivity::class.java)
        }
    }

}
