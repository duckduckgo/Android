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
import android.provider.Settings
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ActivityVpnOnboardingBinding
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.CheckVPNPermission
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.LaunchVPN
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.RequestVPNPermission
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.ShowVpnAlwaysOnConflictDialog
import com.duckduckgo.mobile.android.vpn.ui.onboarding.Command.ShowVpnConflictDialog
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPVpnConflictDialog
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class VpnOnboardingActivity : DuckDuckGoActivity(), AppTPVpnConflictDialog.Listener {

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    private val viewModel: VpnOnboardingViewModel by bindViewModel()
    private val binding: ActivityVpnOnboardingBinding by viewBinding()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setStatusBarColor(getResources().getColor(com.duckduckgo.mobile.android.R.color.atp_onboardingHeaderBg))
        if (isDarkThemeEnabled()) {
            window.decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        }

        setContentView(binding.root)
        configureUI()
        observeViewModel()
    }

    private fun configureUI() {
        binding.onboardingPager.adapter = DeviceShieldOnboardingAdapter(viewModel.pages) {
            launchFAQ()
        }
        binding.onboardingPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                showOnboardingPage(position)
                super.onPageSelected(position)
            }
        })

        binding.onboardingClose.setOnClickListener {
            close()
        }
    }

    private fun observeViewModel() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun launchFAQ() {
        startActivity(DeviceShieldFAQActivity.intent(this))
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

    private fun showEnabledState() {
        showNextPage()
        launchKonfetti()
        binding.onboardingNextCta.setText(R.string.atp_EnabledActivityCTA)
        binding.onboardingNextCta.setOnClickListener {
            startActivity(DeviceShieldTrackerActivity.intent(it.context))
            finish()
        }
    }

    private fun launchKonfetti() {
        val magenta = ResourcesCompat.getColor(getResources(), com.duckduckgo.mobile.android.R.color.magenta, null)
        val blue = ResourcesCompat.getColor(getResources(), com.duckduckgo.mobile.android.R.color.accentBlue, null)
        val purple = ResourcesCompat.getColor(getResources(), com.duckduckgo.mobile.android.R.color.purple, null)
        val green = ResourcesCompat.getColor(getResources(), com.duckduckgo.mobile.android.R.color.green, null)
        val yellow = ResourcesCompat.getColor(getResources(), com.duckduckgo.mobile.android.R.color.yellow, null)

        val displayWidth = resources.displayMetrics.widthPixels

        binding.deviceShieldKonfetti.build()
            .addColors(magenta, blue, purple, green, yellow)
            .setDirection(0.0, 359.0)
            .setSpeed(1f, 5f)
            .setFadeOutEnabled(true)
            .setTimeToLive(2000L)
            .addShapes(Shape.Rectangle(1f))
            .addSizes(Size(8))
            .setPosition(displayWidth / 2f, displayWidth / 2f, -50f, -50f)
            .streamFor(50, 4000L)
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
        finish()
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
        showEnabledState()
        viewModel.onAppTpEnabled()
    }

    private fun launchVPNConflictDialog(isAlwaysOn: Boolean) {
        deviceShieldPixels.didShowVpnConflictDialog()
        val dialog = AppTPVpnConflictDialog.instance(this, isAlwaysOn)
        dialog.show(
            supportFragmentManager,
            AppTPVpnConflictDialog.TAG_VPN_CONFLICT_DIALOG
        )
    }

    override fun onVpnConflictDialogDismiss() {
        deviceShieldPixels.didChooseToDismissVpnConflictDialog()
    }

    override fun onVpnConflictDialogGoToSettings() {
        deviceShieldPixels.didChooseToOpenSettingsFromVpnConflictDialog()

        val intent = Intent(Settings.ACTION_VPN_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onVpnConflictDialogContinue() {
        deviceShieldPixels.didChooseToContinueFromVpnConflictDialog()
        checkVPNPermission()
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        private const val REQUEST_ASK_VPN_PERMISSION = 101

        fun intent(context: Context): Intent {
            return Intent(context, VpnOnboardingActivity::class.java)
        }
    }
}
