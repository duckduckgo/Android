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
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

class DeviceShieldOnboardingActivity : AppCompatActivity(R.layout.activity_device_shield_onboarding) {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private lateinit var viewPager: ViewPager2

    private lateinit var nextOnboardingPageCta: ImageButton
    private lateinit var enableDeviceShieldLayout: View
    private lateinit var onboardingFAQCta: Button
    private lateinit var onboardingClose: ImageButton
    private lateinit var enableDeviceShieldToggle: View

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }

    private val viewModel: DeviceShieldOnboardingViewModel by bindViewModel()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidInjection.inject(this)

        bindViews()
        configureUI()
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
            startVpnIfAllowed()
        }
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
            when (resultCode) {
                RESULT_OK -> {
                    startVpn()
                    return
                }
                else -> Timber.d("Permission not granted")
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startVpnIfAllowed() {
        when (val permissionStatus = checkVpnPermission()) {
            is VpnPermissionStatus.Granted -> startVpn()
            is VpnPermissionStatus.Denied -> obtainVpnRequestPermission(permissionStatus.intent)
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
        viewModel.onDeviceShieldEnabled()
        startActivity(DeviceShieldEnabledActivity.intent(this))
        finish()
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
